// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.media.MediaCrypto;
import android.media.MediaDrm;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

/**
 * A wrapper of the android MediaDrm class. Each MediaDrmBridge manages multiple
 * sessions for a single MediaSourcePlayer.
 */
@JNINamespace("media")
class MediaDrmBridge {

    private static final String TAG = "MediaDrmBridge";
    private static final String SECURITY_LEVEL = "securityLevel";
    private static final String PRIVACY_MODE = "privacyMode";
    private MediaDrm mMediaDrm;
    private UUID mSchemeUUID;
    private int mNativeMediaDrmBridge;
    // TODO(qinmin): we currently only support one session per DRM bridge.
    // Change this to a HashMap if we start to support multiple sessions.
    private String mSessionId;
    private MediaCrypto mMediaCrypto;
    private String mMimeType;
    private Handler mHandler;
    private byte[] mPendingInitData;

    private static UUID getUUIDFromBytes(byte[] data) {
        if (data.length != 16) {
            return null;
        }
        long mostSigBits = 0;
        long leastSigBits = 0;
        for (int i = 0; i < 8; i++) {
            mostSigBits = (mostSigBits << 8) | (data[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            leastSigBits = (leastSigBits << 8) | (data[i] & 0xff);
        }
        return new UUID(mostSigBits, leastSigBits);
    }

    private MediaDrmBridge(UUID schemeUUID, String securityLevel, int nativeMediaDrmBridge)
            throws android.media.UnsupportedSchemeException {
        mSchemeUUID = schemeUUID;
        mMediaDrm = new MediaDrm(schemeUUID);
        mHandler = new Handler();
        mNativeMediaDrmBridge = nativeMediaDrmBridge;
        mMediaDrm.setOnEventListener(new MediaDrmListener());
        mMediaDrm.setPropertyString(PRIVACY_MODE, "enable");
        String currentSecurityLevel = mMediaDrm.getPropertyString(SECURITY_LEVEL);
        Log.e(TAG, "Security level: current " + currentSecurityLevel + ", new " + securityLevel);
        if (!securityLevel.equals(currentSecurityLevel))
            mMediaDrm.setPropertyString(SECURITY_LEVEL, securityLevel);
    }

    /**
     * Create a MediaCrypto object.
     *
     * @return if a MediaCrypto object is successfully created.
     */
    private boolean createMediaCrypto() {
        assert(mSessionId != null);
        assert(mMediaCrypto == null);
        try {
            final byte[] session = mSessionId.getBytes("UTF-8");
            if (MediaCrypto.isCryptoSchemeSupported(mSchemeUUID)) {
                mMediaCrypto = new MediaCrypto(mSchemeUUID, session);
            }
        } catch (android.media.MediaCryptoException e) {
            Log.e(TAG, "Cannot create MediaCrypto " + e.toString());
            return false;
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Cannot create MediaCrypto " + e.toString());
            return false;
        }

        assert(mMediaCrypto != null);
        nativeOnMediaCryptoReady(mNativeMediaDrmBridge);
        return true;
    }

    /**
     * Open a new session and return the sessionId.
     *
     * @return false if unexpected error happens. Return true if a new session
     * is successfully opened, or if provisioning is required to open a session.
     */
    private boolean openSession() {
        assert(mSessionId == null);

        if (mMediaDrm == null) {
            return false;
        }

        try {
            final byte[] sessionId = mMediaDrm.openSession();
            mSessionId = new String(sessionId, "UTF-8");
        } catch (android.media.NotProvisionedException e) {
            Log.e(TAG, "Cannot open a new session: " + e.toString());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Cannot open a new session: " + e.toString());
            return false;
        }

        assert(mSessionId != null);
        return createMediaCrypto();
    }

    /**
     * Check whether the crypto scheme is supported for the given container.
     * If |containerMimeType| is an empty string, we just return whether
     * the crypto scheme is supported.
     * TODO(qinmin): Implement the checking for container.
     *
     * @return true if the container and the crypto scheme is supported, or
     * false otherwise.
     */
    @CalledByNative
    private static boolean isCryptoSchemeSupported(byte[] schemeUUID, String containerMimeType) {
        UUID cryptoScheme = getUUIDFromBytes(schemeUUID);
        return MediaDrm.isCryptoSchemeSupported(cryptoScheme);
    }

    /**
     * Create a new MediaDrmBridge from the crypto scheme UUID.
     *
     * @param schemeUUID Crypto scheme UUID.
     * @param securityLevel Security level to be used.
     * @param nativeMediaDrmBridge Native object of this class.
     */
    @CalledByNative
    private static MediaDrmBridge create(
            byte[] schemeUUID, String securityLevel, int nativeMediaDrmBridge) {
        UUID cryptoScheme = getUUIDFromBytes(schemeUUID);
        if (cryptoScheme == null || !MediaDrm.isCryptoSchemeSupported(cryptoScheme)) {
            return null;
        }

        MediaDrmBridge media_drm_bridge = null;
        try {
            media_drm_bridge = new MediaDrmBridge(
                    cryptoScheme, securityLevel, nativeMediaDrmBridge);
        } catch (android.media.UnsupportedSchemeException e) {
            Log.e(TAG, "Unsupported DRM scheme: " + e.toString());
        } catch (java.lang.IllegalArgumentException e) {
            Log.e(TAG, "Failed to create MediaDrmBridge: " + e.toString());
        } catch (java.lang.IllegalStateException e) {
            Log.e(TAG, "Failed to create MediaDrmBridge: " + e.toString());
        }

        return media_drm_bridge;
    }

    /**
     * Return the MediaCrypto object if available.
     */
    @CalledByNative
    private MediaCrypto getMediaCrypto() {
        return mMediaCrypto;
    }

    /**
     * Release the MediaDrmBridge object.
     */
    @CalledByNative
    private void release() {
        if (mMediaCrypto != null) {
            mMediaCrypto.release();
            mMediaCrypto = null;
        }
        if (mSessionId != null) {
            try {
                final byte[] session = mSessionId.getBytes("UTF-8");
                mMediaDrm.closeSession(session);
            } catch (java.io.UnsupportedEncodingException e) {
                Log.e(TAG, "Failed to close session: " + e.toString());
            }
            mSessionId = null;
        }
        if (mMediaDrm != null) {
            mMediaDrm.release();
            mMediaDrm = null;
        }
    }

    /**
     * Generate a key request and post an asynchronous task to the native side
     * with the response message.
     *
     * @param initData Data needed to generate the key request.
     * @param mime Mime type.
     */
    @CalledByNative
    private void generateKeyRequest(byte[] initData, String mime) {
        Log.d(TAG, "generateKeyRequest().");

        if (mMimeType == null) {
            mMimeType = mime;
        } else if (!mMimeType.equals(mime)) {
            onKeyError();
            return;
        }

        if (mSessionId == null) {
            if (!openSession()) {
                onKeyError();
                return;
            }

            // NotProvisionedException happened during openSession().
            if (mSessionId == null) {
                if (mPendingInitData != null) {
                    Log.e(TAG, "generateKeyRequest called when another call is pending.");
                    onKeyError();
                    return;
                }

                // We assume MediaDrm.EVENT_PROVISION_REQUIRED is always fired if
                // NotProvisionedException is throwed in openSession().
                // generateKeyRequest() will be resumed after provisioning is finished.
                // TODO(xhwang): Double check if this assumption is true. Otherwise we need
                // to handle the exception in openSession more carefully.
                mPendingInitData = initData;
                return;
            }
        }

        try {
            final byte[] session = mSessionId.getBytes("UTF-8");
            HashMap<String, String> optionalParameters = new HashMap<String, String>();
            final MediaDrm.KeyRequest request = mMediaDrm.getKeyRequest(
                    session, initData, mime, MediaDrm.KEY_TYPE_STREAMING, optionalParameters);
            mHandler.post(new Runnable(){
                public void run() {
                    nativeOnKeyMessage(mNativeMediaDrmBridge, mSessionId,
                            request.getData(), request.getDefaultUrl());
                }
            });
            return;
        } catch (android.media.NotProvisionedException e) {
            // MediaDrm.EVENT_PROVISION_REQUIRED is also fired in this case.
            // Provisioning is handled in the handler of that event.
            Log.e(TAG, "Cannot get key request: " + e.toString());
            return;
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Cannot get key request: " + e.toString());
        }
        onKeyError();
    }

    /**
     * Cancel a key request for a session Id.
     *
     * @param sessionId Crypto session Id.
     */
    @CalledByNative
    private void cancelKeyRequest(String sessionId) {
        if (mSessionId == null || !mSessionId.equals(sessionId)) {
            return;
        }
        try {
            final byte[] session = sessionId.getBytes("UTF-8");
            mMediaDrm.removeKeys(session);
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Cannot cancel key request: " + e.toString());
        }
    }

    /**
     * Add a key for a session Id.
     *
     * @param sessionId Crypto session Id.
     * @param key Response data from the server.
     */
    @CalledByNative
    private void addKey(String sessionId, byte[] key) {
        if (mSessionId == null || !mSessionId.equals(sessionId)) {
            return;
        }
        try {
            final byte[] session = sessionId.getBytes("UTF-8");
            try {
                mMediaDrm.provideKeyResponse(session, key);
            } catch (java.lang.IllegalStateException e) {
                // This is not really an exception. Some error code are incorrectly
                // reported as an exception.
                // TODO(qinmin): remove this exception catch when b/10495563 is fixed.
                Log.e(TAG, "Exception intentionally caught when calling provideKeyResponse() "
                        + e.toString());
            }
            mHandler.post(new Runnable() {
                public void run() {
                    nativeOnKeyAdded(mNativeMediaDrmBridge, mSessionId);
                }
            });
            return;
        } catch (android.media.NotProvisionedException e) {
            Log.e(TAG, "failed to provide key response: " + e.toString());
        } catch (android.media.DeniedByServerException e) {
            Log.e(TAG, "failed to provide key response: " + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "failed to provide key response: " + e.toString());
        }
        onKeyError();
    }

    /**
     * Return the security level of this DRM object.
     */
    @CalledByNative
    private String getSecurityLevel() {
        return mMediaDrm.getPropertyString("securityLevel");
    }

    /**
     * Called when the provision response is received.
     *
     * @param response Response data from the provision server.
     */
    private void onProvisionResponse(byte[] response) {
        Log.d(TAG, "onProvisionResponse()");

        if (response == null || response.length == 0) {
            Log.e(TAG, "Invalid provision response.");
            onKeyError();
            return;
        }

        try {
            mMediaDrm.provideProvisionResponse(response);
        } catch (android.media.DeniedByServerException e) {
            Log.e(TAG, "failed to provide provision response: " + e.toString());
            onKeyError();
            return;
        } catch (java.lang.IllegalStateException e) {
            Log.e(TAG, "failed to provide provision response: " + e.toString());
            onKeyError();
            return;
        }

        if (mPendingInitData != null) {
            byte[] initData = mPendingInitData;
            mPendingInitData = null;
            generateKeyRequest(initData, mMimeType);
        }
    }

    private void onKeyError() {
        // TODO(qinmin): pass the error code to native.
        mHandler.post(new Runnable() {
            public void run() {
                nativeOnKeyError(mNativeMediaDrmBridge, mSessionId);
            }
        });
    }

    private class MediaDrmListener implements MediaDrm.OnEventListener {
        @Override
        public void onEvent(MediaDrm mediaDrm, byte[] sessionId, int event, int extra,
                byte[] data) {
            switch(event) {
                case MediaDrm.EVENT_PROVISION_REQUIRED:
                    Log.d(TAG, "MediaDrm.EVENT_PROVISION_REQUIRED.");
                    MediaDrm.ProvisionRequest request = mMediaDrm.getProvisionRequest();
                    PostRequestTask postTask = new PostRequestTask(request.getData());
                    postTask.execute(request.getDefaultUrl());
                    break;
                case MediaDrm.EVENT_KEY_REQUIRED:
                    generateKeyRequest(data, mMimeType);
                    break;
                case MediaDrm.EVENT_KEY_EXPIRED:
                    onKeyError();
                    break;
                case MediaDrm.EVENT_VENDOR_DEFINED:
                    assert(false);
                    break;
                default:
                    Log.e(TAG, "Invalid DRM event " + (int)event);
                    return;
            }
        }
    }

    private class PostRequestTask extends AsyncTask<String, Void, Void> {
        private static final String TAG = "PostRequestTask";

        private byte[] mDrmRequest;
        private byte[] mResponseBody;

        public PostRequestTask(byte[] drmRequest) {
            mDrmRequest = drmRequest;
        }

        @Override
        protected Void doInBackground(String... urls) {
            mResponseBody = postRequest(urls[0], mDrmRequest);
            if (mResponseBody != null) {
                Log.d(TAG, "response length=" + mResponseBody.length);
            }
            return null;
        }

        private byte[] postRequest(String url, byte[] drmRequest) {
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(url + "&signedRequest=" + new String(drmRequest));

            Log.d(TAG, "PostRequest:" + httpPost.getRequestLine());
            try {
                // Add data
                httpPost.setHeader("Accept", "*/*");
                httpPost.setHeader("User-Agent", "Widevine CDM v1.0");
                httpPost.setHeader("Content-Type", "application/json");

                // Execute HTTP Post Request
                HttpResponse response = httpClient.execute(httpPost);

                byte[] responseBody;
                int responseCode = response.getStatusLine().getStatusCode();
                if (responseCode == 200) {
                    responseBody = EntityUtils.toByteArray(response.getEntity());
                } else {
                    Log.d(TAG, "Server returned HTTP error code " + responseCode);
                    return null;
                }
                return responseBody;
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            onProvisionResponse(mResponseBody);
        }
    }

    private native void nativeOnMediaCryptoReady(int nativeMediaDrmBridge);

    private native void nativeOnKeyMessage(int nativeMediaDrmBridge, String sessionId,
                                           byte[] message, String destinationUrl);

    private native void nativeOnKeyAdded(int nativeMediaDrmBridge, String sessionId);

    private native void nativeOnKeyError(int nativeMediaDrmBridge, String sessionId);
}

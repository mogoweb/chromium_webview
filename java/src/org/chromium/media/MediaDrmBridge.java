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
    private MediaDrm mMediaDrm;
    private UUID mSchemeUUID;
    private int mNativeMediaDrmBridge;
    // TODO(qinmin): we currently only support one session per DRM bridge.
    // Change this to a HashMap if we start to support multiple sessions.
    private String mSessionId;
    private MediaCrypto mMediaCrypto;
    private String mMimeType;
    private Handler mhandler;

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

    private MediaDrmBridge(UUID schemeUUID, int nativeMediaDrmBridge) {
        try {
            mSchemeUUID = schemeUUID;
            mMediaDrm = new MediaDrm(schemeUUID);
            mNativeMediaDrmBridge = nativeMediaDrmBridge;
            mMediaDrm.setOnEventListener(new MediaDrmListener());
            mSessionId = openSession();
            mhandler = new Handler();
        } catch (android.media.UnsupportedSchemeException e) {
            Log.e(TAG, "Unsupported DRM scheme " + e.toString());
        }
    }

    /**
     * Open a new session and return the sessionId.
     *
     * @return ID of the session.
     */
    private String openSession() {
        String session = null;
        try {
            final byte[] sessionId = mMediaDrm.openSession();
            session = new String(sessionId, "UTF-8");
        } catch (android.media.NotProvisionedException e) {
            Log.e(TAG, "Cannot open a new session " + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Cannot open a new session " + e.toString());
        }
        return session;
    }

    /**
     * Create a new MediaDrmBridge from the crypto scheme UUID.
     *
     * @param schemeUUID Crypto scheme UUID.
     * @param nativeMediaDrmBridge Native object of this class.
     */
    @CalledByNative
    private static MediaDrmBridge create(byte[] schemeUUID, int nativeMediaDrmBridge) {
        UUID cryptoScheme = getUUIDFromBytes(schemeUUID);
        if (cryptoScheme != null && MediaDrm.isCryptoSchemeSupported(cryptoScheme)) {
            return new MediaDrmBridge(cryptoScheme, nativeMediaDrmBridge);
        }
        return null;
    }

    /**
     * Create a new MediaCrypto object from the session Id.
     *
     * @param sessionId Crypto session Id.
     */
    @CalledByNative
    private MediaCrypto getMediaCrypto() {
        if (mMediaCrypto != null) {
            return mMediaCrypto;
        }
        try {
            final byte[] session = mSessionId.getBytes("UTF-8");
            if (MediaCrypto.isCryptoSchemeSupported(mSchemeUUID)) {
                mMediaCrypto = new MediaCrypto(mSchemeUUID, session);
            }
        } catch (android.media.MediaCryptoException e) {
            Log.e(TAG, "Cannot create MediaCrypto " + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Cannot create MediaCrypto " + e.toString());
        }
        return mMediaCrypto;
    }

    /**
     * Release the MediaDrmBridge object.
     */
    @CalledByNative
    private void release() {
        if (mMediaCrypto != null) {
            mMediaCrypto.release();
        }
        if (mSessionId != null) {
            try {
                final byte[] session = mSessionId.getBytes("UTF-8");
                mMediaDrm.closeSession(session);
            } catch (java.io.UnsupportedEncodingException e) {
                Log.e(TAG, "Failed to close session " + e.toString());
            }
        }
        mMediaDrm.release();
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
        if (mSessionId == null) {
            return;
        }
        try {
            final byte[] session = mSessionId.getBytes("UTF-8");
            mMimeType = mime;
            HashMap<String, String> optionalParameters = new HashMap<String, String>();
            final MediaDrm.KeyRequest request = mMediaDrm.getKeyRequest(
                    session, initData, mime, MediaDrm.KEY_TYPE_STREAMING, optionalParameters);
            mhandler.post(new Runnable(){
                public void run() {
                    nativeOnKeyMessage(mNativeMediaDrmBridge, mSessionId,
                            request.getData(), request.getDefaultUrl());
                }
            });
            return;
        } catch (android.media.NotProvisionedException e) {
            Log.e(TAG, "Cannot get key request " + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Cannot get key request " + e.toString());
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
            Log.e(TAG, "Cannot cancel key request " + e.toString());
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
            mMediaDrm.provideKeyResponse(session, key);
            mhandler.post(new Runnable() {
                public void run() {
                    nativeOnKeyAdded(mNativeMediaDrmBridge, mSessionId);
                }
            });
            return;
        } catch (android.media.NotProvisionedException e) {
            Log.e(TAG, "failed to provide key response " + e.toString());
        } catch (android.media.DeniedByServerException e) {
            Log.e(TAG, "failed to provide key response " + e.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "failed to provide key response " + e.toString());
        }
        onKeyError();
    }

    /**
     * Called when the provision response is received.
     *
     * @param response Response data from the provision server.
     */
    private void onProvisionResponse(byte[] response) {
        try {
            mMediaDrm.provideProvisionResponse(response);
        } catch (android.media.DeniedByServerException e) {
            Log.e(TAG, "failed to provide key response " + e.toString());
        }
    }

    private void onKeyError() {
        // TODO(qinmin): pass the error code to native.
        mhandler.post(new Runnable() {
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

    private native void nativeOnKeyMessage(int nativeMediaDrmBridge, String sessionId,
                                           byte[] message, String destinationUrl);

    private native void nativeOnKeyAdded(int nativeMediaDrmBridge, String sessionId);

    private native void nativeOnKeyError(int nativeMediaDrmBridge, String sessionId);
}

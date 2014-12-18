// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.media.MediaCrypto;
import android.media.MediaDrm;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.UUID;

/**
 * A wrapper of the android MediaDrm class. Each MediaDrmBridge manages multiple
 * sessions for a single MediaSourcePlayer.
 */
@JNINamespace("media")
public class MediaDrmBridge {
    // Implementation Notes:
    // - A media crypto session (mMediaCryptoSession) is opened after MediaDrm
    //   is created. This session will be added to mSessionIds.
    //   a) In multiple session mode, this session will only be used to create
    //      the MediaCrypto object. It's associated mime type is always null and
    //      it's session ID is always INVALID_SESSION_ID.
    //   b) In single session mode, this session will be used to create the
    //      MediaCrypto object and will be used to call getKeyRequest() and
    //      manage all keys.  The session ID will always be the lastest session
    //      ID passed by the caller.
    // - Each createSession() call creates a new session. All sessions are
    //   managed in mSessionIds.
    // - Whenever NotProvisionedException is thrown, we will clean up the
    //   current state and start the provisioning process.
    // - When provisioning is finished, we will try to resume suspended
    //   operations:
    //   a) Create the media crypto session if it's not created.
    //   b) Finish createSession() if previous createSession() was interrupted
    //      by a NotProvisionedException.
    // - Whenever an unexpected error occurred, we'll call release() to release
    //   all resources and clear all states. In that case all calls to this
    //   object will be no-op. All public APIs and callbacks should check
    //   mMediaBridge to make sure release() hasn't been called. Also, we call
    //   release() immediately after the error happens (e.g. after mMediaDrm)
    //   calls. Indirect calls should not call release() again to avoid
    //   duplication (even though it doesn't hurt to call release() twice).

    private static final String TAG = "MediaDrmBridge";
    private static final String SECURITY_LEVEL = "securityLevel";
    private static final String PRIVACY_MODE = "privacyMode";
    private static final String SESSION_SHARING = "sessionSharing";
    private static final String ENABLE = "enable";
    private static final int INVALID_SESSION_ID = 0;

    private MediaDrm mMediaDrm;
    private long mNativeMediaDrmBridge;
    private UUID mSchemeUUID;
    private Handler mHandler;

    // In this mode, we only open one session, i.e. mMediaCryptoSession.
    private boolean mSingleSessionMode;

    // A session only for the purpose of creating a MediaCrypto object.
    // This session is opened when createSession() is called for the first
    // time.
    // - In multiple session mode, all following createSession() calls
    // should create a new session and use it to call getKeyRequest(). No
    // getKeyRequest() should ever be called on this media crypto session.
    // - In single session mode, all createSession() calls use the same
    // media crypto session. When createSession() is called with a new
    // initData, previously added keys may not be available anymore.
    private ByteBuffer mMediaCryptoSession;
    private MediaCrypto mMediaCrypto;

    // The map of all opened sessions to their session reference IDs.
    private HashMap<ByteBuffer, Integer> mSessionIds;
    // The map of all opened sessions to their mime types.
    private HashMap<ByteBuffer, String> mSessionMimeTypes;

    // The queue of all pending createSession() data.
    private ArrayDeque<PendingCreateSessionData> mPendingCreateSessionDataQueue;

    private boolean mResetDeviceCredentialsPending;

    // MediaDrmBridge is waiting for provisioning response from the server.
    //
    // Notes about NotProvisionedException: This exception can be thrown in a
    // lot of cases. To streamline implementation, we do not catch it in private
    // non-native methods and only catch it in public APIs.
    private boolean mProvisioningPending;

    /**
     *  This class contains data needed to call createSession().
     */
    private static class PendingCreateSessionData {
        private final int mSessionId;
        private final byte[] mInitData;
        private final String mMimeType;

        private PendingCreateSessionData(int sessionId, byte[] initData, String mimeType) {
            mSessionId = sessionId;
            mInitData = initData;
            mMimeType = mimeType;
        }

        private int sessionId() { return mSessionId; }
        private byte[] initData() { return mInitData; }
        private String mimeType() { return mMimeType; }
    }

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

    /**
     *  Gets session associated with the sessionId.
     *
     *  @return session if sessionId maps a valid opened session. Returns null
     *  otherwise.
     */
    private ByteBuffer getSession(int sessionId) {
        for (ByteBuffer session : mSessionIds.keySet()) {
            if (mSessionIds.get(session) == sessionId) {
                return session;
            }
        }
        return null;
    }

    private MediaDrmBridge(UUID schemeUUID, long nativeMediaDrmBridge, boolean singleSessionMode)
            throws android.media.UnsupportedSchemeException {
        mSchemeUUID = schemeUUID;
        mMediaDrm = new MediaDrm(schemeUUID);
        mNativeMediaDrmBridge = nativeMediaDrmBridge;
        mHandler = new Handler();
        mSingleSessionMode = singleSessionMode;
        mSessionIds = new HashMap<ByteBuffer, Integer>();
        mSessionMimeTypes = new HashMap<ByteBuffer, String>();
        mPendingCreateSessionDataQueue = new ArrayDeque<PendingCreateSessionData>();
        mResetDeviceCredentialsPending = false;
        mProvisioningPending = false;

        mMediaDrm.setOnEventListener(new MediaDrmListener());
        mMediaDrm.setPropertyString(PRIVACY_MODE, ENABLE);
        if (!mSingleSessionMode) {
            mMediaDrm.setPropertyString(SESSION_SHARING, ENABLE);
        }

        // We could open a MediaCrypto session here to support faster start of
        // clear lead (no need to wait for createSession()). But on
        // Android, memory and battery resources are precious and we should
        // only create a session when we are sure we'll use it.
        // TODO(xhwang): Investigate other options to support fast start.
    }

    /**
     * Create a MediaCrypto object.
     *
     * @return whether a MediaCrypto object is successfully created.
     */
    private boolean createMediaCrypto() throws android.media.NotProvisionedException {
        if (mMediaDrm == null) {
            return false;
        }
        assert !mProvisioningPending;
        assert mMediaCryptoSession == null;
        assert mMediaCrypto == null;

        // Open media crypto session.
        mMediaCryptoSession = openSession();
        if (mMediaCryptoSession == null) {
            Log.e(TAG, "Cannot create MediaCrypto Session.");
            return false;
        }
        Log.d(TAG, "MediaCrypto Session created: " + mMediaCryptoSession);

        // Create MediaCrypto object.
        try {
            if (MediaCrypto.isCryptoSchemeSupported(mSchemeUUID)) {
                final byte[] mediaCryptoSession = mMediaCryptoSession.array();
                mMediaCrypto = new MediaCrypto(mSchemeUUID, mediaCryptoSession);
                assert mMediaCrypto != null;
                Log.d(TAG, "MediaCrypto successfully created!");
                mSessionIds.put(mMediaCryptoSession, INVALID_SESSION_ID);
                // Notify the native code that MediaCrypto is ready.
                nativeOnMediaCryptoReady(mNativeMediaDrmBridge);
                return true;
            } else {
                Log.e(TAG, "Cannot create MediaCrypto for unsupported scheme.");
            }
        } catch (android.media.MediaCryptoException e) {
            Log.e(TAG, "Cannot create MediaCrypto", e);
        }

        release();
        return false;
    }

    /**
     * Open a new session..
     *
     * @return the session opened. Returns null if unexpected error happened.
     */
    private ByteBuffer openSession() throws android.media.NotProvisionedException {
        assert mMediaDrm != null;
        try {
            byte[] session = mMediaDrm.openSession();
            // ByteBuffer.wrap() is backed by the byte[]. Make a clone here in
            // case the underlying byte[] is modified.
            return ByteBuffer.wrap(session.clone());
        } catch (java.lang.RuntimeException e) {  // TODO(xhwang): Drop this?
            Log.e(TAG, "Cannot open a new session", e);
            release();
            return null;
        } catch (android.media.NotProvisionedException e) {
            // Throw NotProvisionedException so that we can startProvisioning().
            throw e;
        } catch (android.media.MediaDrmException e) {
            // Other MediaDrmExceptions (e.g. ResourceBusyException) are not
            // recoverable.
            Log.e(TAG, "Cannot open a new session", e);
            release();
            return null;
        }
    }

    /**
     * Close a session.
     *
     * @param session to be closed.
     */
    private void closeSession(ByteBuffer session) {
        assert mMediaDrm != null;
        mMediaDrm.closeSession(session.array());
    }

    /**
     * Check whether the crypto scheme is supported for the given container.
     * If |containerMimeType| is an empty string, we just return whether
     * the crypto scheme is supported.
     *
     * @return true if the container and the crypto scheme is supported, or
     * false otherwise.
     */
    @CalledByNative
    private static boolean isCryptoSchemeSupported(byte[] schemeUUID, String containerMimeType) {
        UUID cryptoScheme = getUUIDFromBytes(schemeUUID);

        if (containerMimeType.isEmpty()) {
            return MediaDrm.isCryptoSchemeSupported(cryptoScheme);
        }

        return MediaDrm.isCryptoSchemeSupported(cryptoScheme, containerMimeType);
    }

    /**
     * Create a new MediaDrmBridge from the crypto scheme UUID.
     *
     * @param schemeUUID Crypto scheme UUID.
     * @param securityLevel Security level to be used.
     * @param nativeMediaDrmBridge Native object of this class.
     */
    @CalledByNative
    private static MediaDrmBridge create(byte[] schemeUUID, long nativeMediaDrmBridge) {
        UUID cryptoScheme = getUUIDFromBytes(schemeUUID);
        if (cryptoScheme == null || !MediaDrm.isCryptoSchemeSupported(cryptoScheme)) {
            return null;
        }

        boolean singleSessionMode = false;
        if (Build.VERSION.RELEASE.equals("4.4")) {
            singleSessionMode = true;
        }
        Log.d(TAG, "MediaDrmBridge uses " +
                (singleSessionMode ? "single" : "multiple") + "-session mode.");

        MediaDrmBridge mediaDrmBridge = null;
        try {
            mediaDrmBridge = new MediaDrmBridge(
                cryptoScheme, nativeMediaDrmBridge, singleSessionMode);
            Log.d(TAG, "MediaDrmBridge successfully created.");
        } catch (android.media.UnsupportedSchemeException e) {
            Log.e(TAG, "Unsupported DRM scheme", e);
        } catch (java.lang.IllegalArgumentException e) {
            Log.e(TAG, "Failed to create MediaDrmBridge", e);
        } catch (java.lang.IllegalStateException e) {
            Log.e(TAG, "Failed to create MediaDrmBridge", e);
        }

        return mediaDrmBridge;
    }

    /**
     * Set the security level that the MediaDrm object uses.
     * This function should be called right after we construct MediaDrmBridge
     * and before we make any other calls.
     */
    @CalledByNative
    private boolean setSecurityLevel(String securityLevel) {
        if (mMediaDrm == null || mMediaCrypto != null) {
            return false;
        }

        String currentSecurityLevel = mMediaDrm.getPropertyString(SECURITY_LEVEL);
        Log.e(TAG, "Security level: current " + currentSecurityLevel + ", new " + securityLevel);
        if (securityLevel.equals(currentSecurityLevel)) {
            // No need to set the same security level again. This is not just
            // a shortcut! Setting the same security level actually causes an
            // exception in MediaDrm!
            return true;
        }

        try {
            mMediaDrm.setPropertyString(SECURITY_LEVEL, securityLevel);
            return true;
        } catch (java.lang.IllegalArgumentException e) {
            Log.e(TAG, "Failed to set security level " + securityLevel, e);
        } catch (java.lang.IllegalStateException e) {
            Log.e(TAG, "Failed to set security level " + securityLevel, e);
        }

        Log.e(TAG, "Security level " + securityLevel + " not supported!");
        return false;
    }

    /**
     * Return the MediaCrypto object if available.
     */
    @CalledByNative
    private MediaCrypto getMediaCrypto() {
        return mMediaCrypto;
    }

    /**
     * Reset the device DRM credentials.
     */
    @CalledByNative
    private void resetDeviceCredentials() {
        mResetDeviceCredentialsPending = true;
        MediaDrm.ProvisionRequest request = mMediaDrm.getProvisionRequest();
        PostRequestTask postTask = new PostRequestTask(request.getData());
        postTask.execute(request.getDefaultUrl());
    }

    /**
     * Release the MediaDrmBridge object.
     */
    @CalledByNative
    private void release() {
        // Do not reset mHandler and mNativeMediaDrmBridge so that we can still
        // post KeyError back to native code.

        mPendingCreateSessionDataQueue.clear();
        mPendingCreateSessionDataQueue = null;

        for (ByteBuffer session : mSessionIds.keySet()) {
            closeSession(session);
        }
        mSessionIds.clear();
        mSessionIds = null;
        mSessionMimeTypes.clear();
        mSessionMimeTypes = null;

        // This session was closed in the "for" loop above.
        mMediaCryptoSession = null;

        if (mMediaCrypto != null) {
            mMediaCrypto.release();
            mMediaCrypto = null;
        }

        if (mMediaDrm != null) {
            mMediaDrm.release();
            mMediaDrm = null;
        }
    }

    /**
     * Get a key request.
     *
     * @param session Session on which we need to get the key request.
     * @param data Data needed to get the key request.
     * @param mime Mime type to get the key request.
     *
     * @return the key request.
     */
    private MediaDrm.KeyRequest getKeyRequest(ByteBuffer session, byte[] data, String mime)
            throws android.media.NotProvisionedException {
        assert mMediaDrm != null;
        assert mMediaCrypto != null;
        assert !mProvisioningPending;

        HashMap<String, String> optionalParameters = new HashMap<String, String>();
        MediaDrm.KeyRequest request = mMediaDrm.getKeyRequest(
                session.array(), data, mime, MediaDrm.KEY_TYPE_STREAMING, optionalParameters);
        String result = (request != null) ? "successed" : "failed";
        Log.d(TAG, "getKeyRequest " + result + "!");
        return request;
    }

    /**
     * Save data to |mPendingCreateSessionDataQueue| so that we can resume the
     * createSession() call later.
     */
    private void savePendingCreateSessionData(int sessionId, byte[] initData, String mime) {
        Log.d(TAG, "savePendingCreateSessionData()");
        mPendingCreateSessionDataQueue.offer(
                new PendingCreateSessionData(sessionId, initData, mime));
    }

    /**
     * Process all pending createSession() calls synchronously.
     */
    private void processPendingCreateSessionData() {
        Log.d(TAG, "processPendingCreateSessionData()");
        assert mMediaDrm != null;

        // Check mMediaDrm != null because error may happen in createSession().
        // Check !mProvisioningPending because NotProvisionedException may be
        // thrown in createSession().
        while (mMediaDrm != null && !mProvisioningPending &&
                !mPendingCreateSessionDataQueue.isEmpty()) {
            PendingCreateSessionData pendingData = mPendingCreateSessionDataQueue.poll();
            int sessionId = pendingData.sessionId();
            byte[] initData = pendingData.initData();
            String mime = pendingData.mimeType();
            createSession(sessionId, initData, mime);
        }
    }

    /**
     * Process pending operations asynchrnously.
     */
    private void resumePendingOperations() {
        mHandler.post(new Runnable(){
            @Override
            public void run() {
                processPendingCreateSessionData();
            }
        });
    }

    /**
     * Create a session with |sessionId|, |initData| and |mime|.
     * In multiple session mode, a new session will be open. In single session
     * mode, the mMediaCryptoSession will be used.
     *
     * @param sessionId ID for the session to be created.
     * @param initData Data needed to generate the key request.
     * @param mime Mime type.
     */
    @CalledByNative
    private void createSession(int sessionId, byte[] initData, String mime) {
        Log.d(TAG, "createSession()");
        if (mMediaDrm == null) {
            Log.e(TAG, "createSession() called when MediaDrm is null.");
            return;
        }

        if (mProvisioningPending) {
            assert mMediaCrypto == null;
            savePendingCreateSessionData(sessionId, initData, mime);
            return;
        }

        boolean newSessionOpened = false;
        ByteBuffer session = null;
        try {
            // Create MediaCrypto if necessary.
            if (mMediaCrypto == null && !createMediaCrypto()) {
              onSessionError(sessionId);
                return;
            }
            assert mMediaCrypto != null;
            assert mSessionIds.containsKey(mMediaCryptoSession);

            if (mSingleSessionMode) {
                session = mMediaCryptoSession;
                if (mSessionMimeTypes.get(session) != null &&
                        !mSessionMimeTypes.get(session).equals(mime)) {
                    Log.e(TAG, "Only one mime type is supported in single session mode.");
                    onSessionError(sessionId);
                    return;
                }
            } else {
                session = openSession();
                if (session == null) {
                    Log.e(TAG, "Cannot open session in createSession().");
                    onSessionError(sessionId);
                    return;
                }
                newSessionOpened = true;
                assert !mSessionIds.containsKey(session);
            }

            MediaDrm.KeyRequest request = null;
            request = getKeyRequest(session, initData, mime);
            if (request == null) {
                if (newSessionOpened) {
                    closeSession(session);
                }
                onSessionError(sessionId);
                return;
            }

            onSessionCreated(sessionId, getWebSessionId(session));
            onSessionMessage(sessionId, request);
            if (newSessionOpened) {
                Log.d(TAG, "createSession(): Session " + getWebSessionId(session) +
                        " (" + sessionId + ") created.");
            }

            mSessionIds.put(session, sessionId);
            mSessionMimeTypes.put(session, mime);
        } catch (android.media.NotProvisionedException e) {
            Log.e(TAG, "Device not provisioned", e);
            if (newSessionOpened) {
                closeSession(session);
            }
            savePendingCreateSessionData(sessionId, initData, mime);
            startProvisioning();
        }
    }

    /**
     * Returns whether |sessionId| is a valid key session, excluding the media
     * crypto session in multi-session mode.
     *
     * @param sessionId Crypto session Id.
     */
    private boolean sessionExists(ByteBuffer session) {
        if (mMediaCryptoSession == null) {
            assert mSessionIds.isEmpty();
            Log.e(TAG, "Session doesn't exist because media crypto session is not created.");
            return false;
        }
        assert mSessionIds.containsKey(mMediaCryptoSession);

        if (mSingleSessionMode) {
            return mMediaCryptoSession.equals(session);
        }

        return !session.equals(mMediaCryptoSession) && mSessionIds.containsKey(session);
    }

    /**
     * Cancel a key request for a session Id.
     *
     * @param sessionId Reference ID of session to be released.
     */
    @CalledByNative
    private void releaseSession(int sessionId) {
        Log.d(TAG, "releaseSession(): " + sessionId);
        if (mMediaDrm == null) {
            Log.e(TAG, "releaseSession() called when MediaDrm is null.");
            return;
        }

        ByteBuffer session = getSession(sessionId);
        if (session == null) {
            Log.e(TAG, "Invalid sessionId in releaseSession.");
            onSessionError(sessionId);
            return;
        }

        mMediaDrm.removeKeys(session.array());

        // We don't close the media crypto session in single session mode.
        if (!mSingleSessionMode) {
            Log.d(TAG, "Session " + sessionId + "closed.");
            closeSession(session);
            mSessionIds.remove(session);
            onSessionClosed(sessionId);
        }
    }

    /**
     * Add a key for a session Id.
     *
     * @param sessionId Reference ID of session to be updated.
     * @param key Response data from the server.
     */
    @CalledByNative
    private void updateSession(int sessionId, byte[] key) {
        Log.d(TAG, "updateSession(): " + sessionId);
        if (mMediaDrm == null) {
            Log.e(TAG, "updateSession() called when MediaDrm is null.");
            return;
        }

        // TODO(xhwang): We should be able to DCHECK this when WD EME is implemented.
        ByteBuffer session = getSession(sessionId);
        if (!sessionExists(session)) {
            Log.e(TAG, "Invalid session in updateSession.");
            onSessionError(sessionId);
            return;
        }

        try {
            try {
                mMediaDrm.provideKeyResponse(session.array(), key);
            } catch (java.lang.IllegalStateException e) {
                // This is not really an exception. Some error code are incorrectly
                // reported as an exception.
                // TODO(qinmin): remove this exception catch when b/10495563 is fixed.
                Log.e(TAG, "Exception intentionally caught when calling provideKeyResponse()", e);
            }
            onSessionReady(sessionId);
            Log.d(TAG, "Key successfully added for session " + sessionId);
            return;
        } catch (android.media.NotProvisionedException e) {
            // TODO(xhwang): Should we handle this?
            Log.e(TAG, "failed to provide key response", e);
        } catch (android.media.DeniedByServerException e) {
            Log.e(TAG, "failed to provide key response", e);
        }
        onSessionError(sessionId);
        release();
    }

    /**
     * Return the security level of this DRM object.
     */
    @CalledByNative
    private String getSecurityLevel() {
        if (mMediaDrm == null) {
            Log.e(TAG, "getSecurityLevel() called when MediaDrm is null.");
            return null;
        }
        return mMediaDrm.getPropertyString("securityLevel");
    }

    private void startProvisioning() {
        Log.d(TAG, "startProvisioning");
        assert mMediaDrm != null;
        assert !mProvisioningPending;
        mProvisioningPending = true;
        MediaDrm.ProvisionRequest request = mMediaDrm.getProvisionRequest();
        PostRequestTask postTask = new PostRequestTask(request.getData());
        postTask.execute(request.getDefaultUrl());
    }

    /**
     * Called when the provision response is received.
     *
     * @param response Response data from the provision server.
     */
    private void onProvisionResponse(byte[] response) {
        Log.d(TAG, "onProvisionResponse()");
        assert mProvisioningPending;
        mProvisioningPending = false;

        // If |mMediaDrm| is released, there is no need to callback native.
        if (mMediaDrm == null) {
            return;
        }

        boolean success = provideProvisionResponse(response);

        if (mResetDeviceCredentialsPending) {
            nativeOnResetDeviceCredentialsCompleted(mNativeMediaDrmBridge, success);
            mResetDeviceCredentialsPending = false;
        }

        if (success) {
            resumePendingOperations();
        }
    }

    /**
     * Provide the provisioning response to MediaDrm.
     * @returns false if the response is invalid or on error, true otherwise.
     */
    boolean provideProvisionResponse(byte[] response) {
        if (response == null || response.length == 0) {
            Log.e(TAG, "Invalid provision response.");
            return false;
        }

        try {
            mMediaDrm.provideProvisionResponse(response);
            return true;
        } catch (android.media.DeniedByServerException e) {
            Log.e(TAG, "failed to provide provision response", e);
        } catch (java.lang.IllegalStateException e) {
            Log.e(TAG, "failed to provide provision response", e);
        }
        return false;
    }

    private void onSessionCreated(final int sessionId, final String webSessionId) {
        mHandler.post(new Runnable(){
            @Override
            public void run() {
                nativeOnSessionCreated(mNativeMediaDrmBridge, sessionId, webSessionId);
            }
        });
    }

    private void onSessionMessage(final int sessionId, final MediaDrm.KeyRequest request) {
        mHandler.post(new Runnable(){
            @Override
            public void run() {
                nativeOnSessionMessage(mNativeMediaDrmBridge, sessionId,
                        request.getData(), request.getDefaultUrl());
            }
        });
    }

    private void onSessionReady(final int sessionId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                nativeOnSessionReady(mNativeMediaDrmBridge, sessionId);
            }
        });
    }

    private void onSessionClosed(final int sessionId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                nativeOnSessionClosed(mNativeMediaDrmBridge, sessionId);
            }
        });
    }

    private void onSessionError(final int sessionId) {
        // TODO(qinmin): pass the error code to native.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                nativeOnSessionError(mNativeMediaDrmBridge, sessionId);
            }
        });
    }

    private String getWebSessionId(ByteBuffer session) {
        String webSessionId = null;
        try {
            webSessionId = new String(session.array(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "getWebSessionId failed", e);
        } catch (java.lang.NullPointerException e) {
            Log.e(TAG, "getWebSessionId failed", e);
        }
        return webSessionId;
    }

    private class MediaDrmListener implements MediaDrm.OnEventListener {
        @Override
        public void onEvent(
                MediaDrm mediaDrm, byte[] session_array, int event, int extra, byte[] data) {
            if (session_array == null) {
                Log.e(TAG, "MediaDrmListener: Null session.");
                return;
            }
            ByteBuffer session = ByteBuffer.wrap(session_array);
            if (!sessionExists(session)) {
                Log.e(TAG, "MediaDrmListener: Invalid session.");
                return;
            }
            Integer sessionId = mSessionIds.get(session);
            if (sessionId == null || sessionId == INVALID_SESSION_ID) {
                Log.e(TAG, "MediaDrmListener: Invalid session ID.");
                return;
            }
            switch(event) {
                case MediaDrm.EVENT_PROVISION_REQUIRED:
                    Log.d(TAG, "MediaDrm.EVENT_PROVISION_REQUIRED");
                    break;
                case MediaDrm.EVENT_KEY_REQUIRED:
                    Log.d(TAG, "MediaDrm.EVENT_KEY_REQUIRED");
                    if (mProvisioningPending) {
                        return;
                    }
                    String mime = mSessionMimeTypes.get(session);
                    MediaDrm.KeyRequest request = null;
                    try {
                        request = getKeyRequest(session, data, mime);
                    } catch (android.media.NotProvisionedException e) {
                        Log.e(TAG, "Device not provisioned", e);
                        startProvisioning();
                        return;
                    }
                    if (request != null) {
                        onSessionMessage(sessionId, request);
                    } else {
                        onSessionError(sessionId);
                    }
                    break;
                case MediaDrm.EVENT_KEY_EXPIRED:
                    Log.d(TAG, "MediaDrm.EVENT_KEY_EXPIRED");
                    onSessionError(sessionId);
                    break;
                case MediaDrm.EVENT_VENDOR_DEFINED:
                    Log.d(TAG, "MediaDrm.EVENT_VENDOR_DEFINED");
                    assert false;  // Should never happen.
                    break;
                default:
                    Log.e(TAG, "Invalid DRM event " + event);
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

    public static void addKeySystemUuidMapping(String keySystem, UUID uuid) {
        ByteBuffer uuidBuffer = ByteBuffer.allocateDirect(16);
        // MSB (byte) should be positioned at the first element.
        uuidBuffer.order(ByteOrder.BIG_ENDIAN);
        uuidBuffer.putLong(uuid.getMostSignificantBits());
        uuidBuffer.putLong(uuid.getLeastSignificantBits());
        nativeAddKeySystemUuidMapping(keySystem, uuidBuffer);
    }

    private native void nativeOnMediaCryptoReady(long nativeMediaDrmBridge);

    private native void nativeOnSessionCreated(long nativeMediaDrmBridge, int sessionId,
                                               String webSessionId);

    private native void nativeOnSessionMessage(long nativeMediaDrmBridge, int sessionId,
                                               byte[] message, String destinationUrl);

    private native void nativeOnSessionReady(long nativeMediaDrmBridge, int sessionId);

    private native void nativeOnSessionClosed(long nativeMediaDrmBridge, int sessionId);

    private native void nativeOnSessionError(long nativeMediaDrmBridge, int sessionId);

    private native void nativeOnResetDeviceCredentialsCompleted(
            long nativeMediaDrmBridge, boolean success);

    private static native void nativeAddKeySystemUuidMapping(String keySystem, ByteBuffer uuid);
}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.util.Log;
import android.webkit.ValueCallback;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;
import org.chromium.net.AndroidPrivateKey;
import org.chromium.net.DefaultAndroidKeyStore;

import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

/**
 * This class handles the JNI communication logic for the the AwContentsClient class.
 * Both the Java and the native peers of AwContentsClientBridge are owned by the
 * corresponding AwContents instances. This class and its native peer are connected
 * via weak references. The native AwContentsClientBridge sets up and clear these weak
 * references.
 */
@JNINamespace("android_webview")
public class AwContentsClientBridge {
    static final String TAG = "AwContentsClientBridge";

    private AwContentsClient mClient;
    // The native peer of this object.
    private long mNativeContentsClientBridge;

    private DefaultAndroidKeyStore mLocalKeyStore;

    private ClientCertLookupTable mLookupTable;

    // Used for mocking this class in tests.
    protected AwContentsClientBridge(DefaultAndroidKeyStore keyStore,
            ClientCertLookupTable table) {
        mLocalKeyStore = keyStore;
        mLookupTable = table;
    }

    public AwContentsClientBridge(AwContentsClient client, DefaultAndroidKeyStore keyStore,
            ClientCertLookupTable table) {
        assert client != null;
        mClient = client;
        mLocalKeyStore = keyStore;
        mLookupTable = table;
    }

    /**
     * Callback to communicate clientcertificaterequest back to the AwContentsClientBridge.
     * The public methods should be called on UI thread.
     * A request can not be proceeded, ignored  or canceled more than once. Doing this
     * is a programming error and causes an exception.
     */
    public class ClientCertificateRequestCallback {

        private int mId;
        private String mHost;
        private int mPort;
        private boolean mIsCalled;

        public ClientCertificateRequestCallback(int id, String host, int port) {
            mId = id;
            mHost = host;
            mPort = port;
        }

        public void proceed(final PrivateKey privateKey, final X509Certificate[] chain) {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    proceedOnUiThread(privateKey, chain);
                }
            });
        }

        public void ignore() {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ignoreOnUiThread();
                }
            });
        }

        public void cancel() {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cancelOnUiThread();
                }

            });
        }

        private void proceedOnUiThread(PrivateKey privateKey, X509Certificate[] chain) {
            checkIfCalled();

            AndroidPrivateKey key = mLocalKeyStore.createKey(privateKey);

            if (key == null || chain == null || chain.length == 0) {
                Log.w(TAG, "Empty client certificate chain?");
                provideResponse(null, null);
                return;
            }
            // Encode the certificate chain.
            byte[][] encodedChain = new byte[chain.length][];
            try {
                for (int i = 0; i < chain.length; ++i) {
                    encodedChain[i] = chain[i].getEncoded();
                }
            } catch (CertificateEncodingException e) {
                Log.w(TAG, "Could not retrieve encoded certificate chain: " + e);
                provideResponse(null, null);
                return;
            }
            mLookupTable.allow(mHost, mPort, key, encodedChain);
            provideResponse(key, encodedChain);
        }

        private void ignoreOnUiThread() {
            checkIfCalled();
            provideResponse(null, null);
        }

        private void cancelOnUiThread() {
            checkIfCalled();
            mLookupTable.deny(mHost, mPort);
            provideResponse(null, null);
        }

        private void checkIfCalled() {
            if (mIsCalled) {
                throw new IllegalStateException("The callback was already called.");
            }
            mIsCalled = true;
        }

        private void provideResponse(AndroidPrivateKey androidKey, byte[][] certChain) {
            if (mNativeContentsClientBridge == 0) return;
            nativeProvideClientCertificateResponse(mNativeContentsClientBridge, mId,
                    certChain, androidKey);
        }
    }

    // Used by the native peer to set/reset a weak ref to the native peer.
    @CalledByNative
    private void setNativeContentsClientBridge(long nativeContentsClientBridge) {
        mNativeContentsClientBridge = nativeContentsClientBridge;
    }

    // If returns false, the request is immediately canceled, and any call to proceedSslError
    // has no effect. If returns true, the request should be canceled or proceeded using
    // proceedSslError().
    // Unlike the webview classic, we do not keep keep a database of certificates that
    // are allowed by the user, because this functionality is already handled via
    // ssl_policy in native layers.
    @CalledByNative
    private boolean allowCertificateError(int certError, byte[] derBytes, final String url,
            final int id) {
        final SslCertificate cert = SslUtil.getCertificateFromDerBytes(derBytes);
        if (cert == null) {
            // if the certificate or the client is null, cancel the request
            return false;
        }
        final SslError sslError = SslUtil.sslErrorFromNetErrorCode(certError, cert, url);
        ValueCallback<Boolean> callback = new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(final Boolean value) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        proceedSslError(value.booleanValue(), id);
                    }
                });
            }
        };
        mClient.onReceivedSslError(callback, sslError);
        return true;
    }

    private void proceedSslError(boolean proceed, int id) {
        if (mNativeContentsClientBridge == 0) return;
        nativeProceedSslError(mNativeContentsClientBridge, proceed, id);
    }

    // Intentionally not private for testing the native peer of this class.
    @CalledByNative
    protected void selectClientCertificate(final int id, final String[] keyTypes,
            byte[][] encodedPrincipals, final String host, final int port) {
        assert mNativeContentsClientBridge != 0;
        ClientCertLookupTable.Cert cert = mLookupTable.getCertData(host, port);
        if (mLookupTable.isDenied(host, port)) {
            nativeProvideClientCertificateResponse(mNativeContentsClientBridge, id,
                    null, null);
            return;
        }
        if (cert != null) {
            nativeProvideClientCertificateResponse(mNativeContentsClientBridge, id,
                    cert.certChain, cert.privateKey);
            return;
        }
        // Build the list of principals from encoded versions.
        Principal[] principals = null;
        if (encodedPrincipals.length > 0) {
            principals = new X500Principal[encodedPrincipals.length];
            for (int n = 0; n < encodedPrincipals.length; n++) {
                try {
                    principals[n] = new X500Principal(encodedPrincipals[n]);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Exception while decoding issuers list: " + e);
                    nativeProvideClientCertificateResponse(mNativeContentsClientBridge, id,
                        null, null);
                    return;
                }
            }

        }

        final ClientCertificateRequestCallback callback =
                new ClientCertificateRequestCallback(id, host, port);
        mClient.onReceivedClientCertRequest(callback, keyTypes, principals, host, port);
    }

    @CalledByNative
    private void handleJsAlert(String url, String message, int id) {
        JsResultHandler handler = new JsResultHandler(this, id);
        mClient.handleJsAlert(url, message, handler);
    }

    @CalledByNative
    private void handleJsConfirm(String url, String message, int id) {
        JsResultHandler handler = new JsResultHandler(this, id);
        mClient.handleJsConfirm(url, message, handler);
    }

    @CalledByNative
    private void handleJsPrompt(String url, String message, String defaultValue, int id) {
        JsResultHandler handler = new JsResultHandler(this, id);
        mClient.handleJsPrompt(url, message, defaultValue, handler);
    }

    @CalledByNative
    private void handleJsBeforeUnload(String url, String message, int id) {
        JsResultHandler handler = new JsResultHandler(this, id);
        mClient.handleJsBeforeUnload(url, message, handler);
    }

    @CalledByNative
    private boolean shouldOverrideUrlLoading(String url) {
        return mClient.shouldOverrideUrlLoading(url);
    }

    void confirmJsResult(int id, String prompt) {
        if (mNativeContentsClientBridge == 0) return;
        nativeConfirmJsResult(mNativeContentsClientBridge, id, prompt);
    }

    void cancelJsResult(int id) {
        if (mNativeContentsClientBridge == 0) return;
        nativeCancelJsResult(mNativeContentsClientBridge, id);
    }

    //--------------------------------------------------------------------------------------------
    //  Native methods
    //--------------------------------------------------------------------------------------------
    private native void nativeProceedSslError(long nativeAwContentsClientBridge, boolean proceed,
            int id);
    private native void nativeProvideClientCertificateResponse(long nativeAwContentsClientBridge,
            int id, byte[][] certChain, AndroidPrivateKey androidKey);

    private native void nativeConfirmJsResult(long nativeAwContentsClientBridge, int id,
            String prompt);
    private native void nativeCancelJsResult(long nativeAwContentsClientBridge, int id);
}

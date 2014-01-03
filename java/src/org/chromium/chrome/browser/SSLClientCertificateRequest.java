// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.Principal;
import java.security.PrivateKey;
import javax.security.auth.x500.X500Principal;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.util.Log;

import org.chromium.base.ActivityStatus;
import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

@JNINamespace("chrome::android")
class SSLClientCertificateRequest extends AsyncTask<Void, Void, Void>
        implements KeyChainAliasCallback {

    static final String TAG = "SSLClientCertificateRequest";

    // ClientCertRequest models an asynchronous client certificate request on the Java side. Use
    // selectClientCertificate() on the UI thread to start/create a new request, this will launch a
    // system activity through KeyChain.choosePrivateKeyAlias() to let the user select a client
    // certificate.
    //
    // The selected certificate will be sent back as a string alias, which is used to call
    // KeyChain.getCertificateChain() and KeyChain.getPrivateKey(). Unfortunately, these APIs are
    // blocking, thus can't be called from the UI thread.
    //
    // To solve this, start an AsyncTask when the alias is received. It will retrieve the
    // certificate chain and private key in the background, then later send the result back to the
    // UI thread.
    //
    private final int mNativePtr;
    private String mAlias;
    private byte[][] mEncodedChain;
    private PrivateKey mPrivateKey;

    private SSLClientCertificateRequest(int nativePtr) {
        mNativePtr = nativePtr;
        mAlias = null;
        mEncodedChain = null;
        mPrivateKey = null;
    }

    // KeyChainAliasCallback implementation
    @Override
    public void alias(final String alias) {
        // This is called by KeyChainActivity in a background thread. Post task to handle the
        // certificate selection on the UI thread.
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (alias == null) {
                    // No certificate was selected.
                    onPostExecute(null);
                } else {
                    mAlias = alias;
                    // Launch background thread.
                    execute();
                }
            }
        });
    }

    @Override
    protected Void doInBackground(Void... params) {
        // Executed in a background thread, can call blocking APIs.
        X509Certificate[] chain = null;
        PrivateKey key = null;
        Context context = ActivityStatus.getActivity().getApplicationContext();
        try {
            key = KeyChain.getPrivateKey(context, mAlias);
            chain = KeyChain.getCertificateChain(context, mAlias);
        } catch (KeyChainException e) {
            Log.w(TAG, "KeyChainException when looking for '" + mAlias + "' certificate");
            return null;
        } catch (InterruptedException e) {
            Log.w(TAG, "InterruptedException when looking for '" + mAlias + "'certificate");
            return null;
        }

        if (key == null || chain == null || chain.length == 0) {
            Log.w(TAG, "Empty client certificate chain?");
            return null;
        }

        // Get the encoded certificate chain.
        byte[][] encoded_chain = new byte[chain.length][];
        try {
            for (int i = 0; i < chain.length; ++i) {
                encoded_chain[i] = chain[i].getEncoded();
            }
        } catch (CertificateEncodingException e) {
            Log.w(TAG, "Could not retrieve encoded certificate chain: " + e);
            return null;
        }

        mEncodedChain = encoded_chain;
        mPrivateKey = key;
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        // Back to the UI thread.
        nativeOnSystemRequestCompletion(mNativePtr, mEncodedChain, mPrivateKey);
    }


    /**
     * Create a new asynchronous request to select a client certificate.
     *
     * @param nativePtr The native object responsible for this request.
     * @param keyTypes The list of supported key exchange types.
     * @param encodedPrincipals The list of CA DistinguishedNames.
     * @param host_name The server host name is available (empty otherwise).
     * @param port The server port if available (0 otherwise).
     * @return true on success.
     * Note that nativeOnSystemRequestComplete will be called iff this method returns true.
     */
    @CalledByNative
    static private boolean selectClientCertificate(
            int nativePtr, String[] keyTypes, byte[][] encodedPrincipals, String hostName,
            int port) {
        ThreadUtils.assertOnUiThread();

        Activity activity = ActivityStatus.getActivity();
        if (activity == null) {
            Log.w(TAG, "No active Chromium main activity!?");
            return false;
        }

        // Build the list of principals from encoded versions.
        Principal[] principals = null;
        if (encodedPrincipals.length > 0) {
            principals = new X500Principal[encodedPrincipals.length];
            try {
                for (int n = 0; n < encodedPrincipals.length; n++) {
                    principals[n] = new X500Principal(encodedPrincipals[n]);
                }
            } catch (Exception e) {
                // Bail on error.
                Log.w(TAG, "Exception while decoding issuers list: " + e);
                return false;
            }
        }

        // All good, create new request, add it to our list and launch the certificate selection
        // activity.
        SSLClientCertificateRequest request = new SSLClientCertificateRequest(nativePtr);

        KeyChain.choosePrivateKeyAlias(
                activity, request, keyTypes, principals, hostName, port, null);
        return true;
    }

    // Called to pass request results to native side.
    private static native void nativeOnSystemRequestCompletion(
            int requestPtr, byte[][] certChain, PrivateKey privateKey);
}

// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.http.X509TrustManagerExtensions;
import android.os.Build;
import android.security.KeyChain;
import android.util.Log;

import org.chromium.base.JNINamespace;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Utility functions for verifying X.509 certificates.
 */
@JNINamespace("net")
public class X509Util {

    private static final String TAG = "X509Util";

    private static final class TrustStorageListener extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(KeyChain.ACTION_STORAGE_CHANGED)) {
                try {
                    reloadDefaultTrustManager();
                }
                catch (CertificateException e) {
                    Log.e(TAG, "Unable to reload the default TrustManager", e);
                }
                catch (KeyStoreException e) {
                    Log.e(TAG, "Unable to reload the default TrustManager", e);
                }
                catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, "Unable to reload the default TrustManager", e);
                }
            }
        }
    }

    /**
     * Interface that wraps one of X509TrustManager or
     * X509TrustManagerExtensions to support platforms before the latter was
     * added.
     */
    private static interface X509TrustManagerImplementation {
        public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,
                                                        String authType,
                                                        String host) throws CertificateException;
    }

    private static final class X509TrustManagerIceCreamSandwich implements
            X509TrustManagerImplementation {
        private final X509TrustManager mTrustManager;

        public X509TrustManagerIceCreamSandwich(X509TrustManager trustManager) {
            mTrustManager = trustManager;
        }

        @Override
        public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,
                                                        String authType,
                                                        String host) throws CertificateException {
            mTrustManager.checkServerTrusted(chain, authType);
            return Collections.<X509Certificate>emptyList();
        }
    }

    private static final class X509TrustManagerJellyBean implements X509TrustManagerImplementation {
        private final X509TrustManagerExtensions mTrustManagerExtensions;

        @SuppressLint("NewApi")
        public X509TrustManagerJellyBean(X509TrustManager trustManager) {
            mTrustManagerExtensions = new X509TrustManagerExtensions(trustManager);
        }

        @Override
        public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,
                                                        String authType,
                                                        String host) throws CertificateException {
            return mTrustManagerExtensions.checkServerTrusted(chain, authType, host);
        }
    }

    private static CertificateFactory sCertificateFactory;

    private static final String OID_TLS_SERVER_AUTH = "1.3.6.1.5.5.7.3.1";
    private static final String OID_ANY_EKU = "2.5.29.37.0";
    // Server-Gated Cryptography (necessary to support a few legacy issuers):
    //    Netscape:
    private static final String OID_SERVER_GATED_NETSCAPE = "2.16.840.1.113730.4.1";
    //    Microsoft:
    private static final String OID_SERVER_GATED_MICROSOFT = "1.3.6.1.4.1.311.10.3.3";

    /**
     * Trust manager backed up by the read-only system certificate store.
     */
    private static X509TrustManagerImplementation sDefaultTrustManager;

    /**
     * BroadcastReceiver that listens to change in the system keystore to invalidate certificate
     * caches.
     */
    private static TrustStorageListener sTrustStorageListener;

    /**
     * Trust manager backed up by a custom certificate store. We need such manager to plant test
     * root CA to the trust store in testing.
     */
    private static X509TrustManagerImplementation sTestTrustManager;
    private static KeyStore sTestKeyStore;

    /**
     * Lock object used to synchronize all calls that modify or depend on the trust managers.
     */
    private static final Object sLock = new Object();

    /**
     * Allow disabling registering the observer and recording histograms for the certificate
     * changes. Net unit tests do not load native libraries which prevent this to succeed. Moreover,
     * the system does not allow to interact with the certificate store without user interaction.
     */
    private static boolean sDisableNativeCodeForTest = false;

    /**
     * Ensures that the trust managers and certificate factory are initialized.
     */
    private static void ensureInitialized() throws CertificateException,
            KeyStoreException, NoSuchAlgorithmException {
        synchronized (sLock) {
            if (sCertificateFactory == null) {
                sCertificateFactory = CertificateFactory.getInstance("X.509");
            }
            if (sDefaultTrustManager == null) {
                sDefaultTrustManager = X509Util.createTrustManager(null);
            }
            if (sTestKeyStore == null) {
                sTestKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try {
                    sTestKeyStore.load(null);
                } catch (IOException e) {
                    // No IO operation is attempted.
                }
            }
            if (sTestTrustManager == null) {
                sTestTrustManager = X509Util.createTrustManager(sTestKeyStore);
            }
            if (!sDisableNativeCodeForTest && sTrustStorageListener == null) {
                sTrustStorageListener = new TrustStorageListener();
                nativeGetApplicationContext().registerReceiver(sTrustStorageListener,
                        new IntentFilter(KeyChain.ACTION_STORAGE_CHANGED));
            }
        }
    }

    /**
     * Creates a X509TrustManagerImplementation backed up by the given key
     * store. When null is passed as a key store, system default trust store is
     * used.
     * @throws KeyStoreException, NoSuchAlgorithmException on error initializing the TrustManager.
     */
    private static X509TrustManagerImplementation createTrustManager(KeyStore keyStore) throws
            KeyStoreException, NoSuchAlgorithmException {
        String algorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
        tmf.init(keyStore);

        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        return new X509TrustManagerJellyBean((X509TrustManager) tm);
                    } else {
                        return new X509TrustManagerIceCreamSandwich((X509TrustManager) tm);
                    }
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Error creating trust manager: " + e);
                }
            }
        }
        return null;
    }

    /**
     * After each modification of test key store, trust manager has to be generated again.
     */
    private static void reloadTestTrustManager() throws KeyStoreException,
            NoSuchAlgorithmException {
        sTestTrustManager = X509Util.createTrustManager(sTestKeyStore);
    }

    /**
     * After each modification by the system of the key store, trust manager has to be regenerated.
     */
    private static void reloadDefaultTrustManager() throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException {
        sDefaultTrustManager = null;
        nativeNotifyKeyChainChanged();
        ensureInitialized();
    }

    public static void notifyClientCertificatesChanged() {
        Log.d(TAG, "ClientCertificatesChanged!");
        nativeNotifyClientCertificatesChanged();
    }

    /**
     * Convert a DER encoded certificate to an X509Certificate.
     */
    public static X509Certificate createCertificateFromBytes(byte[] derBytes) throws
            CertificateException, KeyStoreException, NoSuchAlgorithmException {
        ensureInitialized();
        return (X509Certificate) sCertificateFactory.generateCertificate(
                new ByteArrayInputStream(derBytes));
    }

    public static void addTestRootCertificate(byte[] rootCertBytes) throws CertificateException,
            KeyStoreException, NoSuchAlgorithmException {
        ensureInitialized();
        X509Certificate rootCert = createCertificateFromBytes(rootCertBytes);
        synchronized (sLock) {
            sTestKeyStore.setCertificateEntry(
                    "root_cert_" + Integer.toString(sTestKeyStore.size()), rootCert);
            reloadTestTrustManager();
        }
    }

    public static void clearTestRootCertificates() throws NoSuchAlgorithmException,
            CertificateException, KeyStoreException {
        ensureInitialized();
        synchronized (sLock) {
            try {
                sTestKeyStore.load(null);
                reloadTestTrustManager();
            } catch (IOException e) {
                // No IO operation is attempted.
            }
        }
    }

    /**
     * If an EKU extension is present in the end-entity certificate, it MUST contain either the
     * anyEKU or serverAuth or netscapeSGC or Microsoft SGC EKUs.
     *
     * @return true if there is no EKU extension or if any of the EKU extensions is one of the valid
     * OIDs for web server certificates.
     *
     * TODO(palmer): This can be removed after the equivalent change is made to the Android default
     * TrustManager and that change is shipped to a large majority of Android users.
     */
    static boolean verifyKeyUsage(X509Certificate certificate) throws CertificateException {
        List<String> ekuOids;
        try {
            ekuOids = certificate.getExtendedKeyUsage();
        } catch (NullPointerException e) {
            // getExtendedKeyUsage() can crash due to an Android platform bug. This probably
            // happens when the EKU extension data is malformed so return false here.
            // See http://crbug.com/233610
            return false;
        }
        if (ekuOids == null)
            return true;

        for (String ekuOid : ekuOids) {
            if (ekuOid.equals(OID_TLS_SERVER_AUTH) ||
                ekuOid.equals(OID_ANY_EKU) ||
                ekuOid.equals(OID_SERVER_GATED_NETSCAPE) ||
                ekuOid.equals(OID_SERVER_GATED_MICROSOFT)) {
                return true;
            }
        }

        return false;
    }

    public static AndroidCertVerifyResult verifyServerCertificates(byte[][] certChain,
                                                                   String authType,
                                                                   String host)
            throws KeyStoreException, NoSuchAlgorithmException {
        if (certChain == null || certChain.length == 0 || certChain[0] == null) {
            throw new IllegalArgumentException("Expected non-null and non-empty certificate " +
                    "chain passed as |certChain|. |certChain|=" + Arrays.deepToString(certChain));
        }


        try {
            ensureInitialized();
        } catch (CertificateException e) {
            return new AndroidCertVerifyResult(CertVerifyStatusAndroid.VERIFY_FAILED);
        }

        X509Certificate[] serverCertificates = new X509Certificate[certChain.length];
        try {
            for (int i = 0; i < certChain.length; ++i) {
                serverCertificates[i] = createCertificateFromBytes(certChain[i]);
            }
        } catch (CertificateException e) {
            return new AndroidCertVerifyResult(CertVerifyStatusAndroid.VERIFY_UNABLE_TO_PARSE);
        }

        // Expired and not yet valid certificates would be rejected by the trust managers, but the
        // trust managers report all certificate errors using the general CertificateException. In
        // order to get more granular error information, cert validity time range is being checked
        // separately.
        try {
            serverCertificates[0].checkValidity();
            if (!verifyKeyUsage(serverCertificates[0])) {
                return new AndroidCertVerifyResult(
                        CertVerifyStatusAndroid.VERIFY_INCORRECT_KEY_USAGE);
            }
        } catch (CertificateExpiredException e) {
            return new AndroidCertVerifyResult(CertVerifyStatusAndroid.VERIFY_EXPIRED);
        } catch (CertificateNotYetValidException e) {
            return new AndroidCertVerifyResult(CertVerifyStatusAndroid.VERIFY_NOT_YET_VALID);
        } catch (CertificateException e) {
            return new AndroidCertVerifyResult(CertVerifyStatusAndroid.VERIFY_FAILED);
        }

        synchronized (sLock) {
            List<X509Certificate> verifiedChain;
            try {
                verifiedChain = sDefaultTrustManager.checkServerTrusted(serverCertificates,
                                                                        authType, host);
            } catch (CertificateException eDefaultManager) {
                try {
                    verifiedChain = sTestTrustManager.checkServerTrusted(serverCertificates,
                                                                         authType, host);
                } catch (CertificateException eTestManager) {
                    // Neither of the trust managers confirms the validity of the certificate chain,
                    // log the error message returned by the system trust manager.
                    Log.i(TAG, "Failed to validate the certificate chain, error: " +
                              eDefaultManager.getMessage());
                    return new AndroidCertVerifyResult(
                            CertVerifyStatusAndroid.VERIFY_NO_TRUSTED_ROOT);
                }
            }

            // TODO(davidben): This code was removed for
            // http://crbug.com/361166. Fix the performance regression and
            // export it again.
            boolean isIssuedByKnownRoot = false;
            return new AndroidCertVerifyResult(CertVerifyStatusAndroid.VERIFY_OK,
                                               isIssuedByKnownRoot, verifiedChain);
        }
    }

    public static void setDisableNativeCodeForTest(boolean disabled) {
        sDisableNativeCodeForTest = disabled;
    }

    private static native void nativeNotifyClientCertificatesChanged();

    /**
     * Notify the native net::CertDatabase instance that the system database has been updated.
     */
    private static native void nativeNotifyKeyChainChanged();

    /**
     * Record histograms on the platform's certificate verification capabilities.
     */
    private static native void nativeRecordCertVerifyCapabilitiesHistogram(
        boolean foundSystemTrustRoots);

    /**
     * Returns the application context.
     */
    private static native Context nativeGetApplicationContext();

}

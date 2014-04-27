// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import org.chromium.net.IRemoteAndroidKeyStoreCallbacks;

/**
 * Interface for communication with an Android KeyStore in another process.
 */
interface IRemoteAndroidKeyStore {
    // Remote calls for SSlClientCertificateRequest - these allow retrieving
    // the alias of the certificate to be used, its encoded chain and a handle
    // for identifying a private key in the remote process.
    String getClientCertificateAlias();
    byte[] getEncodedCertificateChain(in String alias);
    int getPrivateKeyHandle(in String alias);

    // Registers callbacks for service->client communication.
    void setClientCallbacks(IRemoteAndroidKeyStoreCallbacks callbacks);

    // Remote calls for AndroidKeyStore - these functions are performing operations
    // with a PrivateKey in the remote process using the handle provided by
    // |getPrivateKeyHandle|.
    byte[] getRSAKeyModulus(in int handle);
    byte[] getPrivateKeyEncodedBytes(in int handle);
    byte[] getDSAKeyParamQ(in int handle);
    byte[] getECKeyOrder(in int handle);
    byte[] rawSignDigestWithPrivateKey(in int handle, in byte[] message);
    int getPrivateKeyType(in int handle);
    void releaseKey(in int handle);
}

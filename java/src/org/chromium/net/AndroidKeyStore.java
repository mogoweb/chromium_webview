// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Specifies all the dependencies from the native OpenSSL engine on an Android KeyStore.
 */
@JNINamespace("net::android")
public interface AndroidKeyStore {

    /**
     * Returns the public modulus of a given RSA private key as a byte
     * buffer.
     * This can be used by native code to convert the modulus into
     * an OpenSSL BIGNUM object. Required to craft a custom native RSA
     * object where RSA_size() works as expected.
     *
     * @param key A PrivateKey instance, must implement RSAKey.
     * @return A byte buffer corresponding to the modulus. This is
     * big-endian representation of a BigInteger.
     */
    @CalledByNative
    byte[] getRSAKeyModulus(AndroidPrivateKey key);

    /**
     * Returns the 'Q' parameter of a given DSA private key as a byte
     * buffer.
     * This can be used by native code to convert it into an OpenSSL BIGNUM
     * object where DSA_size() works as expected.
     *
     * @param key A PrivateKey instance. Must implement DSAKey.
     * @return A byte buffer corresponding to the Q parameter. This is
     * a big-endian representation of a BigInteger.
     */
    @CalledByNative
    byte[] getDSAKeyParamQ(AndroidPrivateKey key);

    /**
     * Returns the 'order' parameter of a given ECDSA private key as a
     * a byte buffer.
     * @param key A PrivateKey instance. Must implement ECKey.
     * @return A byte buffer corresponding to the 'order' parameter.
     * This is a big-endian representation of a BigInteger.
     */
    @CalledByNative
    byte[] getECKeyOrder(AndroidPrivateKey key);

    /**
     * Returns the encoded data corresponding to a given PrivateKey.
     * Note that this will fail for platform keys on Android 4.0.4
     * and higher. It can be used on 4.0.3 and older platforms to
     * route around the platform bug described below.
     * @param key A PrivateKey instance
     * @return encoded key as PKCS#8 byte array, can be null.
     */
    @CalledByNative
    byte[] getPrivateKeyEncodedBytes(AndroidPrivateKey key);

    /**
     * Sign a given message with a given PrivateKey object. This method
     * shall only be used to implement signing in the context of SSL
     * client certificate support.
     *
     * The message will actually be a hash, computed by OpenSSL itself,
     * depending on the type of the key. The result should match exactly
     * what the vanilla implementations of the following OpenSSL function
     * calls do:
     *
     *  - For a RSA private key, this should be equivalent to calling
     *    RSA_private_encrypt(..., RSA_PKCS1_PADDING), i.e. it must
     *    generate a raw RSA signature. The message must be either a
     *    combined, 36-byte MD5+SHA1 message digest or a DigestInfo
     *    value wrapping a message digest.
     *
     *  - For a DSA and ECDSA private keys, this should be equivalent to
     *    calling DSA_sign(0,...) and ECDSA_sign(0,...) respectively. The
     *    message must be a hash and the function shall compute a direct
     *    DSA/ECDSA signature for it.
     *
     * @param key The PrivateKey handle.
     * @param message The message to sign.
     * @return signature as a byte buffer.
     *
     * Important: Due to a platform bug, this function will always fail on
     *            Android < 4.2 for RSA PrivateKey objects. See the
     *            getOpenSSLHandleForPrivateKey() below for work-around.
     */
    @CalledByNative
    byte[] rawSignDigestWithPrivateKey(AndroidPrivateKey key, byte[] message);

    /**
     * Return the type of a given PrivateKey object. This is an integer
     * that maps to one of the values defined by org.chromium.net.PrivateKeyType,
     * which is itself auto-generated from net/android/private_key_type_list.h
     * @param key The PrivateKey handle
     * @return key type, or PrivateKeyType.INVALID if unknown.
     */
    @CalledByNative
    int getPrivateKeyType(AndroidPrivateKey key);

    /**
     * Return the system EVP_PKEY handle corresponding to a given PrivateKey
     * object.
     *
     * This shall only be used when the "NONEwithRSA" signature is not
     * available, as described in rawSignDigestWithPrivateKey(). I.e.
     * never use this on Android 4.2 or higher.
     *
     * This can only work in Android 4.0.4 and higher, for older versions
     * of the platform (e.g. 4.0.3), there is no system OpenSSL EVP_PKEY,
     * but the private key contents can be retrieved directly with
     * the getEncoded() method.
     *
     * This assumes that the target device uses a vanilla AOSP
     * implementation of its java.security classes, which is also
     * based on OpenSSL (fortunately, no OEM has apperently changed to
     * a different implementation, according to the Android team).
     *
     * Note that the object returned was created with the platform version
     * of OpenSSL, and _not_ the one that comes with Chromium. Whether the
     * object can be used safely with the Chromium OpenSSL library depends
     * on differences between their actual ABI / implementation details.
     *
     * To better understand what's going on below, please refer to the
     * following source files in the Android 4.0.4 and 4.1 source trees:
     * libcore/luni/src/main/java/org/apache/harmony/xnet/provider/jsse/OpenSSLRSAPrivateKey.java
     * libcore/luni/src/main/native/org_apache_harmony_xnet_provider_jsse_NativeCrypto.cpp
     *
     * @param key The PrivateKey handle.
     * @return The EVP_PKEY handle, as a 32-bit integer (0 if not available)
     */
    @CalledByNative
    long getOpenSSLHandleForPrivateKey(AndroidPrivateKey key);

    /**
     * Called when the native OpenSSL engine no longer needs access to the underlying key.
     */
    @CalledByNative
    void releaseKey(AndroidPrivateKey key);
}

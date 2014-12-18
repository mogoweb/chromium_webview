// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.crypto;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.annotation.concurrent.ThreadSafe;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Generates {@link Cipher} instances for encrypting session data that is temporarily stored.
 *
 * When an Activity is sent to the background, Android gives it the opportunity to save state to
 * restore a user's session when the Activity is restarted. In addition to saving state to disk,
 * Android has a mechanism for saving instance state through {@link Bundle}s, which help
 * differentiate between users pausing and ending a session:
 * - If the Activity is killed in the background (e.g. to free up resources for other Activities),
 *   Android gives a {@link Bundle} to the Activity when the user restarts the Activity. The
 *   {@link Bundle} is expected to be small and fast to generate, and is managed by Android.
 * - If the Activity was explicitly killed (e.g. the user swiped away the task from Recent Tasks),
 *   Android does not restore the {@link Bundle} when the user restarts the Activity.
 *
 * To securely save temporary session data to disk:
 * - Encrypt data with a {@link Cipher} from {@link CipherFactory#getCipher(int)} before storing it.
 * - Store {@link Cipher} parameters in the Bundle via {@link CipherFactory#saveToBundle(Bundle)}.
 *
 * Explicitly ending the session destroys the {@link Bundle}, making the previous session's data
 * unreadable.
 */
@ThreadSafe
public class CipherFactory {
    private static final String TAG = "CipherFactory";
    static final int NUM_BYTES = 16;

    static final String BUNDLE_IV = "org.chromium.content.browser.crypto.CipherFactory.IV";
    static final String BUNDLE_KEY = "org.chromium.content.browser.crypto.CipherFactory.KEY";

    /** Holds intermediate data for the computation. */
    private static class CipherData {
        public final Key key;
        public final byte[] iv;

        public CipherData(Key key, byte[] iv) {
            this.key = key;
            this.iv = iv;
        }
    }

    /** Singleton holder for the class. */
    private static class LazyHolder {
        private static CipherFactory sInstance = new CipherFactory();
    }

    /**
     * Synchronization primitive to prevent thrashing the cipher parameters between threads
     * attempting to restore previous parameters and generate new ones.
     */
    private final Object mDataLock = new Object();

    /** Used to generate data needed for the Cipher on a background thread. */
    private FutureTask<CipherData> mDataGenerator;

    /** Holds data for cipher generation. */
    private CipherData mData;

    /** Generates random data for the Ciphers. May be swapped out for tests. */
    private ByteArrayGenerator mRandomNumberProvider;

    /** @return The Singleton instance. Creates it if it doesn't exist. */
    public static CipherFactory getInstance() {
        return LazyHolder.sInstance;
    }

    /**
     * Creates a secure Cipher for encrypting data.
     * This function blocks until data needed to generate a Cipher has been created by the
     * background thread.
     * @param opmode One of Cipher.{ENCRYPT,DECRYPT}_MODE.
     * @return A Cipher, or null if it is not possible to instantiate one.
     */
    public Cipher getCipher(int opmode) {
        CipherData data = getCipherData(true);

        if (data != null) {
            try {
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(opmode, data.key, new IvParameterSpec(data.iv));
                return cipher;
            } catch (GeneralSecurityException e) {
                // Can't do anything here.
            }
        }

        Log.e(TAG, "Error in creating cipher instance.");
        return null;
    }

    /**
     * Returns data required for generating the Cipher.
     * @param generateIfNeeded Generates data on the background thread, blocking until it is done.
     * @return Data to use for the Cipher, null if it couldn't be generated.
     */
    CipherData getCipherData(boolean generateIfNeeded) {
        if (mData == null && generateIfNeeded) {
            // Ideally, this task should have been started way before this.
            triggerKeyGeneration();

            // Grab the data from the task.
            CipherData data;
            try {
                data = mDataGenerator.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }

            // Only the first thread is allowed to save the data.
            synchronized (mDataLock) {
                if (mData == null) mData = data;
            }
        }
        return mData;
    }

    /**
     * Creates a Callable that generates the data required to create a Cipher. This is done on a
     * background thread to prevent blocking on the I/O required for
     * {@link ByteArrayGenerator#getBytes(int)}.
     * @return Callable that generates the Cipher data.
     */
    private Callable<CipherData> createGeneratorCallable() {
        return new Callable<CipherData>() {
            @Override
            public CipherData call() {
                // Poll random data to generate initialization parameters for the Cipher.
                byte[] seed, iv;
                try {
                    seed = mRandomNumberProvider.getBytes(NUM_BYTES);
                    iv = mRandomNumberProvider.getBytes(NUM_BYTES);
                } catch (Exception e) {
                    Log.e(TAG, "Couldn't get generator data.");
                    return null;
                }

                try {
                    // Old versions of SecureRandom do not seed themselves as securely as possible.
                    // This workaround should suffice until the fixed version is deployed to all
                    // users. The seed comes from RandomNumberProvider.getBytes(), which reads
                    // from /dev/urandom, which is as good as the platform can get.
                    //
                    // TODO(palmer): Consider getting rid of this once the updated platform has
                    // shipped to everyone. Alternately, leave this in as a defense against other
                    // bugs in SecureRandom.
                    SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
                    random.setSeed(seed);

                    KeyGenerator generator = KeyGenerator.getInstance("AES");
                    generator.init(128, random);
                    return new CipherData(generator.generateKey(), iv);
                } catch (GeneralSecurityException e) {
                    Log.e(TAG, "Couldn't get generator instances.");
                    return null;
                }
            }
        };
    }

    /**
     * Generates the encryption key and IV on a background thread (if necessary).
     * Should be explicitly called when the Activity determines that it will need a Cipher rather
     * than immediately calling {@link CipherFactory#getCipher(int)}.
     */
    public void triggerKeyGeneration() {
        if (mData != null) return;

        synchronized (mDataLock) {
            if (mDataGenerator == null) {
                mDataGenerator = new FutureTask<CipherData>(createGeneratorCallable());
                AsyncTask.THREAD_POOL_EXECUTOR.execute(mDataGenerator);
            }
        }
    }

    /**
     * Saves the encryption data in a bundle. Expected to be called when an Activity saves its state
     * before being sent to the background.
     *
     * The IV *could* go into the first block of the payload. However, since the staleness of the
     * data is determined by whether or not it's able to be decrypted, the IV should not be read
     * from it.
     *
     * @param outState The data bundle to store data into.
     */
    public void saveToBundle(Bundle outState) {
        CipherData data = getCipherData(false);
        if (data == null) return;

        byte[] wrappedKey = data.key.getEncoded();
        if (wrappedKey != null && data.iv != null) {
            outState.putByteArray(BUNDLE_KEY, wrappedKey);
            outState.putByteArray(BUNDLE_IV, data.iv);
        }
    }

    /**
     * Restores the encryption key from the given Bundle. Expected to be called when an Activity is
     * being restored after being killed in the background. If the Activity was explicitly killed by
     * the user, Android gives no Bundle (and therefore no key).
     *
     * @param savedInstanceState Bundle containing the Activity's previous state. Null if the user
     *                           explicitly killed the Activity.
     * @return                   True if the data was restored successfully from the Bundle, or if
     *                           the CipherData in use matches the Bundle contents.
     *
     */
    public boolean restoreFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState == null) return false;

        byte[] wrappedKey = savedInstanceState.getByteArray(BUNDLE_KEY);
        byte[] iv = savedInstanceState.getByteArray(BUNDLE_IV);
        if (wrappedKey == null || iv == null) return false;

        try {
            Key bundledKey = new SecretKeySpec(wrappedKey, "AES");
            synchronized (mDataLock) {
                if (mData == null) {
                    mData = new CipherData(bundledKey, iv);
                    return true;
                } else if (mData.key.equals(bundledKey) && Arrays.equals(mData.iv, iv)) {
                    return true;
                } else {
                    Log.e(TAG, "Attempted to restore different cipher data.");
                }
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error in restoring the key from the bundle.");
        }

        return false;
    }

    /**
     * Overrides the random number generated that is normally used by the class.
     * @param mockProvider Should be used to provide non-random data.
     */
    void setRandomNumberProviderForTests(ByteArrayGenerator mockProvider) {
        mRandomNumberProvider = mockProvider;
    }

    private CipherFactory() {
        mRandomNumberProvider = new ByteArrayGenerator();
    }
}
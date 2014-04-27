// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import android.os.RemoteException;
import android.util.Log;

/**
 * Provides a remoted implementation of AndroidKeyStore where all calls are forwarded via
 * binder to an external process.
 */
public class RemoteAndroidKeyStore implements AndroidKeyStore {

    private static final String TAG = "AndroidKeyStoreRemoteImpl";

    private static class RemotePrivateKey implements AndroidPrivateKey {
        // Reference to the key on a remote store.
        final int mHandle;
        // Key store handling this key.
        final RemoteAndroidKeyStore mStore;

        RemotePrivateKey(int handle, RemoteAndroidKeyStore store) {
            mHandle = handle;
            mStore = store;
        }

        public int getHandle() {
            return mHandle;
        }

        @Override
        public AndroidKeyStore getKeyStore() {
            return mStore;
        }
    }

    private final IRemoteAndroidKeyStore mRemoteManager;

    public RemoteAndroidKeyStore(IRemoteAndroidKeyStore manager) {
        mRemoteManager = manager;
    }

    @Override
    public byte[] getRSAKeyModulus(AndroidPrivateKey key) {
        RemotePrivateKey remoteKey = (RemotePrivateKey) key;
        try {
            Log.d(TAG, "getRSAKeyModulus");
            return mRemoteManager.getRSAKeyModulus(remoteKey.getHandle());
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
     }

    @Override
    public byte[] getDSAKeyParamQ(AndroidPrivateKey key) {
        RemotePrivateKey remoteKey = (RemotePrivateKey) key;
        try {
            Log.d(TAG, "getDSAKeyParamQ");
            return mRemoteManager.getDSAKeyParamQ(remoteKey.getHandle());
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public byte[] getECKeyOrder(AndroidPrivateKey key) {
        RemotePrivateKey remoteKey = (RemotePrivateKey) key;
        try {
            Log.d(TAG, "getECKeyOrder");
            return mRemoteManager.getECKeyOrder(remoteKey.getHandle());
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public byte[] rawSignDigestWithPrivateKey(AndroidPrivateKey key, byte[] message) {
        RemotePrivateKey remoteKey = (RemotePrivateKey) key;
        try {
            Log.d(TAG, "rawSignDigestWithPrivateKey");
            return mRemoteManager.rawSignDigestWithPrivateKey(remoteKey.getHandle(), message);
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public int getPrivateKeyType(AndroidPrivateKey key) {
        RemotePrivateKey remoteKey = (RemotePrivateKey) key;
        try {
            Log.d(TAG, "getPrivateKeyType");
            return mRemoteManager.getPrivateKeyType(remoteKey.getHandle());
        } catch (RemoteException e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public byte[] getPrivateKeyEncodedBytes(AndroidPrivateKey key) {
        // This should not be called as it's only for older versions of Android.
        assert false;
        return null;
    }

    @Override
    public int getOpenSSLHandleForPrivateKey(AndroidPrivateKey privateKey) {
        // This should not be called as it's only for older versions of Android.
        assert false;
        return 0;
    }

    public AndroidPrivateKey createKey(String alias) {
        try {
            int handle = mRemoteManager.getPrivateKeyHandle(alias);
            return new RemotePrivateKey(handle, this);
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void releaseKey(AndroidPrivateKey key) {
        RemotePrivateKey remoteKey = (RemotePrivateKey) key;
        try {
            Log.d(TAG, "releaseKey");
            mRemoteManager.releaseKey(remoteKey.getHandle());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}

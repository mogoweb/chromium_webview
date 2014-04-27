// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.util.SparseArray;
import android.webkit.ValueCallback;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

/**
 * Bridge between android.webview.WebStorage and native QuotaManager. This object is owned by Java
 * AwBrowserContext and the native side is owned by the native AwBrowserContext.
 *
 * TODO(boliu): Actually make this true after Java AwBrowserContext is added.
 */
@JNINamespace("android_webview")
public class AwQuotaManagerBridge {
    // TODO(boliu): This should be obtained from Java AwBrowserContext that owns this.
    private static native long nativeGetDefaultNativeAwQuotaManagerBridge();

    // TODO(boliu): This should be owned by Java AwBrowserContext, not a singleton.
    private static AwQuotaManagerBridge sInstance;
    public static AwQuotaManagerBridge getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            sInstance = new AwQuotaManagerBridge(nativeGetDefaultNativeAwQuotaManagerBridge());
        }
        return sInstance;
    }

    /**
     * This class represent the callback value of android.webview.WebStorage.getOrigins. The values
     * are optimized for JNI convenience and need to be converted.
     */
    public static class Origins {
        // Origin, usage, and quota data in parallel arrays of same length.
        public final String[] mOrigins;
        public final long[] mUsages;
        public final long[] mQuotas;

        Origins(String[] origins, long[] usages, long[] quotas) {
            mOrigins = origins;
            mUsages = usages;
            mQuotas = quotas;
        }
    }

    // This is not owning. The native object is owned by the native AwBrowserContext.
    private long mNativeAwQuotaManagerBridgeImpl;

    // The Java callbacks are saved here. An incrementing callback id is generated for each saved
    // callback and is passed to the native side to identify callback.
    private int mNextId;
    private SparseArray<ValueCallback<Origins>> mPendingGetOriginCallbacks;
    private SparseArray<ValueCallback<Long>> mPendingGetQuotaForOriginCallbacks;
    private SparseArray<ValueCallback<Long>> mPendingGetUsageForOriginCallbacks;

    private AwQuotaManagerBridge(long nativeAwQuotaManagerBridgeImpl) {
        mNativeAwQuotaManagerBridgeImpl = nativeAwQuotaManagerBridgeImpl;
        mPendingGetOriginCallbacks =
                new SparseArray<ValueCallback<Origins>>();
        mPendingGetQuotaForOriginCallbacks = new SparseArray<ValueCallback<Long>>();
        mPendingGetUsageForOriginCallbacks = new SparseArray<ValueCallback<Long>>();
        nativeInit(mNativeAwQuotaManagerBridgeImpl);
    }

    private int getNextId() {
        ThreadUtils.assertOnUiThread();
        return ++mNextId;
    }

    /*
     * There are five HTML5 offline storage APIs.
     * 1) Web Storage (ie the localStorage and sessionStorage variables)
     * 2) Web SQL database
     * 3) Application cache
     * 4) Indexed Database
     * 5) Filesystem API
     */

    /**
     * Implements WebStorage.deleteAllData(). Clear the storage of all five offline APIs.
     *
     * TODO(boliu): Actually clear Web Storage.
     */
    public void deleteAllData() {
        nativeDeleteAllData(mNativeAwQuotaManagerBridgeImpl);
    }

    /**
     * Implements WebStorage.deleteOrigin(). Clear the storage of APIs 2-5 for the given origin.
     */
    public void deleteOrigin(String origin) {
        nativeDeleteOrigin(mNativeAwQuotaManagerBridgeImpl, origin);
    }

    /**
     * Implements WebStorage.getOrigins. Get the per origin usage and quota of APIs 2-5 in
     * aggregate.
     */
    public void getOrigins(ValueCallback<Origins> callback) {
        int callbackId = getNextId();
        assert mPendingGetOriginCallbacks.get(callbackId) == null;
        mPendingGetOriginCallbacks.put(callbackId, callback);
        nativeGetOrigins(mNativeAwQuotaManagerBridgeImpl, callbackId);
    }

    /**
     * Implements WebStorage.getQuotaForOrigin. Get the quota of APIs 2-5 in aggregate for given
     * origin.
     */
    public void getQuotaForOrigin(String origin, ValueCallback<Long> callback) {
        int callbackId = getNextId();
        assert mPendingGetQuotaForOriginCallbacks.get(callbackId) == null;
        mPendingGetQuotaForOriginCallbacks.put(callbackId, callback);
        nativeGetUsageAndQuotaForOrigin(mNativeAwQuotaManagerBridgeImpl, origin, callbackId, true);
    }

    /**
     * Implements WebStorage.getUsageForOrigin. Get the usage of APIs 2-5 in aggregate for given
     * origin.
     */
    public void getUsageForOrigin(String origin, ValueCallback<Long> callback) {
        int callbackId = getNextId();
        assert mPendingGetUsageForOriginCallbacks.get(callbackId) == null;
        mPendingGetUsageForOriginCallbacks.put(callbackId, callback);
        nativeGetUsageAndQuotaForOrigin(mNativeAwQuotaManagerBridgeImpl, origin, callbackId, false);
    }

    @CalledByNative
    private void onGetOriginsCallback(int callbackId, String[] origin, long[] usages,
            long[] quotas) {
        assert mPendingGetOriginCallbacks.get(callbackId) != null;
        mPendingGetOriginCallbacks.get(callbackId).onReceiveValue(
            new Origins(origin, usages, quotas));
        mPendingGetOriginCallbacks.remove(callbackId);
    }

    @CalledByNative
    private void onGetUsageAndQuotaForOriginCallback(
            int callbackId, boolean isQuota, long usage, long quota) {
        if (isQuota) {
            assert mPendingGetQuotaForOriginCallbacks.get(callbackId) != null;
            mPendingGetQuotaForOriginCallbacks.get(callbackId).onReceiveValue(quota);
            mPendingGetQuotaForOriginCallbacks.remove(callbackId);
        } else {
            assert mPendingGetUsageForOriginCallbacks.get(callbackId) != null;
            mPendingGetUsageForOriginCallbacks.get(callbackId).onReceiveValue(usage);
            mPendingGetUsageForOriginCallbacks.remove(callbackId);
        }
    }

    private native void nativeInit(long nativeAwQuotaManagerBridgeImpl);
    private native void nativeDeleteAllData(long nativeAwQuotaManagerBridgeImpl);
    private native void nativeDeleteOrigin(long nativeAwQuotaManagerBridgeImpl, String origin);
    private native void nativeGetOrigins(long nativeAwQuotaManagerBridgeImpl, int callbackId);
    private native void nativeGetUsageAndQuotaForOrigin(long nativeAwQuotaManagerBridgeImpl,
            String origin, int callbackId, boolean isQuota);
}

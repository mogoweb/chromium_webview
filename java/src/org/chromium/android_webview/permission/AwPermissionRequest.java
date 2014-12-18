// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview.permission;

import android.net.Uri;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

/**
 * This class wraps permission request in Chromium side, and can only be created
 * by native side.
 */
@JNINamespace("android_webview")
public class AwPermissionRequest {
    private static String TAG = "AwPermissionRequest";

    private Uri mOrigin;
    private long mResources;
    private boolean mProcessed;

    // AwPermissionRequest native instance.
    private long mNativeAwPermissionRequest;

    @CalledByNative
    private static AwPermissionRequest create(long nativeAwPermissionRequest, String url,
            long resources) {
        if (nativeAwPermissionRequest == 0) return null;
        Uri origin = Uri.parse(url);
        return new AwPermissionRequest(nativeAwPermissionRequest, origin, resources);
    }

    private AwPermissionRequest(long nativeAwPermissionRequest, Uri origin,
            long resources) {
        mNativeAwPermissionRequest = nativeAwPermissionRequest;
        mOrigin = origin;
        mResources = resources;
    }

    public Uri getOrigin() {
        return mOrigin;
    }

    public long getResources() {
        return mResources;
    }

    public void grant() {
        validate();
        if (mNativeAwPermissionRequest != 0)
            nativeOnAccept(mNativeAwPermissionRequest, true);
        mProcessed = true;
    }

    public void deny() {
        validate();
        if (mNativeAwPermissionRequest != 0)
            nativeOnAccept(mNativeAwPermissionRequest, false);
        mProcessed = true;
    }

    @CalledByNative
    private void detachNativeInstance() {
        mNativeAwPermissionRequest = 0;
    }

    @Override
    protected void finalize() throws Throwable {
        if (mNativeAwPermissionRequest == 0) return;
        Log.e(TAG, "Neither grant() nor deny() has been called, "
                + "the permission request is denied by default.");
        deny();
    }

    private void validate() {
        if (!ThreadUtils.runningOnUiThread())
            throw new IllegalStateException(
                    "Either grant() or deny() should be called on UI thread");

        if (mProcessed)
            throw new IllegalStateException("Either grant() or deny() has been already called.");
    }

    private native void nativeOnAccept(long nativeAwPermissionRequest, boolean allowed);
}

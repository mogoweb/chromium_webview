// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.webcontents;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.WebContents;

/**
 * The WebContentsImpl Java wrapper to allow communicating with the native WebContentsImpl
 * object.
 */
@JNINamespace("content")
//TODO(tedchoc): Remove the package restriction once this class moves to a non-public content
//               package whose visibility will be enforced via DEPS.
/* package */ class WebContentsImpl implements WebContents {

    private long mNativeWebContentsAndroid;
    private NavigationController mNavigationController;

    private WebContentsImpl(
            long nativeWebContentsAndroid, NavigationController navigationController) {
        mNativeWebContentsAndroid = nativeWebContentsAndroid;
        mNavigationController = navigationController;
    }

    @CalledByNative
    private static WebContentsImpl create(
            long nativeWebContentsAndroid, NavigationController navigationController) {
        return new WebContentsImpl(nativeWebContentsAndroid, navigationController);
    }

    @CalledByNative
    private void destroy() {
        mNativeWebContentsAndroid = 0;
        mNavigationController = null;
    }

    @CalledByNative
    private long getNativePointer() {
        return mNativeWebContentsAndroid;
    }

    @Override
    public NavigationController getNavigationController() {
        return mNavigationController;
    }

    @Override
    public String getTitle() {
        return nativeGetTitle(mNativeWebContentsAndroid);
    }

    @Override
    public String getVisibleUrl() {
        return nativeGetVisibleURL(mNativeWebContentsAndroid);
    }

    @Override
    public void stop() {
        nativeStop(mNativeWebContentsAndroid);
    }

    private native String nativeGetTitle(long nativeWebContentsAndroid);
    private native String nativeGetVisibleURL(long nativeWebContentsAndroid);
    private native void nativeStop(long nativeWebContentsAndroid);
}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Rect;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.common.CleanupReference;

import java.io.OutputStream;

// A simple wrapper around a SkPicture, that allows final rendering to be performed using the
// chromium skia library.
@JNINamespace("android_webview")
class AwPicture extends Picture {

    private int mNativeAwPicture;

    // There is no explicit destroy method on Picture base-class, so cleanup is always
    // handled via the CleanupReference.
    private static final class DestroyRunnable implements Runnable {
        private int mNativeAwPicture;
        private DestroyRunnable(int nativeAwPicture) {
            mNativeAwPicture = nativeAwPicture;
        }
        @Override
        public void run() {
            nativeDestroy(mNativeAwPicture);
        }
    }

    private CleanupReference mCleanupReference;

    /**
     * @param nativeAwPicture is an instance of the AwPicture native class. Ownership is
     *                        taken by this java instance.
     */
    AwPicture(int nativeAwPicture) {
        mNativeAwPicture = nativeAwPicture;
        mCleanupReference = new CleanupReference(this, new DestroyRunnable(nativeAwPicture));
    }

    @Override
    public Canvas beginRecording(int width, int height) {
        unsupportedOperation();
        return null;
    }

    @Override
    public void endRecording() {
        // Intentional no-op. The native picture ended recording prior to java c'tor call.
    }

    @Override
    public int getWidth() {
        return nativeGetWidth(mNativeAwPicture);
    }

    @Override
    public int getHeight() {
        return nativeGetHeight(mNativeAwPicture);
    }

    // Effectively a local variable of draw(), but held as a member to avoid GC churn.
    private Rect mClipBoundsTemporary = new Rect();

    @Override
    public void draw(Canvas canvas) {
        canvas.getClipBounds(mClipBoundsTemporary);
        nativeDraw(mNativeAwPicture, canvas,
                mClipBoundsTemporary.left, mClipBoundsTemporary.top,
                mClipBoundsTemporary.right, mClipBoundsTemporary.bottom);
    }

    @Override
    public void writeToStream(OutputStream stream) {
        unsupportedOperation();
    }

    private void unsupportedOperation() {
        throw new IllegalStateException("Unsupported in AwPicture");
    }

    private static native void nativeDestroy(int nativeAwPicture);
    private native int nativeGetWidth(int nativeAwPicture);
    private native int nativeGetHeight(int nativeAwPicture);
    private native void nativeDraw(int nativeAwPicture, Canvas canvas,
            int left, int top, int right, int bottom);
}


// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.gfx;

import android.graphics.SurfaceTexture;
import android.os.Build;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Wrapper class for the underlying platform's SurfaceTexture in order to
 * provide a stable JNI API.
 */
@JNINamespace("gfx")
class SurfaceTexturePlatformWrapper {
    @CalledByNative
    private static SurfaceTexture create(int textureId) {
        return new SurfaceTexture(textureId);
    }

    @CalledByNative
    private static void destroy(SurfaceTexture surfaceTexture) {
        surfaceTexture.setOnFrameAvailableListener(null);
        surfaceTexture.release();
    }

    @CalledByNative
    private static void setFrameAvailableCallback(SurfaceTexture surfaceTexture,
            int nativeSurfaceTextureListener) {
       surfaceTexture.setOnFrameAvailableListener(
               new SurfaceTextureListener(nativeSurfaceTextureListener));
    }

    @CalledByNative
    private static void updateTexImage(SurfaceTexture surfaceTexture) {
        surfaceTexture.updateTexImage();
    }

    @CalledByNative
    private static void setDefaultBufferSize(SurfaceTexture surfaceTexture, int width,
            int height) {
        surfaceTexture.setDefaultBufferSize(width, height);
    }

    @CalledByNative
    private static void getTransformMatrix(SurfaceTexture surfaceTexture, float[] matrix) {
        surfaceTexture.getTransformMatrix(matrix);
    }

    @CalledByNative
    private static void attachToGLContext(SurfaceTexture surfaceTexture, int texName) {
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
        surfaceTexture.attachToGLContext(texName);
    }

    @CalledByNative
    private static void detachFromGLContext(SurfaceTexture surfaceTexture) {
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
        surfaceTexture.detachFromGLContext();
    }
}
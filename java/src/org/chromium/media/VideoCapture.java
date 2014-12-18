// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Video Capture Device base class to interface to native Chromium.
 **/
@JNINamespace("media")
public abstract class VideoCapture implements PreviewCallback {

    protected static class CaptureFormat {
        int mWidth;
        int mHeight;
        final int mFramerate;
        final int mPixelFormat;

        public CaptureFormat(
                int width, int height, int framerate, int pixelformat) {
            mWidth = width;
            mHeight = height;
            mFramerate = framerate;
            mPixelFormat = pixelformat;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getHeight() {
            return mHeight;
        }

        public int getFramerate() {
            return mFramerate;
        }

        public int getPixelFormat() {
            return mPixelFormat;
        }
    }

    protected Camera mCamera;
    protected CaptureFormat mCaptureFormat = null;
    // Lock to mutually exclude execution of OnPreviewFrame {start/stop}Capture.
    protected ReentrantLock mPreviewBufferLock = new ReentrantLock();
    protected Context mContext = null;
    // True when native code has started capture.
    protected boolean mIsRunning = false;

    protected int mId;
    // Native callback context variable.
    protected long mNativeVideoCaptureDeviceAndroid;
    protected int[] mGlTextures = null;
    protected SurfaceTexture mSurfaceTexture = null;
    protected static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

    protected int mCameraOrientation;
    protected int mCameraFacing;
    protected int mDeviceOrientation;
    private static final String TAG = "VideoCapture";

    VideoCapture(Context context,
                 int id,
                 long nativeVideoCaptureDeviceAndroid) {
        mContext = context;
        mId = id;
        mNativeVideoCaptureDeviceAndroid = nativeVideoCaptureDeviceAndroid;
    }

    @CalledByNative
    boolean allocate(int width, int height, int frameRate) {
        Log.d(TAG, "allocate: requested (" + width + "x" + height + ")@" +
                frameRate + "fps");
        try {
            mCamera = Camera.open(mId);
        } catch (RuntimeException ex) {
            Log.e(TAG, "allocate: Camera.open: " + ex);
            return false;
        }

        Camera.CameraInfo cameraInfo = getCameraInfo(mId);
        if (cameraInfo == null) {
            mCamera.release();
            mCamera = null;
            return false;
        }

        mCameraOrientation = cameraInfo.orientation;
        mCameraFacing = cameraInfo.facing;
        mDeviceOrientation = getDeviceOrientation();
        Log.d(TAG, "allocate: orientation dev=" + mDeviceOrientation +
                  ", cam=" + mCameraOrientation + ", facing=" + mCameraFacing);

        Camera.Parameters parameters = getCameraParameters(mCamera);
        if (parameters == null) {
            mCamera = null;
            return false;
        }

        // getSupportedPreviewFpsRange() returns a List with at least one
        // element, but when camera is in bad state, it can return null pointer.
        List<int[]> listFpsRange = parameters.getSupportedPreviewFpsRange();
        if (listFpsRange == null || listFpsRange.size() == 0) {
            Log.e(TAG, "allocate: no fps range found");
            return false;
        }
        int frameRateInMs = frameRate * 1000;
        // Use the first range as default.
        int[] fpsMinMax = listFpsRange.get(0);
        int newFrameRate = (fpsMinMax[0] + 999) / 1000;
        for (int[] fpsRange : listFpsRange) {
            if (fpsRange[0] <= frameRateInMs && frameRateInMs <= fpsRange[1]) {
                fpsMinMax = fpsRange;
                newFrameRate = frameRate;
                break;
            }
        }
        frameRate = newFrameRate;
        Log.d(TAG, "allocate: fps set to " + frameRate);

        // Calculate size.
        List<Camera.Size> listCameraSize =
                parameters.getSupportedPreviewSizes();
        int minDiff = Integer.MAX_VALUE;
        int matchedWidth = width;
        int matchedHeight = height;
        for (Camera.Size size : listCameraSize) {
            int diff = Math.abs(size.width - width) +
                       Math.abs(size.height - height);
            Log.d(TAG, "allocate: supported (" +
                  size.width + ", " + size.height + "), diff=" + diff);
            // TODO(wjia): Remove this hack (forcing width to be multiple
            // of 32) by supporting stride in video frame buffer.
            // Right now, VideoCaptureController requires compact YV12
            // (i.e., with no padding).
            if (diff < minDiff && (size.width % 32 == 0)) {
                minDiff = diff;
                matchedWidth = size.width;
                matchedHeight = size.height;
            }
        }
        if (minDiff == Integer.MAX_VALUE) {
            Log.e(TAG, "allocate: can not find a multiple-of-32 resolution");
            return false;
        }
        Log.d(TAG, "allocate: matched (" + matchedWidth + "x" + matchedHeight + ")");

        if (parameters.isVideoStabilizationSupported()) {
            Log.d(TAG, "Image stabilization supported, currently: " +
                  parameters.getVideoStabilization() + ", setting it.");
            parameters.setVideoStabilization(true);
        } else {
            Log.d(TAG, "Image stabilization not supported.");
        }

        setCaptureParameters(matchedWidth, matchedHeight, frameRate, parameters);
        parameters.setPreviewSize(mCaptureFormat.mWidth,
                                  mCaptureFormat.mHeight);
        parameters.setPreviewFpsRange(fpsMinMax[0], fpsMinMax[1]);
        parameters.setPreviewFormat(mCaptureFormat.mPixelFormat);
        mCamera.setParameters(parameters);

        // Set SurfaceTexture. Android Capture needs a SurfaceTexture even if
        // it is not going to be used.
        mGlTextures = new int[1];
        // Generate one texture pointer and bind it as an external texture.
        GLES20.glGenTextures(1, mGlTextures, 0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mGlTextures[0]);
        // No mip-mapping with camera source.
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        // Clamp to edge is only option.
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        mSurfaceTexture = new SurfaceTexture(mGlTextures[0]);
        mSurfaceTexture.setOnFrameAvailableListener(null);
        try {
            mCamera.setPreviewTexture(mSurfaceTexture);
        } catch (IOException ex) {
            Log.e(TAG, "allocate: " + ex);
            return false;
        }

        allocateBuffers();
        return true;
    }

    @CalledByNative
    public int startCapture() {
        if (mCamera == null) {
            Log.e(TAG, "startCapture: camera is null");
            return -1;
        }

        mPreviewBufferLock.lock();
        try {
            if (mIsRunning) {
                return 0;
            }
            mIsRunning = true;
        } finally {
            mPreviewBufferLock.unlock();
        }
        setPreviewCallback(this);
        try {
            mCamera.startPreview();
        } catch (RuntimeException ex) {
            Log.e(TAG, "startCapture: Camera.startPreview: " + ex);
            return -1;
        }
        return 0;
    }

    @CalledByNative
    public int stopCapture() {
        if (mCamera == null) {
            Log.e(TAG, "stopCapture: camera is null");
            return 0;
        }

        mPreviewBufferLock.lock();
        try {
            if (!mIsRunning) {
                return 0;
            }
            mIsRunning = false;
        } finally {
            mPreviewBufferLock.unlock();
        }

        mCamera.stopPreview();
        setPreviewCallback(null);
        return 0;
    }

    @CalledByNative
    public void deallocate() {
        if (mCamera == null)
            return;

        stopCapture();
        try {
            mCamera.setPreviewTexture(null);
            if (mGlTextures != null)
                GLES20.glDeleteTextures(1, mGlTextures, 0);
            mCaptureFormat = null;
            mCamera.release();
            mCamera = null;
        } catch (IOException ex) {
            Log.e(TAG, "deallocate: failed to deallocate camera, " + ex);
            return;
        }
    }

    // Local hook to allow derived classes to fill capture format and modify
    // camera parameters as they see fit.
    abstract void setCaptureParameters(
            int width,
            int height,
            int frameRate,
            Camera.Parameters cameraParameters);

    // Local hook to allow derived classes to configure and plug capture
    // buffers if needed.
    abstract void allocateBuffers();

    // Local method to be overriden with the particular setPreviewCallback to be
    // used in the implementations.
    abstract void setPreviewCallback(Camera.PreviewCallback cb);

    @CalledByNative
    public int queryWidth() {
        return mCaptureFormat.mWidth;
    }

    @CalledByNative
    public int queryHeight() {
        return mCaptureFormat.mHeight;
    }

    @CalledByNative
    public int queryFrameRate() {
        return mCaptureFormat.mFramerate;
    }

    @CalledByNative
    public int getColorspace() {
        switch (mCaptureFormat.mPixelFormat) {
            case ImageFormat.YV12:
                return AndroidImageFormatList.ANDROID_IMAGEFORMAT_YV12;
            case ImageFormat.NV21:
                return AndroidImageFormatList.ANDROID_IMAGEFORMAT_NV21;
            case ImageFormat.UNKNOWN:
            default:
                return AndroidImageFormatList.ANDROID_IMAGEFORMAT_UNKNOWN;
        }
    }

    protected int getDeviceOrientation() {
        int orientation = 0;
        if (mContext != null) {
            WindowManager wm = (WindowManager) mContext.getSystemService(
                    Context.WINDOW_SERVICE);
            switch(wm.getDefaultDisplay().getRotation()) {
                case Surface.ROTATION_90:
                    orientation = 90;
                    break;
                case Surface.ROTATION_180:
                    orientation = 180;
                    break;
                case Surface.ROTATION_270:
                    orientation = 270;
                    break;
                case Surface.ROTATION_0:
                default:
                    orientation = 0;
                    break;
            }
        }
        return orientation;
    }

    // Method for VideoCapture implementations to call back native code.
    public native void nativeOnFrameAvailable(
            long nativeVideoCaptureDeviceAndroid,
            byte[] data,
            int length,
            int rotation);

    protected static Camera.Parameters getCameraParameters(Camera camera) {
        Camera.Parameters parameters;
        try {
            parameters = camera.getParameters();
        } catch (RuntimeException ex) {
            Log.e(TAG, "getCameraParameters: Camera.getParameters: " + ex);
            camera.release();
            return null;
        }
        return parameters;
    }

    private Camera.CameraInfo getCameraInfo(int id) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        try {
            Camera.getCameraInfo(id, cameraInfo);
        } catch (RuntimeException ex) {
            Log.e(TAG, "getCameraInfo: Camera.getCameraInfo: " + ex);
            return null;
        }
        return cameraInfo;
    }
}
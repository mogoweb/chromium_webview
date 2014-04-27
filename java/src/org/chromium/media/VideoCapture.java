// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** This class implements the listener interface for receiving copies of preview
 * frames from the camera, plus a series of methods to manipulate camera and its
 * capture from the C++ side. Objects of this class are created via
 * createVideoCapture() and are explicitly owned by the creator. All methods
 * are invoked by this owner, including the callback OnPreviewFrame().
 **/
@JNINamespace("media")
public class VideoCapture implements PreviewCallback {
    static class CaptureFormat {
        public CaptureFormat(
                int width, int height, int framerate, int pixelformat) {
            mWidth = width;
            mHeight = height;
            mFramerate = framerate;
            mPixelFormat = pixelformat;
        }
        public int mWidth;
        public int mHeight;
        public final int mFramerate;
        public final int mPixelFormat;
        @CalledByNative("CaptureFormat")
        public int getWidth() {
            return mWidth;
        }
        @CalledByNative("CaptureFormat")
        public int getHeight() {
            return mHeight;
        }
        @CalledByNative("CaptureFormat")
        public int getFramerate() {
            return mFramerate;
        }
        @CalledByNative("CaptureFormat")
        public int getPixelFormat() {
            return mPixelFormat;
        }
    }

    // Some devices don't support YV12 format correctly, even with JELLY_BEAN or
    // newer OS. To work around the issues on those devices, we have to request
    // NV21. Some other devices have troubles with certain capture resolutions
    // under a given one: for those, the resolution is swapped with a known
    // good. Both are supposed to be temporary hacks.
    private static class BuggyDeviceHack {
        private static class IdAndSizes {
            IdAndSizes(String model, String device, int minWidth, int minHeight) {
                mModel = model;
                mDevice = device;
                mMinWidth = minWidth;
                mMinHeight = minHeight;
            }
            public final String mModel;
            public final String mDevice;
            public final int mMinWidth;
            public final int mMinHeight;
        }
        private static final IdAndSizes s_CAPTURESIZE_BUGGY_DEVICE_LIST[] = {
            new IdAndSizes("Nexus 7", "flo", 640, 480)
        };

        private static final String[] s_COLORSPACE_BUGGY_DEVICE_LIST = {
            "SAMSUNG-SGH-I747",
            "ODROID-U2",
        };

        static void applyMinDimensions(CaptureFormat format) {
            // NOTE: this can discard requested aspect ratio considerations.
            for (IdAndSizes buggyDevice : s_CAPTURESIZE_BUGGY_DEVICE_LIST) {
                if (buggyDevice.mModel.contentEquals(android.os.Build.MODEL) &&
                        buggyDevice.mDevice.contentEquals(android.os.Build.DEVICE)) {
                    format.mWidth = (buggyDevice.mMinWidth > format.mWidth)
                                        ? buggyDevice.mMinWidth
                                        : format.mWidth;
                    format.mHeight = (buggyDevice.mMinHeight > format.mHeight)
                                         ? buggyDevice.mMinHeight
                                         : format.mHeight;
                }
            }
        }

        static int getImageFormat() {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                return ImageFormat.NV21;
            }

            for (String buggyDevice : s_COLORSPACE_BUGGY_DEVICE_LIST) {
                if (buggyDevice.contentEquals(android.os.Build.MODEL)) {
                    return ImageFormat.NV21;
                }
            }
            return ImageFormat.YV12;
        }
    }

    private Camera mCamera;
    public ReentrantLock mPreviewBufferLock = new ReentrantLock();
    private Context mContext = null;
    // True when native code has started capture.
    private boolean mIsRunning = false;

    private static final int NUM_CAPTURE_BUFFERS = 3;
    private int mExpectedFrameSize = 0;
    private int mId = 0;
    // Native callback context variable.
    private long mNativeVideoCaptureDeviceAndroid = 0;
    private int[] mGlTextures = null;
    private SurfaceTexture mSurfaceTexture = null;
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

    private int mCameraOrientation = 0;
    private int mCameraFacing = 0;
    private int mDeviceOrientation = 0;

    CaptureFormat mCaptureFormat = null;
    private static final String TAG = "VideoCapture";

    @CalledByNative
    public static VideoCapture createVideoCapture(
            Context context, int id, long nativeVideoCaptureDeviceAndroid) {
        return new VideoCapture(context, id, nativeVideoCaptureDeviceAndroid);
    }

    @CalledByNative
    public static CaptureFormat[] getDeviceSupportedFormats(int id) {
        Camera camera;
        try {
             camera = Camera.open(id);
        } catch (RuntimeException ex) {
            Log.e(TAG, "Camera.open: " + ex);
            return null;
        }
        Camera.Parameters parameters = camera.getParameters();

        ArrayList<CaptureFormat> formatList = new ArrayList<CaptureFormat>();
        // getSupportedPreview{Formats,FpsRange,PreviewSizes}() returns Lists
        // with at least one element, but when the camera is in bad state, they
        // can return null pointers; in that case we use a 0 entry, so we can
        // retrieve as much information as possible.
        List<Integer> pixelFormats = parameters.getSupportedPreviewFormats();
        if (pixelFormats == null) {
            pixelFormats = new ArrayList<Integer>();
        }
        if (pixelFormats.size() == 0) {
            pixelFormats.add(ImageFormat.UNKNOWN);
        }
        for (Integer previewFormat : pixelFormats) {
            int pixelFormat =
                    AndroidImageFormatList.ANDROID_IMAGEFORMAT_UNKNOWN;
            if (previewFormat == ImageFormat.YV12) {
                pixelFormat = AndroidImageFormatList.ANDROID_IMAGEFORMAT_YV12;
            } else if (previewFormat == ImageFormat.NV21) {
                continue;
            }

            List<int[]> listFpsRange = parameters.getSupportedPreviewFpsRange();
            if (listFpsRange == null) {
                listFpsRange = new ArrayList<int[]>();
            }
            if (listFpsRange.size() == 0) {
                listFpsRange.add(new int[] {0, 0});
            }
            for (int[] fpsRange : listFpsRange) {
                List<Camera.Size> supportedSizes =
                        parameters.getSupportedPreviewSizes();
                if (supportedSizes == null) {
                    supportedSizes = new ArrayList<Camera.Size>();
                }
                if (supportedSizes.size() == 0) {
                    supportedSizes.add(camera.new Size(0, 0));
                }
                for (Camera.Size size : supportedSizes) {
                    formatList.add(new CaptureFormat(size.width, size.height,
                            (fpsRange[0] + 999 ) / 1000, pixelFormat));
                }
            }
        }
        camera.release();
        return formatList.toArray(new CaptureFormat[formatList.size()]);
    }

    public VideoCapture(
            Context context, int id, long nativeVideoCaptureDeviceAndroid) {
        mContext = context;
        mId = id;
        mNativeVideoCaptureDeviceAndroid = nativeVideoCaptureDeviceAndroid;
    }

    // Returns true on success, false otherwise.
    @CalledByNative
    public boolean allocate(int width, int height, int frameRate) {
        Log.d(TAG, "allocate: requested (" + width + "x" + height + ")@" +
                 frameRate + "fps");
        try {
            mCamera = Camera.open(mId);
        } catch (RuntimeException ex) {
            Log.e(TAG, "allocate: Camera.open: " + ex);
            return false;
        }

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mId, cameraInfo);
        mCameraOrientation = cameraInfo.orientation;
        mCameraFacing = cameraInfo.facing;
        mDeviceOrientation = getDeviceOrientation();
        Log.d(TAG, "allocate: orientation dev=" + mDeviceOrientation +
                  ", cam=" + mCameraOrientation + ", facing=" + mCameraFacing);

        Camera.Parameters parameters = mCamera.getParameters();

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

        mCaptureFormat = new CaptureFormat(
                matchedWidth, matchedHeight, frameRate,
                BuggyDeviceHack.getImageFormat());
        // Hack to avoid certain capture resolutions under a minimum one,
        // see http://crbug.com/305294
        BuggyDeviceHack.applyMinDimensions(mCaptureFormat);
        Log.d(TAG, "allocate: matched (" + mCaptureFormat.mWidth + "x" +
                mCaptureFormat.mHeight + ")");

        if (parameters.isVideoStabilizationSupported()) {
            Log.d(TAG, "Image stabilization supported, currently: "
                  + parameters.getVideoStabilization() + ", setting it.");
            parameters.setVideoStabilization(true);
        } else {
            Log.d(TAG, "Image stabilization not supported.");
        }
        parameters.setPreviewSize(mCaptureFormat.mWidth,
                                  mCaptureFormat.mHeight);
        parameters.setPreviewFormat(mCaptureFormat.mPixelFormat);
        parameters.setPreviewFpsRange(fpsMinMax[0], fpsMinMax[1]);
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

        int bufSize = mCaptureFormat.mWidth *
                      mCaptureFormat.mHeight *
                      ImageFormat.getBitsPerPixel(
                              mCaptureFormat.mPixelFormat) / 8;
        for (int i = 0; i < NUM_CAPTURE_BUFFERS; i++) {
            byte[] buffer = new byte[bufSize];
            mCamera.addCallbackBuffer(buffer);
        }
        mExpectedFrameSize = bufSize;

        return true;
    }

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
        mCamera.setPreviewCallbackWithBuffer(this);
        mCamera.startPreview();
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
        mCamera.setPreviewCallbackWithBuffer(null);
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

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mPreviewBufferLock.lock();
        try {
            if (!mIsRunning) {
                return;
            }
            if (data.length == mExpectedFrameSize) {
                int rotation = getDeviceOrientation();
                if (rotation != mDeviceOrientation) {
                    mDeviceOrientation = rotation;
                    Log.d(TAG,
                          "onPreviewFrame: device orientation=" +
                          mDeviceOrientation + ", camera orientation=" +
                          mCameraOrientation);
                }
                if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    rotation = 360 - rotation;
                }
                rotation = (mCameraOrientation + rotation) % 360;
                nativeOnFrameAvailable(mNativeVideoCaptureDeviceAndroid,
                        data, mExpectedFrameSize, rotation);
            }
        } finally {
            mPreviewBufferLock.unlock();
            if (camera != null) {
                camera.addCallbackBuffer(data);
            }
        }
    }

    // TODO(wjia): investigate whether reading from texture could give better
    // performance and frame rate, using onFrameAvailable().

    private static class ChromiumCameraInfo {
        private final int mId;
        private final Camera.CameraInfo mCameraInfo;

        private ChromiumCameraInfo(int index) {
            mId = index;
            mCameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(index, mCameraInfo);
        }

        @CalledByNative("ChromiumCameraInfo")
        private static int getNumberOfCameras() {
            return Camera.getNumberOfCameras();
        }

        @CalledByNative("ChromiumCameraInfo")
        private static ChromiumCameraInfo getAt(int index) {
            return new ChromiumCameraInfo(index);
        }

        @CalledByNative("ChromiumCameraInfo")
        private int getId() {
            return mId;
        }

        @CalledByNative("ChromiumCameraInfo")
        private String getDeviceName() {
            return  "camera " + mId + ", facing " +
                    (mCameraInfo.facing ==
                     Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back");
        }

        @CalledByNative("ChromiumCameraInfo")
        private int getOrientation() {
            return mCameraInfo.orientation;
        }
    }

    private native void nativeOnFrameAvailable(
            long nativeVideoCaptureDeviceAndroid,
            byte[] data,
            int length,
            int rotation);

    private int getDeviceOrientation() {
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
}

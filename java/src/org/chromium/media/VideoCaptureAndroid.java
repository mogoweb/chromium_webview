// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * This class extends the VideoCapture base class for manipulating normal video
 * capture devices in Android, including receiving copies of preview frames via
 * Java-allocated buffers. It also includes class BuggyDeviceHack to deal with
 * troublesome devices.
 **/
public class VideoCaptureAndroid extends VideoCapture {

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
                            ? buggyDevice.mMinWidth : format.mWidth;
                    format.mHeight = (buggyDevice.mMinHeight > format.mHeight)
                            ? buggyDevice.mMinHeight : format.mHeight;
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

    private int mExpectedFrameSize;
    private static final int NUM_CAPTURE_BUFFERS = 3;
    private static final String TAG = "VideoCaptureAndroid";

    static CaptureFormat[] getDeviceSupportedFormats(int id) {
        Camera camera;
        try {
             camera = Camera.open(id);
        } catch (RuntimeException ex) {
            Log.e(TAG, "Camera.open: " + ex);
            return null;
        }
        Camera.Parameters parameters = getCameraParameters(camera);
        if (parameters == null) {
            return null;
        }

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
                    formatList.add(new CaptureFormat(size.width,
                                                     size.height,
                                                     (fpsRange[0] + 999) / 1000,
                                                     pixelFormat));
                }
            }
        }
        camera.release();
        return formatList.toArray(new CaptureFormat[formatList.size()]);
    }

    VideoCaptureAndroid(Context context,
                        int id,
                        long nativeVideoCaptureDeviceAndroid) {
        super(context, id, nativeVideoCaptureDeviceAndroid);
    }

    @Override
    protected void setCaptureParameters(
            int width,
            int height,
            int frameRate,
            Camera.Parameters cameraParameters) {
        mCaptureFormat = new CaptureFormat(
                width, height, frameRate, BuggyDeviceHack.getImageFormat());
        // Hack to avoid certain capture resolutions under a minimum one,
        // see http://crbug.com/305294.
        BuggyDeviceHack.applyMinDimensions(mCaptureFormat);
    }

    @Override
    protected void allocateBuffers() {
        mExpectedFrameSize = mCaptureFormat.mWidth * mCaptureFormat.mHeight *
                ImageFormat.getBitsPerPixel(mCaptureFormat.mPixelFormat) / 8;
        for (int i = 0; i < NUM_CAPTURE_BUFFERS; i++) {
            byte[] buffer = new byte[mExpectedFrameSize];
            mCamera.addCallbackBuffer(buffer);
        }
    }

    @Override
    protected void setPreviewCallback(Camera.PreviewCallback cb) {
        mCamera.setPreviewCallbackWithBuffer(cb);
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
}

// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
// Needed for jni_generator.py to guess correctly the origin of
// VideoCapture.CaptureFormat.
import org.chromium.media.VideoCapture;

/**
 * This class implements a factory of Android Video Capture objects for Chrome.
 * The static createVideoCapture() returns either a "normal" VideoCaptureAndroid
 * or a "special" VideoCaptureTango. Cameras are identified by |id|, where Tango
 * cameras have |id| above the standard ones. Video Capture objects allocated
 * via createVideoCapture() are explicitly owned by the caller.
 * ChromiumCameraInfo is an internal class with some static methods needed from
 * the native side to enumerate devices and collect their names and info. It
 * takes into account the mentioned special devices.
 **/
@JNINamespace("media")
class VideoCaptureFactory {

    static class CamParams {
        final int mId;
        final String mName;
        final int mWidth;
        final int mHeight;

        CamParams(int id, String name, int width, int height) {
            mId = id;
            mName = name;
            mWidth = width;
            mHeight = height;
        }
    }

    static class ChromiumCameraInfo {
        private final int mId;
        private final Camera.CameraInfo mCameraInfo;
        // Special devices have more cameras than usual. Those devices are
        // identified by model & device. Currently only the Tango is supported.
        // Note that these devices have no Camera.CameraInfo.
        private static final String[][] s_SPECIAL_DEVICE_LIST = {
            {"Peanut", "peanut"},
        };
        private static final String TAG = "ChromiumCameraInfo";

        private static int sNumberOfSystemCameras = -1;

        private static boolean isSpecialDevice() {
            for (String[] device : s_SPECIAL_DEVICE_LIST) {
                if (device[0].contentEquals(android.os.Build.MODEL) &&
                        device[1].contentEquals(android.os.Build.DEVICE)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isSpecialCamera(int id) {
            return id >= sNumberOfSystemCameras;
        }

        private static int toSpecialCameraId(int id) {
            assert isSpecialCamera(id);
            return id - sNumberOfSystemCameras;
        }

        private ChromiumCameraInfo(int index) {
            mId = index;
            mCameraInfo = isSpecialCamera(index) ? null : getCameraInfo(mId);
        }

        @CalledByNative("ChromiumCameraInfo")
        private static int getNumberOfCameras(Context appContext) {
            // Camera.getNumberOfCammeras() will not fail without permission, but the
            // following operation on camera will do. Without permission isn't fatal
            // error in WebView, specially for those application which has no purpose
            // to use camera, but happens to load page required it.
            // So, we output a warning log and pretend system have no camera at all.
            if (sNumberOfSystemCameras == -1) {
                if (PackageManager.PERMISSION_GRANTED ==
                        appContext.getPackageManager().checkPermission(
                                "android.permission.CAMERA", appContext.getPackageName())) {
                    sNumberOfSystemCameras = Camera.getNumberOfCameras();
                } else {
                    sNumberOfSystemCameras = 0;
                    Log.w(TAG, "Missing android.permission.CAMERA permission, "
                            + "no system camera available.");
                }
            }
            if (isSpecialDevice()) {
                Log.d(TAG, "Special device: " + android.os.Build.MODEL);
                return sNumberOfSystemCameras +
                       VideoCaptureTango.numberOfCameras();
            } else {
                return sNumberOfSystemCameras;
            }
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
            if (isSpecialCamera(mId)) {
                return VideoCaptureTango.getCamParams(toSpecialCameraId(mId)).mName;
            } else {
                if (mCameraInfo == null) {
                    return "";
                }
                Log.d(TAG, "Camera enumerated: " + (mCameraInfo.facing ==
                        Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" :
                        "back"));
                return "camera " + mId + ", facing " + (mCameraInfo.facing ==
                        Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" :
                        "back");
            }
        }

        @CalledByNative("ChromiumCameraInfo")
        private int getOrientation() {
            if (isSpecialCamera(mId)) {
                return Camera.CameraInfo.CAMERA_FACING_BACK;
            } else {
                return (mCameraInfo == null ? 0 : mCameraInfo.orientation);
            }
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

    // Factory methods.
    @CalledByNative
    static VideoCapture createVideoCapture(
            Context context, int id, long nativeVideoCaptureDeviceAndroid) {
      if (ChromiumCameraInfo.isSpecialCamera(id)) {
          return new VideoCaptureTango(context, ChromiumCameraInfo.toSpecialCameraId(id),
                  nativeVideoCaptureDeviceAndroid);
      } else {
          return new VideoCaptureAndroid(context, id,
                  nativeVideoCaptureDeviceAndroid);
      }
    }

    @CalledByNative
    static VideoCapture.CaptureFormat[] getDeviceSupportedFormats(int id) {
        return ChromiumCameraInfo.isSpecialCamera(id) ?
                VideoCaptureTango.getDeviceSupportedFormats(
                        ChromiumCameraInfo.toSpecialCameraId(id)) :
                VideoCaptureAndroid.getDeviceSupportedFormats(id);
    }

    @CalledByNative
    static int getCaptureFormatWidth(VideoCapture.CaptureFormat format) {
        return format.getWidth();
    }

    @CalledByNative
    static int getCaptureFormatHeight(VideoCapture.CaptureFormat format) {
        return format.getHeight();
    }

    @CalledByNative
    static int getCaptureFormatFramerate(VideoCapture.CaptureFormat format) {
        return format.getFramerate();
    }

    @CalledByNative
    static int getCaptureFormatPixelFormat(VideoCapture.CaptureFormat format) {
        return format.getPixelFormat();
    }
}

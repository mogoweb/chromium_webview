// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.common.ScreenOrientationValues;

/**
 * This is the implementation of the C++ counterpart ScreenOrientationProvider.
 */
@JNINamespace("content")
class ScreenOrientationProvider {
    private static final String TAG = "ScreenOrientationProvider";

    private int getOrientationFromWebScreenOrientations(byte orientations) {
        switch (orientations) {
            case ScreenOrientationValues.DEFAULT:
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
            case ScreenOrientationValues.PORTRAIT_PRIMARY:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case ScreenOrientationValues.PORTRAIT_SECONDARY:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case ScreenOrientationValues.LANDSCAPE_PRIMARY:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case ScreenOrientationValues.LANDSCAPE_SECONDARY:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            case ScreenOrientationValues.PORTRAIT:
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
            case ScreenOrientationValues.LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            case ScreenOrientationValues.ANY:
                return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
            default:
                Log.w(TAG, "Trying to lock to unsupported orientation!");
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }

    @VisibleForTesting
    @CalledByNative
    static ScreenOrientationProvider create() {
        return new ScreenOrientationProvider();
    }

    @CalledByNative
    void lockOrientation(byte orientations) {
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (activity == null) {
            return;
        }

        int orientation = getOrientationFromWebScreenOrientations(orientations);
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            return;
        }

        activity.setRequestedOrientation(orientation);
    }

    @CalledByNative
    void unlockOrientation() {
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (activity == null) {
            return;
        }

        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private ScreenOrientationProvider() {
    }
}

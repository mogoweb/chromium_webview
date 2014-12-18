// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.base;

import android.content.Context;
import android.content.pm.PackageManager;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Simple proxy to let us query the touch device from C++
 */
@JNINamespace("ui")
public class TouchDevice {

    /**
     * Static methods only so make constructor private.
     */
    private TouchDevice() { }

    /**
     * Returns the number of supported touch points.
     *
     * @return Maximum supported touch points.
     */
    @CalledByNative
    private static int maxTouchPoints(Context context) {
        // Android only tells us if the device belongs to a "Touchscreen Class"
        // which only guarantees a minimum number of touch points. Be
        // conservative and return the minimum, checking membership from the
        // highest class down.
        if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND)) {
            return 5;
        } else if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)) {
            return 2;
        } else if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)) {
            return 2;
        } else if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN)) {
            return 1;
        } else {
            return 0;
        }
    }
}

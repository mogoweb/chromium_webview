// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.CalledByNative;

/**
 * Utilities to support startup metrics - Android version.
 */
public class UmaUtils {

    private static long sApplicationStartWallClockMs;

    /**
     * Record the time at which the activity started. This should be called asap after
     * the start of the activity's onCreate function.
     */
    public static void recordMainEntryPointTime() {
        // We can't simply pass this down through a JNI call, since the JNI for chrome
        // isn't initialized until we start the native content browser component, and we
        // then need the start time in the C++ side before we return to Java. As such we
        // save it in a static that the C++ can fetch once it has initialized the JNI.
        sApplicationStartWallClockMs = System.currentTimeMillis();
    }

    @CalledByNative
    private static long getMainEntryPointTime() {
        return sApplicationStartWallClockMs;
    }

}

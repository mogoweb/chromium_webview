// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;

import org.chromium.base.CommandLine;
import org.chromium.content.common.ContentSwitches;

/**
 * A utility class that has helper methods for device configuration.
 */
public class DeviceUtils {

    /**
     * The minimum width that would classify the device as a tablet.
     */
    private static final int MINIMUM_TABLET_WIDTH_DP = 600;

    private static Boolean sIsTablet = null;

    /**
     * @param context Android's context
     * @return        Whether the app is should treat the device as a tablet for layout.
     */
    public static boolean isTablet(Context context) {
        if (sIsTablet == null) {
            int minimumScreenWidthDp = context.getResources().getConfiguration().
                    smallestScreenWidthDp;
            sIsTablet = minimumScreenWidthDp >= MINIMUM_TABLET_WIDTH_DP;
        }
        return sIsTablet;
    }

    /**
     * Appends the switch specifying which user agent should be used for this device.
     * @param context The context for the caller activity.
     */
    public static void addDeviceSpecificUserAgentSwitch(Context context) {
        if (isTablet(context)) {
            CommandLine.getInstance().appendSwitch(ContentSwitches.TABLET_UI);
        } else {
            CommandLine.getInstance().appendSwitch(ContentSwitches.USE_MOBILE_UA);
        }
    }
}

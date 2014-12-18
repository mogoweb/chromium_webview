// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;

import org.chromium.base.CommandLine;
import org.chromium.content.common.ContentSwitches;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * A utility class that has helper methods for device configuration.
 */
public class DeviceUtils {

    /**
     * @param context Android's context
     * @return        Whether the app is should treat the device as a tablet for layout.
     */
    // TODO(tedchoc): Transition all call sites to use DeviceFormFactor directly.  Then
    //                remove this method.
    public static boolean isTablet(Context context) {
        return DeviceFormFactor.isTablet(context);
    }

    /**
     * Appends the switch specifying which user agent should be used for this device.
     * @param context The context for the caller activity.
     */
    public static void addDeviceSpecificUserAgentSwitch(Context context) {
        if (!isTablet(context)) {
            CommandLine.getInstance().appendSwitch(ContentSwitches.USE_MOBILE_UA);
        }
    }
}

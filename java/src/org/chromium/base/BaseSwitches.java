// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

/**
 * Contains all of the command line switches that are specific to the base/
 * portion of Chromium on Android.
 */
public abstract class BaseSwitches {
    // Block onCreate() of Chrome until a Java debugger is attached.
    public static final String WAIT_FOR_JAVA_DEBUGGER = "wait-for-java-debugger";

    // Overrides low-end device detection, disabling low-end device optimizations.
    public static final String DISABLE_LOW_END_DEVICE_MODE = "disable-low-end-device-mode";

    // Overrides low-end device detection, enabling low-end device optimizations.
    public static final String ENABLE_LOW_END_DEVICE_MODE = "enable-low-end-device-mode";

    // Adds additional thread idle time information into the trace event output.
    public static final String ENABLE_IDLE_TRACING = "enable-idle-tracing";

    // Prevent instantiation.
    private BaseSwitches() {}
}

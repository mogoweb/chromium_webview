// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.ThreadUtils;

import java.util.concurrent.FutureTask;

/**
 * Implements the Java side of LocationProviderAndroid.
 * Delegates all real functionality to the implementation
 * returned from LocationProviderFactory.
 * See detailed documentation on
 * content/browser/geolocation/android_location_api_adapter.h.
 * Based on android.webkit.GeolocationService.java
 */
@VisibleForTesting
public class LocationProviderAdapter {

    // Delegate handling the real work in the main thread.
    private LocationProviderFactory.LocationProvider mImpl;

    private LocationProviderAdapter(Context context) {
        mImpl = LocationProviderFactory.get(context);
    }

    @CalledByNative
    static LocationProviderAdapter create(Context context) {
        return new LocationProviderAdapter(context);
    }

    /**
     * Start listening for location updates until we're told to quit. May be
     * called in any thread.
     * @param gpsEnabled Whether or not we're interested in high accuracy GPS.
     */
    @CalledByNative
    public boolean start(final boolean gpsEnabled) {
        FutureTask<Void> task = new FutureTask<Void>(new Runnable() {
            @Override
            public void run() {
                mImpl.start(gpsEnabled);
            }
        }, null);
        ThreadUtils.runOnUiThread(task);
        return true;
    }

    /**
     * Stop listening for location updates. May be called in any thread.
     */
    @CalledByNative
    public void stop() {
        FutureTask<Void> task = new FutureTask<Void>(new Runnable() {
            @Override
            public void run() {
                mImpl.stop();
            }
        }, null);
        ThreadUtils.runOnUiThread(task);
    }

    /**
     * Returns true if we are currently listening for location updates, false if not.
     * Must be called only in the UI thread.
     */
    public boolean isRunning() {
        assert ThreadUtils.runningOnUiThread();
        return mImpl.isRunning();
    }

    public static void newLocationAvailable(double latitude, double longitude, double timestamp,
            boolean hasAltitude, double altitude,
            boolean hasAccuracy, double accuracy,
            boolean hasHeading, double heading,
            boolean hasSpeed, double speed) {
        nativeNewLocationAvailable(latitude, longitude, timestamp, hasAltitude, altitude,
                hasAccuracy, accuracy, hasHeading, heading, hasSpeed, speed);
    }

    public static void newErrorAvailable(String message) {
        nativeNewErrorAvailable(message);
    }

    // Native functions
    private static native void nativeNewLocationAvailable(
            double latitude, double longitude, double timeStamp,
            boolean hasAltitude, double altitude,
            boolean hasAccuracy, double accuracy,
            boolean hasHeading, double heading,
            boolean hasSpeed, double speed);
    private static native void nativeNewErrorAvailable(String message);
}

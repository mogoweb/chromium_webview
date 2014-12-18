// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Android implementation of the battery status APIs.
 */
@JNINamespace("content")
class BatteryStatusManager {

    private static final String TAG = "BatteryStatusManager";

    // A reference to the application context in order to acquire the SensorService.
    private final Context mAppContext;
    private final IntentFilter mFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BatteryStatusManager.this.onReceive(intent);
        }
    };

    // Non-zero if and only if we're listening for events.
    // To avoid race conditions on the C++ side, access must be synchronized.
    private long mNativePtr;
    // The lock to access the mNativePtr.
    private final Object mNativePtrLock = new Object();

    private boolean mEnabled = false;

    protected BatteryStatusManager(Context context) {
        mAppContext = context.getApplicationContext();
    }

    @CalledByNative
    static BatteryStatusManager getInstance(Context appContext) {
        return new BatteryStatusManager(appContext);
    }

    /**
     * Start listening for intents
     * @return True on success.
     */
    @CalledByNative
    boolean start(long nativePtr) {
        synchronized (mNativePtrLock) {
            if (!mEnabled && mAppContext.registerReceiver(mReceiver, mFilter) != null) {
                // success
                mNativePtr = nativePtr;
                mEnabled = true;
            }
        }
        return mEnabled;
    }

    /**
     * Stop listening to intents.
     */
    @CalledByNative
    void stop() {
        synchronized (mNativePtrLock) {
            if (mEnabled) {
                mAppContext.unregisterReceiver(mReceiver);
                mNativePtr = 0;
                mEnabled = false;
            }
        }
    }

    @VisibleForTesting
    void onReceive(Intent intent) {
       if (!intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
           Log.e(TAG, "Unexpected intent.");
           return;
       }

       boolean present = ignoreBatteryPresentState() ?
               true : intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false);

       if (!present) {
           // No battery, return default values.
           gotBatteryStatus(true, 0, Double.POSITIVE_INFINITY, 1);
           return;
       }

       int current = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
       int max = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
       double level = (double)current / (double)max;
       if (level < 0 || level > 1) {
           // Sanity check, assume default value in this case.
           level = 1.0;
       }

       int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
       boolean charging = !(status == BatteryManager.BATTERY_STATUS_DISCHARGING);

       // TODO(timvolodine) : add proper projection for chargingTime, dischargingTime.
       double chargingTime = (status == BatteryManager.BATTERY_STATUS_FULL) ?
               0 : Double.POSITIVE_INFINITY;
       double dischargingTime = Double.POSITIVE_INFINITY;

       gotBatteryStatus(charging, chargingTime, dischargingTime, level);
    }

    /**
     * Returns whether the BatteryStatusManager should ignore the battery present state.
     * It is required for some devices that incorrectly set the EXTRA_PRESENT property.
     */
    protected boolean ignoreBatteryPresentState() {
        // BatteryManager.EXTRA_PRESENT appears to be unreliable on Galaxy Nexus,
        // Android 4.2.1, it always reports false. See crbug.com/384348.
        return Build.MODEL.equals("Galaxy Nexus");
    }

    protected void gotBatteryStatus(boolean charging, double chargingTime,
            double dischargingTime, double level) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotBatteryStatus(mNativePtr, charging, chargingTime, dischargingTime, level);
            }
        }
    }

    /**
     * Native JNI call
     * see content/browser/battery_status/battery_status_manager.cc
     */
    private native void nativeGotBatteryStatus(long nativeBatteryStatusManager,
            boolean charging, double chargingTime, double dischargingTime, double level);
}

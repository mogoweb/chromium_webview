// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Android implementation details for content::TimeZoneMonitorAndroid.
 */
@JNINamespace("content")
class TimeZoneMonitor {
    private static final String TAG = "TimeZoneMonitor";

    private final Context mAppContext;
    private final IntentFilter mFilter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           if (!intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
               Log.e(TAG, "unexpected intent");
               return;
           }

           nativeTimeZoneChangedFromJava(mNativePtr);
        }
    };

    private long mNativePtr;

    /**
     * Start listening for intents.
     * @param nativePtr The native content::TimeZoneMonitorAndroid to notify of time zone changes.
     */
    private TimeZoneMonitor(Context context, long nativePtr) {
        mAppContext = context.getApplicationContext();
        mNativePtr = nativePtr;
        mAppContext.registerReceiver(mBroadcastReceiver, mFilter);
    }

    @CalledByNative
    static TimeZoneMonitor getInstance(Context context, long nativePtr) {
        return new TimeZoneMonitor(context, nativePtr);
    }

    /**
     * Stop listening for intents.
     */
    @CalledByNative
    void stop() {
        mAppContext.unregisterReceiver(mBroadcastReceiver);
        mNativePtr = 0;
    }

    /**
     * Native JNI call to content::TimeZoneMonitorAndroid::TimeZoneChanged.
     * See content/browser/time_zone_monitor_android.cc.
     */
    private native void nativeTimeZoneChangedFromJava(long nativeTimeZoneMonitorAndroid);
}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import org.chromium.content.browser.LocationProviderAdapter;
import org.chromium.content.browser.LocationProviderFactory;

/**
 * A mock location provider. When started, runs a background thread that periodically
 * posts location updates. This does not involve any system Location APIs and thus
 * does not require any special permissions in the test app or on the device.
 */
public class MockLocationProvider implements LocationProviderFactory.LocationProvider {
    private boolean mIsRunning;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private static final Object mLock = new Object();

    private static final int UPDATE_LOCATION_MSG = 100;

    public MockLocationProvider() {
    }

    public void stopUpdates() {
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }

    @Override
    public void start(boolean gpsEnabled) {
        if (mIsRunning) return;

        if (mHandlerThread == null) {
            startMockLocationProviderThread();
        }

        mIsRunning = true;
        synchronized (mLock) {
            mHandler.sendEmptyMessage(UPDATE_LOCATION_MSG);
        }
    }

    @Override
    public void stop() {
        if (!mIsRunning) return;
        mIsRunning = false;
        synchronized (mLock) {
            mHandler.removeMessages(UPDATE_LOCATION_MSG);
        }
    }

    @Override
    public boolean isRunning() {
        return mIsRunning;
    }

    private void startMockLocationProviderThread() {
        assert mHandlerThread == null;
        assert mHandler == null;

        mHandlerThread = new HandlerThread("MockLocationProviderImpl");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                synchronized (mLock) {
                    if (msg.what == UPDATE_LOCATION_MSG) {
                        newLocation();
                        sendEmptyMessageDelayed(UPDATE_LOCATION_MSG, 250);
                    }
                }
            }
        };
    }

    private void newLocation() {
        LocationProviderAdapter.newLocationAvailable(
                0, 0, System.currentTimeMillis() / 1000.0,
                false, 0,
                true, 0.5,
                false, 0,
                false, 0);
    }
};


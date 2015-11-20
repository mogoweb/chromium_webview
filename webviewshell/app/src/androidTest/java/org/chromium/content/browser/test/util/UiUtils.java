// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import android.app.Activity;
import android.app.Instrumentation;

import junit.framework.Assert;

import static org.chromium.base.test.util.ScalableTimeout.scaleTimeout;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Collection of UI utilities.
 */
public class UiUtils {
    // timeout to wait for runOnUiThread()
    private static final long WAIT_FOR_RESPONSE_MS = scaleTimeout(10000);

    /**
     * Runs the runnable on the UI thread.
     *
     * @param activity The activity on which the runnable must run.
     * @param runnable The runnable to run.
     */
    public static void runOnUiThread(Activity activity, final Runnable runnable) {
        final Semaphore finishedSemaphore = new Semaphore(0);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                runnable.run();
                finishedSemaphore.release();
            }
        });
        try {
            Assert.assertTrue(finishedSemaphore.tryAcquire(1, WAIT_FOR_RESPONSE_MS,
                    TimeUnit.MILLISECONDS));
        } catch (InterruptedException ignored) {
            Assert.assertTrue("Interrupted while waiting for main thread Runnable", false);
        }
    }

    /**
     * Waits for the UI thread to settle down.
     * <p>
     * Waits for an extra period of time after the UI loop is idle.
     *
     * @param instrumentation Instrumentation object used by the test.
     */
    public static void settleDownUI(Instrumentation instrumentation) throws InterruptedException {
        instrumentation.waitForIdleSync();
        Thread.sleep(1000);
    }
}

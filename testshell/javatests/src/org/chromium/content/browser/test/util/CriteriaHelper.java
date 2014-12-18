// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import static org.chromium.base.test.util.ScalableTimeout.scaleTimeout;

import android.os.SystemClock;

import org.chromium.base.ThreadUtils;

import java.util.concurrent.Callable;

/**
 * Helper methods for creating and managing criteria.
 *
 * <p>
 * If possible, use callbacks or testing delegates instead of criteria as they
 * do not introduce any polling delays.  Should only use Criteria if no suitable
 * other approach exists.
 */
public class CriteriaHelper {

    /** The default maximum time to wait for a criteria to become valid. */
    public static final long DEFAULT_MAX_TIME_TO_POLL = scaleTimeout(3000);
    /** The default polling interval to wait between checking for a satisfied criteria. */
    public static final long DEFAULT_POLLING_INTERVAL = 50;

    /**
     * Checks whether the given Criteria is satisfied at a given interval, until either
     * the criteria is satisfied, or the specified maxTimeoutMs number of ms has elapsed.
     * @param criteria The Criteria that will be checked.
     * @param maxTimeoutMs The maximum number of ms that this check will be performed for
     * before timeout.
     * @param checkIntervalMs The number of ms between checks.
     * @return true iff checking has ended with the criteria being satisfied.
     * @throws InterruptedException
     */
    public static boolean pollForCriteria(Criteria criteria, long maxTimeoutMs,
            long checkIntervalMs) throws InterruptedException {
        boolean isSatisfied = criteria.isSatisfied();
        long startTime = SystemClock.uptimeMillis();
        while (!isSatisfied && SystemClock.uptimeMillis() - startTime < maxTimeoutMs) {
            Thread.sleep(checkIntervalMs);
            isSatisfied = criteria.isSatisfied();
        }
        return isSatisfied;
    }

    /**
     * Checks whether the given Criteria is satisfied polling at a default interval.
     *
     * @param criteria The Criteria that will be checked.
     * @return iff checking has ended with the criteria being satisfied.
     * @throws InterruptedException
     * @see #pollForCriteria(Criteria, long, long)
     */
    public static boolean pollForCriteria(Criteria criteria) throws InterruptedException {
        return pollForCriteria(criteria, DEFAULT_MAX_TIME_TO_POLL, DEFAULT_POLLING_INTERVAL);
    }

    /**
     * Checks whether the given Criteria is satisfied polling at a default interval on the UI
     * thread.
     * @param criteria The Criteria that will be checked.
     * @return iff checking has ended with the criteria being satisfied.
     * @throws InterruptedException
     * @see #pollForCriteria(Criteria)
     */
    public static boolean pollForUIThreadCriteria(final Criteria criteria)
            throws InterruptedException {
        final Callable<Boolean> callable = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return criteria.isSatisfied();
            }
        };

        return pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                return ThreadUtils.runOnUiThreadBlockingNoException(callable);
            }
        });
    }

    /**
     * Performs the runnable action, then checks whether the given criteria are satisfied
     * until the specified timeout, using the pollForCriteria method. If not, then the runnable
     * action is performed again, to a maximum of maxAttempts tries.
     */
    public static boolean runUntilCriteria(Runnable runnable, Criteria criteria,
            int maxAttempts, long maxTimeoutMs, long checkIntervalMs) throws InterruptedException {
        int count = 0;
        boolean success = false;
        while (count < maxAttempts && !success) {
            count++;
            runnable.run();
            success = pollForCriteria(criteria, maxTimeoutMs, checkIntervalMs);
        }
        return success;
    }
}

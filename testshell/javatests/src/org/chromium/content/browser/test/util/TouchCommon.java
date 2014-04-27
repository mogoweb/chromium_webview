// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.test.TouchUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Touch-related functionality reused across test cases.
 */
public class TouchCommon {
    private ActivityInstrumentationTestCase2 mActivityTestCase;

    // TODO(leandrogracia): This method should receive and use an activity
    // instead of the ActivityInstrumentationTestCase2. However this is causing
    // problems downstream. Any fix for this should be landed downstream first.
    public TouchCommon(ActivityInstrumentationTestCase2 activityTestCase) {
        mActivityTestCase = activityTestCase;
    }

    /**
     * Starts (synchronously) a drag motion. Normally followed by dragTo() and dragEnd().
     *
     * @param x
     * @param y
     * @param downTime (in ms)
     * @see TouchUtils
     */
    public void dragStart(float x, float y, long downTime) {
        MotionEvent event = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        dispatchTouchEvent(event);
    }

    /**
     * Drags / moves (synchronously) to the specified coordinates. Normally preceeded by
     * dragStart() and followed by dragEnd()
     *
     * @param fromX
     * @param toX
     * @param fromY
     * @param toY
     * @param stepCount
     * @param downTime (in ms)
     * @see TouchUtils
     */
    public void dragTo(float fromX, float toX, float fromY,
            float toY, int stepCount, long downTime) {
        float x = fromX;
        float y = fromY;
        float yStep = (toY - fromY) / stepCount;
        float xStep = (toX - fromX) / stepCount;
        for (int i = 0; i < stepCount; ++i) {
            y += yStep;
            x += xStep;
            long eventTime = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(downTime, eventTime,
                    MotionEvent.ACTION_MOVE, x, y, 0);
            dispatchTouchEvent(event);
        }
    }

    /**
     * Finishes (synchronously) a drag / move at the specified coordinate.
     * Normally preceeded by dragStart() and dragTo().
     *
     * @param x
     * @param y
     * @param downTime (in ms)
     * @see TouchUtils
     */
    public void dragEnd(float x, float y, long downTime) {
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_UP, x, y, 0);
        dispatchTouchEvent(event);
    }

    /**
     * Sends (synchronously) a single click to an absolute screen coordinates.
     *
     * @param x screen absolute
     * @param y screen absolute
     * @see TouchUtils
     */
    public void singleClick(float x, float y) {

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent event = MotionEvent.obtain(downTime, eventTime,
                                               MotionEvent.ACTION_DOWN, x, y, 0);
        dispatchTouchEvent(event);

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP,
                                   x, y, 0);
        dispatchTouchEvent(event);
    }

    /**
     * Sends (synchronously) a single click to the View at the specified coordinates.
     *
     * @param v The view to be clicked.
     * @param x Relative x location to v
     * @param y Relative y location to v
     */
    public void singleClickView(View v, int x, int y) {
        int location[] = getAbsoluteLocationFromRelative(v, x, y);
        int absoluteX = location[0];
        int absoluteY = location[1];
        singleClick(absoluteX, absoluteY);
    }

    /**
     * Sends (synchronously) a single click to the center of the View.
     */
    public void singleClickView(View v) {
        singleClickView(v, v.getWidth() / 2, v.getHeight() / 2);
    }

    /**
     * Sends (synchronously) a single click on the specified relative coordinates inside
     * a given view.
     *
     * @param view The view to be clicked.
     * @param x screen absolute
     * @param y screen absolute
     * @see TouchUtils
     */
    public void singleClickViewRelative(View view, int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent event = MotionEvent.obtain(downTime, eventTime,
                                               MotionEvent.ACTION_DOWN, x, y, 0);
        dispatchTouchEvent(view, event);

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP,
                                   x, y, 0);
        dispatchTouchEvent(view, event);
    }

    /**
     * Sends (synchronously) a long press to an absolute screen coordinates.
     *
     * @param x screen absolute
     * @param y screen absolute
     * @see TouchUtils
     */
    public void longPress(float x, float y) {

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent event = MotionEvent.obtain(downTime, eventTime,
                                               MotionEvent.ACTION_DOWN, x, y, 0);
        dispatchTouchEvent(event);

        int longPressTimeout = ViewConfiguration.get(
                mActivityTestCase.getActivity()).getLongPressTimeout();

        // Long press is flaky with just longPressTimeout. Doubling the time to be safe.
        SystemClock.sleep(longPressTimeout * 2);

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP,
                                   x, y, 0);
        dispatchTouchEvent(event);
    }

    /**
     * Sends (synchronously) a long press to the View at the specified coordinates.
     *
     * @param v The view to be clicked.
     * @param x Relative x location to v
     * @param y Relative y location to v
     */
    public void longPressView(View v, int x, int y) {
        int location[] = getAbsoluteLocationFromRelative(v, x, y);
        int absoluteX = location[0];
        int absoluteY = location[1];
        longPress(absoluteX, absoluteY);
    }

    /**
     * Send a MotionEvent to the root view of the activity.
     * @param event
     */
    private void dispatchTouchEvent(final MotionEvent event) {
        View view =
                mActivityTestCase.getActivity().findViewById(android.R.id.content).getRootView();
        dispatchTouchEvent(view, event);
    }

    /**
     * Send a MotionEvent to the specified view instead of the root view.
     * For example AutofillPopup window that is above the root view.
     * @param view The view that should receive the event.
     * @param event The view to be dispatched.
     */
    private void dispatchTouchEvent(final View view, final MotionEvent event) {
        try {
            mActivityTestCase.runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    view.dispatchTouchEvent(event);
                }
            });
        } catch (Throwable e) {
            throw new RuntimeException("Dispatching touch event failed", e);
        }
    }

    /**
     * Returns the absolute location in screen coordinates from location relative
     * to view.
     * @param v The view the coordinates are relative to.
     * @param x Relative x location.
     * @param y Relative y location.
     * @return absolute x and y location in an array.
     */
    private static int[] getAbsoluteLocationFromRelative(View v, int x, int y) {
        int location[] = new int[2];
        v.getLocationOnScreen(location);
        location[0] += x;
        location[1] += y;
        return location;
    }
}

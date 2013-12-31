// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerProperties;
import android.view.MotionEvent.PointerCoords;
import android.view.ViewConfiguration;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Provides a Java-side implementation for simulating touch gestures,
 * such as scroll or pinch.
 */
@JNINamespace("content")
public class GenericTouchGesture {
    private final ContentViewCore mContentViewCore;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private TimeAnimator mTimeAnimator;

    private int mNativePtr;
    private long mDownTime;

    private final byte STATE_INITIAL = 0;
    private final byte STATE_MOVING = 1;
    private final byte STATE_PENDING_UP = 2;
    private final byte STATE_FINAL = 3;
    private byte state = STATE_INITIAL;

    private static class TouchPointer {
        private final float mStartX;
        private final float mStartY;
        private final float mDeltaX;
        private final float mDeltaY;
        private final float mStepX;
        private final float mStepY;

        private PointerProperties mProperties;
        private PointerCoords mCoords;


        // Class representing a single pointer being moved over the screen.
        TouchPointer(int startX, int startY, int deltaX, int deltaY,
                int id, float scale, int scaledTouchSlop) {
            mStartX = startX * scale;
            mStartY = startY * scale;

            float scaledDeltaX = deltaX * scale;
            float scaledDeltaY = deltaY * scale;

            if (scaledDeltaX != 0 || scaledDeltaY != 0) {
                // The touch handler only considers a pointer as moving once
                // it's been moved by more than scaledTouchSlop pixels. We
                // thus increase the delta distance so the move is actually
                // registered as covering the specified distance.
                float distance = (float)Math.sqrt(scaledDeltaX * scaledDeltaX +
                        scaledDeltaY * scaledDeltaY);
                mDeltaX = scaledDeltaX * (1 + scaledTouchSlop / distance);
                mDeltaY = scaledDeltaY * (1 + scaledTouchSlop / distance);
            }
            else {
                mDeltaX = scaledDeltaX;
                mDeltaY = scaledDeltaY;
            }

            if (deltaX != 0 || deltaY != 0) {
                mStepX = mDeltaX / Math.abs(mDeltaX + mDeltaY);
                mStepY = mDeltaY / Math.abs(mDeltaX + mDeltaY);
            } else {
                mStepX = 0;
                mStepY = 0;
            }

            mProperties = new PointerProperties();
            mProperties.id = id;
            mProperties.toolType = MotionEvent.TOOL_TYPE_FINGER;

            mCoords = new PointerCoords();
            mCoords.x = mStartX;
            mCoords.y = mStartY;
            mCoords.pressure = 1.0f;
        }

        PointerProperties getProperties() {
            return mProperties;
        }

        PointerCoords getCoords() {
            return mCoords;
        }

        float getCurrentX() {
            return mCoords.x;
        }

        float getCurrentY() {
            return mCoords.y;
        }

        void moveBy(float delta) {
            mCoords.x += mStepX * delta;
            mCoords.y += mStepY * delta;
        }

        boolean hasArrived() {
            return Math.abs(mCoords.x - mStartX) >= Math.abs(mDeltaX) &&
                   Math.abs(mCoords.y - mStartY) >= Math.abs(mDeltaY);
        }
    }

    private TouchPointer[] mPointers;
    private final PointerProperties[] mPointerProperties;
    private final PointerCoords[] mPointerCoords;


    GenericTouchGesture(ContentViewCore contentViewCore,
            int startX, int startY, int deltaX, int deltaY) {
        mContentViewCore = contentViewCore;

        float scale = mContentViewCore.getRenderCoordinates().getDeviceScaleFactor();
        int scaledTouchSlop = getScaledTouchSlop();

        mPointers = new TouchPointer[1];
        mPointers[0] = new TouchPointer(startX, startY, deltaX, deltaY, 0,
            scale, scaledTouchSlop);

        mPointerProperties = new PointerProperties[1];
        mPointerProperties[0] = mPointers[0].getProperties();

        mPointerCoords = new PointerCoords[1];
        mPointerCoords[0] = mPointers[0].getCoords();
    }

    GenericTouchGesture(ContentViewCore contentViewCore,
            int startX0, int startY0, int deltaX0, int deltaY0,
            int startX1, int startY1, int deltaX1, int deltaY1) {
        mContentViewCore = contentViewCore;

        float scale = mContentViewCore.getRenderCoordinates().getDeviceScaleFactor();
        int scaledTouchSlop = getScaledTouchSlop();

        mPointers = new TouchPointer[2];
        mPointers[0] = new TouchPointer(startX0, startY0, deltaX0, deltaY0, 0,
            scale, scaledTouchSlop);
        mPointers[1] = new TouchPointer(startX1, startY1, deltaX1, deltaY1, 1,
            scale, scaledTouchSlop);

        mPointerProperties = new PointerProperties[2];
        mPointerProperties[0] = mPointers[0].getProperties();
        mPointerProperties[1] = mPointers[1].getProperties();

        mPointerCoords = new PointerCoords[2];
        mPointerCoords[0] = mPointers[0].getCoords();
        mPointerCoords[1] = mPointers[1].getCoords();
    }

    private int getScaledTouchSlop() {
        return ViewConfiguration.get(mContentViewCore.getContext()).getScaledTouchSlop();
    }

    @CalledByNative
    void start(int nativePtr) {
        assert mNativePtr == 0;
        mNativePtr = nativePtr;

        Runnable runnable = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ?
            createJBRunnable() : createPreJBRunnable();
        mHandler.post(runnable);
    }

    boolean sendEvent(long time) {
        switch (state) {
            case STATE_INITIAL: {
                mDownTime = SystemClock.uptimeMillis();

                // Touch the first pointer down. This initiates the gesture.
                MotionEvent event = MotionEvent.obtain(mDownTime, time,
                        MotionEvent.ACTION_DOWN,
                        mPointers[0].getCurrentX(), mPointers[0].getCurrentY(), 0);
                mContentViewCore.onTouchEvent(event);
                event.recycle();

                // If there are more pointers, touch them down too.
                if (mPointers.length > 1) {
                    event = MotionEvent.obtain(mDownTime, time,
                            MotionEvent.ACTION_POINTER_DOWN,
                            mPointers.length, mPointerProperties, mPointerCoords,
                            0, 0, 1, 1, 0, 0, 0, 0);
                    mContentViewCore.onTouchEvent(event);
                    event.recycle();
                }
                state = STATE_MOVING;
                break;
            }
            case STATE_MOVING: {
                float delta = nativeGetDelta(
                    mNativePtr, mContentViewCore.getRenderCoordinates().getDeviceScaleFactor());
                if (delta != 0) {
                    for (TouchPointer pointer : mPointers) {
                        pointer.moveBy((float)delta);
                    }
                    MotionEvent event = MotionEvent.obtain(mDownTime, time,
                            MotionEvent.ACTION_MOVE,
                            mPointers.length, mPointerProperties, mPointerCoords,
                            0, 0, 1, 1, 0, 0, 0, 0);
                    mContentViewCore.onTouchEvent(event);
                    event.recycle();
                }
                if (havePointersArrived())
                    state = STATE_PENDING_UP;
                break;
            }
            case STATE_PENDING_UP: {
                // If there are more than one pointers, lift them up first.
                if (mPointers.length > 1) {
                    MotionEvent event = MotionEvent.obtain(mDownTime, time,
                            MotionEvent.ACTION_POINTER_UP,
                            mPointers.length, mPointerProperties, mPointerCoords,
                            0, 0, 1, 1, 0, 0, 0, 0);
                    mContentViewCore.onTouchEvent(event);
                    event.recycle();
                }

                // Finally, lift the first pointer up to finish the gesture.
                MotionEvent event = MotionEvent.obtain(mDownTime, time,
                        MotionEvent.ACTION_UP,
                        mPointers[0].getCurrentX(), mPointers[0].getCurrentY(), 0);
                mContentViewCore.onTouchEvent(event);
                event.recycle();

                nativeSetHasFinished(mNativePtr);
                state = STATE_FINAL;
                break;
            }
        }
        return state != STATE_FINAL;
    }

    private boolean havePointersArrived() {
        boolean arrived = true;
        for (TouchPointer pointer : mPointers) {
            arrived = arrived && pointer.hasArrived();
        }
        return arrived;
    }

    private Runnable createJBRunnable() {
        // On JB, we rely on TimeAnimator to send events tied with vsync.
        return new Runnable() {
            @Override
            public void run() {
                mTimeAnimator = new TimeAnimator();
                mTimeAnimator.setTimeListener(new TimeListener() {
                    @Override
                    public void onTimeUpdate(TimeAnimator animation, long totalTime,
                            long deltaTime) {
                        if (!sendEvent(mDownTime + totalTime)) {
                            mTimeAnimator.end();
                        }
                    }
                });
                mTimeAnimator.start();
            }
        };
    }

    private Runnable createPreJBRunnable() {
        // Pre-JB there's no TimeAnimator, so we keep posting messages.
        return new Runnable() {
            @Override
            public void run() {
                if (sendEvent(SystemClock.uptimeMillis())) {
                    mHandler.post(this);
                }
            }
        };
    }

    private native float nativeGetDelta(
            int nativeGenericTouchGestureAndroid, float scale);
    private native void nativeSetHasFinished(int nativeGenericTouchGestureAndroid);
}

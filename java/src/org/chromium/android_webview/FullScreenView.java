// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.AbsoluteLayout;

/**
 * A view that is used to render the web contents in fullscreen mode, ie.
 * html controls and subtitles, over the {@link ContentVideoView}.
 */
public class FullScreenView extends AbsoluteLayout {

    private AwViewMethods mAwViewMethods;
    private InternalAccessAdapter mInternalAccessAdapter;

    public FullScreenView(Context context, AwViewMethods awViewMethods) {
        super(context);
        setAwViewMethods(awViewMethods);
        mInternalAccessAdapter = new InternalAccessAdapter();
    }

    public InternalAccessAdapter getInternalAccessAdapter() {
        return mInternalAccessAdapter;
    }

    public void setAwViewMethods(AwViewMethods awViewMethods) {
        mAwViewMethods = awViewMethods;
    }

    @Override
    public void onDraw(final Canvas canvas) {
        mAwViewMethods.onDraw(canvas);
    }

    @Override
    public void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        mAwViewMethods.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean requestFocus(final int direction, final Rect previouslyFocusedRect) {
        mAwViewMethods.requestFocus();
        return super.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public void setLayerType(int layerType, Paint paint) {
        super.setLayerType(layerType, paint);
        mAwViewMethods.setLayerType(layerType, paint);
    }

    @Override
    public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
        return mAwViewMethods.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        return mAwViewMethods.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        return mAwViewMethods.dispatchKeyEvent(event);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        return mAwViewMethods.onTouchEvent(event);
    }

    @Override
    public boolean onHoverEvent(final MotionEvent event) {
        return mAwViewMethods.onHoverEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(final MotionEvent event) {
        return mAwViewMethods.onGenericMotionEvent(event);
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        mAwViewMethods.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAwViewMethods.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAwViewMethods.onDetachedFromWindow();
    }

    @Override
    public void onWindowFocusChanged(final boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        mAwViewMethods.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    public void onFocusChanged(final boolean focused, final int direction,
            final Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        mAwViewMethods.onFocusChanged(
                focused, direction, previouslyFocusedRect);
    }

    @Override
    public void onSizeChanged(final int w, final int h, final int ow, final int oh) {
        super.onSizeChanged(w, h, ow, oh);
        mAwViewMethods.onSizeChanged(w, h, ow, oh);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        mAwViewMethods.onVisibilityChanged(changedView, visibility);
    }

    @Override
    public void onWindowVisibilityChanged(final int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mAwViewMethods.onWindowVisibilityChanged(visibility);
    }

    // AwContents.InternalAccessDelegate implementation --------------------------------------
    private class InternalAccessAdapter implements AwContents.InternalAccessDelegate {

        @Override
        public boolean drawChild(Canvas canvas, View child, long drawingTime) {
            // Intentional no-op
            return false;
        }

        @Override
        public boolean super_onKeyUp(int keyCode, KeyEvent event) {
            return FullScreenView.super.onKeyUp(keyCode, event);
        }

        @Override
        public boolean super_dispatchKeyEventPreIme(KeyEvent event) {
            return FullScreenView.super.dispatchKeyEventPreIme(event);
        }

        @Override
        public boolean super_dispatchKeyEvent(KeyEvent event) {
            return FullScreenView.super.dispatchKeyEvent(event);
        }

        @Override
        public boolean super_onGenericMotionEvent(MotionEvent event) {
            return FullScreenView.super.onGenericMotionEvent(event);
        }

        @Override
        public void super_onConfigurationChanged(Configuration newConfig) {
            // Intentional no-op
        }

        @Override
        public int super_getScrollBarStyle() {
            return FullScreenView.super.getScrollBarStyle();
        }

        @Override
        public boolean awakenScrollBars() {
            return false;
        }

        @Override
        public boolean super_awakenScrollBars(int startDelay, boolean invalidate) {
            return false;
        }

        @Override
        public void onScrollChanged(int lPix, int tPix, int oldlPix, int oldtPix) {
            // Intentional no-op.
        }

        @Override
        public void overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY,
                int scrollRangeX, int scrollRangeY, int maxOverScrollX,
                int maxOverScrollY, boolean isTouchEvent) {
            // Intentional no-op.
        }

        @Override
        public void super_scrollTo(int scrollX, int scrollY) {
            // Intentional no-op.
        }

        @Override
        public void setMeasuredDimension(int measuredWidth, int measuredHeight) {
            FullScreenView.this.setMeasuredDimension(measuredWidth, measuredHeight);
        }
    }
}

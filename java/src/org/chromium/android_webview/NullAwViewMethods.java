// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.chromium.android_webview.AwContents.InternalAccessDelegate;

/**
 * No-op implementation of {@link AwViewMethods} that follows the null object pattern.
 * This {@link NullAwViewMethods} is hooked up to the WebView in fullscreen mode, and
 * to the {@link FullScreenView} in embedded mode, but not to both at the same time.
 */
class NullAwViewMethods implements AwViewMethods {
    private AwContents mAwContents;
    private InternalAccessDelegate mInternalAccessAdapter;
    private View mContainerView;

    public NullAwViewMethods(
            AwContents awContents, InternalAccessDelegate internalAccessAdapter,
            View containerView) {
        mAwContents = awContents;
        mInternalAccessAdapter = internalAccessAdapter;
        mContainerView = containerView;
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawColor(mAwContents.getEffectiveBackgroundColor());
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // When the containerView is using the NullAwViewMethods then it is not
        // attached to the AwContents. As such, we don't have any contents to measure
        // and using the last measured dimension is the best we can do.
        mInternalAccessAdapter.setMeasuredDimension(
                mContainerView.getMeasuredWidth(), mContainerView.getMeasuredHeight());
    }

    @Override
    public void requestFocus() {
        // Intentional no-op.
    }

    @Override
    public void setLayerType(int layerType, Paint paint) {
        // Intentional no-op.
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return null; // Intentional no-op.
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false; // Intentional no-op.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return false; // Intentional no-op.
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false; // Intentional no-op.
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return false; // Intentional no-op.
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return false; // Intentional no-op.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Intentional no-op.
    }

    @Override
    public void onAttachedToWindow() {
        // Intentional no-op.
    }

    @Override
    public void onDetachedFromWindow() {
        // Intentional no-op.
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        // Intentional no-op.
    }

    @Override
    public void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        // Intentional no-op.
    }

    @Override
    public void onSizeChanged(int w, int h, int ow, int oh) {
        // Intentional no-op.
    }

    @Override
    public void onVisibilityChanged(View changedView, int visibility) {
        // Intentional no-op.
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        // Intentional no-op.
    }
}

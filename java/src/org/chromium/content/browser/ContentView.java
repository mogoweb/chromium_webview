// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;

import org.chromium.base.TraceEvent;

/**
 * The containing view for {@link ContentViewCore} that exists in the Android UI hierarchy and
 * exposes the various {@link View} functionality to it.
 */
public class ContentView extends FrameLayout
        implements ContentViewCore.InternalAccessDelegate, SmartClipProvider {

    private static final String TAG = "ContentView";

    protected final ContentViewCore mContentViewCore;

    /**
     * Creates an instance of a ContentView.
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param cvc A pointer to the content view core managing this content view.
     * @return A ContentView instance.
     */
    public static ContentView newInstance(Context context, ContentViewCore cvc) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return new ContentView(context, cvc);
        } else {
            return new JellyBeanContentView(context, cvc);
        }
    }

    protected ContentView(Context context, ContentViewCore cvc) {
        super(context, null, android.R.attr.webViewStyle);

        if (getScrollBarStyle() == View.SCROLLBARS_INSIDE_OVERLAY) {
            setHorizontalScrollBarEnabled(false);
            setVerticalScrollBarEnabled(false);
        }

        setFocusable(true);
        setFocusableInTouchMode(true);

        mContentViewCore = cvc;
    }

    // Needed by ContentViewCore.InternalAccessDelegate
    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return super.drawChild(canvas, child, drawingTime);
    }

    // Needed by ContentViewCore.InternalAccessDelegate
    @Override
    public void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        TraceEvent.begin();
        super.onSizeChanged(w, h, ow, oh);
        mContentViewCore.onSizeChanged(w, h, ow, oh);
        TraceEvent.end();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return mContentViewCore.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return mContentViewCore.onCheckIsTextEditor();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        TraceEvent.begin();
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        mContentViewCore.onFocusChanged(gainFocus);
        TraceEvent.end();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        mContentViewCore.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mContentViewCore.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        return mContentViewCore.dispatchKeyEventPreIme(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isFocused()) {
            return mContentViewCore.dispatchKeyEvent(event);
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mContentViewCore.onTouchEvent(event);
    }

    /**
     * Mouse move events are sent on hover enter, hover move and hover exit.
     * They are sent on hover exit because sometimes it acts as both a hover
     * move and hover exit.
     */
    @Override
    public boolean onHoverEvent(MotionEvent event) {
        boolean consumed = mContentViewCore.onHoverEvent(event);
        if (!mContentViewCore.isTouchExplorationEnabled()) super.onHoverEvent(event);
        return consumed;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mContentViewCore.onGenericMotionEvent(event);
    }

    @Override
    public boolean performLongClick() {
        return false;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        mContentViewCore.onConfigurationChanged(newConfig);
    }

    /**
     * Currently the ContentView scrolling happens in the native side. In
     * the Java view system, it is always pinned at (0, 0). scrollBy() and scrollTo()
     * are overridden, so that View's mScrollX and mScrollY will be unchanged at
     * (0, 0). This is critical for drawing ContentView correctly.
     */
    @Override
    public void scrollBy(int x, int y) {
        mContentViewCore.scrollBy(x, y);
    }

    @Override
    public void scrollTo(int x, int y) {
        mContentViewCore.scrollTo(x, y);
    }

    @Override
    protected int computeHorizontalScrollExtent() {
        // TODO(dtrainor): Need to expose scroll events properly to public. Either make getScroll*
        // work or expose computeHorizontalScrollOffset()/computeVerticalScrollOffset as public.
        return mContentViewCore.computeHorizontalScrollExtent();
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return mContentViewCore.computeHorizontalScrollOffset();
    }

    @Override
    protected int computeHorizontalScrollRange() {
        return mContentViewCore.computeHorizontalScrollRange();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return mContentViewCore.computeVerticalScrollExtent();
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mContentViewCore.computeVerticalScrollOffset();
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mContentViewCore.computeVerticalScrollRange();
    }

    // End FrameLayout overrides.

    @Override
    public boolean awakenScrollBars(int startDelay, boolean invalidate) {
        return mContentViewCore.awakenScrollBars(startDelay, invalidate);
    }

    @Override
    public boolean awakenScrollBars() {
        return super.awakenScrollBars();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        mContentViewCore.onInitializeAccessibilityNodeInfo(info);
    }

    /**
     * Fills in scrolling values for AccessibilityEvents.
     * @param event Event being fired.
     */
    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        mContentViewCore.onInitializeAccessibilityEvent(event);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mContentViewCore.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContentViewCore.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        mContentViewCore.onVisibilityChanged(changedView, visibility);
    }

    // Implements SmartClipProvider
    @Override
    public void extractSmartClipData(int x, int y, int width, int height) {
        mContentViewCore.extractSmartClipData(x, y, width, height);
    }

    // Implements SmartClipProvider
    @Override
    public void setSmartClipResultHandler(final Handler resultHandler) {
        if (resultHandler == null) {
            mContentViewCore.setSmartClipDataListener(null);
            return;
        }
        mContentViewCore.setSmartClipDataListener(new ContentViewCore.SmartClipDataListener() {
            public void onSmartClipDataExtracted(String text, String html, Rect clipRect) {
                Bundle bundle = new Bundle();
                bundle.putString("url", mContentViewCore.getWebContents().getVisibleUrl());
                bundle.putString("title", mContentViewCore.getWebContents().getTitle());
                bundle.putParcelable("rect", clipRect);
                bundle.putString("text", text);
                bundle.putString("html", html);
                try {
                    Message msg = Message.obtain(resultHandler, 0);
                    msg.setData(bundle);
                    msg.sendToTarget();
                } catch (Exception e) {
                    Log.e(TAG, "Error calling handler for smart clip data: ", e);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //              Start Implementation of ContentViewCore.InternalAccessDelegate               //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean super_onKeyUp(int keyCode, KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean super_dispatchKeyEventPreIme(KeyEvent event) {
        return super.dispatchKeyEventPreIme(event);
    }

    @Override
    public boolean super_dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean super_onGenericMotionEvent(MotionEvent event) {
        return super.onGenericMotionEvent(event);
    }

    @Override
    public void super_onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean super_awakenScrollBars(int startDelay, boolean invalidate) {
        return super.awakenScrollBars(startDelay, invalidate);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //                End Implementation of ContentViewCore.InternalAccessDelegate               //
    ///////////////////////////////////////////////////////////////////////////////////////////////
}

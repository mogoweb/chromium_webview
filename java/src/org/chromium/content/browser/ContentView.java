// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.TraceEvent;
import org.chromium.ui.base.WindowAndroid;

/**
 * The containing view for {@link ContentViewCore} that exists in the Android UI hierarchy and
 * exposes the various {@link View} functionality to it.
 *
 * TODO(joth): Remove any methods overrides from this class that were added for WebView
 *             compatibility.
 */
public class ContentView extends FrameLayout
        implements ContentViewCore.InternalAccessDelegate, PageInfo {

    private final ContentViewCore mContentViewCore;

    private float mCurrentTouchOffsetX;
    private float mCurrentTouchOffsetY;
    private final int[] mLocationInWindow = new int[2];

    /**
     * Creates an instance of a ContentView.
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param nativeWebContents A pointer to the native web contents.
     * @param windowAndroid An instance of the WindowAndroid.
     * @return A ContentView instance.
     */
    public static ContentView newInstance(Context context, long nativeWebContents,
            WindowAndroid windowAndroid) {
        return newInstance(context, nativeWebContents, windowAndroid, null,
                android.R.attr.webViewStyle);
    }

    /**
     * Creates an instance of a ContentView.
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param nativeWebContents A pointer to the native web contents.
     * @param windowAndroid An instance of the WindowAndroid.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @return A ContentView instance.
     */
    public static ContentView newInstance(Context context, long nativeWebContents,
            WindowAndroid windowAndroid, AttributeSet attrs) {
        // TODO(klobag): use the WebViewStyle as the default style for now. It enables scrollbar.
        // When ContentView is moved to framework, we can define its own style in the res.
        return newInstance(context, nativeWebContents, windowAndroid, attrs,
                android.R.attr.webViewStyle);
    }

    /**
     * Creates an instance of a ContentView.
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param nativeWebContents A pointer to the native web contents.
     * @param windowAndroid An instance of the WindowAndroid.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view.
     * @return A ContentView instance.
     */
    public static ContentView newInstance(Context context, long nativeWebContents,
            WindowAndroid windowAndroid, AttributeSet attrs, int defStyle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return new ContentView(context, nativeWebContents, windowAndroid, attrs, defStyle);
        } else {
            return new JellyBeanContentView(context, nativeWebContents, windowAndroid, attrs,
                    defStyle);
        }
    }

    protected ContentView(Context context, long nativeWebContents, WindowAndroid windowAndroid,
            AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (getScrollBarStyle() == View.SCROLLBARS_INSIDE_OVERLAY) {
            setHorizontalScrollBarEnabled(false);
            setVerticalScrollBarEnabled(false);
        }

        setFocusable(true);
        setFocusableInTouchMode(true);

        mContentViewCore = new ContentViewCore(context);
        mContentViewCore.initialize(this, this, nativeWebContents, windowAndroid);
    }

    /**
     * @return The URL of the page.
     */
    public String getUrl() {
        return mContentViewCore.getUrl();
    }

    // PageInfo implementation.

    @Override
    public String getTitle() {
        return mContentViewCore.getTitle();
    }

    @Override
    public int getBackgroundColor() {
        return mContentViewCore.getBackgroundColor();
    }

    @Override
    public View getView() {
        return this;
    }

    /**
     * @return The core component of the ContentView that handles JNI communication.  Should only be
     *         used for passing to native.
     */
    public ContentViewCore getContentViewCore() {
        return mContentViewCore;
    }

    /**
     * @return The cache of scales and positions used to convert coordinates from/to CSS.
     */
    public RenderCoordinates getRenderCoordinates() {
        return mContentViewCore.getRenderCoordinates();
    }

    /**
     * Destroy the internal state of the WebView. This method may only be called
     * after the WebView has been removed from the view system. No other methods
     * may be called on this WebView after this method has been called.
     */
    public void destroy() {
        mContentViewCore.destroy();
    }

    /**
     * Returns true initially, false after destroy() has been called.
     * It is illegal to call any other public method after destroy().
     */
    public boolean isAlive() {
        return mContentViewCore.isAlive();
    }

    public void setContentViewClient(ContentViewClient client) {
        mContentViewCore.setContentViewClient(client);
    }

    @VisibleForTesting
    public ContentViewClient getContentViewClient() {
        return mContentViewCore.getContentViewClient();
    }

    /**
     * Load url without fixing up the url string. Consumers of ContentView are responsible for
     * ensuring the URL passed in is properly formatted (i.e. the scheme has been added if left
     * off during user input).
     *
     * @param params Parameters for this load.
     */
    public void loadUrl(LoadUrlParams params) {
        mContentViewCore.loadUrl(params);
    }

    /**
     * @return Whether the current WebContents has a previous navigation entry.
     */
    public boolean canGoBack() {
        return mContentViewCore.canGoBack();
    }

    /**
     * @return Whether the current WebContents has a navigation entry after the current one.
     */
    public boolean canGoForward() {
        return mContentViewCore.canGoForward();
    }

    /**
     * Goes to the navigation entry before the current one.
     */
    public void goBack() {
        mContentViewCore.goBack();
    }

    /**
     * Goes to the navigation entry following the current one.
     */
    public void goForward() {
        mContentViewCore.goForward();
    }

    /**
     * Fling the ContentView from the current position.
     * @param x Fling touch starting position
     * @param y Fling touch starting position
     * @param velocityX Initial velocity of the fling (X) measured in pixels per second.
     * @param velocityY Initial velocity of the fling (Y) measured in pixels per second.
     */
    @VisibleForTesting
    public void fling(long timeMs, int x, int y, int velocityX, int velocityY) {
        mContentViewCore.flingForTest(timeMs, x, y, velocityX, velocityY);
    }

    /**
     * Injects the passed JavaScript code in the current page and evaluates it.
     *
     * @throws IllegalStateException If the ContentView has been destroyed.
     */
    public void evaluateJavaScript(String script) throws IllegalStateException {
        mContentViewCore.evaluateJavaScript(script, null);
    }

    /**
     * To be called when the ContentView is shown.
     **/
    public void onShow() {
        mContentViewCore.onShow();
    }

    /**
     * To be called when the ContentView is hidden.
     **/
    public void onHide() {
        mContentViewCore.onHide();
    }

    /**
     * Hides the select action bar.
     */
    public void hideSelectActionBar() {
        mContentViewCore.hideSelectActionBar();
    }

    // FrameLayout overrides.

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
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            getLocationInWindow(mLocationInWindow);
            mContentViewCore.onLocationInWindowChanged(mLocationInWindow[0], mLocationInWindow[1]);
        }
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
        MotionEvent offset = createOffsetMotionEvent(event);
        boolean consumed = mContentViewCore.onTouchEvent(offset);
        offset.recycle();
        return consumed;
    }

    /**
     * Mouse move events are sent on hover enter, hover move and hover exit.
     * They are sent on hover exit because sometimes it acts as both a hover
     * move and hover exit.
     */
    @Override
    public boolean onHoverEvent(MotionEvent event) {
        MotionEvent offset = createOffsetMotionEvent(event);
        boolean consumed = mContentViewCore.onHoverEvent(offset);
        offset.recycle();
        super.onHoverEvent(event);
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

    /**
     * Sets the current amount to offset incoming touch events by.  This is used to handle content
     * moving and not lining up properly with the android input system.
     * @param dx The X offset in pixels to shift touch events.
     * @param dy The Y offset in pixels to shift touch events.
     */
    public void setCurrentMotionEventOffsets(float dx, float dy) {
        mCurrentTouchOffsetX = dx;
        mCurrentTouchOffsetY = dy;
    }

    private MotionEvent createOffsetMotionEvent(MotionEvent src) {
        MotionEvent dst = MotionEvent.obtain(src);
        dst.offsetLocation(mCurrentTouchOffsetX, mCurrentTouchOffsetY);
        return dst;
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

    public int getSingleTapX()  {
        return mContentViewCore.getSingleTapX();
    }

    public int getSingleTapY()  {
        return mContentViewCore.getSingleTapY();
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

    /**
     * Return the current scale of the WebView
     * @return The current scale.
     */
    public float getScale() {
        return mContentViewCore.getScale();
    }

    /**
     * Enable or disable accessibility features.
     */
    public void setAccessibilityState(boolean state) {
        mContentViewCore.setAccessibilityState(state);
    }

    /**
     * Inform WebKit that Fullscreen mode has been exited by the user.
     */
    public void exitFullscreen() {
        mContentViewCore.exitFullscreen();
    }

    /**
     * Return content scroll y.
     *
     * @return The vertical scroll position in pixels.
     */
    public int getContentScrollY() {
        return mContentViewCore.computeVerticalScrollOffset();
    }

    /**
     * Return content height.
     *
     * @return The height of the content in pixels.
     */
    public int getContentHeight() {
        return mContentViewCore.computeVerticalScrollRange();
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

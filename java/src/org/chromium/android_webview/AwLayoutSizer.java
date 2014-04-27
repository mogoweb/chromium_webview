// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.view.View;
import android.view.View.MeasureSpec;

/**
 * Helper methods used to manage the layout of the View that contains AwContents.
 */
public class AwLayoutSizer {
    public static final int FIXED_LAYOUT_HEIGHT = 0;

    // These are used to prevent a re-layout if the content size changes within a dimension that is
    // fixed by the view system.
    private boolean mWidthMeasurementIsFixed;
    private boolean mHeightMeasurementIsFixed;

    // Size of the rendered content, as reported by native.
    private int mContentHeightCss;
    private int mContentWidthCss;

    // Page scale factor. This is set to zero initially so that we don't attempt to do a layout if
    // we get the content size change notification first and a page scale change second.
    private float mPageScaleFactor = 0.0f;
    // The page scale factor that was used in the most recent onMeasure call.
    private float mLastMeasuredPageScaleFactor = 0.0f;

    // Whether to postpone layout requests.
    private boolean mFreezeLayoutRequests;
    // Did we try to request a layout since the last time mPostponeLayoutRequests was set to true.
    private boolean mFrozenLayoutRequestPending;

    private double mDIPScale;

    // Was our height larger than the AT_MOST constraint the last time onMeasure was called?
    private boolean mHeightMeasurementLimited;
    // If mHeightMeasurementLimited is true then this contains the height limit.
    private int mHeightMeasurementLimit;

    // The most recent width and height seen in onSizeChanged.
    private int mLastWidth;
    private int mLastHeight;

    // Used to prevent sending multiple setFixedLayoutSize notifications with the same values.
    private int mLastSentFixedLayoutSizeWidth = -1;
    private int mLastSentFixedLayoutSizeHeight = -1;

    // Callback object for interacting with the View.
    private Delegate mDelegate;

    public interface Delegate {
        void requestLayout();
        void setMeasuredDimension(int measuredWidth, int measuredHeight);
        void setFixedLayoutSize(int widthDip, int heightDip);
        boolean isLayoutParamsHeightWrapContent();
    }

    /**
     * Default constructor. Note: both setDelegate and setDIPScale must be called before the class
     * is ready for use.
     */
    public AwLayoutSizer() {
    }

    public void setDelegate(Delegate delegate) {
        mDelegate = delegate;
    }

    public void setDIPScale(double dipScale) {
        mDIPScale = dipScale;
    }

    /**
     * Postpone requesting layouts till unfreezeLayoutRequests is called.
     */
    public void freezeLayoutRequests() {
        mFreezeLayoutRequests = true;
        mFrozenLayoutRequestPending = false;
    }

    /**
     * Stop postponing layout requests and request layout if such a request would have been made
     * had the freezeLayoutRequests method not been called before.
     */
    public void unfreezeLayoutRequests() {
        mFreezeLayoutRequests = false;
        if (mFrozenLayoutRequestPending) {
            mFrozenLayoutRequestPending = false;
            mDelegate.requestLayout();
        }
    }

    /**
     * Update the contents size.
     * This should be called whenever the content size changes (due to DOM manipulation or page
     * load, for example).
     * The width and height should be in CSS pixels.
     */
    public void onContentSizeChanged(int widthCss, int heightCss) {
        doUpdate(widthCss, heightCss, mPageScaleFactor);
    }

    /**
     * Update the contents page scale.
     * This should be called whenever the content page scale factor changes (due to pinch zoom, for
     * example).
     */
    public void onPageScaleChanged(float pageScaleFactor) {
        doUpdate(mContentWidthCss, mContentHeightCss, pageScaleFactor);
    }

    private void doUpdate(int widthCss, int heightCss, float pageScaleFactor) {
        // We want to request layout only if the size or scale change, however if any of the
        // measurements are 'fixed', then changing the underlying size won't have any effect, so we
        // ignore changes to dimensions that are 'fixed'.
        final int heightPix = (int) (heightCss * mPageScaleFactor * mDIPScale);
        boolean pageScaleChanged = mPageScaleFactor != pageScaleFactor;
        boolean contentHeightChangeMeaningful = !mHeightMeasurementIsFixed &&
            (!mHeightMeasurementLimited || heightPix < mHeightMeasurementLimit);
        boolean pageScaleChangeMeaningful =
            !mWidthMeasurementIsFixed || contentHeightChangeMeaningful;
        boolean layoutNeeded = (mContentWidthCss != widthCss && !mWidthMeasurementIsFixed) ||
            (mContentHeightCss != heightCss && contentHeightChangeMeaningful) ||
            (pageScaleChanged && pageScaleChangeMeaningful);

        mContentWidthCss = widthCss;
        mContentHeightCss = heightCss;
        mPageScaleFactor = pageScaleFactor;

        if (layoutNeeded) {
            if (mFreezeLayoutRequests) {
                mFrozenLayoutRequestPending = true;
            } else {
                mDelegate.requestLayout();
            }
        } else if (pageScaleChanged && mLastWidth != 0) {
            // Because the fixed layout size is directly impacted by the pageScaleFactor we must
            // update it even if the physical size of the view doesn't change.
            updateFixedLayoutSize(mLastWidth, mLastHeight, mPageScaleFactor);
        }
    }

    /**
     * Calculate the size of the view.
     * This is designed to be used to implement the android.view.View#onMeasure() method.
     */
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        int contentHeightPix = (int) (mContentHeightCss * mPageScaleFactor * mDIPScale);
        int contentWidthPix = (int) (mContentWidthCss * mPageScaleFactor * mDIPScale);

        int measuredHeight = contentHeightPix;
        int measuredWidth = contentWidthPix;

        mLastMeasuredPageScaleFactor = mPageScaleFactor;

        // Always use the given size unless unspecified. This matches WebViewClassic behavior.
        mWidthMeasurementIsFixed = (widthMode != MeasureSpec.UNSPECIFIED);
        mHeightMeasurementIsFixed = (heightMode == MeasureSpec.EXACTLY);
        mHeightMeasurementLimited =
            (heightMode == MeasureSpec.AT_MOST) && (contentHeightPix > heightSize);
        mHeightMeasurementLimit = heightSize;

        if (mHeightMeasurementIsFixed || mHeightMeasurementLimited) {
            measuredHeight = heightSize;
        }

        if (mWidthMeasurementIsFixed) {
            measuredWidth = widthSize;
        }

        if (measuredHeight < contentHeightPix) {
            measuredHeight |= View.MEASURED_STATE_TOO_SMALL;
        }

        if (measuredWidth < contentWidthPix) {
            measuredWidth |= View.MEASURED_STATE_TOO_SMALL;
        }

        mDelegate.setMeasuredDimension(measuredWidth, measuredHeight);
    }

    /**
     * Notify the AwLayoutSizer that the size of the view has changed.
     * This should be called by the Android view system after onMeasure if the view's size has
     * changed.
     */
    public void onSizeChanged(int w, int h, int ow, int oh) {
        mLastWidth = w;
        mLastHeight = h;
        updateFixedLayoutSize(mLastWidth, mLastHeight, mLastMeasuredPageScaleFactor);
    }

    /**
     * Notify the AwLayoutSizer that the layout pass requested via Delegate.requestLayout has
     * completed.
     * This should be called after onSizeChanged regardless of whether the size has changed or not.
     */
    public void onLayoutChange() {
        updateFixedLayoutSize(mLastWidth, mLastHeight, mLastMeasuredPageScaleFactor);
    }

    private void setFixedLayoutSize(int widthDip, int heightDip) {
        if (widthDip == mLastSentFixedLayoutSizeWidth &&
                heightDip == mLastSentFixedLayoutSizeHeight)
            return;
        mLastSentFixedLayoutSizeWidth = widthDip;
        mLastSentFixedLayoutSizeHeight = heightDip;

        mDelegate.setFixedLayoutSize(widthDip, heightDip);
    }

    // This needs to be called every time either the physical size of the view is changed or the
    // pageScale is changed.  Since we need to ensure that this is called immediately after
    // onSizeChanged we can't just wait for onLayoutChange. At the same time we can't only make this
    // call from onSizeChanged, since onSizeChanged won't fire if the view's physical size doesn't
    // change.
    private void updateFixedLayoutSize(int w, int h, float pageScaleFactor) {
        boolean wrapContentForHeight = mDelegate.isLayoutParamsHeightWrapContent();
        // If the WebView's size in the Android view system depends on the size of its contents then
        // the viewport size cannot be directly calculated from the WebView's physical size as that
        // can result in the layout being unstable (for example loading the following contents
        //   <div style="height:150%">a</a>
        // would cause the WebView to indefinitely attempt to increase its height by 50%).
        // If both the width and height are fixed (specified by the parent View) then content size
        // changes will not cause subsequent layout passes and so we don't need to do anything
        // special.
        // We assume the width is 'fixed' if the parent View specified an EXACT or an AT_MOST
        // measureSpec for the width (in which case the AT_MOST upper bound is the width).
        // That means that the WebView will ignore LayoutParams.width set to WRAP_CONTENT and will
        // instead try to take up as much width as possible. This is necessary because it's not
        // practical to do web layout without a set width.
        // For height the behavior is different because for a given width it is possible to
        // calculate the minimum height required to display all of the content. As such the WebView
        // can size itself vertically to match the content height. Because certain container views
        // (LinearLayout with a WRAP_CONTENT height, for example) can result in onMeasure calls with
        // both EXACTLY and AT_MOST height measureSpecs it is not possible to infer the sizing
        // policy for the whole subtree based on the parameters passed to the onMeasure call.
        // For that reason the LayoutParams.height property of the WebView is used. This behaves
        // more predictably and means that toggling the fixedLayoutSize mode (which can have
        // significant impact on how the web contents is laid out) is a direct consequence of the
        // developer's choice. The downside is that it could result in the Android layout being
        // unstable if a parent of the WebView has a wrap_content height while the WebView itself
        // has height set to match_parent. Unfortunately addressing this edge case is costly so it
        // will have to stay as is (this is compatible with Classic behavior).
        if ((mWidthMeasurementIsFixed && !wrapContentForHeight) || pageScaleFactor == 0) {
            setFixedLayoutSize(0, 0);
            return;
        }

        final double dipAndPageScale = pageScaleFactor * mDIPScale;
        final int contentWidthPix = (int) (mContentWidthCss * dipAndPageScale);

        int widthDip = (int) Math.ceil(w / dipAndPageScale);

        // Make sure that we don't introduce rounding errors if the viewport is to be exactly as
        // wide as the contents.
        if (w == contentWidthPix) {
            widthDip = mContentWidthCss;
        }

        // This is workaround due to the fact that in wrap content mode we need to use a fixed
        // layout size independent of view height, otherwise things like <div style="height:120%">
        // cause the webview to grow indefinitely. We need to use a height independent of the
        // webview's height. 0 is the value used in WebViewClassic.
        setFixedLayoutSize(widthDip, FIXED_LAYOUT_HEIGHT);
    }
}

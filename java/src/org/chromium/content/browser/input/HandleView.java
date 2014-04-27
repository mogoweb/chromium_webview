// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.PopupWindow;

import org.chromium.content.browser.PositionObserver;

/**
 * View that displays a selection or insertion handle for text editing.
 *
 * While a HandleView is logically a child of some other view, it does not exist in that View's
 * hierarchy.
 *
 */
public class HandleView extends View {
    private static final float FADE_DURATION = 200.f;

    private Drawable mDrawable;
    private final PopupWindow mContainer;

    // The position of the handle relative to the parent view.
    private int mPositionX;
    private int mPositionY;

    // The position of the parent relative to the application's root view.
    private int mParentPositionX;
    private int mParentPositionY;

    // The offset from this handles position to the "tip" of the handle.
    private float mHotspotX;
    private float mHotspotY;

    private final CursorController mController;
    private boolean mIsDragging;
    private float mTouchToWindowOffsetX;
    private float mTouchToWindowOffsetY;

    private final int mLineOffsetY;
    private float mDownPositionX, mDownPositionY;
    private long mTouchTimer;
    private boolean mIsInsertionHandle = false;
    private float mAlpha;
    private long mFadeStartTime;

    private final View mParent;
    private InsertionHandleController.PastePopupMenu mPastePopupWindow;

    private final int mTextSelectHandleLeftRes;
    private final int mTextSelectHandleRightRes;
    private final int mTextSelectHandleRes;

    private Drawable mSelectHandleLeft;
    private Drawable mSelectHandleRight;
    private Drawable mSelectHandleCenter;

    private final Rect mTempRect = new Rect();

    static final int LEFT = 0;
    static final int CENTER = 1;
    static final int RIGHT = 2;

    private final PositionObserver mParentPositionObserver;
    private final PositionObserver.Listener mParentPositionListener;

    // Number of dips to subtract from the handle's y position to give a suitable
    // y coordinate for the corresponding text position. This is to compensate for the fact
    // that the handle position is at the base of the line of text.
    private static final float LINE_OFFSET_Y_DIP = 5.0f;

    private static final int[] TEXT_VIEW_HANDLE_ATTRS = {
        android.R.attr.textSelectHandleLeft,
        android.R.attr.textSelectHandle,
        android.R.attr.textSelectHandleRight,
    };

    HandleView(CursorController controller, int pos, View parent,
            PositionObserver parentPositionObserver) {
        super(parent.getContext());
        Context context = parent.getContext();
        mParent = parent;
        mController = controller;
        mContainer = new PopupWindow(context, null, android.R.attr.textSelectHandleWindowStyle);
        mContainer.setSplitTouchEnabled(true);
        mContainer.setClippingEnabled(false);

        TypedArray a = context.obtainStyledAttributes(TEXT_VIEW_HANDLE_ATTRS);
        mTextSelectHandleLeftRes = a.getResourceId(a.getIndex(LEFT), 0);
        mTextSelectHandleRes = a.getResourceId(a.getIndex(CENTER), 0);
        mTextSelectHandleRightRes = a.getResourceId(a.getIndex(RIGHT), 0);
        a.recycle();

        setOrientation(pos);

        // Convert line offset dips to pixels.
        mLineOffsetY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                LINE_OFFSET_Y_DIP, context.getResources().getDisplayMetrics());

        mAlpha = 1.f;

        mParentPositionListener = new PositionObserver.Listener() {
            @Override
            public void onPositionChanged(int x, int y) {
                updateParentPosition(x, y);
            }
        };
        mParentPositionObserver = parentPositionObserver;
    }

    void setOrientation(int pos) {
        int handleWidth;
        switch (pos) {
            case LEFT: {
                if (mSelectHandleLeft == null) {
                    mSelectHandleLeft = getContext().getResources().getDrawable(
                            mTextSelectHandleLeftRes);
                }
                mDrawable = mSelectHandleLeft;
                handleWidth = mDrawable.getIntrinsicWidth();
                mHotspotX = (handleWidth * 3) / 4f;
                break;
            }

            case RIGHT: {
                if (mSelectHandleRight == null) {
                    mSelectHandleRight = getContext().getResources().getDrawable(
                            mTextSelectHandleRightRes);
                }
                mDrawable = mSelectHandleRight;
                handleWidth = mDrawable.getIntrinsicWidth();
                mHotspotX = handleWidth / 4f;
                break;
            }

            case CENTER:
            default: {
                if (mSelectHandleCenter == null) {
                    mSelectHandleCenter = getContext().getResources().getDrawable(
                            mTextSelectHandleRes);
                }
                mDrawable = mSelectHandleCenter;
                handleWidth = mDrawable.getIntrinsicWidth();
                mHotspotX = handleWidth / 2f;
                mIsInsertionHandle = true;
                break;
            }
        }

        mHotspotY = 0;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mDrawable.getIntrinsicWidth(),
                mDrawable.getIntrinsicHeight());
    }

    private void updateParentPosition(int parentPositionX, int parentPositionY) {
        // Hide paste popup window as soon as a scroll occurs.
        if (mPastePopupWindow != null) mPastePopupWindow.hide();

        mTouchToWindowOffsetX += parentPositionX - mParentPositionX;
        mTouchToWindowOffsetY += parentPositionY - mParentPositionY;
        mParentPositionX = parentPositionX;
        mParentPositionY = parentPositionY;
        onPositionChanged();
    }

    private int getContainerPositionX() {
        return mParentPositionX + mPositionX;
    }

    private int getContainerPositionY() {
        return mParentPositionY + mPositionY;
    }

    private void onPositionChanged() {
        mContainer.update(getContainerPositionX(), getContainerPositionY(),
                getRight() - getLeft(), getBottom() - getTop());
    }

    private void showContainer() {
        mContainer.showAtLocation(mParent, 0, getContainerPositionX(), getContainerPositionY());
    }

    void show() {
        // While hidden, the parent position may have become stale. It must be updated before
        // checking isPositionVisible().
        updateParentPosition(mParentPositionObserver.getPositionX(),
                mParentPositionObserver.getPositionY());
        if (!isPositionVisible()) {
            hide();
            return;
        }
        mParentPositionObserver.addListener(mParentPositionListener);
        mContainer.setContentView(this);
        showContainer();

        // Hide paste view when handle is moved on screen.
        if (mPastePopupWindow != null) {
            mPastePopupWindow.hide();
        }
    }

    void hide() {
        mIsDragging = false;
        mContainer.dismiss();
        mParentPositionObserver.removeListener(mParentPositionListener);
        if (mPastePopupWindow != null) {
            mPastePopupWindow.hide();
        }
    }

    boolean isShowing() {
        return mContainer.isShowing();
    }

    private boolean isPositionVisible() {
        // Always show a dragging handle.
        if (mIsDragging) {
            return true;
        }

        final Rect clip = mTempRect;
        clip.left = 0;
        clip.top = 0;
        clip.right = mParent.getWidth();
        clip.bottom = mParent.getHeight();

        final ViewParent parent = mParent.getParent();
        if (parent == null || !parent.getChildVisibleRect(mParent, clip, null)) {
            return false;
        }

        final int posX = getContainerPositionX() + (int) mHotspotX;
        final int posY = getContainerPositionY() + (int) mHotspotY;

        return posX >= clip.left && posX <= clip.right &&
                posY >= clip.top && posY <= clip.bottom;
    }

    // x and y are in physical pixels.
    void moveTo(int x, int y) {
        int previousPositionX = mPositionX;
        int previousPositionY = mPositionY;

        mPositionX = x;
        mPositionY = y;
        if (isPositionVisible()) {
            if (mContainer.isShowing()) {
                onPositionChanged();
                // Hide paste popup window as soon as the handle is dragged.
                if (mPastePopupWindow != null &&
                        (previousPositionX != mPositionX || previousPositionY != mPositionY)) {
                    mPastePopupWindow.hide();
                }
            } else {
                show();
            }

            if (mIsDragging) {
                // Hide paste popup window as soon as the handle is dragged.
                if (mPastePopupWindow != null) {
                    mPastePopupWindow.hide();
                }
            }
        } else {
            hide();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        updateAlpha();
        mDrawable.setBounds(0, 0, getRight() - getLeft(), getBottom() - getTop());
        mDrawable.draw(c);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDownPositionX = ev.getRawX();
                mDownPositionY = ev.getRawY();
                mTouchToWindowOffsetX = mDownPositionX - mPositionX;
                mTouchToWindowOffsetY = mDownPositionY - mPositionY;
                mIsDragging = true;
                mController.beforeStartUpdatingPosition(this);
                mTouchTimer = SystemClock.uptimeMillis();
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                updatePosition(ev.getRawX(), ev.getRawY());
                break;
            }

            case MotionEvent.ACTION_UP:
                if (mIsInsertionHandle) {
                    long delay = SystemClock.uptimeMillis() - mTouchTimer;
                    if (delay < ViewConfiguration.getTapTimeout()) {
                        if (mPastePopupWindow != null && mPastePopupWindow.isShowing()) {
                            // Tapping on the handle dismisses the displayed paste view,
                            mPastePopupWindow.hide();
                        } else {
                            showPastePopupWindow();
                        }
                    }
                }
                mIsDragging = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                break;

            default:
                return false;
        }
        return true;
    }

    boolean isDragging() {
        return mIsDragging;
    }

    /**
     * @return Returns the x position of the handle
     */
    int getPositionX() {
        return mPositionX;
    }

    /**
     * @return Returns the y position of the handle
     */
    int getPositionY() {
        return mPositionY;
    }

    private void updatePosition(float rawX, float rawY) {
        final float newPosX = rawX - mTouchToWindowOffsetX + mHotspotX;
        final float newPosY = rawY - mTouchToWindowOffsetY + mHotspotY - mLineOffsetY;

        mController.updatePosition(this, Math.round(newPosX), Math.round(newPosY));
    }

    // x and y are in physical pixels.
    void positionAt(int x, int y) {
        moveTo(x - Math.round(mHotspotX), y - Math.round(mHotspotY));
    }

    // Returns the x coordinate of the position that the handle appears to be pointing to relative
    // to the handles "parent" view.
    int getAdjustedPositionX() {
        return mPositionX + Math.round(mHotspotX);
    }

    // Returns the y coordinate of the position that the handle appears to be pointing to relative
    // to the handles "parent" view.
    int getAdjustedPositionY() {
        return mPositionY + Math.round(mHotspotY);
    }

    // Returns the x coordinate of the postion that the handle appears to be pointing to relative to
    // the root view of the application.
    int getRootViewRelativePositionX() {
        return getContainerPositionX() + Math.round(mHotspotX);
    }

    // Returns the y coordinate of the postion that the handle appears to be pointing to relative to
    // the root view of the application.
    int getRootViewRelativePositionY() {
        return getContainerPositionY() + Math.round(mHotspotY);
    }

    // Returns a suitable y coordinate for the text position corresponding to the handle.
    // As the handle points to a position on the base of the line of text, this method
    // returns a coordinate a small number of pixels higher (i.e. a slightly smaller number)
    // than getAdjustedPositionY.
    int getLineAdjustedPositionY() {
        return (int) (mPositionY + mHotspotY - mLineOffsetY);
    }

    Drawable getDrawable() {
        return mDrawable;
    }

    private void updateAlpha() {
        if (mAlpha == 1.f) return;
        mAlpha = Math.min(1.f, (System.currentTimeMillis() - mFadeStartTime) / FADE_DURATION);
        mDrawable.setAlpha((int) (255 * mAlpha));
        invalidate();
    }

    /**
     * If the handle is not visible, sets its visibility to View.VISIBLE and begins fading it in.
     */
    void beginFadeIn() {
        if (getVisibility() == VISIBLE) return;
        mAlpha = 0.f;
        mFadeStartTime = System.currentTimeMillis();
        setVisibility(VISIBLE);
    }

    void showPastePopupWindow() {
        InsertionHandleController ihc = (InsertionHandleController) mController;
        if (mIsInsertionHandle && ihc.canPaste()) {
            if (mPastePopupWindow == null) {
                // Lazy initialization: create when actually shown only.
                mPastePopupWindow = ihc.new PastePopupMenu();
            }
            mPastePopupWindow.show();
        }
    }
}

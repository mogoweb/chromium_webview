///*
// * Copyright (C) 2011 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License"); you may not
// * use this file except in compliance with the License. You may obtain a copy of
// * the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// * License for the specific language governing permissions and limitations under
// * the License.
// */
//
//package com.mogoweb.browser;
//
//
//import android.animation.Animator;
//import android.animation.AnimatorListenerAdapter;
//import android.animation.AnimatorSet;
//import android.animation.ObjectAnimator;
//import android.content.Context;
//import android.database.DataSetObserver;
//import android.graphics.Canvas;
//import android.util.AttributeSet;
//import android.view.Gravity;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.animation.DecelerateInterpolator;
//import android.widget.BaseAdapter;
//import android.widget.LinearLayout;
//
//import com.mogoweb.browser.view.ScrollerView;
//
///**
// * custom view for displaying tabs in the nav screen
// */
//public class NavTabScroller extends ScrollerView {
//
//    static final int INVALID_POSITION = -1;
//    static final float[] PULL_FACTOR = { 2.5f, 0.9f };
//
//    interface OnRemoveListener {
//        public void onRemovePosition(int position);
//    }
//
//    interface OnLayoutListener {
//        public void onLayout(int l, int t, int r, int b);
//    }
//
//    private ContentLayout mContentView;
//    private BaseAdapter mAdapter;
//    private OnRemoveListener mRemoveListener;
//    private OnLayoutListener mLayoutListener;
//    private int mGap;
//    private int mGapPosition;
//    private ObjectAnimator mGapAnimator;
//
//    // after drag animation velocity in pixels/sec
//    private static final float MIN_VELOCITY = 1500;
//    private AnimatorSet mAnimator;
//
//    private float mFlingVelocity;
//    private boolean mNeedsScroll;
//    private int mScrollPosition;
//
//    DecelerateInterpolator mCubic;
//    int mPullValue;
//
//    public NavTabScroller(Context context, AttributeSet attrs, int defStyle) {
//        super(context, attrs, defStyle);
//        init(context);
//    }
//
//    public NavTabScroller(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init(context);
//    }
//
//    public NavTabScroller(Context context) {
//        super(context);
//        init(context);
//    }
//
//    private void init(Context ctx) {
//        mCubic = new DecelerateInterpolator(1.5f);
//        mGapPosition = INVALID_POSITION;
//        setHorizontalScrollBarEnabled(false);
//        setVerticalScrollBarEnabled(false);
//        mContentView = new ContentLayout(ctx, this);
//        mContentView.setOrientation(LinearLayout.HORIZONTAL);
//        addView(mContentView);
//        mContentView.setLayoutParams(
//                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
//        // ProGuard !
//        setGap(getGap());
//        mFlingVelocity = getContext().getResources().getDisplayMetrics().density
//                * MIN_VELOCITY;
//    }
//
//    protected int getScrollValue() {
//        return mHorizontal ? mScrollX : mScrollY;
//    }
//
//    protected void setScrollValue(int value) {
//        scrollTo(mHorizontal ? value : 0, mHorizontal ? 0 : value);
//    }
//
//    protected NavTabView getTabView(int pos) {
//        return (NavTabView) mContentView.getChildAt(pos);
//    }
//
//    protected boolean isHorizontal() {
//        return mHorizontal;
//    }
//
//    public void setOrientation(int orientation) {
//        mContentView.setOrientation(orientation);
//        if (orientation == LinearLayout.HORIZONTAL) {
//            mContentView.setLayoutParams(
//                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
//        } else {
//            mContentView.setLayoutParams(
//                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
//        }
//        super.setOrientation(orientation);
//    }
//
//    @Override
//    protected void onMeasure(int wspec, int hspec) {
//        super.onMeasure(wspec, hspec);
//        calcPadding();
//    }
//
//    private void calcPadding() {
//        if (mAdapter.getCount() > 0) {
//            View v = mContentView.getChildAt(0);
//            if (mHorizontal) {
//                int pad = (getMeasuredWidth() - v.getMeasuredWidth()) / 2 + 2;
//                mContentView.setPadding(pad, 0, pad, 0);
//            } else {
//                int pad = (getMeasuredHeight() - v.getMeasuredHeight()) / 2 + 2;
//                mContentView.setPadding(0, pad, 0, pad);
//            }
//        }
//    }
//
//    public void setAdapter(BaseAdapter adapter) {
//        setAdapter(adapter, 0);
//    }
//
//
//    public void setOnRemoveListener(OnRemoveListener l) {
//        mRemoveListener = l;
//    }
//
//    public void setOnLayoutListener(OnLayoutListener l) {
//        mLayoutListener = l;
//    }
//
//    protected void setAdapter(BaseAdapter adapter, int selection) {
//        mAdapter = adapter;
//        mAdapter.registerDataSetObserver(new DataSetObserver() {
//
//            @Override
//            public void onChanged() {
//                super.onChanged();
//                handleDataChanged();
//            }
//
//            @Override
//            public void onInvalidated() {
//                super.onInvalidated();
//            }
//        });
//        handleDataChanged(selection);
//    }
//
//    protected ViewGroup getContentView() {
//        return mContentView;
//    }
//
//    protected int getRelativeChildTop(int ix) {
//        return mContentView.getChildAt(ix).getTop() - mScrollY;
//    }
//
//    protected void handleDataChanged() {
//        handleDataChanged(INVALID_POSITION);
//    }
//
//    void handleDataChanged(int newscroll) {
//        int scroll = getScrollValue();
//        if (mGapAnimator != null) {
//            mGapAnimator.cancel();
//        }
//        mContentView.removeAllViews();
//        for (int i = 0; i < mAdapter.getCount(); i++) {
//            View v = mAdapter.getView(i, null, mContentView);
//            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
//                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
//            lp.gravity = (mHorizontal ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
//            mContentView.addView(v, lp);
//            if (mGapPosition > INVALID_POSITION){
//                adjustViewGap(v, i);
//            }
//        }
//        if (newscroll > INVALID_POSITION) {
//            newscroll = Math.min(mAdapter.getCount() - 1, newscroll);
//            mNeedsScroll = true;
//            mScrollPosition = newscroll;
//            requestLayout();
//        } else {
//            setScrollValue(scroll);
//        }
//    }
//
//    protected void finishScroller() {
//        mScroller.forceFinished(true);
//    }
//
//    @Override
//    protected void onLayout(boolean changed, int l, int t, int r, int b) {
//        super.onLayout(changed, l, t, r, b);
//        if (mNeedsScroll) {
//            mScroller.forceFinished(true);
//            snapToSelected(mScrollPosition, false);
//            mNeedsScroll = false;
//        }
//        if (mLayoutListener != null) {
//            mLayoutListener.onLayout(l, t, r, b);
//            mLayoutListener = null;
//        }
//    }
//
//    void clearTabs() {
//        mContentView.removeAllViews();
//    }
//
//    void snapToSelected(int pos, boolean smooth) {
//        if (pos < 0) return;
//        View v = mContentView.getChildAt(pos);
//        if (v == null) return;
//        int sx = 0;
//        int sy = 0;
//        if (mHorizontal) {
//            sx = (v.getLeft() + v.getRight() - getWidth()) / 2;
//        } else {
//            sy = (v.getTop() + v.getBottom() - getHeight()) / 2;
//        }
//        if ((sx != mScrollX) || (sy != mScrollY)) {
//            if (smooth) {
//                smoothScrollTo(sx,sy);
//            } else {
//                scrollTo(sx, sy);
//            }
//        }
//    }
//
//    protected void animateOut(View v) {
//        if (v == null) return;
//        animateOut(v, -mFlingVelocity);
//    }
//
//    private void animateOut(final View v, float velocity) {
//        float start = mHorizontal ? v.getTranslationY() : v.getTranslationX();
//        animateOut(v, velocity, start);
//    }
//
//    private void animateOut(final View v, float velocity, float start) {
//        if ((v == null) || (mAnimator != null)) return;
//        final int position = mContentView.indexOfChild(v);
//        int target = 0;
//        if (velocity < 0) {
//            target = mHorizontal ? -getHeight() :  -getWidth();
//        } else {
//            target = mHorizontal ? getHeight() : getWidth();
//        }
//        int distance = target - (mHorizontal ? v.getTop() : v.getLeft());
//        long duration = (long) (Math.abs(distance) * 1000 / Math.abs(velocity));
//        int scroll = 0;
//        int translate = 0;
//        int gap = mHorizontal ? v.getWidth() : v.getHeight();
//        int centerView = getViewCenter(v);
//        int centerScreen = getScreenCenter();
//        int newpos = INVALID_POSITION;
//        if (centerView < centerScreen - gap / 2) {
//            // top view
//            scroll = - (centerScreen - centerView - gap);
//            translate = (position > 0) ? gap : 0;
//            newpos = position;
//        } else if (centerView > centerScreen + gap / 2) {
//            // bottom view
//            scroll = - (centerScreen + gap - centerView);
//            if (position < mAdapter.getCount() - 1) {
//                translate = -gap;
//            }
//        } else {
//            // center view
//            scroll = - (centerScreen - centerView);
//            if (position < mAdapter.getCount() - 1) {
//                translate = -gap;
//            } else {
//                scroll -= gap;
//            }
//        }
//        mGapPosition = position;
//        final int pos = newpos;
//        ObjectAnimator trans = ObjectAnimator.ofFloat(v,
//                (mHorizontal ? TRANSLATION_Y : TRANSLATION_X), start, target);
//        ObjectAnimator alpha = ObjectAnimator.ofFloat(v, ALPHA, getAlpha(v,start),
//                getAlpha(v,target));
//        AnimatorSet set1 = new AnimatorSet();
//        set1.playTogether(trans, alpha);
//        set1.setDuration(duration);
//        mAnimator = new AnimatorSet();
//        ObjectAnimator trans2 = null;
//        ObjectAnimator scroll1 = null;
//        if (scroll != 0) {
//            if (mHorizontal) {
//                scroll1 = ObjectAnimator.ofInt(this, "scrollX", getScrollX(), getScrollX() + scroll);
//            } else {
//                scroll1 = ObjectAnimator.ofInt(this, "scrollY", getScrollY(), getScrollY() + scroll);
//            }
//        }
//        if (translate != 0) {
//            trans2 = ObjectAnimator.ofInt(this, "gap", 0, translate);
//        }
//        final int duration2 = 200;
//        if (scroll1 != null) {
//            if (trans2 != null) {
//                AnimatorSet set2 = new AnimatorSet();
//                set2.playTogether(scroll1, trans2);
//                set2.setDuration(duration2);
//                mAnimator.playSequentially(set1, set2);
//            } else {
//                scroll1.setDuration(duration2);
//                mAnimator.playSequentially(set1, scroll1);
//            }
//        } else {
//            if (trans2 != null) {
//                trans2.setDuration(duration2);
//                mAnimator.playSequentially(set1, trans2);
//            }
//        }
//        mAnimator.addListener(new AnimatorListenerAdapter() {
//            public void onAnimationEnd(Animator a) {
//                if (mRemoveListener !=  null) {
//                    mRemoveListener.onRemovePosition(position);
//                    mAnimator = null;
//                    mGapPosition = INVALID_POSITION;
//                    mGap = 0;
//                    handleDataChanged(pos);
//                }
//            }
//        });
//        mAnimator.start();
//    }
//
//    public void setGap(int gap) {
//        if (mGapPosition != INVALID_POSITION) {
//            mGap = gap;
//            postInvalidate();
//        }
//    }
//
//    public int getGap() {
//        return mGap;
//    }
//
//    void adjustGap() {
//        for (int i = 0; i < mContentView.getChildCount(); i++) {
//            final View child = mContentView.getChildAt(i);
//            adjustViewGap(child, i);
//        }
//    }
//
//    private void adjustViewGap(View view, int pos) {
//        if ((mGap < 0 && pos > mGapPosition)
//                || (mGap > 0 && pos < mGapPosition)) {
//            if (mHorizontal) {
//                view.setTranslationX(mGap);
//            } else {
//                view.setTranslationY(mGap);
//            }
//        }
//    }
//
//    private int getViewCenter(View v) {
//        if (mHorizontal) {
//            return v.getLeft() + v.getWidth() / 2;
//        } else {
//            return v.getTop() + v.getHeight() / 2;
//        }
//    }
//
//    private int getScreenCenter() {
//        if (mHorizontal) {
//            return getScrollX() + getWidth() / 2;
//        } else {
//            return getScrollY() + getHeight() / 2;
//        }
//    }
//
//    @Override
//    public void draw(Canvas canvas) {
//        if (mGapPosition > INVALID_POSITION) {
//            adjustGap();
//        }
//        super.draw(canvas);
//    }
//
//    @Override
//    protected View findViewAt(int x, int y) {
//        x += mScrollX;
//        y += mScrollY;
//        final int count = mContentView.getChildCount();
//        for (int i = count - 1; i >= 0; i--) {
//            View child = mContentView.getChildAt(i);
//            if (child.getVisibility() == View.VISIBLE) {
//                if ((x >= child.getLeft()) && (x < child.getRight())
//                        && (y >= child.getTop()) && (y < child.getBottom())) {
//                    return child;
//                }
//            }
//        }
//        return null;
//    }
//
//    @Override
//    protected void onOrthoDrag(View v, float distance) {
//        if ((v != null) && (mAnimator == null)) {
//            offsetView(v, distance);
//        }
//    }
//
//    @Override
//    protected void onOrthoDragFinished(View downView) {
//        if (mAnimator != null) return;
//        if (mIsOrthoDragged && downView != null) {
//            // offset
//            float diff = mHorizontal ? downView.getTranslationY() : downView.getTranslationX();
//            if (Math.abs(diff) > (mHorizontal ? downView.getHeight() : downView.getWidth()) / 2) {
//                // remove it
//                animateOut(downView, Math.signum(diff) * mFlingVelocity, diff);
//            } else {
//                // snap back
//                offsetView(downView, 0);
//            }
//        }
//    }
//
//    @Override
//    protected void onOrthoFling(View v, float velocity) {
//        if (v == null) return;
//        if (mAnimator == null && Math.abs(velocity) > mFlingVelocity / 2) {
//            animateOut(v, velocity);
//        } else {
//            offsetView(v, 0);
//        }
//    }
//
//    private void offsetView(View v, float distance) {
//        v.setAlpha(getAlpha(v, distance));
//        if (mHorizontal) {
//            v.setTranslationY(distance);
//        } else {
//            v.setTranslationX(distance);
//        }
//    }
//
//    private float getAlpha(View v, float distance) {
//        return 1 - (float) Math.abs(distance) / (mHorizontal ? v.getHeight() : v.getWidth());
//    }
//
//    private float ease(DecelerateInterpolator inter, float value, float start,
//            float dist, float duration) {
//        return start + dist * inter.getInterpolation(value / duration);
//    }
//
//    @Override
//    protected void onPull(int delta) {
//        boolean layer = false;
//        int count = 2;
//        if (delta == 0 && mPullValue == 0) return;
//        if (delta == 0 && mPullValue != 0) {
//            // reset
//            for (int i = 0; i < count; i++) {
//                View child = mContentView.getChildAt((mPullValue < 0)
//                        ? i
//                        : mContentView.getChildCount() - 1 - i);
//                if (child == null) break;
//                ObjectAnimator trans = ObjectAnimator.ofFloat(child,
//                        mHorizontal ? "translationX" : "translationY",
//                                mHorizontal ? getTranslationX() : getTranslationY(),
//                                0);
//                ObjectAnimator rot = ObjectAnimator.ofFloat(child,
//                        mHorizontal ? "rotationY" : "rotationX",
//                                mHorizontal ? getRotationY() : getRotationX(),
//                                0);
//                AnimatorSet set = new AnimatorSet();
//                set.playTogether(trans, rot);
//                set.setDuration(100);
//                set.start();
//            }
//            mPullValue = 0;
//        } else {
//            if (mPullValue == 0) {
//                layer = true;
//            }
//            mPullValue += delta;
//        }
//        final int height = mHorizontal ? getWidth() : getHeight();
//        int oscroll = Math.abs(mPullValue);
//        int factor = (mPullValue <= 0) ? 1 : -1;
//        for (int i = 0; i < count; i++) {
//            View child = mContentView.getChildAt((mPullValue < 0)
//                    ? i
//                    : mContentView.getChildCount() - 1 - i);
//            if (child == null) break;
//            if (layer) {
//            }
//            float k = PULL_FACTOR[i];
//            float rot = -factor * ease(mCubic, oscroll, 0, k * 2, height);
//            int y =  factor * (int) ease(mCubic, oscroll, 0, k*20, height);
//            if (mHorizontal) {
//                child.setTranslationX(y);
//            } else {
//                child.setTranslationY(y);
//            }
//            if (mHorizontal) {
//                child.setRotationY(-rot);
//            } else {
//                child.setRotationX(rot);
//            }
//        }
//    }
//
//    static class ContentLayout extends LinearLayout {
//
//        NavTabScroller mScroller;
//
//        public ContentLayout(Context context, NavTabScroller scroller) {
//            super(context);
//            mScroller = scroller;
//        }
//
//        @Override
//        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//            if (mScroller.getGap() != 0) {
//                View v = getChildAt(0);
//                if (v != null) {
//                    if (mScroller.isHorizontal()) {
//                        int total = v.getMeasuredWidth() + getMeasuredWidth();
//                        setMeasuredDimension(total, getMeasuredHeight());
//                    } else {
//                        int total = v.getMeasuredHeight() + getMeasuredHeight();
//                        setMeasuredDimension(getMeasuredWidth(), total);
//                    }
//                }
//
//            }
//        }
//
//    }
//
//}
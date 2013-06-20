/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogoweb.browser.view;

import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Adapter;

import java.util.ArrayList;

/**
 * common code for pie views
 */
public abstract class BasePieView implements PieMenu.PieView {

    protected Adapter mAdapter;
    private DataSetObserver mObserver;
    protected ArrayList<View> mViews;

    protected OnLayoutListener mListener;

    protected int mCurrent;
    protected int mChildWidth;
    protected int mChildHeight;
    protected int mWidth;
    protected int mHeight;
    protected int mLeft;
    protected int mTop;

    public BasePieView() {
    }

    public void setLayoutListener(OnLayoutListener l) {
        mListener = l;
    }

    public void setAdapter(Adapter adapter) {
        mAdapter = adapter;
        if (adapter == null) {
            if (mAdapter != null) {
                mAdapter.unregisterDataSetObserver(mObserver);
            }
            mViews = null;
            mCurrent = -1;
        } else {
            mObserver = new DataSetObserver() {
                @Override
                public void onChanged() {
                    buildViews();
                }

                @Override
                public void onInvalidated() {
                    mViews.clear();
                }
            };
            mAdapter.registerDataSetObserver(mObserver);
            setCurrent(0);
        }
    }

    public void setCurrent(int ix) {
        mCurrent = ix;
    }

    public Adapter getAdapter() {
        return mAdapter;
    }

    protected void buildViews() {
        if (mAdapter != null) {
            final int n = mAdapter.getCount();
            if (mViews == null) {
                mViews = new ArrayList<View>(n);
            } else {
                mViews.clear();
            }
            mChildWidth = 0;
            mChildHeight = 0;
            for (int i = 0; i < n; i++) {
                View view = mAdapter.getView(i, null, null);
                view.measure(View.MeasureSpec.UNSPECIFIED,
                        View.MeasureSpec.UNSPECIFIED);
                mChildWidth = Math.max(mChildWidth, view.getMeasuredWidth());
                mChildHeight = Math.max(mChildHeight, view.getMeasuredHeight());
                mViews.add(view);
            }
        }
    }

    /**
     * this will be called before the first draw call
     * needs to set top, left, width, height
     */
    @Override
    public void layout(int anchorX, int anchorY, boolean left, float angle,
            int parentHeight) {
        if (mListener != null) {
            mListener.onLayout(anchorX, anchorY, left);
        }
    }


    @Override
    public abstract void draw(Canvas canvas);

    protected void drawView(View view, Canvas canvas) {
        final int state = canvas.save();
        canvas.translate(view.getLeft(), view.getTop());
        view.draw(canvas);
        canvas.restoreToCount(state);
    }

    protected abstract int findChildAt(int y);

    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        int action = evt.getActionMasked();
        int evtx = (int) evt.getX();
        int evty = (int) evt.getY();
        if ((evtx < mLeft) || (evtx >= mLeft + mWidth)
                || (evty < mTop) || (evty >= mTop + mHeight)) {
            return false;
        }
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                View v = mViews.get(mCurrent);
                setCurrent(Math.max(0, Math.min(mViews.size() -1,
                        findChildAt(evty))));
                View v1 = mViews.get(mCurrent);
                if (v != v1) {
                    v.setPressed(false);
                    v1.setPressed(true);
                }
                break;
            case MotionEvent.ACTION_UP:
                mViews.get(mCurrent).performClick();
                mViews.get(mCurrent).setPressed(false);
                break;
            default:
                break;
        }
        return true;
    }

}

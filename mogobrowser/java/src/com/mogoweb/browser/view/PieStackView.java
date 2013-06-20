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

import com.mogoweb.browser.R;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * shows views in a stack
 */
public class PieStackView extends BasePieView {

    private static final int SLOP = 5;

    private OnCurrentListener mCurrentListener;
    private int mMinHeight;

    public interface OnCurrentListener {
        public void onSetCurrent(int index);
    }

    public PieStackView(Context ctx) {
        mMinHeight = (int) ctx.getResources()
                .getDimension(R.dimen.qc_tab_title_height);
    }

    public void setOnCurrentListener(OnCurrentListener l) {
        mCurrentListener = l;
    }

    @Override
    public void setCurrent(int ix) {
        super.setCurrent(ix);
        if (mCurrentListener != null) {
            mCurrentListener.onSetCurrent(ix);
        }
    }

    /**
     * this will be called before the first draw call
     */
    @Override
    public void layout(int anchorX, int anchorY, boolean left, float angle,
            int pHeight) {
        super.layout(anchorX, anchorY, left, angle, pHeight);
        buildViews();
        mWidth = mChildWidth;
        mHeight = mChildHeight + (mViews.size() - 1) * mMinHeight;
        mLeft = anchorX + (left ? SLOP : -(SLOP + mChildWidth));
        mTop = anchorY - mHeight / 2;
        if (mViews != null) {
            layoutChildrenLinear();
        }
    }

    private void layoutChildrenLinear() {
        final int n = mViews.size();
        int top = mTop;
        int dy = (n == 1) ? 0 : (mHeight - mChildHeight) / (n - 1);
        for (View view : mViews) {
            int x = mLeft;
            view.layout(x, top, x + mChildWidth, top + mChildHeight);
            top += dy;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if ((mViews != null) && (mCurrent > -1)) {
            final int n = mViews.size();
            for (int i = 0; i < mCurrent; i++) {
                drawView(mViews.get(i), canvas);
            }
            for (int i = n - 1; i > mCurrent; i--) {
                drawView(mViews.get(i), canvas);
            }
            drawView(mViews.get(mCurrent), canvas);
        }
    }

    @Override
    protected int findChildAt(int y) {
        final int ix = (y - mTop) * mViews.size() / mHeight;
        return ix;
    }

}

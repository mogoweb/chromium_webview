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
import android.graphics.Paint;
import android.view.View;

/**
 * shows views in a menu style list
 */
public class PieListView extends BasePieView {

    private Paint mBgPaint;

    public PieListView(Context ctx) {
        mBgPaint = new Paint();
        mBgPaint.setColor(ctx.getResources().getColor(R.color.qcMenuBackground));
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
        mHeight = mChildHeight * mAdapter.getCount();
        mLeft = anchorX + (left ? 0 : - mChildWidth);
        mTop = Math.max(anchorY - mHeight / 2, 0);
        if (mTop + mHeight > pHeight) {
            mTop = pHeight - mHeight;
        }
        if (mViews != null) {
            layoutChildrenLinear();
        }
    }

    protected void layoutChildrenLinear() {
        final int n = mViews.size();
        int top = mTop;
        for (View view : mViews) {
            view.layout(mLeft, top, mLeft + mChildWidth, top + mChildHeight);
            top += mChildHeight;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRect(mLeft, mTop, mLeft + mWidth, mTop + mHeight, mBgPaint);
        if (mViews != null) {
            for (View view : mViews) {
                drawView(view, canvas);
            }
        }
    }

    @Override
    protected int findChildAt(int y) {
        final int ix = (y - mTop) * mViews.size() / mHeight;
        return ix;
    }

}

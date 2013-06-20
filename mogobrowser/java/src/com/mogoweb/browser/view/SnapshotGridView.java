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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.GridView;

public class SnapshotGridView extends GridView {

    private static final int MAX_COLUMNS = 5;

    private int mColWidth;

    public SnapshotGridView(Context context) {
        super(context);
    }

    public SnapshotGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SnapshotGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthSize > 0 && mColWidth > 0) {
            int numCols = widthSize / mColWidth;
            widthSize = Math.min(
                    Math.min(numCols, MAX_COLUMNS) * mColWidth,
                    widthSize);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(widthSize, widthMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setColumnWidth(int columnWidth) {
        mColWidth = columnWidth;
        super.setColumnWidth(columnWidth);
    }
}

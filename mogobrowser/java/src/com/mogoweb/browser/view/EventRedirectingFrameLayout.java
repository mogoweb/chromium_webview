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
 * limitations under the License
 */

package com.mogoweb.browser.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public class EventRedirectingFrameLayout extends FrameLayout {

    private int mTargetChild;

    public EventRedirectingFrameLayout(Context context) {
        super(context);
    }

    public EventRedirectingFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EventRedirectingFrameLayout(
            Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setTargetChild(int index) {
        if (index >= 0 && index < getChildCount()) {
            mTargetChild = index;
            getChildAt(mTargetChild).requestFocus();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View child = getChildAt(mTargetChild);
        if (child != null)
            return child.dispatchTouchEvent(ev);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        View child = getChildAt(mTargetChild);
        if (child != null)
            return child.dispatchKeyEvent(event);
        return false;
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        View child = getChildAt(mTargetChild);
        if (child != null)
            return child.dispatchKeyEventPreIme(event);
        return false;
    }

}

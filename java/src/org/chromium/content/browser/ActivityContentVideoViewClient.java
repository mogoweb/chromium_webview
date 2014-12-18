// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Uses an existing Activity to handle displaying video in full screen.
 */
public class ActivityContentVideoViewClient implements ContentVideoViewClient {
    private final Activity mActivity;
    private View mView;

    public ActivityContentVideoViewClient(Activity activity)  {
        this.mActivity = activity;
    }

    @Override
    public boolean onShowCustomView(View view) {
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        decor.addView(view, 0,
            new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
        setSystemUiVisibility(decor, true);
        mView = view;
        return true;
    }

    @Override
    public void onDestroyContentVideoView() {
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        decor.removeView(mView);
        setSystemUiVisibility(decor, false);
        mView = null;
    }

    @Override
    public View getVideoLoadingProgressView() {
        return null;
    }

    /**
     * Returns the system ui visibility after entering or exiting fullscreen.
     * @param view The decor view belongs to the activity window
     * @param enterFullscreen True if video is going fullscreen, or false otherwise.
     */
    @SuppressLint("InlinedApi")
    private void setSystemUiVisibility(View view, boolean enterFullscreen) {
        if (enterFullscreen) {
            mActivity.getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }

        int systemUiVisibility = view.getSystemUiVisibility();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (enterFullscreen) {
            systemUiVisibility |= flags;
        } else {
            systemUiVisibility &= ~flags;
        }
        view.setSystemUiVisibility(systemUiVisibility);
    }
}

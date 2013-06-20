/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.mogoweb.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.mogoweb.browser.UrlInputView.StateListener;
import com.mogoweb.chrome.WebView;

/**
 * Ui for regular phone screen sizes
 */
public class PhoneUi extends BaseUi {

    private static final String LOGTAG = "PhoneUi";
    private static final int MSG_INIT_NAVSCREEN = 100;

//    private NavScreen mNavScreen;
    private AnimScreen mAnimScreen;
    private NavigationBarPhone mNavigationBar;
    private int mActionBarHeight;

    boolean mAnimating;
    boolean mShowNav = false;

    /**
     * @param browser
     * @param controller
     */
    public PhoneUi(Activity browser, UiController controller) {
        super(browser, controller);
        setUseQuickControls(BrowserSettings.getInstance().useQuickControls());
        mNavigationBar = (NavigationBarPhone) mTitleBar.getNavigationBar();
        TypedValue heightValue = new TypedValue();
//        browser.getTheme().resolveAttribute(
//                com.android.internal.R.attr.actionBarSize, heightValue, true);
        mActionBarHeight = TypedValue.complexToDimensionPixelSize(heightValue.data,
                browser.getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        hideTitleBar();
    }

    @Override
    public void editUrl(boolean clearInput, boolean forceIME) {
        if (mUseQuickControls) {
            mTitleBar.setShowProgressOnly(false);
        }
        //Do nothing while at Nav show screen.
        if (mShowNav) return;
        super.editUrl(clearInput, forceIME);
    }

    @Override
    public boolean onBackKey() {
        if (showingNavScreen()) {
//            mNavScreen.close(mUiController.getTabControl().getCurrentPosition());
            return true;
        }
        return super.onBackKey();
    }

    private boolean showingNavScreen() {
//        return mNavScreen != null && mNavScreen.getVisibility() == View.VISIBLE;
        return false;
    }

    @Override
    public boolean dispatchKey(int code, KeyEvent event) {
        return false;
    }

    @Override
    public void onProgressChanged(Tab tab) {
        super.onProgressChanged(tab);
//        if (mNavScreen == null && getTitleBar().getHeight() > 0) {
//            mHandler.sendEmptyMessage(MSG_INIT_NAVSCREEN);
//        }
    }

    @Override
    protected void handleMessage(Message msg) {
        super.handleMessage(msg);
        if (msg.what == MSG_INIT_NAVSCREEN) {
//            if (mNavScreen == null) {
//                mNavScreen = new NavScreen(mActivity, mUiController, this);
//                mCustomViewContainer.addView(mNavScreen, COVER_SCREEN_PARAMS);
//                mNavScreen.setVisibility(View.GONE);
//            }
            if (mAnimScreen == null) {
                mAnimScreen = new AnimScreen(mActivity);
                // initialize bitmaps
                mAnimScreen.set(getTitleBar(), getWebView());
            }
        }
    }

    @Override
    public void setActiveTab(final Tab tab) {
        mTitleBar.cancelTitleBarAnimation(true);
        mTitleBar.setSkipTitleBarAnimations(true);
        super.setActiveTab(tab);

        //if at Nav screen show, detach tab like what showNavScreen() do.
        if (mShowNav) {
            detachTab(mActiveTab);
        }

        BrowserWebView view = (BrowserWebView) tab.getWebView();
        // TabControl.setCurrentTab has been called before this,
        // so the tab is guaranteed to have a webview
        if (view == null) {
            Log.e(LOGTAG, "active tab with no webview detected");
            return;
        }
        // Request focus on the top window.
        if (mUseQuickControls) {
            mPieControl.forceToTop(mContentView);
            view.setTitleBar(null);
            mTitleBar.setShowProgressOnly(true);
        } else {
            view.setTitleBar(mTitleBar);
        }
        // update nav bar state
        mNavigationBar.onStateChanged(StateListener.STATE_NORMAL);
        updateLockIconToLatest(tab);
        mTitleBar.setSkipTitleBarAnimations(false);
    }

    // menu handling callbacks

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuState(mActiveTab, menu);
        return true;
    }

    @Override
    public void updateMenuState(Tab tab, Menu menu) {
        MenuItem bm = menu.findItem(R.id.bookmarks_menu_id);
        if (bm != null) {
            bm.setVisible(!showingNavScreen());
        }
        MenuItem abm = menu.findItem(R.id.add_bookmark_menu_id);
        if (abm != null) {
            abm.setVisible((tab != null) && !tab.isSnapshot() && !showingNavScreen());
        }
        MenuItem info = menu.findItem(R.id.page_info_menu_id);
        if (info != null) {
            info.setVisible(false);
        }
        MenuItem newtab = menu.findItem(R.id.new_tab_menu_id);
        if (newtab != null && !mUseQuickControls) {
            newtab.setVisible(false);
        }
        MenuItem incognito = menu.findItem(R.id.incognito_menu_id);
        if (incognito != null) {
            incognito.setVisible(showingNavScreen() || mUseQuickControls);
        }
        MenuItem closeOthers = menu.findItem(R.id.close_other_tabs_id);
        if (closeOthers != null) {
            boolean isLastTab = true;
            if (tab != null) {
                isLastTab = (mTabControl.getTabCount() <= 1);
            }
            closeOthers.setEnabled(!isLastTab);
        }
        if (showingNavScreen()) {
            menu.setGroupVisible(R.id.LIVE_MENU, false);
            menu.setGroupVisible(R.id.SNAPSHOT_MENU, false);
            menu.setGroupVisible(R.id.NAV_MENU, false);
            menu.setGroupVisible(R.id.COMBO_MENU, true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (showingNavScreen()
                && (item.getItemId() != R.id.history_menu_id)
                && (item.getItemId() != R.id.snapshots_menu_id)) {
            hideNavScreen(mUiController.getTabControl().getCurrentPosition(), false);
        }
        return false;
    }

    @Override
    public void onContextMenuCreated(Menu menu) {
        hideTitleBar();
    }

    @Override
    public void onContextMenuClosed(Menu menu, boolean inLoad) {
        if (inLoad) {
            showTitleBar();
        }
    }

    // action mode callbacks

    @Override
    public void onActionModeStarted(ActionMode mode) {
        if (!isEditingUrl()) {
            hideTitleBar();
        } else {
            mTitleBar.animate().translationY(mActionBarHeight);
        }
    }

    @Override
    public void onActionModeFinished(boolean inLoad) {
        mTitleBar.animate().translationY(0);
        if (inLoad) {
            if (mUseQuickControls) {
                mTitleBar.setShowProgressOnly(true);
            }
            showTitleBar();
        }
    }

    @Override
    public boolean isWebShowing() {
        return super.isWebShowing() && !showingNavScreen();
    }

    @Override
    public void showWeb(boolean animate) {
        super.showWeb(animate);
        hideNavScreen(mUiController.getTabControl().getCurrentPosition(), animate);
    }

    void showNavScreen() {
//        mShowNav = true;
//        mUiController.setBlockEvents(true);
//        if (mNavScreen == null) {
//            mNavScreen = new NavScreen(mActivity, mUiController, this);
//            mCustomViewContainer.addView(mNavScreen, COVER_SCREEN_PARAMS);
//        } else {
//            mNavScreen.setVisibility(View.VISIBLE);
//            mNavScreen.setAlpha(1f);
//            mNavScreen.refreshAdapter();
//        }
//        mActiveTab.capture();
//        if (mAnimScreen == null) {
//            mAnimScreen = new AnimScreen(mActivity);
//        } else {
//            mAnimScreen.mMain.setAlpha(1f);
//            mAnimScreen.mTitle.setAlpha(1f);
//            mAnimScreen.setScaleFactor(1f);
//        }
//        mAnimScreen.set(getTitleBar(), getWebView());
//        if (mAnimScreen.mMain.getParent() == null) {
//            mCustomViewContainer.addView(mAnimScreen.mMain, COVER_SCREEN_PARAMS);
//        }
//        mCustomViewContainer.setVisibility(View.VISIBLE);
//        mCustomViewContainer.bringToFront();
//        mAnimScreen.mMain.layout(0, 0, mContentView.getWidth(),
//                mContentView.getHeight());
//        int fromLeft = 0;
//        int fromTop = getTitleBar().getHeight();
//        int fromRight = mContentView.getWidth();
//        int fromBottom = mContentView.getHeight();
//        int width = mActivity.getResources().getDimensionPixelSize(R.dimen.nav_tab_width);
//        int height = mActivity.getResources().getDimensionPixelSize(R.dimen.nav_tab_height);
//        int ntth = mActivity.getResources().getDimensionPixelSize(R.dimen.nav_tab_titleheight);
//        int toLeft = (mContentView.getWidth() - width) / 2;
//        int toTop = ((fromBottom - (ntth + height)) / 2 + ntth);
//        int toRight = toLeft + width;
//        int toBottom = toTop + height;
//        float scaleFactor = width / (float) mContentView.getWidth();
//        detachTab(mActiveTab);
//        mContentView.setVisibility(View.GONE);
//        AnimatorSet set1 = new AnimatorSet();
//        AnimatorSet inanim = new AnimatorSet();
//        ObjectAnimator tx = ObjectAnimator.ofInt(mAnimScreen.mContent, "left",
//                fromLeft, toLeft);
//        ObjectAnimator ty = ObjectAnimator.ofInt(mAnimScreen.mContent, "top",
//                fromTop, toTop);
//        ObjectAnimator tr = ObjectAnimator.ofInt(mAnimScreen.mContent, "right",
//                fromRight, toRight);
//        ObjectAnimator tb = ObjectAnimator.ofInt(mAnimScreen.mContent, "bottom",
//                fromBottom, toBottom);
//        ObjectAnimator title = ObjectAnimator.ofFloat(mAnimScreen.mTitle, "alpha",
//                1f, 0f);
//        ObjectAnimator sx = ObjectAnimator.ofFloat(mAnimScreen, "scaleFactor",
//                1f, scaleFactor);
//        ObjectAnimator blend1 = ObjectAnimator.ofFloat(mAnimScreen.mMain,
//                "alpha", 1f, 0f);
//        blend1.setDuration(100);
//
//        inanim.playTogether(tx, ty, tr, tb, sx, title);
//        inanim.setDuration(200);
//        set1.addListener(new AnimatorListenerAdapter() {
//            @Override
//            public void onAnimationEnd(Animator anim) {
//                mCustomViewContainer.removeView(mAnimScreen.mMain);
//                finishAnimationIn();
//                mUiController.setBlockEvents(false);
//            }
//        });
//        set1.playSequentially(inanim, blend1);
//        set1.start();
    }

    private void finishAnimationIn() {
//        if (showingNavScreen()) {
//            // notify accessibility manager about the screen change
//            mNavScreen.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
//            mTabControl.setOnThumbnailUpdatedListener(mNavScreen);
//        }
    }

    void hideNavScreen(int position, boolean animate) {
//        mShowNav = false;
//        if (!showingNavScreen()) return;
//        final Tab tab = mUiController.getTabControl().getTab(position);
//        if ((tab == null) || !animate) {
//            if (tab != null) {
//                setActiveTab(tab);
//            } else if (mTabControl.getTabCount() > 0) {
//                // use a fallback tab
//                setActiveTab(mTabControl.getCurrentTab());
//            }
//            mContentView.setVisibility(View.VISIBLE);
//            finishAnimateOut();
//            return;
//        }
//        NavTabView tabview = (NavTabView) mNavScreen.getTabView(position);
//        if (tabview == null) {
//            if (mTabControl.getTabCount() > 0) {
//                // use a fallback tab
//                setActiveTab(mTabControl.getCurrentTab());
//            }
//            mContentView.setVisibility(View.VISIBLE);
//            finishAnimateOut();
//            return;
//        }
//        mUiController.setBlockEvents(true);
//        mUiController.setActiveTab(tab);
//        mContentView.setVisibility(View.VISIBLE);
//        if (mAnimScreen == null) {
//            mAnimScreen = new AnimScreen(mActivity);
//        }
//        mAnimScreen.set(tab.getScreenshot());
//        if (mAnimScreen.mMain.getParent() == null) {
//            mCustomViewContainer.addView(mAnimScreen.mMain, COVER_SCREEN_PARAMS);
//        }
//        mAnimScreen.mMain.layout(0, 0, mContentView.getWidth(),
//                mContentView.getHeight());
//        mNavScreen.mScroller.finishScroller();
//        ImageView target = tabview.mImage;
//        int toLeft = 0;
//        int toTop = (tab.getWebView() != null) ? tab.getWebView().getVisibleTitleHeight() : 0;
//        int toRight = mContentView.getWidth();
//        int width = target.getDrawable().getIntrinsicWidth();
//        int height = target.getDrawable().getIntrinsicHeight();
//        int fromLeft = tabview.getLeft() + target.getLeft() - mNavScreen.mScroller.getScrollX();
//        int fromTop = tabview.getTop() + target.getTop() - mNavScreen.mScroller.getScrollY();
//        int fromRight = fromLeft + width;
//        int fromBottom = fromTop + height;
//        float scaleFactor = mContentView.getWidth() / (float) width;
//        int toBottom = toTop + (int) (height * scaleFactor);
//        mAnimScreen.mContent.setLeft(fromLeft);
//        mAnimScreen.mContent.setTop(fromTop);
//        mAnimScreen.mContent.setRight(fromRight);
//        mAnimScreen.mContent.setBottom(fromBottom);
//        mAnimScreen.setScaleFactor(1f);
//        AnimatorSet set1 = new AnimatorSet();
//        ObjectAnimator fade2 = ObjectAnimator.ofFloat(mAnimScreen.mMain, "alpha", 0f, 1f);
//        ObjectAnimator fade1 = ObjectAnimator.ofFloat(mNavScreen, "alpha", 1f, 0f);
//        set1.playTogether(fade1, fade2);
//        set1.setDuration(100);
//        AnimatorSet set2 = new AnimatorSet();
//        ObjectAnimator l = ObjectAnimator.ofInt(mAnimScreen.mContent, "left",
//                fromLeft, toLeft);
//        ObjectAnimator t = ObjectAnimator.ofInt(mAnimScreen.mContent, "top",
//                fromTop, toTop);
//        ObjectAnimator r = ObjectAnimator.ofInt(mAnimScreen.mContent, "right",
//                fromRight, toRight);
//        ObjectAnimator b = ObjectAnimator.ofInt(mAnimScreen.mContent, "bottom",
//                fromBottom, toBottom);
//        ObjectAnimator scale = ObjectAnimator.ofFloat(mAnimScreen, "scaleFactor",
//                1f, scaleFactor);
//        ObjectAnimator otheralpha = ObjectAnimator.ofFloat(mCustomViewContainer, "alpha", 1f, 0f);
//        otheralpha.setDuration(100);
//        set2.playTogether(l, t, r, b, scale);
//        set2.setDuration(200);
//        AnimatorSet combo = new AnimatorSet();
//        combo.playSequentially(set1, set2, otheralpha);
//        combo.addListener(new AnimatorListenerAdapter() {
//            @Override
//            public void onAnimationEnd(Animator anim) {
//                mCustomViewContainer.removeView(mAnimScreen.mMain);
//                finishAnimateOut();
//                mUiController.setBlockEvents(false);
//            }
//        });
//        combo.start();
    }

    private void finishAnimateOut() {
//        mTabControl.setOnThumbnailUpdatedListener(null);
//        mNavScreen.setVisibility(View.GONE);
//        mCustomViewContainer.setAlpha(1f);
//        mCustomViewContainer.setVisibility(View.GONE);
    }

    @Override
    public boolean needsRestoreAllTabs() {
        return false;
    }

    public void toggleNavScreen() {
        if (!showingNavScreen()) {
            showNavScreen();
        } else {
            hideNavScreen(mUiController.getTabControl().getCurrentPosition(), false);
        }
    }

    @Override
    public boolean shouldCaptureThumbnails() {
        return true;
    }

    static class AnimScreen {

        private View mMain;
        private ImageView mTitle;
        private ImageView mContent;
        private float mScale;
        private Bitmap mTitleBarBitmap;
        private Bitmap mContentBitmap;

        public AnimScreen(Context ctx) {
            mMain = LayoutInflater.from(ctx).inflate(R.layout.anim_screen,
                    null);
            mTitle = (ImageView) mMain.findViewById(R.id.title);
            mContent = (ImageView) mMain.findViewById(R.id.content);
            mContent.setScaleType(ImageView.ScaleType.MATRIX);
            mContent.setImageMatrix(new Matrix());
            mScale = 1.0f;
            setScaleFactor(getScaleFactor());
        }

        public void set(TitleBar tbar, WebView web) {
            if (tbar == null || web == null) {
                return;
            }
            if (tbar.getWidth() > 0 && tbar.getEmbeddedHeight() > 0) {
                if (mTitleBarBitmap == null
                        || mTitleBarBitmap.getWidth() != tbar.getWidth()
                        || mTitleBarBitmap.getHeight() != tbar.getEmbeddedHeight()) {
                    mTitleBarBitmap = safeCreateBitmap(tbar.getWidth(),
                            tbar.getEmbeddedHeight());
                }
                if (mTitleBarBitmap != null) {
                    Canvas c = new Canvas(mTitleBarBitmap);
                    tbar.draw(c);
                    c.setBitmap(null);
                }
            } else {
                mTitleBarBitmap = null;
            }
            mTitle.setImageBitmap(mTitleBarBitmap);
            mTitle.setVisibility(View.VISIBLE);
            int h = web.getHeight() - tbar.getEmbeddedHeight();
            if (mContentBitmap == null
                    || mContentBitmap.getWidth() != web.getWidth()
                    || mContentBitmap.getHeight() != h) {
                mContentBitmap = safeCreateBitmap(web.getWidth(), h);
            }
            if (mContentBitmap != null) {
                Canvas c = new Canvas(mContentBitmap);
                int tx = web.getScrollX();
                int ty = web.getScrollY();
                c.translate(-tx, -ty - tbar.getEmbeddedHeight());
                web.draw(c);
                c.setBitmap(null);
            }
            mContent.setImageBitmap(mContentBitmap);
        }

        private Bitmap safeCreateBitmap(int width, int height) {
            if (width <= 0 || height <= 0) {
                Log.w(LOGTAG, "safeCreateBitmap failed! width: " + width
                        + ", height: " + height);
                return null;
            }
            return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        }

        public void set(Bitmap image) {
            mTitle.setVisibility(View.GONE);
            mContent.setImageBitmap(image);
        }

        private void setScaleFactor(float sf) {
            mScale = sf;
            Matrix m = new Matrix();
            m.postScale(sf,sf);
            mContent.setImageMatrix(m);
        }

        private float getScaleFactor() {
            return mScale;
        }

    }

}

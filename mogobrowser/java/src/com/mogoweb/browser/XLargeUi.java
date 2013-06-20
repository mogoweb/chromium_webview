///*
// * Copyright (C) 2010 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.mogoweb.browser;
//
//import android.app.ActionBar;
//import android.app.Activity;
//import android.content.res.Resources;
//import android.graphics.Bitmap;
//import android.graphics.drawable.BitmapDrawable;
//import android.graphics.drawable.Drawable;
//import android.graphics.drawable.LayerDrawable;
//import android.graphics.drawable.PaintDrawable;
//import android.os.Bundle;
//import android.os.Handler;
//import android.util.Log;
//import android.view.ActionMode;
//import android.view.KeyEvent;
//import android.view.Menu;
//import android.view.MenuItem;
//import android.webkit.WebView;
//import android.webkit.WebViewClassic;
//
//import java.util.List;
//
///**
// * Ui for xlarge screen sizes
// */
//public class XLargeUi extends BaseUi {
//
//    private static final String LOGTAG = "XLargeUi";
//
//    private PaintDrawable mFaviconBackground;
//
//    private ActionBar mActionBar;
//    private TabBar mTabBar;
//
//    private NavigationBarTablet mNavBar;
//
//    private Handler mHandler;
//
//    /**
//     * @param browser
//     * @param controller
//     */
//    public XLargeUi(Activity browser, UiController controller) {
//        super(browser, controller);
//        mHandler = new Handler();
//        mNavBar = (NavigationBarTablet) mTitleBar.getNavigationBar();
//        mTabBar = new TabBar(mActivity, mUiController, this);
//        mActionBar = mActivity.getActionBar();
//        setupActionBar();
//        setUseQuickControls(BrowserSettings.getInstance().useQuickControls());
//    }
//
//    private void setupActionBar() {
//        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
//        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
//        mActionBar.setCustomView(mTabBar);
//    }
//
//    public void showComboView(ComboViews startWith, Bundle extras) {
//        super.showComboView(startWith, extras);
//        if (mUseQuickControls) {
//            mActionBar.show();
//        }
//    }
//
//    @Override
//    public void setUseQuickControls(boolean useQuickControls) {
//        super.setUseQuickControls(useQuickControls);
//        checkHideActionBar();
//        if (!useQuickControls) {
//            mActionBar.show();
//        }
//        mTabBar.setUseQuickControls(mUseQuickControls);
//        // We need to update the tabs with this change
//        for (Tab t : mTabControl.getTabs()) {
//            t.updateShouldCaptureThumbnails();
//        }
//    }
//
//    private void checkHideActionBar() {
//        if (mUseQuickControls) {
//            mHandler.post(new Runnable() {
//                public void run() {
//                    mActionBar.hide();
//                }
//            });
//        }
//    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        mNavBar.clearCompletions();
//        checkHideActionBar();
//    }
//
//    @Override
//    public void onDestroy() {
//        hideTitleBar();
//    }
//
//    void stopWebViewScrolling() {
//        BrowserWebView web = (BrowserWebView) mUiController.getCurrentWebView();
//        if (web != null) {
//            WebViewClassic.fromWebView(web).stopScroll();
//        }
//    }
//
//    @Override
//    public boolean onPrepareOptionsMenu(Menu menu) {
//        MenuItem bm = menu.findItem(R.id.bookmarks_menu_id);
//        if (bm != null) {
//            bm.setVisible(false);
//        }
//        return true;
//    }
//
//
//    // WebView callbacks
//
//    @Override
//    public void addTab(Tab tab) {
//        mTabBar.onNewTab(tab);
//    }
//
//    protected void onAddTabCompleted(Tab tab) {
//        checkHideActionBar();
//    }
//
//    @Override
//    public void setActiveTab(final Tab tab) {
//        mTitleBar.cancelTitleBarAnimation(true);
//        mTitleBar.setSkipTitleBarAnimations(true);
//        super.setActiveTab(tab);
//        BrowserWebView view = (BrowserWebView) tab.getWebView();
//        // TabControl.setCurrentTab has been called before this,
//        // so the tab is guaranteed to have a webview
//        if (view == null) {
//            Log.e(LOGTAG, "active tab with no webview detected");
//            return;
//        }
//        mTabBar.onSetActiveTab(tab);
//        updateLockIconToLatest(tab);
//        mTitleBar.setSkipTitleBarAnimations(false);
//    }
//
//    @Override
//    public void updateTabs(List<Tab> tabs) {
//        mTabBar.updateTabs(tabs);
//        checkHideActionBar();
//    }
//
//    @Override
//    public void removeTab(Tab tab) {
//        mTitleBar.cancelTitleBarAnimation(true);
//        mTitleBar.setSkipTitleBarAnimations(true);
//        super.removeTab(tab);
//        mTabBar.onRemoveTab(tab);
//        mTitleBar.setSkipTitleBarAnimations(false);
//    }
//
//    protected void onRemoveTabCompleted(Tab tab) {
//        checkHideActionBar();
//    }
//
//    int getContentWidth() {
//        if (mContentView != null) {
//            return mContentView.getWidth();
//        }
//        return 0;
//    }
//
//    @Override
//    public void editUrl(boolean clearInput, boolean forceIME) {
//        if (mUseQuickControls) {
//            mTitleBar.setShowProgressOnly(false);
//        }
//        super.editUrl(clearInput, forceIME);
//    }
//
//    // action mode callbacks
//
//    @Override
//    public void onActionModeStarted(ActionMode mode) {
//        if (!mTitleBar.isEditingUrl()) {
//            // hide the title bar when CAB is shown
//            hideTitleBar();
//        }
//    }
//
//    @Override
//    public void onActionModeFinished(boolean inLoad) {
//        checkHideActionBar();
//        if (inLoad) {
//            // the titlebar was removed when the CAB was shown
//            // if the page is loading, show it again
//            if (mUseQuickControls) {
//                mTitleBar.setShowProgressOnly(true);
//            }
//            showTitleBar();
//        }
//    }
//
//    @Override
//    protected void updateNavigationState(Tab tab) {
//        mNavBar.updateNavigationState(tab);
//    }
//
//    @Override
//    public void setUrlTitle(Tab tab) {
//        super.setUrlTitle(tab);
//        mTabBar.onUrlAndTitle(tab, tab.getUrl(), tab.getTitle());
//    }
//
//    // Set the favicon in the title bar.
//    @Override
//    public void setFavicon(Tab tab) {
//        super.setFavicon(tab);
//        mTabBar.onFavicon(tab, tab.getFavicon());
//    }
//
//    @Override
//    public void onHideCustomView() {
//        super.onHideCustomView();
//        checkHideActionBar();
//    }
//
//    @Override
//    public boolean dispatchKey(int code, KeyEvent event) {
//        if (mActiveTab != null) {
//            WebView web = mActiveTab.getWebView();
//            if (event.getAction() == KeyEvent.ACTION_DOWN) {
//                switch (code) {
//                    case KeyEvent.KEYCODE_TAB:
//                    case KeyEvent.KEYCODE_DPAD_UP:
//                    case KeyEvent.KEYCODE_DPAD_LEFT:
//                        if ((web != null) && web.hasFocus() && !mTitleBar.hasFocus()) {
//                            editUrl(false, false);
//                            return true;
//                        }
//                }
//                boolean ctrl = event.hasModifiers(KeyEvent.META_CTRL_ON);
//                if (!ctrl && isTypingKey(event) && !mTitleBar.isEditingUrl()) {
//                    editUrl(true, false);
//                    return mContentView.dispatchKeyEvent(event);
//                }
//            }
//        }
//        return false;
//    }
//
//    private boolean isTypingKey(KeyEvent evt) {
//        return evt.getUnicodeChar() > 0;
//    }
//
//    TabBar getTabBar() {
//        return mTabBar;
//    }
//
//    @Override
//    public boolean shouldCaptureThumbnails() {
//        return mUseQuickControls;
//    }
//
//    private Drawable getFaviconBackground() {
//        if (mFaviconBackground == null) {
//            mFaviconBackground = new PaintDrawable();
//            Resources res = mActivity.getResources();
//            mFaviconBackground.getPaint().setColor(
//                    res.getColor(R.color.tabFaviconBackground));
//            mFaviconBackground.setCornerRadius(
//                    res.getDimension(R.dimen.tab_favicon_corner_radius));
//        }
//        return mFaviconBackground;
//    }
//
//    @Override
//    public Drawable getFaviconDrawable(Bitmap icon) {
//        Drawable[] array = new Drawable[2];
//        array[0] = getFaviconBackground();
//        if (icon == null) {
//            array[1] = mGenericFavicon;
//        } else {
//            array[1] = new BitmapDrawable(mActivity.getResources(), icon);
//        }
//        LayerDrawable d = new LayerDrawable(array);
//        d.setLayerInset(1, 2, 2, 2, 2);
//        return d;
//    }
//
//}

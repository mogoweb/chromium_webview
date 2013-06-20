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

package com.mogoweb.browser;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.mogoweb.browser.UI.ComboViews;
import com.mogoweb.browser.view.PieItem;
import com.mogoweb.browser.view.PieMenu;
import com.mogoweb.browser.view.PieMenu.PieView.OnLayoutListener;
import com.mogoweb.browser.view.PieStackView;
import com.mogoweb.browser.view.PieStackView.OnCurrentListener;
import com.mogoweb.chrome.WebView;

/**
 * Controller for Quick Controls pie menu
 */
public class PieControl implements PieMenu.PieController, OnClickListener {

    protected Activity mActivity;
    protected UiController mUiController;
    protected PieMenu mPie;
    protected int mItemSize;
    protected TextView mTabsCount;
    private BaseUi mUi;
    private PieItem mBack;
    private PieItem mForward;
    private PieItem mRefresh;
    private PieItem mUrl;
    private PieItem mOptions;
    private PieItem mBookmarks;
    private PieItem mHistory;
    private PieItem mAddBookmark;
    private PieItem mNewTab;
    private PieItem mIncognito;
    private PieItem mClose;
    private PieItem mShowTabs;
    private PieItem mInfo;
    private PieItem mFind;
    private PieItem mShare;
    private PieItem mRDS;
    private TabAdapter mTabAdapter;

    public PieControl(Activity activity, UiController controller, BaseUi ui) {
        mActivity = activity;
        mUiController = controller;
        mItemSize = (int) activity.getResources().getDimension(R.dimen.qc_item_size);
        mUi = ui;
    }

    public void stopEditingUrl() {
        mUi.stopEditingUrl();
    }

    protected void attachToContainer(FrameLayout container) {
        if (mPie == null) {
            mPie = new PieMenu(mActivity);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            mPie.setLayoutParams(lp);
            populateMenu();
            mPie.setController(this);
        }
        container.addView(mPie);
    }

    protected void removeFromContainer(FrameLayout container) {
        container.removeView(mPie);
    }

    protected void forceToTop(FrameLayout container) {
        if (mPie.getParent() != null) {
            container.removeView(mPie);
            container.addView(mPie);
        }
    }

    protected void setClickListener(OnClickListener listener, PieItem... items) {
        for (PieItem item : items) {
            item.getView().setOnClickListener(listener);
        }
    }

    @Override
    public boolean onOpen() {
        int n = mUiController.getTabControl().getTabCount();
        mTabsCount.setText(Integer.toString(n));
        Tab tab = mUiController.getCurrentTab();
        if (tab != null) {
            mForward.setEnabled(tab.canGoForward());
        }
        WebView view = mUiController.getCurrentWebView();
        if (view != null) {
            ImageView icon = (ImageView) mRDS.getView();
            if (mUiController.getSettings().hasDesktopUseragent(view)) {
                icon.setImageResource(R.drawable.ic_mobile);
            } else {
                icon.setImageResource(R.drawable.ic_desktop_holo_dark);
            }
        }
        return true;
    }

    protected void populateMenu() {
        mBack = makeItem(R.drawable.ic_back_holo_dark, 1);
        mUrl = makeItem(R.drawable.ic_web_holo_dark, 1);
        mBookmarks = makeItem(R.drawable.ic_bookmarks_holo_dark, 1);
        mHistory = makeItem(R.drawable.ic_history_holo_dark, 1);
        mAddBookmark = makeItem(R.drawable.ic_bookmark_on_holo_dark, 1);
        mRefresh = makeItem(R.drawable.ic_refresh_holo_dark, 1);
        mForward = makeItem(R.drawable.ic_forward_holo_dark, 1);
        mNewTab = makeItem(R.drawable.ic_new_window_holo_dark, 1);
        mIncognito = makeItem(R.drawable.ic_new_incognito_holo_dark, 1);
        mClose = makeItem(R.drawable.ic_close_window_holo_dark, 1);
        mInfo = makeItem(android.R.drawable.ic_menu_info_details, 1);
        mFind = makeItem(R.drawable.ic_search_holo_dark, 1);
        mShare = makeItem(R.drawable.ic_share_holo_dark, 1);
        View tabs = makeTabsView();
        mShowTabs = new PieItem(tabs, 1);
        mOptions = makeItem(R.drawable.ic_settings_holo_dark, 1);
        mRDS = makeItem(R.drawable.ic_desktop_holo_dark, 1);
        mTabAdapter = new TabAdapter(mActivity, mUiController);
        PieStackView stack = new PieStackView(mActivity);
        stack.setLayoutListener(new OnLayoutListener() {
            @Override
            public void onLayout(int ax, int ay, boolean left) {
                buildTabs();
            }
        });
        stack.setOnCurrentListener(mTabAdapter);
        stack.setAdapter(mTabAdapter);
        mShowTabs.setPieView(stack);
        setClickListener(this, mBack, mRefresh, mForward, mUrl, mFind, mInfo,
                mShare, mBookmarks, mNewTab, mIncognito, mClose, mHistory,
                mAddBookmark, mOptions, mRDS);
        if (!BrowserActivity.isTablet(mActivity)) {
            mShowTabs.getView().setOnClickListener(this);
        }
        // level 1
        mPie.addItem(mOptions);
        mOptions.addItem(mRDS);
        mOptions.addItem(makeFiller());
        mOptions.addItem(makeFiller());
        mOptions.addItem(makeFiller());
        mPie.addItem(mBack);
        mBack.addItem(mRefresh);
        mBack.addItem(mForward);
        mBack.addItem(makeFiller());
        mBack.addItem(makeFiller());
        mPie.addItem(mUrl);
        mUrl.addItem(mFind);
        mUrl.addItem(mShare);
        mUrl.addItem(makeFiller());
        mUrl.addItem(makeFiller());
        mPie.addItem(mShowTabs);
        mShowTabs.addItem(mClose);
        mShowTabs.addItem(mIncognito);
        mShowTabs.addItem(mNewTab);
        mShowTabs.addItem(makeFiller());
        mPie.addItem(mBookmarks);
        mBookmarks.addItem(makeFiller());
        mBookmarks.addItem(makeFiller());
        mBookmarks.addItem(mAddBookmark);
        mBookmarks.addItem(mHistory);
    }

    @Override
    public void onClick(View v) {
        Tab tab = mUiController.getTabControl().getCurrentTab();
        WebView web = tab.getWebView();
        if (mBack.getView() == v) {
            tab.goBack();
        } else if (mForward.getView() == v) {
            tab.goForward();
        } else if (mRefresh.getView() == v) {
            if (tab.inPageLoad()) {
                web.stopLoading();
            } else {
                web.reload();
            }
        } else if (mUrl.getView() == v) {
            mUi.editUrl(false, true);
        } else if (mBookmarks.getView() == v) {
            mUiController.bookmarksOrHistoryPicker(ComboViews.Bookmarks);
        } else if (mHistory.getView() == v) {
            mUiController.bookmarksOrHistoryPicker(ComboViews.History);
        } else if (mAddBookmark.getView() == v) {
            mUiController.bookmarkCurrentPage();
        } else if (mNewTab.getView() == v) {
            mUiController.openTabToHomePage();
            mUi.editUrl(false, true);
        } else if (mIncognito.getView() == v) {
            mUiController.openIncognitoTab();
            mUi.editUrl(false, true);
        } else if (mClose.getView() == v) {
            mUiController.closeCurrentTab();
        } else if (mOptions.getView() == v) {
            mUiController.openPreferences();
        } else if (mShare.getView() == v) {
            mUiController.shareCurrentPage();
        } else if (mInfo.getView() == v) {
            mUiController.showPageInfo();
        } else if (mFind.getView() == v) {
            mUiController.findOnPage();
        } else if (mRDS.getView() == v) {
            mUiController.toggleUserAgent();
        } else if (mShowTabs.getView() == v) {
            ((PhoneUi) mUi).showNavScreen();
        }
    }

    private void buildTabs() {
        final List<Tab> tabs = mUiController.getTabs();
        mUi.getActiveTab().capture();
        mTabAdapter.setTabs(tabs);
        PieStackView sym = (PieStackView) mShowTabs.getPieView();
        sym.setCurrent(mUiController.getTabControl().getCurrentPosition());
    }

    protected PieItem makeItem(int image, int l) {
        ImageView view = new ImageView(mActivity);
        view.setImageResource(image);
        view.setMinimumWidth(mItemSize);
        view.setMinimumHeight(mItemSize);
        view.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        return new PieItem(view, l);
    }

    protected PieItem makeFiller() {
        return new PieItem(null, 1);
    }

    protected View makeTabsView() {
        View v = mActivity.getLayoutInflater().inflate(R.layout.qc_tabs_view, null);
        mTabsCount = (TextView) v.findViewById(R.id.label);
        mTabsCount.setText("1");
        ImageView image = (ImageView) v.findViewById(R.id.icon);
        image.setImageResource(R.drawable.ic_windows_holo_dark);
        image.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        v.setLayoutParams(lp);
        return v;
    }

    static class TabAdapter extends BaseAdapter implements OnCurrentListener {

        LayoutInflater mInflater;
        UiController mUiController;
        private List<Tab> mTabs;
        private int mCurrent;

        public TabAdapter(Context ctx, UiController ctl) {
            mInflater = LayoutInflater.from(ctx);
            mUiController = ctl;
            mTabs = new ArrayList<Tab>();
            mCurrent = -1;
        }

        public void setTabs(List<Tab> tabs) {
            mTabs = tabs;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public Tab getItem(int position) {
            return mTabs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Tab tab = mTabs.get(position);
            View view = mInflater.inflate(R.layout.qc_tab,
                    null);
            ImageView thumb = (ImageView) view.findViewById(R.id.thumb);
            TextView title1 = (TextView) view.findViewById(R.id.title1);
            TextView title2 = (TextView) view.findViewById(R.id.title2);
            Bitmap b = tab.getScreenshot();
            if (b != null) {
                thumb.setImageBitmap(b);
            }
            if (position > mCurrent) {
                title1.setVisibility(View.GONE);
                title2.setText(tab.getTitle());
            } else {
                title2.setVisibility(View.GONE);
                title1.setText(tab.getTitle());
            }
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mUiController.switchToTab(tab);
                }
            });
            return view;
        }

        @Override
        public void onSetCurrent(int index) {
            mCurrent = index;
        }

    }

}

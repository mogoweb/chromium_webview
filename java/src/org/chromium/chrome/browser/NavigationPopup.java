// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.ListView.FixedViewInfo;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.chromium.base.CalledByNative;
import org.chromium.base.ThreadUtils;
import org.chromium.ui.LocalizationUtils;
import org.chromium.chrome.R;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.NavigationClient;
import org.chromium.content.browser.NavigationEntry;
import org.chromium.content.browser.NavigationHistory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A popup that handles displaying the navigation history for a given tab.
 */
public class NavigationPopup extends ListPopupWindow implements AdapterView.OnItemClickListener {

    private static final int FAVICON_SIZE_DP = 16;

    private static final int MAXIMUM_HISTORY_ITEMS = 8;

    private final Context mContext;
    private final NavigationClient mNavigationClient;
    private final NavigationHistory mHistory;
    private final NavigationAdapter mAdapter;
    private final ListItemFactory mListItemFactory;

    private final int mFaviconSize;

    private int mNativeNavigationPopup;

    /**
     * Constructs a new popup with the given history information.
     *
     * @param context The context used for building the popup.
     * @param navigationClient The owner of the history being displayed.
     * @param isForward Whether to request forward navigation entries.
     */
    public NavigationPopup(
            Context context, NavigationClient navigationClient, boolean isForward) {
        super(context, null, android.R.attr.popupMenuStyle);
        mContext = context;
        mNavigationClient = navigationClient;
        mHistory = mNavigationClient.getDirectedNavigationHistory(
                isForward, MAXIMUM_HISTORY_ITEMS);
        mAdapter = new NavigationAdapter();

        float density = mContext.getResources().getDisplayMetrics().density;
        mFaviconSize = (int) (density * FAVICON_SIZE_DP);

        setModal(true);
        setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        setOnItemClickListener(this);

        setAdapter(new HeaderViewListAdapter(null, null, mAdapter));

        mListItemFactory = new ListItemFactory(context);
    }

    /**
     * @return Whether a navigation popup is valid for the given page.
     */
    public boolean shouldBeShown() {
        return mHistory.getEntryCount() > 0;
    }

    @Override
    public void show() {
        if (mNativeNavigationPopup == 0) initializeNative();
        super.show();
    }

    @Override
    public void dismiss() {
        if (mNativeNavigationPopup != 0) {
            nativeDestroy(mNativeNavigationPopup);
            mNativeNavigationPopup = 0;
        }
        super.dismiss();
    }

    private void initializeNative() {
        ThreadUtils.assertOnUiThread();
        mNativeNavigationPopup = nativeInit();

        Set<String> requestedUrls = new HashSet<String>();
        for (int i = 0; i < mHistory.getEntryCount(); i++) {
            NavigationEntry entry = mHistory.getEntryAtIndex(i);
            if (entry.getFavicon() != null) continue;
            String url = entry.getUrl();
            if (!requestedUrls.contains(url)) {
                nativeFetchFaviconForUrl(mNativeNavigationPopup, url);
                requestedUrls.add(url);
            }
        }
        nativeFetchFaviconForUrl(mNativeNavigationPopup, nativeGetHistoryUrl());
    }

    @CalledByNative
    private void onFaviconUpdated(String url, Object favicon) {
        for (int i = 0; i < mHistory.getEntryCount(); i++) {
            NavigationEntry entry = mHistory.getEntryAtIndex(i);
            if (TextUtils.equals(url, entry.getUrl())) entry.updateFavicon((Bitmap) favicon);
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        NavigationEntry entry = (NavigationEntry) parent.getItemAtPosition(position);
        mNavigationClient.goToNavigationIndex(entry.getIndex());
        dismiss();
    }

    private void updateBitmapForTextView(TextView view, Bitmap bitmap) {
        Drawable faviconDrawable = null;
        if (bitmap != null) {
            faviconDrawable = new BitmapDrawable(mContext.getResources(), bitmap);
            ((BitmapDrawable) faviconDrawable).setGravity(Gravity.FILL);
        } else {
            faviconDrawable = new ColorDrawable(Color.TRANSPARENT);
        }
        faviconDrawable.setBounds(0, 0, mFaviconSize, mFaviconSize);
        view.setCompoundDrawables(faviconDrawable, null, null, null);
    }

    private static class ListItemFactory {
        private static final int LIST_ITEM_HEIGHT_DP = 48;
        private static final int PADDING_DP = 8;
        private static final int TEXT_SIZE_SP = 18;
        private static final float FADE_LENGTH_DP = 25.0f;
        private static final float FADE_STOP = 0.75f;

        int mFadeEdgeLength;
        int mFadePadding;
        int mListItemHeight;
        int mPadding;
        boolean mIsLayoutDirectionRTL;
        Context mContext;

        public ListItemFactory(Context context) {
            mContext = context;
            computeFadeDimensions();
        }

        private void computeFadeDimensions() {
            // Fade with linear gradient starting 25dp from right margin.
            // Reaches 0% opacity at 75% length. (Simulated with extra padding)
            float density = mContext.getResources().getDisplayMetrics().density;
            float fadeLength = (FADE_LENGTH_DP * density);
            mFadeEdgeLength = (int)(fadeLength * FADE_STOP);
            mFadePadding = (int)(fadeLength * (1 - FADE_STOP));
            mListItemHeight = (int) (density * LIST_ITEM_HEIGHT_DP);
            mPadding = (int) (density * PADDING_DP);
            mIsLayoutDirectionRTL = LocalizationUtils.isSystemLayoutDirectionRtl();
        }

        public TextView createListItem() {
            TextView view = new TextView(mContext);
            view.setFadingEdgeLength(mFadeEdgeLength);
            view.setHorizontalFadingEdgeEnabled(true);
            view.setSingleLine();
            view.setTextSize(TEXT_SIZE_SP);
            view.setMinimumHeight(mListItemHeight);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setCompoundDrawablePadding(mPadding);
            if (!mIsLayoutDirectionRTL) {
                view.setPadding(mPadding, 0, mPadding + mFadePadding , 0);
            }
            else {
                view.setPadding(mPadding + mFadePadding, 0, mPadding, 0);
            }
            return view;
        }
    }

    private class NavigationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mHistory.getEntryCount();
        }

        @Override
        public Object getItem(int position) {
            return mHistory.getEntryAtIndex(position);
        }

        @Override
        public long getItemId(int position) {
            return ((NavigationEntry) getItem(position)).getIndex();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView != null && convertView instanceof TextView) {
                view = (TextView) convertView;
            } else {
                view = mListItemFactory.createListItem();
            }
            NavigationEntry entry = (NavigationEntry) getItem(position);

            String entryText = entry.getTitle();
            if (TextUtils.isEmpty(entryText)) entryText = entry.getVirtualUrl();
            if (TextUtils.isEmpty(entryText)) entryText = entry.getUrl();
            view.setText(entryText);
            updateBitmapForTextView(view, entry.getFavicon());

            return view;
        }
    }

    private static native String nativeGetHistoryUrl();

    private native int nativeInit();
    private native void nativeDestroy(int nativeNavigationPopup);
    private native void nativeFetchFaviconForUrl(int nativeNavigationPopup, String url);
}

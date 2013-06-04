// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.impl;

import org.chromium.content.browser.NavigationEntry;

import android.graphics.Bitmap;

import com.mogoweb.chrome.WebHistoryItem;

public class WebHistoryItemImpl extends WebHistoryItem {
    private final int mId;
    private final String mUrl;
    private final String mOriginalUrl;
    private final String mTitle;
    private Bitmap mFavicon;

    private WebHistoryItemImpl(WebHistoryItemImpl item) {
        mId = item.mId;
        mUrl = item.mUrl;
        mOriginalUrl = item.mOriginalUrl;
        mTitle = item.mTitle;
        mFavicon = item.mFavicon;
    }

    public WebHistoryItemImpl(NavigationEntry entry) {
        mId = entry.getIndex();
        mUrl = entry.getUrl();
        mOriginalUrl = entry.getOriginalUrl();
        mTitle = entry.getTitle();
        mFavicon = entry.getFavicon();
    }

    /**
     * Return an identifier for this history item. If an item is a copy of
     * another item, the identifiers will be the same even if they are not the
     * same object.
     * @return The id for this item.
     * @deprecated This method is now obsolete.
     * @hide Since API level {@link android.os.Build.VERSION_CODES#JELLY_BEAN_MR1}
     */
    @Deprecated
    @Override
    public int getId() {
        return mId;
    }

    /**
     * Return the url of this history item. The url is the base url of this
     * history item. See getTargetUrl() for the url that is the actual target of
     * this history item.
     * @return The base url of this history item.
     * Note: The VM ensures 32-bit atomic read/write operations so we don't have
     * to synchronize this method.
     */
    @Override
    public String getUrl() {
        return mUrl;
    }

    /**
     * Return the original url of this history item. This was the requested
     * url, the final url may be different as there might have been
     * redirects while loading the site.
     * @return The original url of this history item.
     */
    @Override
    public String getOriginalUrl() {
        return mOriginalUrl;
    }

    /**
     * Return the document title of this history item.
     * @return The document title of this history item.
     * Note: The VM ensures 32-bit atomic read/write operations so we don't have
     * to synchronize this method.
     */
    @Override
    public String getTitle() {
        return mTitle;
    }

    /**
     * Return the favicon of this history item or null if no favicon was found.
     * @return A Bitmap containing the favicon for this history item or null.
     * Note: The VM ensures 32-bit atomic read/write operations so we don't have
     * to synchronize this method.
     */
    @Override
    public Bitmap getFavicon() {
        return mFavicon;
    }

    /**
     * Clone the history item for use by clients of WebView.
     */
    @Override
    public synchronized WebHistoryItem clone() {
        return new WebHistoryItemImpl(this);
    }
}

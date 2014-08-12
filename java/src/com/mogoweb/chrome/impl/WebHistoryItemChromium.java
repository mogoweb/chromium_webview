// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.mogoweb.chrome.impl;

import org.chromium.content.browser.NavigationEntry;

import android.graphics.Bitmap;

import com.mogoweb.chrome.WebHistoryItem;

/**
 * WebView Chromium implementation of WebHistoryItem. Simple immutable wrapper
 * around NavigationEntry
 */
public class WebHistoryItemChromium extends WebHistoryItem {
    private final String mUrl;
    private final String mOriginalUrl;
    private final String mTitle;
    private final Bitmap mFavicon;

    /* package */ WebHistoryItemChromium(NavigationEntry entry) {
        mUrl = entry.getUrl();
        mOriginalUrl = entry.getOriginalUrl();
        mTitle = entry.getTitle();
        mFavicon = entry.getFavicon();
    }

    /**
     * See {@link android.webkit.WebHistoryItem#getId}.
     */
    @Override
    public int getId() {
        // This method is deprecated in superclass. Returning constant -1 now.
        return -1;
    }

    /**
     * See {@link android.webkit.WebHistoryItem#getUrl}.
     */
    @Override
    public String getUrl() {
        return mUrl;
    }

    /**
     * See {@link android.webkit.WebHistoryItem#getOriginalUrl}.
     */
    @Override
    public String getOriginalUrl() {
        return mOriginalUrl;
    }

    /**
     * See {@link android.webkit.WebHistoryItem#getTitle}.
     */
    @Override
    public String getTitle() {
        return mTitle;
    }

    /**
     * See {@link android.webkit.WebHistoryItem#getFavicon}.
     */
    @Override
    public Bitmap getFavicon() {
        return mFavicon;
    }

    // Clone constructor.
    private WebHistoryItemChromium(
            String url, String originalUrl, String title, Bitmap favicon) {
        mUrl = url;
        mOriginalUrl = originalUrl;
        mTitle = title;
        mFavicon = favicon;
    }

    /**
     * See {@link android.webkit.WebHistoryItem#clone}.
     */
    @Override
    public synchronized WebHistoryItemChromium clone() {
        return new WebHistoryItemChromium(mUrl, mOriginalUrl, mTitle, mFavicon);
    }
}

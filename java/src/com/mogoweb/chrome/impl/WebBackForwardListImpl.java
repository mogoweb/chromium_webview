// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.impl;

import java.util.ArrayList;

import org.chromium.content.browser.NavigationEntry;
import org.chromium.content.browser.NavigationHistory;

import com.mogoweb.chrome.WebBackForwardList;
import com.mogoweb.chrome.WebHistoryItem;

public class WebBackForwardListImpl extends WebBackForwardList {

    // Current position in the list.
    private int mCurrentIndex;
    // ArrayList of WebHistoryItems for maintaining our copy.
    private ArrayList<WebHistoryItem> mArray;

    public WebBackForwardListImpl(NavigationHistory history) {
        int size = history.getEntryCount();
        mArray = new ArrayList<WebHistoryItem>(size);
        for (int i = 0; i < size; i++) {
            NavigationEntry entry = history.getEntryAtIndex(i);
            WebHistoryItemImpl item = new WebHistoryItemImpl(entry);
            mArray.add(item);
        }
        mCurrentIndex = history.getCurrentEntryIndex();
    }

    private WebBackForwardListImpl(WebBackForwardListImpl list) {
        int size = list.getSize();
        mArray = new ArrayList<WebHistoryItem>(size);
        for (int i = 0; i < size; i++) {
            WebHistoryItemImpl item = (WebHistoryItemImpl)list.getItemAtIndex(i);
            mArray.add(item.clone());
        }
        mCurrentIndex = list.getCurrentIndex();
    }

    /**
     * Return the current history item. This method returns null if the list is
     * empty.
     * @return The current history item.
     */
    @Override
    public synchronized WebHistoryItem getCurrentItem() {
        return getItemAtIndex(mCurrentIndex);
    }

    /**
     * Get the index of the current history item. This index can be used to
     * directly index into the array list.
     * @return The current index from 0...n or -1 if the list is empty.
     */
    @Override
    public synchronized int getCurrentIndex() {
        return mCurrentIndex;
    }

    /**
     * Get the history item at the given index. The index range is from 0...n
     * where 0 is the first item and n is the last item.
     * @param index The index to retrieve.
     */
    @Override
    public synchronized WebHistoryItem getItemAtIndex(int index) {
        return mArray.get(index);
    }

    /**
     * Get the total size of the back/forward list.
     * @return The size of the list.
     */
    @Override
    public synchronized int getSize() {
        return mArray.size();
    }

    /**
     * Clone the entire object to be used in the UI thread by clients of
     * WebView. This creates a copy that should never be modified by any of the
     * webkit package classes.
     */
    @Override
    public synchronized WebBackForwardList clone() {
        return new WebBackForwardListImpl(this);
    }
}

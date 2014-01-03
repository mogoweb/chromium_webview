// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.CalledByNative;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * This class allows Java code to get and clear the list of recently closed tabs.
 */
public class RecentlyClosedBridge {
    private int mNativeRecentlyClosedTabsBridge;

    /**
     * Callback interface for getting notified when the list of recently closed tabs is updated.
     */
    public interface RecentlyClosedCallback {
        /**
         * This method will be called every time the list of recently closed tabs is updated.
         *
         * It's a good place to call {@link RecentlyClosedBridge#getRecentlyClosedTabs()} to get the
         * updated list of tabs.
         */
        @CalledByNative("RecentlyClosedCallback")
        void onUpdated();
    }

    /**
     * Represents a recently closed tab.
     */
    public static class RecentlyClosedTab {
        public final int id;
        public final String title;
        public final String url;

        private RecentlyClosedTab(int id, String title, String url) {
            this.id = id;
            this.title = title;
            this.url = url;
        }
    }

    @CalledByNative
    private static void pushTab(
            List<RecentlyClosedTab> tabs, int id, String title, String url) {
        RecentlyClosedTab tab = new RecentlyClosedTab(id, title, url);
        tabs.add(tab);
    }

    /**
     * Initializes this class with the given profile.
     * @param profile The Profile whose recently closed tabs will be queried.
     */
    public RecentlyClosedBridge(Profile profile) {
        mNativeRecentlyClosedTabsBridge = nativeInit(profile);
    }

    @Override
    protected void finalize() {
        // Ensure that destroy() was called.
        assert mNativeRecentlyClosedTabsBridge == 0;
    }

    /**
     * Cleans up the C++ side of this class. This instance must not be used after calling destroy().
     */
    public void destroy() {
        assert mNativeRecentlyClosedTabsBridge != 0;
        nativeDestroy(mNativeRecentlyClosedTabsBridge);
    }

    /**
     * Sets the callback to be called whenever the list of recently closed tabs changes.
     * @param callback The RecentlyClosedCallback to be notified, or null.
     */
    public void setRecentlyClosedCallback(RecentlyClosedCallback callback) {
        nativeSetRecentlyClosedCallback(mNativeRecentlyClosedTabsBridge, callback);
    }

    /**
     * @return The list of recently closed tabs.
     */
    public List<RecentlyClosedTab> getRecentlyClosedTabs() {
        List<RecentlyClosedTab> tabs = new ArrayList<RecentlyClosedTab>();
        boolean received = nativeGetRecentlyClosedTabs(mNativeRecentlyClosedTabsBridge, tabs);
        return received ? tabs : null;
    }

    /**
     * Opens a recently closed tab in a new tab.
     * Note: this will change to open in the current tab once http://crbug.com/257102 is fixed.
     *
     * @param tab The current TabBase.
     * @param recentTab The RecentlyClosedTab to open.
     * @return Whether the tab was successfully opened.
     */
    public boolean openRecentlyClosedTab(TabBase tab, RecentlyClosedTab recentTab) {
        return nativeOpenRecentlyClosedTab(mNativeRecentlyClosedTabsBridge, tab, recentTab.id);
    }

    /**
     * Clears all recently closed tabs.
     */
    public void clearRecentlyClosedTabs() {
        nativeClearRecentlyClosedTabs(mNativeRecentlyClosedTabsBridge);
    }

    private native int nativeInit(Profile profile);
    private native void nativeDestroy(int nativeRecentlyClosedTabsBridge);
    private native void nativeSetRecentlyClosedCallback(
            int nativeRecentlyClosedTabsBridge, RecentlyClosedCallback callback);
    private native boolean nativeGetRecentlyClosedTabs(
            int nativeRecentlyClosedTabsBridge, List<RecentlyClosedTab> tabs);
    private native boolean nativeOpenRecentlyClosedTab(
            int nativeRecentlyClosedTabsBridge, TabBase tab, int recentTabId);
    private native void nativeClearRecentlyClosedTabs(int nativeRecentlyClosedTabsBridge);
}

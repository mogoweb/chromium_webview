// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.content.browser.PageInfo;

/**
 * An interface for pages that will be shown in a tab using Android views instead of html.
 */
public interface NativePage extends PageInfo {
    /**
     * @return The URL of the page.
     */
    String getUrl();

    /**
     * @return The hostname for this page, e.g. "newtab" or "bookmarks".
     */
    public String getHost();

    /**
     * Called after a page has been removed from the view hierarchy and will no longer be used.
     */
    public void destroy();

    /**
     * Updates the native page based on the given url.
     */
    public void updateForUrl(String url);
}

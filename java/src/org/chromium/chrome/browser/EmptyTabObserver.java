// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

/**
 * An implementation of the {@link TabObserver} which has empty implementations of all methods.
 */
public class EmptyTabObserver implements TabObserver {

    @Override
    public void onDestroyed(TabBase tab) { }

    @Override
    public void onContentChanged(TabBase tab) { }

    @Override
    public void onFaviconUpdated(TabBase tab) { }

    @Override
    public void onLoadProgressChanged(TabBase tab, int progress) { }

    @Override
    public void onUpdateUrl(TabBase tab, String url) { }

    @Override
    public void onToggleFullscreenMode(TabBase tab, boolean enable) { }

    @Override
    public void onDidFailLoad(TabBase tab, boolean isProvisionalLoad, boolean isMainFrame,
            int errorCode, String description, String failingUrl) { }
}

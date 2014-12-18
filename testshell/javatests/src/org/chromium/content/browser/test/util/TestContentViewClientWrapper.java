// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import android.content.Context;
import android.view.ActionMode;
import android.view.KeyEvent;

import org.chromium.content.browser.ContentVideoViewClient;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.SelectActionModeCallback.ActionHandler;

/**
 * Simplistic {@link TestContentViewClient} for browser tests. Wraps around existing client so that
 * specific methods can be overridden if needed. This class MUST override ALL METHODS OF the
 * ContentViewClient and pass them to the wrapped client.
 */
public class TestContentViewClientWrapper extends TestContentViewClient {

    private ContentViewClient mWrappedClient;

    public TestContentViewClientWrapper(ContentViewClient wrappedClient) {
        assert wrappedClient != null;
        mWrappedClient = wrappedClient;
    }

    @Override
    public void onUpdateTitle(String title) {
        super.onUpdateTitle(title);
        mWrappedClient.onUpdateTitle(title);
    }

    @Override
    public boolean shouldOverrideKeyEvent(KeyEvent event) {
        return mWrappedClient.shouldOverrideKeyEvent(event);
    }

    @Override
    public void onImeEvent() {
        super.onImeEvent();
        mWrappedClient.onImeEvent();
    }

    @Override
    public ActionMode.Callback getSelectActionModeCallback(Context context,
            ActionHandler actionHandler, boolean incognito) {
        return mWrappedClient.getSelectActionModeCallback(context, actionHandler, incognito);
    }

    @Override
    public void onContextualActionBarShown() {
        super.onContextualActionBarShown();
        mWrappedClient.onContextualActionBarShown();
    }

    @Override
    public void onContextualActionBarHidden() {
        super.onContextualActionBarHidden();
        mWrappedClient.onContextualActionBarHidden();
    }

    @Override
    public void onStartContentIntent(Context context, String contentUrl) {
        super.onStartContentIntent(context, contentUrl);
        mWrappedClient.onStartContentIntent(context, contentUrl);
    }

    @Override
    public boolean doesPerformWebSearch() {
        return mWrappedClient.doesPerformWebSearch();
    }

    @Override
    public ContentVideoViewClient getContentVideoViewClient() {
        return mWrappedClient.getContentVideoViewClient();
    }

    @Override
    public void onBackgroundColorChanged(int color) {
        super.onBackgroundColorChanged(color);
        mWrappedClient.onBackgroundColorChanged(color);
    }

    @Override
    public void onOffsetsForFullscreenChanged(float topControlsOffsetYPix, float contentOffsetYPix,
            float overdrawBottomHeightPix) {
        super.onOffsetsForFullscreenChanged(topControlsOffsetYPix, contentOffsetYPix,
                overdrawBottomHeightPix);
        mWrappedClient.onOffsetsForFullscreenChanged(topControlsOffsetYPix, contentOffsetYPix,
                overdrawBottomHeightPix);
    }

    @Override
    public void onImeStateChangeRequested(boolean requestShow) {
        super.onImeStateChangeRequested(requestShow);
        mWrappedClient.onImeStateChangeRequested(requestShow);
    }

    @Override
    public void performWebSearch(String searchQuery) {
        super.performWebSearch(searchQuery);
        mWrappedClient.performWebSearch(searchQuery);
    }

    @Override
    public void onSelectionChanged(String selection) {
        super.onSelectionChanged(selection);
        mWrappedClient.onSelectionChanged(selection);
    }

    @Override
    public boolean shouldBlockMediaRequest(String url) {
        return mWrappedClient.shouldBlockMediaRequest(url);
    }

}

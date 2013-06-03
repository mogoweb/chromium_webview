// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import android.content.Context;
import android.view.ActionMode;
import android.view.KeyEvent;

import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.SelectActionModeCallback;
import org.chromium.content.browser.SelectActionModeCallback.ActionHandler;
import org.chromium.content.browser.test.util.TestContentViewClient;

/**
 * Simplistic {@link TestContentViewClient} for browser tests.
 * Wraps around existing client so that specific methods can be overridden if needed.
 * This class MUST override ALL METHODS OF the ContentViewClient and pass them
 * to the wrapped client.
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
    public void onScaleChanged(float oldScale, float newScale) {
        super.onScaleChanged(oldScale, newScale);
        mWrappedClient.onScaleChanged(oldScale, newScale);
    }

    @Override
    public void onTabCrash() {
        super.onTabCrash();
        mWrappedClient.onTabCrash();
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
    public boolean shouldOverrideScroll(float deltaX, float deltaY, float currX, float currY) {
        return mWrappedClient.shouldOverrideScroll(deltaX, deltaY, currX, currX);
    }

    @Override
    public ActionMode.Callback getSelectActionModeCallback(
            Context context, ActionHandler actionHandler, boolean incognito) {
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
}

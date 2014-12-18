// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import android.content.Context;

import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.test.util.TestCallbackHelperContainer.OnStartContentIntentHelper;

/**
 * The default ContentViewClient used by ContentView tests.
 * <p>
 * Tests that need to supply their own ContentViewClient should do that
 * by extending this one.
 */
public class TestContentViewClient extends ContentViewClient {

    private final OnStartContentIntentHelper mOnStartContentIntentHelper;

    public TestContentViewClient() {
        mOnStartContentIntentHelper = new OnStartContentIntentHelper();
    }

    public OnStartContentIntentHelper getOnStartContentIntentHelper() {
        return mOnStartContentIntentHelper;
    }

    /**
     * ATTENTION!: When overriding the following method, be sure to call
     * the corresponding method in the super class. Otherwise
     * {@link CallbackHelper#waitForCallback()} methods will
     * stop working!
     */

    @Override
    public void onStartContentIntent(Context context, String contentUrl) {
        mOnStartContentIntentHelper.notifyCalled(contentUrl);
    }
}

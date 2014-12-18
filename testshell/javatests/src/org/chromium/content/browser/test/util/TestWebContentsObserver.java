// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.WebContentsObserverAndroid;
import org.chromium.content.browser.test.util.TestCallbackHelperContainer.OnPageFinishedHelper;
import org.chromium.content.browser.test.util.TestCallbackHelperContainer.OnPageStartedHelper;
import org.chromium.content.browser.test.util.TestCallbackHelperContainer.OnReceivedErrorHelper;
import org.chromium.content_public.browser.WebContents;

/**
 * The default WebContentsObserverAndroid used by ContentView tests. The below callbacks can be
 * accessed by using {@link TestCallbackHelperContainer} or extending this class.
 */
public class TestWebContentsObserver extends WebContentsObserverAndroid {

    private final OnPageStartedHelper mOnPageStartedHelper;
    private final OnPageFinishedHelper mOnPageFinishedHelper;
    private final OnReceivedErrorHelper mOnReceivedErrorHelper;

    // TODO(yfriedman): Switch everyone to use the WebContents constructor.
    public TestWebContentsObserver(ContentViewCore contentViewCore) {
        this(contentViewCore.getWebContents());
    }

    public TestWebContentsObserver(WebContents webContents) {
        super(webContents);
        mOnPageStartedHelper = new OnPageStartedHelper();
        mOnPageFinishedHelper = new OnPageFinishedHelper();
        mOnReceivedErrorHelper = new OnReceivedErrorHelper();
    }

    public OnPageStartedHelper getOnPageStartedHelper() {
        return mOnPageStartedHelper;
    }

    public OnPageFinishedHelper getOnPageFinishedHelper() {
        return mOnPageFinishedHelper;
    }

    public OnReceivedErrorHelper getOnReceivedErrorHelper() {
        return mOnReceivedErrorHelper;
    }

    /**
     * ATTENTION!: When overriding the following methods, be sure to call
     * the corresponding methods in the super class. Otherwise
     * {@link CallbackHelper#waitForCallback()} methods will
     * stop working!
     */
    @Override
    public void didStartLoading(String url) {
        super.didStartLoading(url);
        mOnPageStartedHelper.notifyCalled(url);
    }

    @Override
    public void didStopLoading(String url) {
        super.didStopLoading(url);
        mOnPageFinishedHelper.notifyCalled(url);
    }

    @Override
    public void didFailLoad(boolean isProvisionalLoad, boolean isMainFrame,
            int errorCode, String description, String failingUrl) {
        super.didFailLoad(isProvisionalLoad, isMainFrame, errorCode, description, failingUrl);
        mOnReceivedErrorHelper.notifyCalled(errorCode, description, failingUrl);
    }
}

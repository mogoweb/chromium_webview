// Copyright (c) 2013 mogoweb. All rights reserved.
// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.shell.test;

import org.chromium.base.test.util.Feature;
import org.chromium.content.browser.test.util.TestCallbackHelperContainer;

import android.test.suitebuilder.annotation.MediumTest;

import com.mogoweb.chrome.WebView;

/**
 * Tests for the WebViewClient.onPageFinished() method.
 */
public class ClientOnPageFinishedTest extends WebViewShellTestBase {

    private TestWebViewClient mWebViewClient;
    private WebView mWebView;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mWebViewClient = new TestWebViewClient();
        mWebView = getActivity().getWebView();
        mWebView.setWebViewClient(mWebViewClient);
    }

    @MediumTest
    @Feature({"AndroidWebView"})
    public void testOnPageFinishedPassesCorrectUrl() throws Throwable {
        TestCallbackHelperContainer.OnPageFinishedHelper onPageFinishedHelper =
                mWebViewClient.getOnPageFinishedHelper();

        String html = "<html><body>Simple page.</body></html>";
        int currentCallCount = onPageFinishedHelper.getCallCount();
        loadDataAsync(mWebView, html, "text/html", "utf-8");

        onPageFinishedHelper.waitForCallback(currentCallCount);
        assertEquals("data:text/html," + html, onPageFinishedHelper.getUrl());
    }
}

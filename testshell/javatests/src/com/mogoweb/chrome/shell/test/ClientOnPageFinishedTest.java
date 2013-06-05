// Copyright (c) 2013 mogoweb. All rights reserved.
// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.shell.test;

import java.util.concurrent.TimeUnit;

import org.chromium.base.test.util.Feature;
import org.chromium.content.browser.test.util.TestCallbackHelperContainer;

import android.test.suitebuilder.annotation.MediumTest;

/**
 * Tests for the WebViewClient.onPageFinished() method.
 */
public class ClientOnPageFinishedTest extends WebViewShellTestBase {

    private TestWebViewClient mWebViewClient;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mWebViewClient = new TestWebViewClient();
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

    @MediumTest
    @Feature({"AndroidWebView"})
    public void testOnPageFinishedCalledAfterError() throws Throwable {
        TestCallbackHelperContainer.OnReceivedErrorHelper onReceivedErrorHelper =
                mWebViewClient.getOnReceivedErrorHelper();
        TestCallbackHelperContainer.OnPageFinishedHelper onPageFinishedHelper =
                mWebViewClient.getOnPageFinishedHelper();

        assertEquals(0, onReceivedErrorHelper.getCallCount());

        String url = "http://localhost:7/non_existent";
        int onReceivedErrorCallCount = onReceivedErrorHelper.getCallCount();
        int onPageFinishedCallCount = onPageFinishedHelper.getCallCount();
        loadUrlAsync(mWebView, url);

        onReceivedErrorHelper.waitForCallback(onReceivedErrorCallCount,
                                              1 /* numberOfCallsToWaitFor */,
                                              WAIT_TIMEOUT_SECONDS,
                                              TimeUnit.SECONDS);
        onPageFinishedHelper.waitForCallback(onPageFinishedCallCount,
                                              1 /* numberOfCallsToWaitFor */,
                                              WAIT_TIMEOUT_SECONDS,
                                              TimeUnit.SECONDS);
        assertEquals(1, onReceivedErrorHelper.getCallCount());
    }

    @MediumTest
    @Feature({"AndroidWebView"})
    public void testOnPageFinishedNotCalledForValidSubresources() throws Throwable {
        TestCallbackHelperContainer.OnPageFinishedHelper onPageFinishedHelper =
                mWebViewClient.getOnPageFinishedHelper();

        TestWebServer webServer = null;
        try {
            webServer = new TestWebServer(false);

            final String testHtml = "<html><head>Header</head><body>Body</body></html>";
            final String testPath = "/test.html";
            final String syncPath = "/sync.html";

            webServer.setResponse(testPath, testHtml, null);
            final String syncUrl = webServer.setResponse(syncPath, testHtml, null);

            assertEquals(0, onPageFinishedHelper.getCallCount());
            final int pageWithSubresourcesCallCount = onPageFinishedHelper.getCallCount();
            loadDataAsync(mWebView,
                          "<html><iframe src=\"" + testPath + "\" /></html>",
                          "text/html",
                          "utf-8");

            onPageFinishedHelper.waitForCallback(pageWithSubresourcesCallCount);

            // Rather than wait a fixed time to see that an onPageFinished callback isn't issued
            // we load another valid page. Since callbacks arrive sequentially if the next callback
            // we get is for the synchronizationUrl we know that the previous load did not schedule
            // a callback for the iframe.
            final int synchronizationPageCallCount = onPageFinishedHelper.getCallCount();
            loadUrlAsync(mWebView, syncUrl);

            onPageFinishedHelper.waitForCallback(synchronizationPageCallCount);
            assertEquals(syncUrl, onPageFinishedHelper.getUrl());
            assertEquals(2, onPageFinishedHelper.getCallCount());

        } finally {
            if (webServer != null) webServer.shutdown();
        }
    }
}

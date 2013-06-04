// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.shell.test;

import org.chromium.content.browser.test.util.TestCallbackHelperContainer.OnPageFinishedHelper;
import org.chromium.content.browser.test.util.TestCallbackHelperContainer.OnPageStartedHelper;

import android.graphics.Bitmap;

import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.WebViewClient;

public class TestWebViewClient extends WebViewClient {

    private final OnPageStartedHelper mOnPageStartedHelper;
    private final OnPageFinishedHelper mOnPageFinishedHelper;

    public TestWebViewClient() {
        mOnPageStartedHelper = new OnPageStartedHelper();
        mOnPageFinishedHelper = new OnPageFinishedHelper();
    }

    public OnPageStartedHelper getOnPageStartedHelper() {
        return mOnPageStartedHelper;
    }

    public OnPageFinishedHelper getOnPageFinishedHelper() {
        return mOnPageFinishedHelper;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        mOnPageStartedHelper.notifyCalled(url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        mOnPageFinishedHelper.notifyCalled(url);
    }
}

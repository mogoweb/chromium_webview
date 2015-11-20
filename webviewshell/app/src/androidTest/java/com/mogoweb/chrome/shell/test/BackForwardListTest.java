// Copyright (c) 2013 mogoweb. All rights reserved.
// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.shell.test;

import org.chromium.base.test.util.Feature;

import android.test.suitebuilder.annotation.SmallTest;

import com.mogoweb.chrome.WebBackForwardList;
import com.mogoweb.chrome.WebHistoryItem;

/**
 * Tests for the WebBackForwardList class.
 */
public class BackForwardListTest extends WebViewShellTestBase {

    private TestWebViewClient mWebViewClient = new TestWebViewClient();

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mWebView.setWebViewClient(mWebViewClient);
    }

    @SmallTest
    @Feature({"AndroidWebView"})
    public void testCopyBackForwardList() throws Throwable {
        loadUrlSync(mWebView,
                mWebViewClient.getOnPageFinishedHelper(), "http://mogoweb.net");

        WebBackForwardList list = mWebView.copyBackForwardList();
        assertEquals(1, list.getSize());
        assertEquals(0, list.getCurrentIndex());
        WebHistoryItem item = list.getCurrentItem();
        assertNotNull(item);
        assertEquals(item.getUrl(), "http://mogoweb.net/");
        assertNull(item.getFavicon());
    }
}

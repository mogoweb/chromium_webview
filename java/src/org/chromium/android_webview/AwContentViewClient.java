// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;

import org.chromium.content.browser.ContentVideoView;
import org.chromium.content.browser.ContentVideoViewClient;
import org.chromium.content.browser.ContentViewClient;

/**
 * ContentViewClient implementation for WebView
 */
public class AwContentViewClient extends ContentViewClient {

    private class AwContentVideoViewClient implements ContentVideoViewClient {
        @Override
        public void onShowCustomView(View view) {
            WebChromeClient.CustomViewCallback cb = new WebChromeClient.CustomViewCallback() {
                @Override
                public void onCustomViewHidden() {
                    ContentVideoView contentVideoView = ContentVideoView.getContentVideoView();
                    if (contentVideoView != null)
                        contentVideoView.exitFullscreen(false);
                }
            };
            mAwContentsClient.onShowCustomView(view, cb);
        }

        @Override
        public void onDestroyContentVideoView() {
            mAwContentsClient.onHideCustomView();
        }

        @Override
        public View getVideoLoadingProgressView() {
            return mAwContentsClient.getVideoLoadingProgressView();
        }
    }

    private AwContentsClient mAwContentsClient;
    private AwSettings mAwSettings;

    public AwContentViewClient(AwContentsClient awContentsClient, AwSettings awSettings) {
        mAwContentsClient = awContentsClient;
        mAwSettings = awSettings;
    }

    @Override
    public void onBackgroundColorChanged(int color) {
        mAwContentsClient.onBackgroundColorChanged(color);
    }

    @Override
    public void onStartContentIntent(Context context, String contentUrl) {
        //  Callback when detecting a click on a content link.
        mAwContentsClient.shouldOverrideUrlLoading(contentUrl);
    }

    @Override
    public void onUpdateTitle(String title) {
        mAwContentsClient.onReceivedTitle(title);
    }

    @Override
    public boolean shouldOverrideKeyEvent(KeyEvent event) {
        return mAwContentsClient.shouldOverrideKeyEvent(event);
    }

    @Override
    public final ContentVideoViewClient getContentVideoViewClient() {
        return new AwContentVideoViewClient();
    }

    @Override
    public boolean shouldBlockMediaRequest(String url) {
        return mAwSettings != null ?
                mAwSettings.getBlockNetworkLoads() && URLUtil.isNetworkUrl(url) : true;
    }
}

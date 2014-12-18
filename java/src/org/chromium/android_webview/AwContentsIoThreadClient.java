// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.util.ArrayMap;

import org.chromium.android_webview.AwContentsClient;
import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Delegate for handling callbacks. All methods are called on the IO thread.
 *
 * You should create a separate instance for every WebContents that requires the
 * provided functionality.
 */
@JNINamespace("android_webview")
public abstract class AwContentsIoThreadClient {
    @CalledByNative
    public abstract int getCacheMode();

    @CalledByNative
    public abstract boolean shouldBlockContentUrls();

    @CalledByNative
    public abstract boolean shouldBlockFileUrls();

    @CalledByNative
    public abstract boolean shouldBlockNetworkLoads();

    @CalledByNative
    public abstract boolean shouldAcceptThirdPartyCookies();

    @CalledByNative
    public abstract void onDownloadStart(String url, String userAgent,
        String contentDisposition, String mimeType, long contentLength);

    @CalledByNative
    public abstract void newLoginRequest(String realm, String account, String args);

    public abstract AwWebResourceResponse shouldInterceptRequest(
            AwContentsClient.ShouldInterceptRequestParams params);

    // Protected methods ---------------------------------------------------------------------------

    @CalledByNative
    protected AwWebResourceResponse shouldInterceptRequest(String url, boolean isMainFrame,
            boolean hasUserGesture, String method, String[] requestHeaderNames,
            String[] requestHeaderValues) {
        AwContentsClient.ShouldInterceptRequestParams params =
            new AwContentsClient.ShouldInterceptRequestParams();
        params.url = url;
        params.isMainFrame = isMainFrame;
        params.hasUserGesture = hasUserGesture;
        params.method = method;
        params.requestHeaders = new ArrayMap<String, String>(requestHeaderNames.length);
        for (int i = 0; i < requestHeaderNames.length; ++i) {
            params.requestHeaders.put(requestHeaderNames[i], requestHeaderValues[i]);
        }
        return shouldInterceptRequest(params);
    }
}

// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.impl;

import org.chromium.android_webview.AwContentsClient;
import org.chromium.android_webview.AwHttpAuthHandler;
import org.chromium.android_webview.InterceptedRequestData;
import org.chromium.android_webview.JsPromptResultReceiver;
import org.chromium.android_webview.JsResultReceiver;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Picture;
import android.net.http.SslError;
import android.os.Build;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions.Callback;
import android.webkit.ValueCallback;
import android.webkit.WebResourceResponse;

import com.mogoweb.chrome.DownloadListener;
import com.mogoweb.chrome.JsResult;
import com.mogoweb.chrome.WebChromeClient;
import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.WebView.FindListener;
import com.mogoweb.chrome.WebViewClient;

/** Glue that passes calls from the Chromium view to a WebChromeClient. */
public class ChromeAwContentsClientProxy extends AwContentsClient {
    // Inspired from
    //     chromium/src/android_webview/test/shell/src/org/chromium/android_webview/test/NullContentsClient:w

    //     chromium/src/android_webview/javatests/src/org/chromium/android_webview/tests/*ContentsClient
    //     http://developer.android.com/reference/android/webkit/WebChromeClient.html

    /** The view whose clients are proxied by this instance. */
    private final WebView mWebView;

    /** ChromeView equivalent of WebViewClient. */
    private WebViewClient mWebViewClient;

    /** ChromeView equivalent of WebChromeClient. */
    private WebChromeClient mWebChromeClient;

    /** Receives download notifications. */
    private DownloadListener mDownloadListener;

    /** Receives find results notifications. */
    private FindListener mFindListener;


    /** Resets the WebViewClient proxy target. */
    public void setWebViewClient(WebViewClient webViewClient) {
        mWebViewClient = webViewClient;
    }

    /** Resets the WebChromeClient proxy target. */
    public void setWebChromeClient(WebChromeClient webChromeClient) {
        mWebChromeClient = webChromeClient;
    }

    /** Resets the DownloadListener proxy target. */
    public void setDownloadListener(DownloadListener downloadListener) {
        mDownloadListener = downloadListener;
    }

    /** Resets the FindListener proxy target. */
    public void setFindListener(FindListener findListener) {
        mFindListener = findListener;
    }

    /**
     * Creates a new proxy.
     *
     * @param chromeView The view whose clients are proxied by this instance.
     */
    public ChromeAwContentsClientProxy(WebView webView) {
        mWebView = webView;
        mWebViewClient = null;
        mWebChromeClient = null;
    }

    @Override
    public void onHideCustomView() {
        // TODO Auto-generated method stub
    }

    @Override
    public Bitmap getDefaultVideoPoster() {
        // TODO Auto-generated method stub
        return null;
    }

    //// WebChromeClient inexact proxies.
    @Override
    protected void handleJsAlert(String url, String message,
            JsResultReceiver receiver) {
        if (mWebChromeClient != null) {
            JsResult result = new JsResult(
                    new ChromeJsResultReceiverProxy(receiver));
            if (mWebChromeClient.onJsAlert(mWebView, url, message, result)) {
                return;  // Alert will be handled by the client.
            }
        }
        receiver.cancel();  // Default alert handling.
    }

    @Override
    protected void handleJsBeforeUnload(String url, String message,
            JsResultReceiver receiver) {
        if (mWebChromeClient != null) {
            JsResult result = new JsResult(
                    new ChromeJsResultReceiverProxy(receiver));
            if (mWebChromeClient.onJsBeforeUnload(mWebView, url, message,
                    result)) {
                return;  // Alert will be handled by the client.
            }
        }
        receiver.cancel();  // Default alert handling.
    }

    @Override
    protected void handleJsConfirm(String url, String message,
            JsResultReceiver receiver) {
        if (mWebChromeClient != null) {
            JsResult result = new JsResult(
                    new ChromeJsResultReceiverProxy(receiver));
            if (mWebChromeClient.onJsAlert(mWebView, url, message, result)) {
                return;  // Alert will be handled by the client.
            }
        }
        receiver.cancel();  // Default alert handling.
    }

    @Override
    protected void handleJsPrompt(String url, String message,
            String defaultValue, JsPromptResultReceiver receiver) {
        if (mWebChromeClient != null) {
            JsResult result = new JsResult(
                    new ChromeJsPromptResultProxy(receiver));
            if (mWebChromeClient.onJsAlert(mWebView, url, message, result)) {
                return;  // Alert will be handled by the client.
            }
        }
        receiver.cancel();  // Default alert handling.
    }

    //// WebChromeClient proxy methods.
    @Override
    public void onProgressChanged(int progress) {
        if (mWebChromeClient != null)
            mWebChromeClient.onProgressChanged(mWebView, progress);
    }

    @Override
    public void onReceivedIcon(Bitmap bitmap) {
        if (mWebChromeClient != null)
            mWebChromeClient.onReceivedIcon(mWebView, bitmap);
    }

    @Override
    public void onReceivedTouchIconUrl(String url, boolean precomposed) {
        if (mWebChromeClient != null)
            mWebChromeClient.onReceivedTouchIconUrl(mWebView, url, precomposed);
    }

    @Override
    public void onShowCustomView(View view, int requestedOrientation,
            android.webkit.WebChromeClient.CustomViewCallback callback) {
//        if (mWebChromeClient != null) {
//            mWebChromeClient.onShowCustomView(mWebView, requestedOrientation,
//                    callback);
//        }
    }

    @Override
    protected boolean onCreateWindow(boolean isDialog, boolean isUserGesture) {
        if (mWebChromeClient != null) {
            // TODO(pwnall): figure out what to do here
            Message resultMsg = new Message();
            resultMsg.setTarget(null);
            resultMsg.obj = null; // WebView.WebViewTransport
            return mWebChromeClient.onCreateWindow(mWebView, isDialog,
                    isUserGesture, resultMsg);
        } else {
            return false;
        }
    }

    @Override
    protected void onRequestFocus() {
        if (mWebChromeClient != null)
            mWebChromeClient.onRequestFocus(mWebView);
    }

    @Override
    protected void onCloseWindow() {
        if (mWebChromeClient != null)
            mWebChromeClient.onCloseWindow(mWebView);
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin,
            Callback callback) {
//        if (mWebChromeClient != null) {
//            mWebChromeClient.onGeolocationPermissionsShowPrompt(origin, callback);
//        } else {
//            callback.invoke(origin, false, false);
//        }
    }

    @Override
    public void onGeolocationPermissionsHidePrompt() {
        if (mWebChromeClient != null)
            mWebChromeClient.onGeolocationPermissionsHidePrompt();
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        if (mWebChromeClient != null) {
            return mWebChromeClient.onConsoleMessage(consoleMessage);
        } else {
            return false;
        }
    }

    @Override
    protected View getVideoLoadingProgressView() {
        if (mWebChromeClient != null) {
            return mWebChromeClient.getVideoLoadingProgressView();
        } else {
            return null;
        }
    }

    @Override
    public void getVisitedHistory(ValueCallback<String[]> callback) {
        if (mWebChromeClient != null) {
            mWebChromeClient.getVisitedHistory(callback);
        } else {
            callback.onReceiveValue(new String[] {});
        }
    }

    @Override
    public void onReceivedTitle(String title) {
        if (mWebChromeClient != null) {
            mWebChromeClient.onReceivedTitle(mWebView, title);
        }
    }

    //// WebViewClient proxy methods.
    @Override
    public void onPageStarted(String url) {
        if (mWebViewClient != null)
            mWebViewClient.onPageStarted(mWebView, url, null);
    }

    @Override
    public void onPageFinished(String url) {
        if (mWebViewClient != null)
            mWebViewClient.onPageFinished(mWebView, url);
    }

    @Override
    public void onLoadResource(String url) {
        if (mWebViewClient != null)
            mWebViewClient.onLoadResource(mWebView, url);
    }

    @Override
    public InterceptedRequestData shouldInterceptRequest(String url) {
        if (mWebViewClient != null) {
            WebResourceResponse response =
                    mWebViewClient.shouldInterceptRequest(mWebView, url);
            if (response != null) {
                return new InterceptedRequestData(response.getMimeType(),
                        response.getEncoding(), response.getData());
            }
        }
        return null;
    }

    @Override
    public void onReceivedError(int errorCode, String description,
            String failingUrl) {
        if (mWebViewClient != null) {
            mWebViewClient.onReceivedError(mWebView, errorCode, description, failingUrl);
        }
    }

    @Override
    public void onFormResubmission(Message dontResend, Message resend) {
        if (mWebViewClient != null) {
            mWebViewClient.onFormResubmission(mWebView, dontResend, resend);
        } else {
            dontResend.sendToTarget();
        }
    }

    @Override
    public void doUpdateVisitedHistory(String url, boolean isReload) {
        if (mWebViewClient != null)
            mWebViewClient.doUpdateVisitedHistory(mWebView, url, isReload);
    }

    @Override
    public void onReceivedSslError(ValueCallback<Boolean> callback,
            SslError error) {
//        if (mWebViewClient != null) {
//            ChromeSslErrorHandlerProxy handler =
//                    new ChromeSslErrorHandlerProxy(callback);
//            mWebViewClient.onReceivedSslError(mWebView, handler, error);
//        } else {
//            callback.onReceiveValue(false);
//        }
    }

    @Override
    public void onReceivedHttpAuthRequest(AwHttpAuthHandler handler,
            String host, String realm) {
//        if (mWebViewClient != null) {
//            ChromeHttpAuthHandlerProxy httpAuthHandler =
//                    new ChromeHttpAuthHandlerProxy(handler);
//            mWebViewClient.onReceivedHttpAuthRequest(mWebView, httpAuthHandler,
//                    host, realm);
//        } else {
//            handler.cancel();
//        }
    }

    @Override
    public void onUnhandledKeyEvent(KeyEvent event) {
        if (mWebViewClient != null)
            mWebViewClient.onUnhandledKeyEvent(mWebView, event);
    }

    @Override
    public void onScaleChangedScaled(float oldScale, float newScale) {
        if (mWebViewClient != null)
            mWebViewClient.onScaleChanged(mWebView, oldScale, newScale);
    }

    @Override
    public void onReceivedLoginRequest(String realm, String account,
            String args) {
        if (mWebViewClient != null) {
            mWebViewClient.onReceivedLoginRequest(mWebView, realm, account,
                    args);
        }
    }

    @Override
    public boolean shouldOverrideKeyEvent(KeyEvent event) {
        if (mWebViewClient != null) {
            return mWebViewClient.shouldOverrideKeyEvent(mWebView, event);
        } else {
            return false;
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(String url) {
        if (mWebViewClient != null) {
            return mWebViewClient.shouldOverrideUrlLoading(mWebView, url);
        } else {
            return false;
        }
    }

    // DownloadListener proxy methods.
    @Override
    public void onDownloadStart(String url, String userAgent,
            String contentDisposition, String mimeType, long contentLength) {
        if (mDownloadListener != null) {
            mDownloadListener.onDownloadStart(url, userAgent, contentDisposition,
                    mimeType, contentLength);
        }
    }

    // FindListener proxy methods.
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onFindResultReceived(int activeMatchOrdinal,
            int numberOfMatches, boolean isDoneCounting) {
        if (mFindListener != null) {
            mFindListener.onFindResultReceived(activeMatchOrdinal, numberOfMatches,
                    isDoneCounting);
        }
    }

    // PictureListener is deprecated, so we don't proxy it.
    @Override
    public void onNewPicture(Picture picture) {
        return;
    }
}

// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogoweb.chrome.impl;

import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.chromium.android_webview.AwContentsClient;
import org.chromium.android_webview.AwHttpAuthHandler;
import org.chromium.android_webview.AwWebResourceResponse;
import org.chromium.android_webview.JsPromptResultReceiver;
import org.chromium.android_webview.JsResultReceiver;
import org.chromium.base.TraceEvent;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentViewClient;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Message;
import android.provider.Browser;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.CustomViewCallback;
import android.webkit.WebResourceResponse;

import com.mogoweb.chrome.DownloadListener;
import com.mogoweb.chrome.JsPromptResult;
import com.mogoweb.chrome.JsResult;
import com.mogoweb.chrome.SslErrorHandler;
import com.mogoweb.chrome.WebChromeClient;
import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.WebViewClient;

/**
 * An adapter class that forwards the callbacks from {@link ContentViewClient}
 * to the appropriate {@link WebViewClient} or {@link WebChromeClient}.
 *
 * An instance of this class is associated with one {@link WebViewChromium}
 * instance. A WebViewChromium is a WebView implementation provider (that is
 * android.webkit.WebView delegates all functionality to it) and has exactly
 * one corresponding {@link ContentView} instance.
 *
 * A {@link ContentViewClient} may be shared between multiple {@link ContentView}s,
 * and hence multiple WebViews. Many WebViewClient methods pass the source
 * WebView as an argument. This means that we either need to pass the
 * corresponding ContentView to the corresponding ContentViewClient methods,
 * or use an instance of ContentViewClientAdapter per WebViewChromium, to
 * allow the source WebView to be injected by ContentViewClientAdapter. We
 * choose the latter, because it makes for a cleaner design.
 */
public class WebViewContentsClientAdapter extends AwContentsClient {
    // TAG is chosen for consistency with classic webview tracing.
    private static final String TAG = "WebViewCallback";
    // Enables API callback tracing
    private static final boolean TRACE = DebugFlags.TRACE_CALLBACK;
    // The WebView instance that this adapter is serving.
    private final WebView mWebView;
    // The WebViewClient instance that was passed to WebView.setWebViewClient().
    private WebViewClient mWebViewClient;
    // The WebChromeClient instance that was passed to WebView.setContentViewClient().
    private WebChromeClient mWebChromeClient;
    // The listener receiving find-in-page API results.
    private WebView.FindListener mFindListener;

    private DownloadListener mDownloadListener;

    private Handler mUiThreadHandler;

    private SoftReference<Bitmap> mCachedDefaultVideoPoster;

    private static boolean sMethodsLoaded = false;
    private static Method sErrorStringsGetString = null;

    private static final int NEW_WEBVIEW_CREATED = 100;

    /**
     * Adapter constructor.
     *
     * @param webView the {@link WebView} instance that this adapter is serving.
     */
    WebViewContentsClientAdapter(WebView webView) {
        if (webView == null) {
            throw new IllegalArgumentException("webView can't be null");
        }

        mWebView = webView;
        setWebViewClient(null);

        mUiThreadHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case NEW_WEBVIEW_CREATED:
                        WebView.WebViewTransport t = (WebView.WebViewTransport) msg.obj;
                        WebView newWebView = t.getWebView();
                        if (newWebView == mWebView) {
                            throw new IllegalArgumentException(
                                    "Parent WebView cannot host it's own popup window. Please " +
                                    "use WebSettings.setSupportMultipleWindows(false)");
                        }

                        if (newWebView != null && newWebView.copyBackForwardList().getSize() != 0) {
                            throw new IllegalArgumentException(
                                    "New WebView for popup window must not have been previously " +
                                    "navigated.");
                        }

                        WebViewChromium.completeWindowCreation(mWebView, newWebView);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        };

        if (!sMethodsLoaded) {
            loadMethods();
        }
    }

    // WebViewClassic is coded in such a way that even if a null WebViewClient is set,
    // certain actions take place.
    // We choose to replicate this behavior by using a NullWebViewClient implementation (also known
    // as the Null Object pattern) rather than duplicating the WebViewClassic approach in
    // ContentView.
    static class NullWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
            // TODO: Investigate more and add a test case.
            // This is a copy of what Clank does. The WebViewCore key handling code and Clank key
            // handling code differ enough that it's not trivial to figure out how keycodes are
            // being filtered.
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_CALL ||
                keyCode == KeyEvent.KEYCODE_ENDCALL ||
                keyCode == KeyEvent.KEYCODE_POWER ||
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_CAMERA ||
                keyCode == KeyEvent.KEYCODE_FOCUS ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Intent intent;
            // Perform generic parsing of the URI to turn it into an Intent.
            try {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ex) {
                Log.w(TAG, "Bad URI " + url + ": " + ex.getMessage());
                return false;
            }
            // Sanitize the Intent, ensuring web pages can not bypass browser
            // security (only access to BROWSABLE activities).
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            // Pass the package name as application ID so that the intent from the
            // same application can be opened in the same tab.
            intent.putExtra(Browser.EXTRA_APPLICATION_ID,
                    view.getContext().getPackageName());
            try {
                view.getContext().startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                Log.w(TAG, "No application can handle " + url);
                return false;
            }
            return true;
        }
    }

    void setWebViewClient(WebViewClient client) {
        if (client != null) {
            mWebViewClient = client;
        } else {
            mWebViewClient = new NullWebViewClient();
        }
    }

    void setWebChromeClient(WebChromeClient client) {
        mWebChromeClient = client;
    }

    void setDownloadListener(DownloadListener listener) {
        mDownloadListener = listener;
    }

    void setFindListener(WebView.FindListener listener) {
        mFindListener = listener;
    }

    //--------------------------------------------------------------------------------------------
    //                        Adapter for all the methods.
    //--------------------------------------------------------------------------------------------

    /**
     * @see AwContentsClient#getVisitedHistory
     */
    @Override
    public void getVisitedHistory(ValueCallback<String[]> callback) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "getVisitedHistory");
            mWebChromeClient.getVisitedHistory(callback);
        }
        TraceEvent.end(TAG);
    }

    /**
     * @see AwContentsClient#doUpdateVisiteHistory(String, boolean)
     */
    @Override
    public void doUpdateVisitedHistory(String url, boolean isReload) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "doUpdateVisitedHistory=" + url + " reload=" + isReload);
        mWebViewClient.doUpdateVisitedHistory(mWebView, url, isReload);
        TraceEvent.end(TAG);
    }

    /**
     * @see AwContentsClient#onProgressChanged(int)
     */
    @Override
    public void onProgressChanged(int progress) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onProgressChanged=" + progress);
            mWebChromeClient.onProgressChanged(mWebView, progress);
        }
        TraceEvent.end(TAG);
    }

    /**
     * @see AwContentsClient#shouldInterceptRequest(java.lang.String)
     */
    @Override
    public AwWebResourceResponse shouldInterceptRequest(AwWebResourceRequest request) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "shouldInterceptRequest=" + request.url);
        WebResourceResponse response = mWebViewClient.shouldInterceptRequest(mWebView,
                request.url);
        TraceEvent.end(TAG);
        if (response == null) return null;

        // AwWebResourceResponse should support null headers. b/16332774.
        Map<String, String> responseHeaders = null/*response.getResponseHeaders()*/;
        if (responseHeaders == null)
            responseHeaders = new HashMap<String, String>();

        return new AwWebResourceResponse(
                response.getMimeType(),
                response.getEncoding(),
                response.getData(),
                0/*response.getStatusCode()*/,
                ""/*response.getReasonPhrase()*/,
                responseHeaders);
    }

    /**
     * @see AwContentsClient#shouldOverrideUrlLoading(java.lang.String)
     */
    @Override
    public boolean shouldOverrideUrlLoading(String url) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "shouldOverrideUrlLoading=" + url);
        boolean result = mWebViewClient.shouldOverrideUrlLoading(mWebView, url);
        TraceEvent.end(TAG);
        return result;
    }

    /**
     * @see AwContentsClient#onUnhandledKeyEvent(android.view.KeyEvent)
     */
    @Override
    public void onUnhandledKeyEvent(KeyEvent event) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onUnhandledKeyEvent");
        mWebViewClient.onUnhandledKeyEvent(mWebView, event);
        TraceEvent.end(TAG);
    }

    /**
     * @see AwContentsClient#onConsoleMessage(android.webkit.ConsoleMessage)
     */
    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        TraceEvent.begin(TAG);
        boolean result;
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onConsoleMessage");
            result = mWebChromeClient.onConsoleMessage(consoleMessage);
            String message = consoleMessage.message();
            if (result && message != null && message.startsWith("[blocked]")) {
                Log.e(TAG, "Blocked URL: " + message);
            }
        } else {
            result = false;
        }
        TraceEvent.end(TAG);
        return result;
    }

    /**
     * @see AwContentsClient#onFindResultReceived(int,int,boolean)
     */
    @Override
    public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
            boolean isDoneCounting) {
        if (mFindListener == null) return;
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onFindResultReceived");
        mFindListener.onFindResultReceived(activeMatchOrdinal, numberOfMatches, isDoneCounting);
        TraceEvent.end(TAG);
    }

    /**
     * @See AwContentsClient#onNewPicture(Picture)
     */
    @Override
    public void onNewPicture(Picture picture) {
    }

    @Override
    public void onLoadResource(String url) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onLoadResource=" + url);
        mWebViewClient.onLoadResource(mWebView, url);
        TraceEvent.end(TAG);
    }

    @Override
    public boolean onCreateWindow(boolean isDialog, boolean isUserGesture) {
        Message m = mUiThreadHandler.obtainMessage(
                NEW_WEBVIEW_CREATED, mWebView.new WebViewTransport());
        TraceEvent.begin(TAG);
        boolean result;
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onCreateWindow");
            result = mWebChromeClient.onCreateWindow(mWebView, isDialog, isUserGesture, m);
        } else {
            result = false;
        }
        TraceEvent.end(TAG);
        return result;
    }

    /**
     * @see AwContentsClient#onCloseWindow()
     */
    @Override
    public void onCloseWindow() {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onCloseWindow");
            mWebChromeClient.onCloseWindow(mWebView);
        }
        TraceEvent.end(TAG);
    }

    /**
     * @see AwContentsClient#onRequestFocus()
     */
    @Override
    public void onRequestFocus() {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onRequestFocus");
            mWebChromeClient.onRequestFocus(mWebView);
        }
        TraceEvent.end(TAG);
    }

    /**
     * @see AwContentsClient#onReceivedTouchIconUrl(String url, boolean precomposed)
     */
    @Override
    public void onReceivedTouchIconUrl(String url, boolean precomposed) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onReceivedTouchIconUrl=" + url);
            mWebChromeClient.onReceivedTouchIconUrl(mWebView, url, precomposed);
        }
        TraceEvent.end(TAG);
    }

    /**
     * @see AwContentsClient#onReceivedIcon(Bitmap bitmap)
     */
    @Override
    public void onReceivedIcon(Bitmap bitmap) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onReceivedIcon");
            mWebChromeClient.onReceivedIcon(mWebView, bitmap);
        }
        TraceEvent.end(TAG);
    }

    /**
     * @see ContentViewClient#onPageStarted(String)
     */
    @Override
    public void onPageStarted(String url) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onPageStarted=" + url);
        mWebViewClient.onPageStarted(mWebView, url, mWebView.getFavicon());
        TraceEvent.end(TAG);
    }

    /**
     * @see ContentViewClient#onPageFinished(String)
     */
    @Override
    public void onPageFinished(String url) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onPageFinished=" + url);
        mWebViewClient.onPageFinished(mWebView, url);
        TraceEvent.end(TAG);
    }

    /**
     * @see ContentViewClient#onReceivedError(int,String,String)
     */
    @Override
    public void onReceivedError(int errorCode, String description, String failingUrl) {
        if (description == null || description.isEmpty()) {
            // ErrorStrings is @hidden, so we can't do this in AwContents.
            // Normally the net/ layer will set a valid description, but for synthesized callbacks
            // (like in the case for intercepted requests) AwContents will pass in null.
            description = getErrorString(errorCode, mWebView.getContext());
        }
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onReceivedError=" + failingUrl);
        mWebViewClient.onReceivedError(mWebView, errorCode, description, failingUrl);
        TraceEvent.end(TAG);
    }

    /**
     * @see ContentViewClient#onReceivedTitle(String)
     */
    @Override
    public void onReceivedTitle(String title) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onReceivedTitle");
            mWebChromeClient.onReceivedTitle(mWebView, title);
        }
        TraceEvent.end(TAG);
    }


    /**
     * @see ContentViewClient#shouldOverrideKeyEvent(KeyEvent)
     */
    @Override
    public boolean shouldOverrideKeyEvent(KeyEvent event) {
        // TODO(joth): The expression here is a workaround for http://b/7697782 :-
        // 1. The check for system key should be made in AwContents or ContentViewCore,
        //    before shouldOverrideKeyEvent() is called at all.
        // 2. shouldOverrideKeyEvent() should be called in onKeyDown/onKeyUp, not from
        //    dispatchKeyEvent().
        if (event.isSystem()) return true;
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "shouldOverrideKeyEvent");
        boolean result = mWebViewClient.shouldOverrideKeyEvent(mWebView, event);
        TraceEvent.end(TAG);
        return result;
    }


    /**
     * @see ContentViewClient#onStartContentIntent(Context, String)
     * Callback when detecting a click on a content link.
     */
    // TODO: Delete this method when removed from base class.
    public void onStartContentIntent(Context context, String contentUrl) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "shouldOverrideUrlLoading=" + contentUrl);
        mWebViewClient.shouldOverrideUrlLoading(mWebView, contentUrl);
        TraceEvent.end(TAG);
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin,
            GeolocationPermissions.Callback callback) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onGeolocationPermissionsShowPrompt");
            mWebChromeClient.onGeolocationPermissionsShowPrompt(origin, callback);
        }
        TraceEvent.end(TAG);
    }

    @Override
    public void onGeolocationPermissionsHidePrompt() {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onGeolocationPermissionsHidePrompt");
            mWebChromeClient.onGeolocationPermissionsHidePrompt();
        }
        TraceEvent.end(TAG);
    }

    private static class JsPromptResultReceiverAdapter implements JsResult.ResultReceiver {
        private JsPromptResultReceiver mChromePromptResultReceiver;
        private JsResultReceiver mChromeResultReceiver;
        // We hold onto the JsPromptResult here, just to avoid the need to downcast
        // in onJsResultComplete.
        private final JsPromptResult mPromptResult = new JsPromptResult(this);

        public JsPromptResultReceiverAdapter(JsPromptResultReceiver receiver) {
            mChromePromptResultReceiver = receiver;
        }

        public JsPromptResultReceiverAdapter(JsResultReceiver receiver) {
            mChromeResultReceiver = receiver;
        }

        public JsPromptResult getPromptResult() {
            return mPromptResult;
        }

        @Override
        public void onJsResultComplete(JsResult result) {
            if (mChromePromptResultReceiver != null) {
                if (mPromptResult.getResult()) {
                    mChromePromptResultReceiver.confirm(mPromptResult.getStringResult());
                } else {
                    mChromePromptResultReceiver.cancel();
                }
            } else {
                if (mPromptResult.getResult()) {
                    mChromeResultReceiver.confirm();
                } else {
                    mChromeResultReceiver.cancel();
                }
            }
        }
    }

    @Override
    public void handleJsAlert(String url, String message, JsResultReceiver receiver) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            final JsPromptResult res =
                    new JsPromptResultReceiverAdapter(receiver).getPromptResult();
            if (TRACE) Log.d(TAG, "onJsAlert");
            if (!mWebChromeClient.onJsAlert(mWebView, url, message, res)) {
                new JsDialogHelper(res, JsDialogHelper.ALERT, null, message, url)
                        .showDialog(mWebView.getContext());
            }
        } else {
            receiver.cancel();
        }
        TraceEvent.end(TAG);
    }

    @Override
    public void handleJsBeforeUnload(String url, String message, JsResultReceiver receiver) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            final JsPromptResult res =
                    new JsPromptResultReceiverAdapter(receiver).getPromptResult();
            if (TRACE) Log.d(TAG, "onJsBeforeUnload");
            if (!mWebChromeClient.onJsBeforeUnload(mWebView, url, message, res)) {
                new JsDialogHelper(res, JsDialogHelper.UNLOAD, null, message, url)
                        .showDialog(mWebView.getContext());
            }
        } else {
            receiver.cancel();
        }
        TraceEvent.end(TAG);
    }

    @Override
    public void handleJsConfirm(String url, String message, JsResultReceiver receiver) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            final JsPromptResult res =
                    new JsPromptResultReceiverAdapter(receiver).getPromptResult();
            if (TRACE) Log.d(TAG, "onJsConfirm");
            if (!mWebChromeClient.onJsConfirm(mWebView, url, message, res)) {
                new JsDialogHelper(res, JsDialogHelper.CONFIRM, null, message, url)
                        .showDialog(mWebView.getContext());
            }
        } else {
            receiver.cancel();
        }
        TraceEvent.end(TAG);
    }

    @Override
    public void handleJsPrompt(String url, String message, String defaultValue,
            JsPromptResultReceiver receiver) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            final JsPromptResult res =
                    new JsPromptResultReceiverAdapter(receiver).getPromptResult();
            if (TRACE) Log.d(TAG, "onJsPrompt");
            if (!mWebChromeClient.onJsPrompt(mWebView, url, message, defaultValue, res)) {
                new JsDialogHelper(res, JsDialogHelper.PROMPT, defaultValue, message, url)
                        .showDialog(mWebView.getContext());
            }
        } else {
            receiver.cancel();
        }
        TraceEvent.end(TAG);
    }

    @Override
    public void onReceivedHttpAuthRequest(AwHttpAuthHandler handler, String host, String realm) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onReceivedHttpAuthRequest=" + host);
        mWebViewClient.onReceivedHttpAuthRequest(mWebView,
                new AwHttpAuthHandlerAdapter(handler), host, realm);
        TraceEvent.end(TAG);
    }

    @Override
    public void onReceivedSslError(final ValueCallback<Boolean> callback, SslError error) {
        SslErrorHandler handler = new SslErrorHandler() {
            @Override
            public void proceed() {
                postProceed(true);
            }
            @Override
            public void cancel() {
                postProceed(false);
            }
            private void postProceed(final boolean proceed) {
                post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onReceiveValue(proceed);
                        }
                    });
            }
        };
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onReceivedSslError");
        mWebViewClient.onReceivedSslError(mWebView, handler, error);
        TraceEvent.end(TAG);
    }

    @Override
    public void onReceivedLoginRequest(String realm, String account, String args) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onReceivedLoginRequest=" + realm);
        mWebViewClient.onReceivedLoginRequest(mWebView, realm, account, args);
        TraceEvent.end(TAG);
    }

    @Override
    public void onFormResubmission(Message dontResend, Message resend) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, "onFormResubmission");
        mWebViewClient.onFormResubmission(mWebView, dontResend, resend);
        TraceEvent.end(TAG);
    }

    @Override
    public void onDownloadStart(String url,
                                String userAgent,
                                String contentDisposition,
                                String mimeType,
                                long contentLength) {
        if (mDownloadListener != null) {
            TraceEvent.begin(TAG);
            if (TRACE) Log.d(TAG, "onDownloadStart");
            mDownloadListener.onDownloadStart(url,
                                              userAgent,
                                              contentDisposition,
                                              mimeType,
                                              contentLength);
            TraceEvent.end(TAG);
        }
    }

    @Override
    public void onScaleChangedScaled(float oldScale, float newScale) {
        TraceEvent.begin(TAG);
        if (TRACE) Log.d(TAG, " onScaleChangedScaled");
        mWebViewClient.onScaleChanged(mWebView, oldScale, newScale);
        TraceEvent.end(TAG);
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback cb) {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onShowCustomView");
            mWebChromeClient.onShowCustomView(view, cb);
        }
        TraceEvent.end(TAG);
    }

    @Override
    public void onHideCustomView() {
        TraceEvent.begin(TAG);
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "onHideCustomView");
            mWebChromeClient.onHideCustomView();
        }
        TraceEvent.end(TAG);
    }

    @Override
    protected View getVideoLoadingProgressView() {
        TraceEvent.begin(TAG);
        View result;
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "getVideoLoadingProgressView");
            result = mWebChromeClient.getVideoLoadingProgressView();
        } else {
            result = null;
        }
        TraceEvent.end(TAG);
        return result;
    }

    @Override
    public Bitmap getDefaultVideoPoster() {
        TraceEvent.begin(TAG);
        Bitmap result = null;
        if (mWebChromeClient != null) {
            if (TRACE) Log.d(TAG, "getDefaultVideoPoster");
            result = mWebChromeClient.getDefaultVideoPoster();
        }
        if (result == null) {
            if (mCachedDefaultVideoPoster != null) {
                result = mCachedDefaultVideoPoster.get();
            }
            if (result == null) {
                Bitmap poster = BitmapFactory.decodeResource(
                        mWebView.getContext().getResources(),
                        com.mogoweb.chrome.R.drawable.ic_media_video_poster);
                result = Bitmap.createBitmap(poster.getWidth(),
                                             poster.getHeight(),
                                             poster.getConfig());
                result.eraseColor(Color.GRAY);
                Canvas canvas = new Canvas(result);
                canvas.drawBitmap(poster, 0f, 0f, null);
                mCachedDefaultVideoPoster = new SoftReference<Bitmap>(result);
            }
        }
        TraceEvent.end(TAG);
        return result;
    }

    private static class AwHttpAuthHandlerAdapter extends com.mogoweb.chrome.HttpAuthHandler {
        private AwHttpAuthHandler mAwHandler;

        public AwHttpAuthHandlerAdapter(AwHttpAuthHandler awHandler) {
            mAwHandler = awHandler;
        }

        @Override
        public void proceed(String username, String password) {
            if (username == null) {
                username = "";
            }

            if (password == null) {
                password = "";
            }
            mAwHandler.proceed(username, password);
        }

        @Override
        public void cancel() {
            mAwHandler.cancel();
        }

        @Override
        public boolean useHttpAuthUsernamePassword() {
            return mAwHandler.isFirstAttempt();
        }
    }

    private static void loadMethods() {
        try {
            ClassLoader classLoader = WebViewContentsClientAdapter.class.getClassLoader();
            Class<?> errorStringsClass = classLoader.loadClass("android.net.http.ErrorStrings");
            sErrorStringsGetString = errorStringsClass.getMethod("getString", new Class[] { Integer.class, Context.class });
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "loadMethods(): " + e.getMessage());
            sErrorStringsGetString = null;
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "loadMethods(): " + e.getMessage());
            sErrorStringsGetString = null;
        }

        sMethodsLoaded = true;
    }

    private static String getErrorString(int errorCode, Context context) {
        String errorString = "unknown error";
        if (sErrorStringsGetString != null) {
            try {
                errorString = (String)sErrorStringsGetString.invoke(null, errorCode, context);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "getErrorString(): " + e.getMessage());
            } catch (IllegalAccessException e) {
                Log.e(TAG, "getErrorString(): " + e.getMessage());
            } catch (InvocationTargetException e) {
                Log.e(TAG, "getErrorString(): " + e.getMessage());
            }
        }
        return errorString;
    }

}

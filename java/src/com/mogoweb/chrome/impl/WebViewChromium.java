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

import java.io.BufferedWriter;
import java.io.File;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.chromium.android_webview.AwContents;
import org.chromium.android_webview.AwPrintDocumentAdapter;
import org.chromium.android_webview.AwSettings;
import org.chromium.base.ThreadUtils;
import org.chromium.content.browser.LoadUrlParams;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.http.SslCertificate;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.print.PrintDocumentAdapter;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.ValueCallback;
import android.widget.TextView;

import com.mogoweb.chrome.DownloadListener;
import com.mogoweb.chrome.JavascriptInterface;
import com.mogoweb.chrome.R;
import com.mogoweb.chrome.WebBackForwardList;
import com.mogoweb.chrome.WebChromeClient;
import com.mogoweb.chrome.WebSettings;
import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.WebViewClient;

/**
 * This class is the delegate to which WebViewProxy forwards all API calls.
 *
 * Most of the actual functionality is implemented by AwContents (or ContentViewCore within
 * it). This class also contains WebView-specific APIs that require the creation of other
 * adapters (otherwise org.chromium.content would depend on the webview.chromium package)
 * and a small set of no-op deprecated APIs.
 */
class WebViewChromium implements WebViewProvider,
          WebViewProvider.ScrollDelegate, WebViewProvider.ViewDelegate {

    private class WebViewChromiumRunQueue {
        public WebViewChromiumRunQueue() {
            mQueue = new ConcurrentLinkedQueue<Runnable>();
        }

        public void addTask(Runnable task) {
            mQueue.add(task);
            if (mFactory.hasStarted()) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        drainQueue();
                    }
                });
            }
        }

        public void drainQueue() {
            if (mQueue == null || mQueue.isEmpty()) {
                return;
            }

            Runnable task = mQueue.poll();
            while(task != null) {
                task.run();
                task = mQueue.poll();
            }
        }

        private Queue<Runnable> mQueue;
    }

    private WebViewChromiumRunQueue mRunQueue;

    private static final String TAG = WebViewChromium.class.getSimpleName();

    // The WebView that this WebViewChromium is the provider for.
    WebView mWebView;
    // Lets us access protected View-derived methods on the WebView instance we're backing.
    WebView.PrivateAccess mWebViewPrivate;
    // The client adapter class.
    private WebViewContentsClientAdapter mContentsClientAdapter;

    // Variables for functionality provided by this adapter ---------------------------------------
    private ContentSettingsAdapter mWebSettings;
    // The WebView wrapper for ContentViewCore and required browser compontents.
    private AwContents mAwContents;
    // Non-null if this webview is using the GL accelerated draw path.
    private DrawGLFunctor mGLfunctor;

    private final WebView.HitTestResult mHitTestResult;

    private final int mAppTargetSdkVersion;

    private WebViewChromiumFactoryProvider mFactory;

    // This does not touch any global / non-threadsafe state, but note that
    // init is ofter called right after and is NOT threadsafe.
    public WebViewChromium(WebViewChromiumFactoryProvider factory, WebView webView,
            WebView.PrivateAccess webViewPrivate) {
        mWebView = webView;
        mWebViewPrivate = webViewPrivate;
        mHitTestResult = new WebView.HitTestResult();
        mAppTargetSdkVersion = mWebView.getContext().getApplicationInfo().targetSdkVersion;
        mFactory = factory;
        mRunQueue = new WebViewChromiumRunQueue();
    }

    static void completeWindowCreation(WebView parent, WebView child) {
        AwContents parentContents = ((WebViewChromium) parent.getWebViewProvider()).mAwContents;
        AwContents childContents =
                child == null ? null : ((WebViewChromium) child.getWebViewProvider()).mAwContents;
        parentContents.supplyContentsForPopup(childContents);
    }

    private <T> T runBlockingFuture(FutureTask<T> task) {
        if (!mFactory.hasStarted()) throw new RuntimeException("Must be started before we block!");
        if (ThreadUtils.runningOnUiThread()) {
            throw new IllegalStateException("This method should only be called off the UI thread");
        }
        mRunQueue.addTask(task);
        try {
            return task.get(4, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Probable deadlock detected due to WebView API being called "
                    + "on incorrect thread while the UI thread is blocked.", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // We have a 4 second timeout to try to detect deadlocks to detect and aid in debuggin
    // deadlocks.
    // Do not call this method while on the UI thread!
    private void runVoidTaskOnUiThreadBlocking(Runnable r) {
        FutureTask<Void> task = new FutureTask<Void>(r, null);
        runBlockingFuture(task);
    }

    private <T> T runOnUiThreadBlocking(Callable<T> c) {
        return runBlockingFuture(new FutureTask<T>(c));
    }

    // WebViewProvider methods --------------------------------------------------------------------

    @Override
    // BUG=6790250 |javaScriptInterfaces| was only ever used by the obsolete DumpRenderTree
    // so is ignored. TODO: remove it from WebViewProvider.
    public void init(final Map<String, Object> javaScriptInterfaces,
            final boolean privateBrowsing) {
        if (privateBrowsing) {
            mFactory.startYourEngines(true);
            final String msg = "Private browsing is not supported in WebView.";
            if (mAppTargetSdkVersion >= Build.VERSION_CODES.KITKAT) {
                throw new IllegalArgumentException(msg);
            } else {
                Log.w(TAG, msg);
                TextView warningLabel = new TextView(mWebView.getContext());
                warningLabel.setText(mWebView.getContext().getString(
                        R.string.webviewchromium_private_browsing_warning));
                mWebView.addView(warningLabel);
            }
        }

        // We will defer real initialization until we know which thread to do it on, unless:
        // - we are on the main thread already (common case),
        // - the app is targeting >= JB MR2, in which case checkThread enforces that all usage
        //   comes from a single thread. (Note in JB MR2 this exception was in WebView.java).
        if (mAppTargetSdkVersion >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mFactory.startYourEngines(false);
            checkThread();
        } else if (!mFactory.hasStarted()) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                mFactory.startYourEngines(true);
            }
        }

        final boolean isAccessFromFileURLsGrantedByDefault =
                mAppTargetSdkVersion < Build.VERSION_CODES.JELLY_BEAN;
        final boolean areLegacyQuirksEnabled =
                mAppTargetSdkVersion < Build.VERSION_CODES.KITKAT;

        mContentsClientAdapter = new WebViewContentsClientAdapter(mWebView);
        mWebSettings = new ContentSettingsAdapter(new AwSettings(
                mWebView.getContext(), isAccessFromFileURLsGrantedByDefault,
                areLegacyQuirksEnabled));

        mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    initForReal();
                    if (privateBrowsing) {
                        // Intentionally irreversibly disable the webview instance, so that private
                        // user data cannot leak through misuse of a non-privateBrowing WebView
                        // instance. Can't just null out mAwContents as we never null-check it
                        // before use.
                        destroy();
                    }
                }
        });
    }

    private void initForReal() {
        mAwContents = new AwContents(mFactory.getBrowserContext(), mWebView,
                new InternalAccessAdapter(), mContentsClientAdapter,
                mWebSettings.getAwSettings());

        if (mAppTargetSdkVersion >= Build.VERSION_CODES.KITKAT) {
            // On KK and above, favicons are automatically downloaded as the method
            // old apps use to enable that behavior is deprecated.
            AwContents.setShouldDownloadFavicons();
        }
    }

    void startYourEngine() {
        mRunQueue.drainQueue();
    }

    private RuntimeException createThreadException() {
        return new IllegalStateException(
                "Calling View methods on another thread than the UI thread.");
    }

    private boolean checkNeedsPost() {
        boolean needsPost = !mFactory.hasStarted() || !ThreadUtils.runningOnUiThread();
        if (!needsPost && mAwContents == null) {
            throw new IllegalStateException(
                    "AwContents must be created if we are not posting!");
        }
        return needsPost;
    }

    //  Intentionally not static, as no need to check thread on static methods
    private void checkThread() {
        if (!ThreadUtils.runningOnUiThread()) {
            final RuntimeException threadViolation = createThreadException();
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    throw threadViolation;
                }
            });
            throw createThreadException();
        }
    }

    @Override
    public void setHorizontalScrollbarOverlay(final boolean overlay) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    setHorizontalScrollbarOverlay(overlay);
                }
            });
            return;
        }
        mAwContents.setHorizontalScrollbarOverlay(overlay);
    }

    @Override
    public void setVerticalScrollbarOverlay(final boolean overlay) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    setVerticalScrollbarOverlay(overlay);
                }
            });
            return;
        }
        mAwContents.setVerticalScrollbarOverlay(overlay);
    }

    @Override
    public boolean overlayHorizontalScrollbar() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return overlayHorizontalScrollbar();
                }
            });
            return ret;
        }
        return mAwContents.overlayHorizontalScrollbar();
    }

    @Override
    public boolean overlayVerticalScrollbar() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return overlayVerticalScrollbar();
                }
            });
            return ret;
        }
        return mAwContents.overlayVerticalScrollbar();
    }

    @Override
    public int getVisibleTitleHeight() {
        // This is deprecated in WebView and should always return 0.
        return 0;
    }

    @Override
    public SslCertificate getCertificate() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            SslCertificate ret = runOnUiThreadBlocking(new Callable<SslCertificate>() {
                @Override
                public SslCertificate call() {
                    return getCertificate();
                }
            });
            return ret;
        }
        return mAwContents.getCertificate();
    }

    @Override
    public void setCertificate(SslCertificate certificate) {
        // intentional no-op
    }

    @Override
    public void savePassword(String host, String username, String password) {
        // This is a deprecated API: intentional no-op.
    }

    @Override
    public void setHttpAuthUsernamePassword(final String host, final String realm,
            final String username, final String password) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    setHttpAuthUsernamePassword(host, realm, username, password);
                }
            });
            return;
        }
        mAwContents.setHttpAuthUsernamePassword(host, realm, username, password);
    }

    @Override
    public String[] getHttpAuthUsernamePassword(final String host, final String realm) {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            String[] ret = runOnUiThreadBlocking(new Callable<String[]>() {
                @Override
                public String[] call() {
                    return getHttpAuthUsernamePassword(host, realm);
                }
            });
            return ret;
        }
        return mAwContents.getHttpAuthUsernamePassword(host, realm);
    }

    @Override
    public void destroy() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    destroy();
                }
            });
            return;
        }

        mAwContents.destroy();
        if (mGLfunctor != null) {
            mGLfunctor.destroy();
            mGLfunctor = null;
        }
    }

    @Override
    public void setNetworkAvailable(final boolean networkUp) {
        // Note that this purely toggles the JS navigator.online property.
        // It does not in affect chromium or network stack state in any way.
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    setNetworkAvailable(networkUp);
                }
            });
            return;
        }
        mAwContents.setNetworkAvailable(networkUp);
    }

    @Override
    public WebBackForwardList saveState(final Bundle outState) {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            WebBackForwardList ret = runOnUiThreadBlocking(new Callable<WebBackForwardList>() {
                @Override
                public WebBackForwardList call() {
                    return saveState(outState);
                }
            });
            return ret;
        }
        if (outState == null) return null;
        if (!mAwContents.saveState(outState)) return null;
        return copyBackForwardList();
    }

    @Override
    public boolean savePicture(Bundle b, File dest) {
        // Intentional no-op: hidden method on WebView.
        return false;
    }

    @Override
    public boolean restorePicture(Bundle b, File src) {
        // Intentional no-op: hidden method on WebView.
        return false;
    }

    @Override
    public WebBackForwardList restoreState(final Bundle inState) {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            WebBackForwardList ret = runOnUiThreadBlocking(new Callable<WebBackForwardList>() {
                @Override
                public WebBackForwardList call() {
                    return restoreState(inState);
                }
            });
            return ret;
        }
        if (inState == null) return null;
        if (!mAwContents.restoreState(inState)) return null;
        return copyBackForwardList();
    }

    @Override
    public void loadUrl(final String url, Map<String, String> additionalHttpHeaders) {
        // TODO: We may actually want to do some sanity checks here (like filter about://chrome).

        // For backwards compatibility, apps targeting less than K will have JS URLs evaluated
        // directly and any result of the evaluation will not replace the current page content.
        // Matching Chrome behavior more closely; apps targetting >= K that load a JS URL will
        // have the result of that URL replace the content of the current page.
        final String JAVASCRIPT_SCHEME = "javascript:";
        if (mAppTargetSdkVersion < Build.VERSION_CODES.KITKAT &&
                url != null && url.startsWith(JAVASCRIPT_SCHEME)) {
            mFactory.startYourEngines(true);
            if (checkNeedsPost()) {
                mRunQueue.addTask(new Runnable() {
                    @Override
                    public void run() {
                        mAwContents.evaluateJavaScriptEvenIfNotYetNavigated(
                                url.substring(JAVASCRIPT_SCHEME.length()));
                    }
                });
            } else {
                mAwContents.evaluateJavaScriptEvenIfNotYetNavigated(
                        url.substring(JAVASCRIPT_SCHEME.length()));
            }
            return;
        }

        LoadUrlParams params = new LoadUrlParams(url);
        if (additionalHttpHeaders != null) params.setExtraHeaders(additionalHttpHeaders);
        loadUrlOnUiThread(params);
    }

    @Override
    public void loadUrl(String url) {
        // Early out to match old WebView implementation
        if (url == null) {
            return;
        }
        loadUrl(url, null);
    }

    @Override
    public void postUrl(String url, byte[] postData) {
        LoadUrlParams params = LoadUrlParams.createLoadHttpPostParams(url, postData);
        Map<String,String> headers = new HashMap<String,String>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        params.setExtraHeaders(headers);
        loadUrlOnUiThread(params);
    }

    private static String fixupMimeType(String mimeType) {
        return TextUtils.isEmpty(mimeType) ? "text/html" : mimeType;
    }

    private static String fixupData(String data) {
        return TextUtils.isEmpty(data) ? "" : data;
    }

    private static String fixupBase(String url) {
        return TextUtils.isEmpty(url) ? "about:blank" : url;
    }

    private static String fixupHistory(String url) {
        return TextUtils.isEmpty(url) ? "about:blank" : url;
    }

    private static boolean isBase64Encoded(String encoding) {
        return "base64".equals(encoding);
    }

    @Override
    public void loadData(String data, String mimeType, String encoding) {
        loadUrlOnUiThread(LoadUrlParams.createLoadDataParams(
                fixupData(data), fixupMimeType(mimeType), isBase64Encoded(encoding)));
    }

    @Override
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding,
            String historyUrl) {
        data = fixupData(data);
        mimeType = fixupMimeType(mimeType);
        LoadUrlParams loadUrlParams;
        baseUrl = fixupBase(baseUrl);
        historyUrl = fixupHistory(historyUrl);

        if (baseUrl.startsWith("data:")) {
            // For backwards compatibility with WebViewClassic, we use the value of |encoding|
            // as the charset, as long as it's not "base64".
            boolean isBase64 = isBase64Encoded(encoding);
            loadUrlParams = LoadUrlParams.createLoadDataParamsWithBaseUrl(
                    data, mimeType, isBase64, baseUrl, historyUrl, isBase64 ? null : encoding);
        } else {
            // When loading data with a non-data: base URL, the classic WebView would effectively
            // "dump" that string of data into the WebView without going through regular URL
            // loading steps such as decoding URL-encoded entities. We achieve this same behavior by
            // base64 encoding the data that is passed here and then loading that as a data: URL.
            try {
                loadUrlParams = LoadUrlParams.createLoadDataParamsWithBaseUrl(
                        Base64.encodeToString(data.getBytes("utf-8"), Base64.DEFAULT), mimeType,
                        true, baseUrl, historyUrl, "utf-8");
            } catch (java.io.UnsupportedEncodingException e) {
                Log.wtf(TAG, "Unable to load data string " + data, e);
                return;
            }
        }
        loadUrlOnUiThread(loadUrlParams);

        // Data url's with a base url will be resolved in Blink, and not cause an onPageStarted
        // event to be sent. Sending the callback directly from here.
        final String finalBaseUrl = loadUrlParams.getBaseUrl();
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                mContentsClientAdapter.onPageStarted(finalBaseUrl);
            }
        });
    }

    private void loadUrlOnUiThread(final LoadUrlParams loadUrlParams) {
        // This is the last point that we can delay starting the Chromium backend up
        // and if the app has not caused us to bind the Chromium UI thread to a background thread
        // we now bind Chromium's notion of the UI thread to the app main thread.
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            // Disallowed in WebView API for apps targetting a new SDK
            assert mAppTargetSdkVersion < Build.VERSION_CODES.JELLY_BEAN_MR2;
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    mAwContents.loadUrl(loadUrlParams);
                }
            });
            return;
        }
        mAwContents.loadUrl(loadUrlParams);
    }

    public void evaluateJavaScript(String script, ValueCallback<String> resultCallback) {
        checkThread();
        mAwContents.evaluateJavaScript(script, resultCallback);
    }

    @Override
    public void saveWebArchive(String filename) {
        saveWebArchive(filename, false, null);
    }

    @Override
    public void saveWebArchive(final String basename, final boolean autoname,
            final ValueCallback<String> callback) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    saveWebArchive(basename, autoname, callback);
                }
            });
            return;
        }
        mAwContents.saveWebArchive(basename, autoname, callback);
    }

    @Override
    public void stopLoading() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    stopLoading();
                }
            });
            return;
        }

        mAwContents.stopLoading();
    }

    @Override
    public void reload() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    reload();
                }
            });
            return;
        }
        mAwContents.reload();
    }

    @Override
    public boolean canGoBack() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            Boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return canGoBack();
                }
            });
            return ret;
        }
        return mAwContents.canGoBack();
    }

    @Override
    public void goBack() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    goBack();
                }
            });
            return;
        }
        mAwContents.goBack();
    }

    @Override
    public boolean canGoForward() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            Boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return canGoForward();
                }
            });
            return ret;
        }
        return mAwContents.canGoForward();
    }

    @Override
    public void goForward() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    goForward();
                }
            });
            return;
        }
        mAwContents.goForward();
    }

    @Override
    public boolean canGoBackOrForward(final int steps) {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            Boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return canGoBackOrForward(steps);
                }
            });
            return ret;
        }
        return mAwContents.canGoBackOrForward(steps);
    }

    @Override
    public void goBackOrForward(final int steps) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    goBackOrForward(steps);
                }
            });
            return;
        }
        mAwContents.goBackOrForward(steps);
    }

    @Override
    public boolean isPrivateBrowsingEnabled() {
        // Not supported in this WebView implementation.
        return false;
    }

    @Override
    public boolean pageUp(final boolean top) {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            Boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return pageUp(top);
                }
            });
            return ret;
        }
        return mAwContents.pageUp(top);
    }

    @Override
    public boolean pageDown(final boolean bottom) {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            Boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return pageDown(bottom);
                }
            });
            return ret;
        }
        return mAwContents.pageDown(bottom);
    }

    @Override
    public void clearView() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    clearView();
                }
            });
            return;
        }
        mAwContents.clearView();
    }

    @Override
    public Picture capturePicture() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            Picture ret = runOnUiThreadBlocking(new Callable<Picture>() {
                @Override
                public Picture call() {
                    return capturePicture();
                }
            });
            return ret;
        }
        return mAwContents.capturePicture();
    }

    @Override
    public PrintDocumentAdapter createPrintDocumentAdapter() {
        checkThread();
        return new AwPrintDocumentAdapter(mAwContents.getPdfExporter());
    }

    @Override
    public float getScale() {
        // No checkThread() as it is mostly thread safe (workaround for b/10652991).
        mFactory.startYourEngines(true);
        return mAwContents.getScale();
    }

    @Override
    public void setInitialScale(final int scaleInPercent) {
        // No checkThread() as it is thread safe
        mWebSettings.getAwSettings().setInitialPageScale(scaleInPercent);
    }

    @Override
    public void invokeZoomPicker() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    invokeZoomPicker();
                }
            });
            return;
        }
        mAwContents.invokeZoomPicker();
    }

    @Override
    public WebView.HitTestResult getHitTestResult() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            WebView.HitTestResult ret = runOnUiThreadBlocking(
                    new Callable<WebView.HitTestResult>() {
                @Override
                public WebView.HitTestResult call() {
                    return getHitTestResult();
                }
            });
            return ret;
        }
        AwContents.HitTestData data = mAwContents.getLastHitTestResult();
        mHitTestResult.setType(data.hitTestResultType);
        mHitTestResult.setExtra(data.hitTestResultExtraData);
        return mHitTestResult;
    }

    @Override
    public void requestFocusNodeHref(final Message hrefMsg) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    requestFocusNodeHref(hrefMsg);
                }
            });
            return;
        }
        mAwContents.requestFocusNodeHref(hrefMsg);
    }

    @Override
    public void requestImageRef(final Message msg) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    requestImageRef(msg);
                }
            });
            return;
        }
        mAwContents.requestImageRef(msg);
    }

    @Override
    public String getUrl() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            String ret = runOnUiThreadBlocking(new Callable<String>() {
                @Override
                public String call() {
                    return getUrl();
                }
            });
            return ret;
        }
        String url =  mAwContents.getUrl();
        if (url == null || url.trim().isEmpty()) return null;
        return url;
    }

    @Override
    public String getOriginalUrl() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            String ret = runOnUiThreadBlocking(new Callable<String>() {
                @Override
                public String call() {
                    return getOriginalUrl();
                }
            });
            return ret;
        }
        String url =  mAwContents.getOriginalUrl();
        if (url == null || url.trim().isEmpty()) return null;
        return url;
    }

    @Override
    public String getTitle() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            String ret = runOnUiThreadBlocking(new Callable<String>() {
                @Override
                public String call() {
                    return getTitle();
                }
            });
            return ret;
        }
        return mAwContents.getTitle();
    }

    @Override
    public Bitmap getFavicon() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            Bitmap ret = runOnUiThreadBlocking(new Callable<Bitmap>() {
                @Override
                public Bitmap call() {
                    return getFavicon();
                }
            });
            return ret;
        }
        return mAwContents.getFavicon();
    }

    @Override
    public String getTouchIconUrl() {
        // Intentional no-op: hidden method on WebView.
        return null;
    }

    @Override
    public int getProgress() {
        if (mAwContents == null) return 100;
        // No checkThread() because the value is cached java side (workaround for b/10533304).
        return mAwContents.getMostRecentProgress();
    }

    @Override
    public int getContentHeight() {
        if (mAwContents == null) return 0;
        // No checkThread() as it is mostly thread safe (workaround for b/10594869).
        return mAwContents.getContentHeightCss();
    }

    @Override
    public int getContentWidth() {
        if (mAwContents == null) return 0;
        // No checkThread() as it is mostly thread safe (workaround for b/10594869).
        return mAwContents.getContentWidthCss();
    }

    @Override
    public void pauseTimers() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    pauseTimers();
                }
            });
            return;
        }
        mAwContents.pauseTimers();
    }

    @Override
    public void resumeTimers() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    resumeTimers();
                }
            });
            return;
        }
        mAwContents.resumeTimers();
    }

    @Override
    public void onPause() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onPause();
                }
            });
            return;
        }
        mAwContents.onPause();
    }

    @Override
    public void onResume() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onResume();
                }
            });
            return;
        }
        mAwContents.onResume();
    }

    @Override
    public boolean isPaused() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            Boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return isPaused();
                }
            });
            return ret;
        }
        return mAwContents.isPaused();
    }

    @Override
    public void freeMemory() {
        // Intentional no-op. Memory is managed automatically by Chromium.
    }

    @Override
    public void clearCache(final boolean includeDiskFiles) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    clearCache(includeDiskFiles);
                }
            });
            return;
        }
        mAwContents.clearCache(includeDiskFiles);
    }

    /**
     * This is a poorly named method, but we keep it for historical reasons.
     */
    @Override
    public void clearFormData() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    clearFormData();
                }
            });
            return;
        }
        mAwContents.hideAutofillPopup();
    }

    @Override
    public void clearHistory() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    clearHistory();
                }
            });
            return;
        }
        mAwContents.clearHistory();
    }

    @Override
    public void clearSslPreferences() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    clearSslPreferences();
                }
            });
            return;
        }
        mAwContents.clearSslPreferences();
    }

    @Override
    public WebBackForwardList copyBackForwardList() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            WebBackForwardList ret = runOnUiThreadBlocking(new Callable<WebBackForwardList>() {
                @Override
                public WebBackForwardList call() {
                    return copyBackForwardList();
                }
            });
            return ret;
        }
        return new WebBackForwardListChromium(
                mAwContents.getNavigationHistory());
    }

    @Override
    public void setFindListener(WebView.FindListener listener) {
        mContentsClientAdapter.setFindListener(listener);
    }

    @Override
    public void findNext(final boolean forwards) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    findNext(forwards);
                }
            });
            return;
        }
        mAwContents.findNext(forwards);
    }

    @Override
    public int findAll(final String searchString) {
        findAllAsync(searchString);
        return 0;
    }

    @Override
    public void findAllAsync(final String searchString) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    findAllAsync(searchString);
                }
            });
            return;
        }
        mAwContents.findAllAsync(searchString);
    }

    @Override
    public boolean showFindDialog(final String text, final boolean showIme) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            return false;
        }
        if (mWebView.getParent() == null) {
            return false;
        }

        FindActionModeCallback findAction = new FindActionModeCallback(mWebView.getContext());
        if (findAction == null) {
            return false;
        }

        mWebView.startActionMode(findAction);
        findAction.setWebView(mWebView);
        if (showIme) {
            findAction.showSoftInput();
        }

        if (text != null) {
            findAction.setText(text);
            findAction.findAll();
        }

        return true;
    }

    @Override
    public void notifyFindDialogDismissed() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    notifyFindDialogDismissed();
                }
            });
            return;
        }
        clearMatches();
    }

    @Override
    public void clearMatches() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    clearMatches();
                }
            });
            return;
        }
        mAwContents.clearMatches();
    }

    @Override
    public void documentHasImages(final Message response) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    documentHasImages(response);
                }
            });
            return;
        }
        mAwContents.documentHasImages(response);
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        mContentsClientAdapter.setWebViewClient(client);
    }

    @Override
    public void setDownloadListener(DownloadListener listener) {
        mContentsClientAdapter.setDownloadListener(listener);
    }

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        mContentsClientAdapter.setWebChromeClient(client);
    }

    @Override
    public void addJavascriptInterface(final Object obj, final String interfaceName) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    addJavascriptInterface(obj, interfaceName);
                }
            });
            return;
        }
        Class<? extends Annotation> requiredAnnotation = null;
        if (mAppTargetSdkVersion >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
           requiredAnnotation = JavascriptInterface.class;
        }
        mAwContents.addPossiblyUnsafeJavascriptInterface(obj, interfaceName, requiredAnnotation);
    }

    @Override
    public void removeJavascriptInterface(final String interfaceName) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    removeJavascriptInterface(interfaceName);
                }
            });
            return;
        }
        mAwContents.removeJavascriptInterface(interfaceName);
    }

    @Override
    public WebSettings getSettings() {
        return mWebSettings;
    }

    @Override
    public void setMapTrackballToArrowKeys(boolean setMap) {
        // This is a deprecated API: intentional no-op.
    }

    @Override
    public void flingScroll(final int vx, final int vy) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    flingScroll(vx, vy);
                }
            });
            return;
        }
        mAwContents.flingScroll(vx, vy);
    }

    @Override
    public View getZoomControls() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            return null;
        }

        // This was deprecated in 2009 and hidden in JB MR1, so just provide the minimum needed
        // to stop very out-dated applications from crashing.
        Log.w(TAG, "WebView doesn't support getZoomControls");
        return mAwContents.getSettings().supportZoom() ? new View(mWebView.getContext()) : null;
    }

    @Override
    public boolean canZoomIn() {
        if (checkNeedsPost()) {
            return false;
        }
        return mAwContents.canZoomIn();
    }

    @Override
    public boolean canZoomOut() {
        if (checkNeedsPost()) {
            return false;
        }
        return mAwContents.canZoomOut();
    }

    @Override
    public boolean zoomIn() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return zoomIn();
                }
            });
            return ret;
        }
        return mAwContents.zoomIn();
    }

    @Override
    public boolean zoomOut() {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return zoomOut();
                }
            });
            return ret;
        }
        return mAwContents.zoomOut();
    }

    @Override
    public void dumpViewHierarchyWithProperties(BufferedWriter out, int level) {
        // Intentional no-op
    }

    @Override
    public View findHierarchyView(String className, int hashCode) {
        // Intentional no-op
        return null;
    }

    // WebViewProvider glue methods ---------------------------------------------------------------

    @Override
    // This needs to be kept thread safe!
    public WebViewProvider.ViewDelegate getViewDelegate() {
        return this;
    }

    @Override
    // This needs to be kept thread safe!
    public WebViewProvider.ScrollDelegate getScrollDelegate() {
        return this;
    }


    // WebViewProvider.ViewDelegate implementation ------------------------------------------------

    // TODO: remove from WebViewProvider and use default implementation from
    // ViewGroup.
    // @Override
    public boolean shouldDelayChildPressedState() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return shouldDelayChildPressedState();
                }
            });
            return ret;
        }
        return true;
    }

//    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            AccessibilityNodeProvider ret = runOnUiThreadBlocking(
                    new Callable<AccessibilityNodeProvider>() {
                @Override
                public AccessibilityNodeProvider call() {
                    return getAccessibilityNodeProvider();
                }
            });
            return ret;
        }
        return mAwContents.getAccessibilityNodeProvider();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(final AccessibilityNodeInfo info) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            runVoidTaskOnUiThreadBlocking(new Runnable() {
                @Override
                public void run() {
                    onInitializeAccessibilityNodeInfo(info);
                }
            });
            return;
        }
        mAwContents.onInitializeAccessibilityNodeInfo(info);
    }

    @Override
    public void onInitializeAccessibilityEvent(final AccessibilityEvent event) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            runVoidTaskOnUiThreadBlocking(new Runnable() {
                @Override
                public void run() {
                    onInitializeAccessibilityEvent(event);
                }
            });
            return;
        }
        mAwContents.onInitializeAccessibilityEvent(event);
    }

    @Override
    public boolean performAccessibilityAction(final int action, final Bundle arguments) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return performAccessibilityAction(action, arguments);
                }
            });
            return ret;
        }
        if (mAwContents.supportsAccessibilityAction(action)) {
            return mAwContents.performAccessibilityAction(action, arguments);
        }
        return mWebViewPrivate.super_performAccessibilityAction(action, arguments);
    }

    @Override
    public void setOverScrollMode(final int mode) {
        // This gets called from the android.view.View c'tor that WebView inherits from. This
        // causes the method to be called when mAwContents == null.
        // It's safe to ignore these calls however since AwContents will read the current value of
        // this setting when it's created.
        if (mAwContents == null) return;

        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    setOverScrollMode(mode);
                }
            });
            return;
        }
        mAwContents.setOverScrollMode(mode);
    }

    @Override
    public void setScrollBarStyle(final int style) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    setScrollBarStyle(style);
                }
            });
            return;
        }
        mAwContents.setScrollBarStyle(style);
    }

    @Override
    public void onDrawVerticalScrollBar(final Canvas canvas, final Drawable scrollBar, final int l,
            final int t, final int r, final int b) {
        // WebViewClassic was overriding this method to handle rubberband over-scroll. Since
        // WebViewChromium doesn't support that the vanilla implementation of this method can be
        // used.
//        mWebViewPrivate.super_onDrawVerticalScrollBar(canvas, scrollBar, l, t, r, b);
    }

    @Override
    public void onOverScrolled(final int scrollX, final int scrollY, final boolean clampedX,
            final boolean clampedY) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onOverScrolled(scrollX, scrollY, clampedX, clampedY);
                }
            });
            return;
        }
        mAwContents.onContainerViewOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }

    @Override
    public void onWindowVisibilityChanged(final int visibility) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onWindowVisibilityChanged(visibility);
                }
            });
            return;
        }
        mAwContents.onWindowVisibilityChanged(visibility);
    }

    @Override
    public void onDraw(final Canvas canvas) {
        mFactory.startYourEngines(true);
        if (checkNeedsPost()) {
            runVoidTaskOnUiThreadBlocking(new Runnable() {
                @Override
                public void run() {
                    onDraw(canvas);
                }
            });
            return;
        }
        mAwContents.onDraw(canvas);
    }

    @Override
    public void setLayoutParams(final ViewGroup.LayoutParams layoutParams) {
        // This API is our strongest signal from the View system that this
        // WebView is going to be bound to a View hierarchy and so at this
        // point we must bind Chromium's UI thread to the current thread.
        mFactory.startYourEngines(false);
        checkThread();
        mWebViewPrivate.super_setLayoutParams(layoutParams);
    }

    @Override
    public boolean performLongClick() {
        // Return false unless the WebView is attached to a View with a parent
        return mWebView.getParent() != null ? mWebViewPrivate.super_performLongClick() : false;
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onConfigurationChanged(newConfig);
                }
            });
            return;
        }
        mAwContents.onConfigurationChanged(newConfig);
    }

    @Override
    public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
           return null;
        }
        return mAwContents.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onKeyMultiple(final int keyCode, final int repeatCount, final KeyEvent event) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return onKeyMultiple(keyCode, repeatCount, event);
                }
            });
            return ret;
        }
//        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return onKeyDown(keyCode, event);
                }
            });
            return ret;
        }
//        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return onKeyUp(keyCode, event);
                }
            });
            return ret;
        }
        return mAwContents.onKeyUp(keyCode, event);
    }

    @Override
    public void onAttachedToWindow() {
        // This API is our strongest signal from the View system that this
        // WebView is going to be bound to a View hierarchy and so at this
        // point we must bind Chromium's UI thread to the current thread.
        mFactory.startYourEngines(false);
        checkThread();
        mAwContents.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onDetachedFromWindow();
                }
            });
            return;
        }

        Runnable detachAwContents = new Runnable() {
            @Override
            public void run() {
                mAwContents.onDetachedFromWindow();
            }
        };

        if (mGLfunctor == null /*|| !mWebView.executeHardwareAction(detachAwContents)*/) {
            detachAwContents.run();
        }

        if (mGLfunctor != null) {
            mGLfunctor.detach();
        }
    }

    @Override
    public void onVisibilityChanged(final View changedView, final int visibility) {
        // The AwContents will find out the container view visibility before the first draw so we
        // can safely ignore onVisibilityChanged callbacks that happen before init().
        if (mAwContents == null) return;

        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onVisibilityChanged(changedView, visibility);
                }
            });
            return;
        }
        mAwContents.onVisibilityChanged(changedView, visibility);
    }

    @Override
    public void onWindowFocusChanged(final boolean hasWindowFocus) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onWindowFocusChanged(hasWindowFocus);
                }
            });
            return;
        }
        mAwContents.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    public void onFocusChanged(final boolean focused, final int direction,
            final Rect previouslyFocusedRect) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onFocusChanged(focused, direction, previouslyFocusedRect);
                }
            });
            return;
        }
        mAwContents.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    public boolean setFrame(final int left, final int top, final int right, final int bottom) {
        return mWebViewPrivate.super_setFrame(left, top, right, bottom);
    }

    @Override
    public void onSizeChanged(final int w, final int h, final int ow, final int oh) {
        if (checkNeedsPost()) {
            mRunQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onSizeChanged(w, h, ow, oh);
                }
            });
            return;
        }
        mAwContents.onSizeChanged(w, h, ow, oh);
    }

    @Override
    public void onScrollChanged(int l, int t, int oldl, int oldt) {
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return dispatchKeyEvent(event);
                }
            });
            return ret;
        }
        return mAwContents.dispatchKeyEvent(event);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return onTouchEvent(ev);
                }
            });
            return ret;
        }
        return mAwContents.onTouchEvent(ev);
    }

    @Override
    public boolean onHoverEvent(final MotionEvent event) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return onHoverEvent(event);
                }
            });
            return ret;
        }
        return mAwContents.onHoverEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(final MotionEvent event) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return onGenericMotionEvent(event);
                }
            });
            return ret;
        }
        return mAwContents.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        // Trackball event not handled, which eventually gets converted to DPAD keyevents
        return false;
    }

    @Override
    public boolean requestFocus(final int direction, final Rect previouslyFocusedRect) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return requestFocus(direction, previouslyFocusedRect);
                }
            });
            return ret;
        }
        mAwContents.requestFocus();
        return mWebViewPrivate.super_requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            runVoidTaskOnUiThreadBlocking(new Runnable() {
                @Override
                public void run() {
                    onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            });
            return;
        }
        mAwContents.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean requestChildRectangleOnScreen(final View child, final Rect rect,
            final boolean immediate) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            boolean ret = runOnUiThreadBlocking(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return requestChildRectangleOnScreen(child, rect, immediate);
                }
            });
            return ret;
        }
        return mAwContents.requestChildRectangleOnScreen(child, rect, immediate);
    }

    @Override
    public void setBackgroundColor(final int color) {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setBackgroundColor(color);
                }
            });
            return;
        }
        mAwContents.setBackgroundColor(color);
    }

    @Override
    public void setLayerType(int layerType, Paint paint) {
        // Intentional no-op
    }

    // Remove from superclass
    public void preDispatchDraw(Canvas canvas) {
        // TODO(leandrogracia): remove this method from WebViewProvider if we think
        // we won't need it again.
    }

    // WebViewProvider.ScrollDelegate implementation ----------------------------------------------

    @Override
    public int computeHorizontalScrollRange() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            int ret = runOnUiThreadBlocking(new Callable<Integer>() {
                @Override
                public Integer call() {
                    return computeHorizontalScrollRange();
                }
            });
            return ret;
        }
        return mAwContents.computeHorizontalScrollRange();
    }

    @Override
    public int computeHorizontalScrollOffset() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            int ret = runOnUiThreadBlocking(new Callable<Integer>() {
                @Override
                public Integer call() {
                    return computeHorizontalScrollOffset();
                }
            });
            return ret;
        }
        return mAwContents.computeHorizontalScrollOffset();
    }

    @Override
    public int computeVerticalScrollRange() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            int ret = runOnUiThreadBlocking(new Callable<Integer>() {
                @Override
                public Integer call() {
                    return computeVerticalScrollRange();
                }
            });
            return ret;
        }
        return mAwContents.computeVerticalScrollRange();
    }

    @Override
    public int computeVerticalScrollOffset() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            int ret = runOnUiThreadBlocking(new Callable<Integer>() {
                @Override
                public Integer call() {
                    return computeVerticalScrollOffset();
                }
            });
            return ret;
        }
        return mAwContents.computeVerticalScrollOffset();
    }

    @Override
    public int computeVerticalScrollExtent() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            int ret = runOnUiThreadBlocking(new Callable<Integer>() {
                @Override
                public Integer call() {
                    return computeVerticalScrollExtent();
                }
            });
            return ret;
        }
        return mAwContents.computeVerticalScrollExtent();
    }

    @Override
    public void computeScroll() {
        mFactory.startYourEngines(false);
        if (checkNeedsPost()) {
            runVoidTaskOnUiThreadBlocking(new Runnable() {
                @Override
                public void run() {
                    computeScroll();
                }
            });
            return;
        }
        mAwContents.computeScroll();
    }

    // AwContents.InternalAccessDelegate implementation --------------------------------------
    private class InternalAccessAdapter implements AwContents.InternalAccessDelegate {
        @Override
        public boolean drawChild(Canvas arg0, View arg1, long arg2) {
            UnimplementedWebViewApi.invoke();
            return false;
        }

        @Override
        public boolean super_onKeyUp(int arg0, KeyEvent arg1) {
            // Intentional no-op
            return false;
        }

        @Override
        public boolean super_dispatchKeyEventPreIme(KeyEvent arg0) {
            UnimplementedWebViewApi.invoke();
            return false;
        }

        @Override
        public boolean super_dispatchKeyEvent(KeyEvent event) {
            return mWebViewPrivate.super_dispatchKeyEvent(event);
        }

        @Override
        public boolean super_onGenericMotionEvent(MotionEvent arg0) {
            return mWebViewPrivate.super_onGenericMotionEvent(arg0);
        }

        @Override
        public void super_onConfigurationChanged(Configuration arg0) {
            // Intentional no-op
        }

        @Override
        public int super_getScrollBarStyle() {
            return mWebViewPrivate.super_getScrollBarStyle();
        }

        @Override
        public boolean awakenScrollBars() {
            mWebViewPrivate.awakenScrollBars(0);
            // TODO: modify the WebView.PrivateAccess to provide a return value.
            return true;
        }

        @Override
        public boolean super_awakenScrollBars(int arg0, boolean arg1) {
            // TODO: need method on WebView.PrivateAccess?
            UnimplementedWebViewApi.invoke();
            return false;
        }

        @Override
        public void onScrollChanged(int l, int t, int oldl, int oldt) {
//            mWebViewPrivate.setScrollXRaw(l);
//            mWebViewPrivate.setScrollYRaw(t);
            mWebViewPrivate.onScrollChanged(l, t, oldl, oldt);
        }

        @Override
        public void overScrollBy(int deltaX, int deltaY,
            int scrollX, int scrollY,
            int scrollRangeX, int scrollRangeY,
            int maxOverScrollX, int maxOverScrollY,
            boolean isTouchEvent) {
            mWebViewPrivate.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                    scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
        }

        @Override
        public void super_scrollTo(int scrollX, int scrollY) {
            mWebViewPrivate.super_scrollTo(scrollX, scrollY);
        }

        @Override
        public void setMeasuredDimension(int measuredWidth, int measuredHeight) {
            mWebViewPrivate.setMeasuredDimension(measuredWidth, measuredHeight);
        }

        @Override
        public boolean requestDrawGL(Canvas canvas) {
            if (mGLfunctor == null) {
                mGLfunctor = new DrawGLFunctor(mAwContents.getAwDrawGLViewContext());
            }
            return mGLfunctor.requestDrawGL(canvas, mWebView);
        }
    }
}

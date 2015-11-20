package com.mogoweb.chrome.shell.test;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.chromium.content.browser.LoadUrlParams;
import org.chromium.content.browser.test.util.CallbackHelper;
import org.chromium.content.browser.test.util.Criteria;
import org.chromium.content.browser.test.util.CriteriaHelper;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.text.TextUtils;

import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.shell.ShellActivity;

public class WebViewShellTestBase extends
        ActivityInstrumentationTestCase2<ShellActivity> {

    protected WebView mWebView;

    /** The maximum time the waitForWebViewToBeDoneLoading method will wait. */
    private static final long WAIT_FOR_WEBVIEW_LOADING_TIMEOUT = 10000;

    protected final static int WAIT_TIMEOUT_SECONDS = 60;
    private static final int CHECK_INTERVAL = 100;

    WebViewShellTestBase() {
        super(ShellActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mWebView = getActivity().getWebView();
    }

    /**
     * Starts the WebViewShell activity and loads the given URL.
     */
    protected ShellActivity launchWebViewShellWithUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (url != null) intent.setData(Uri.parse(url));
        intent.setComponent(new ComponentName(getInstrumentation().getTargetContext(),
                ShellActivity.class));
        setActivityIntent(intent);
        return getActivity();
    }

    /**
     * Waits for the Active shell to finish loading.  This times out after
     * WAIT_FOR_ACTIVE_SHELL_LOADING_TIMEOUT milliseconds and it shouldn't be used for long
     * loading pages. Instead it should be used more for test initialization. The proper way
     * to wait is to use a TestCallbackHelperContainer after the initial load is completed.
     * @return Whether or not the Shell was actually finished loading.
     * @throws Exception
     */
    protected boolean waitForActiveShellToBeDoneLoading() throws InterruptedException {
        final ShellActivity activity = getActivity();

        // Wait for the Content Shell to be initialized.
        return CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                try {
                    final AtomicBoolean isLoaded = new AtomicBoolean(false);
                    runTestOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            WebView webview = activity.getWebView();
                            if (webview != null) {
                                isLoaded.set(!activity.isLoading()
                                        && !TextUtils.isEmpty(webview.getUrl()));
                            } else {
                                isLoaded.set(false);
                            }
                        }
                    });

                    return isLoaded.get();
                } catch (Throwable e) {
                    return false;
                }
            }
        }, WAIT_FOR_WEBVIEW_LOADING_TIMEOUT, CriteriaHelper.DEFAULT_POLLING_INTERVAL);
    }

    /**
     * Runs a {@link Callable} on the main thread, blocking until it is
     * complete, and returns the result. Calls
     * {@link Instrumentation#waitForIdleSync()} first to help avoid certain
     * race conditions.
     *
     * @param <R> Type of result to return
     */
    public <R> R runTestOnUiThreadAndGetResult(Callable<R> callable)
            throws Exception {
        FutureTask<R> task = new FutureTask<R>(callable);
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(task);
        return task.get();
    }

    /**
     * Loads url on the UI thread and blocks until onPageFinished is called.
     */
    protected void loadUrlSync(final WebView webview,
                               CallbackHelper onPageFinishedHelper,
                               final String url) throws Exception {
        int currentCallCount = onPageFinishedHelper.getCallCount();
        loadUrlAsync(webview, url);
        onPageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    protected void loadUrlSyncAndExpectError(final WebView webview,
            CallbackHelper onPageFinishedHelper,
            CallbackHelper onReceivedErrorHelper,
            final String url) throws Exception {
        int onErrorCallCount = onReceivedErrorHelper.getCallCount();
        int onFinishedCallCount = onPageFinishedHelper.getCallCount();
        loadUrlAsync(webview, url);
        onReceivedErrorHelper.waitForCallback(onErrorCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
        onPageFinishedHelper.waitForCallback(onFinishedCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    /**
     * Loads url on the UI thread but does not block.
     */
    protected void loadUrlAsync(final WebView webview,
                                final String url) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                webview.loadUrl(url);
            }
        });
    }

//    /**
//     * Posts url on the UI thread and blocks until onPageFinished is called.
//     */
//    protected void postUrlSync(final WebView webview,
//            CallbackHelper onPageFinishedHelper, final String url,
//            byte[] postData) throws Exception {
//        int currentCallCount = onPageFinishedHelper.getCallCount();
//        postUrlAsync(webview, url, postData);
//        onPageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
//                TimeUnit.SECONDS);
//    }
//
//    /**
//     * Loads url on the UI thread but does not block.
//     */
//    protected void postUrlAsync(final WebView webview,
//            final String url, byte[] postData) throws Exception {
//        class PostUrl implements Runnable {
//            byte[] mPostData;
//            public PostUrl(byte[] postData) {
//                mPostData = postData;
//            }
//            @Override
//            public void run() {
//                webview.loadUrl(LoadUrlParams.createLoadHttpPostParams(url,
//                        mPostData));
//            }
//        }
//        getInstrumentation().runOnMainSync(new PostUrl(postData));
//    }
//
//    /**
//     * Loads data on the UI thread and blocks until onPageFinished is called.
//     */
//    protected void loadDataSync(final WebView webview,
//                                CallbackHelper onPageFinishedHelper,
//                                final String data, final String mimeType,
//                                final boolean isBase64Encoded) throws Exception {
//        int currentCallCount = onPageFinishedHelper.getCallCount();
//        loadDataAsync(webview, data, mimeType, isBase64Encoded);
//        onPageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
//                TimeUnit.SECONDS);
//    }
//
//    protected void loadDataSyncWithCharset(final WebView webview,
//                                           CallbackHelper onPageFinishedHelper,
//                                           final String data, final String mimeType,
//                                           final boolean isBase64Encoded, final String charset)
//            throws Exception {
//        int currentCallCount = onPageFinishedHelper.getCallCount();
//        getInstrumentation().runOnMainSync(new Runnable() {
//            @Override
//            public void run() {
//                webview.loadUrl(LoadUrlParams.createLoadDataParams(
//                        data, mimeType, isBase64Encoded, charset));
//            }
//        });
//        onPageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
//                TimeUnit.SECONDS);
//    }

    /**
     * Loads data on the UI thread but does not block.
     */
    protected void loadDataAsync(final WebView webview, final String data,
                                 final String mimeType, final String encoding)
            throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                webview.loadData(data, mimeType, encoding);
            }
        });
    }
}

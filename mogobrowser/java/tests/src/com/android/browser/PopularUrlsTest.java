/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.mogoweb.browser;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Environment;
import android.provider.Browser;
import android.test.ActivityInstrumentationTestCase2;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.ClientCertRequestHandler;
import android.webkit.DownloadListener;
import android.webkit.HttpAuthHandler;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClassic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * Iterates over a list of URLs from a file and outputs the time to load each.
 */
public class PopularUrlsTest extends ActivityInstrumentationTestCase2<BrowserActivity> {

    private final static String TAG = "PopularUrlsTest";
    private final static String newLine = System.getProperty("line.separator");
    private final static String sInputFile = "popular_urls.txt";
    private final static String sOutputFile = "test_output.txt";
    private final static String sStatusFile = "test_status.txt";
    private final static File sExternalStorage = Environment.getExternalStorageDirectory();

    private final static int PERF_LOOPCOUNT = 10;
    private final static int STABILITY_LOOPCOUNT = 1;
    private final static int PAGE_LOAD_TIMEOUT = 120000; // 2 minutes

    private BrowserActivity mActivity = null;
    private Controller mController = null;
    private Instrumentation mInst = null;
    private CountDownLatch mLatch = new CountDownLatch(1);
    private RunStatus mStatus;
    private boolean pageLoadFinishCalled, pageProgressFull;

    public PopularUrlsTest() {
        super(BrowserActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("about:blank"));
        i.putExtra(Controller.NO_CRASH_RECOVERY, true);
        setActivityIntent(i);
        mActivity = getActivity();
        mController = mActivity.getController();
        mInst = getInstrumentation();
        mInst.waitForIdleSync();

        mStatus = RunStatus.load();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mStatus != null) {
            mStatus.cleanUp();
        }

        super.tearDown();
    }

    BufferedReader getInputStream() throws FileNotFoundException {
        return getInputStream(sInputFile);
    }

    BufferedReader getInputStream(String inputFile) throws FileNotFoundException {
        FileReader fileReader = new FileReader(new File(sExternalStorage, inputFile));
        BufferedReader bufferedReader = new BufferedReader(fileReader);

        return bufferedReader;
    }

    OutputStreamWriter getOutputStream() throws IOException {
        return getOutputStream(sOutputFile);
    }

    OutputStreamWriter getOutputStream(String outputFile) throws IOException {
        return new FileWriter(new File(sExternalStorage, outputFile), mStatus.getIsRecovery());
    }

    /**
     * Gets the browser ready for testing by starting the application
     * and wrapping the WebView's helper clients.
     */
    void setUpBrowser() {
        mInst.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                setupBrowserInternal();
            }
        });
    }

    void setupBrowserInternal() {
        Tab tab = mController.getTabControl().getCurrentTab();
        WebView webView = tab.getWebView();

        webView.setWebChromeClient(new TestWebChromeClient(
                WebViewClassic.fromWebView(webView).getWebChromeClient()) {

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress >= 100) {
                    if (!pageProgressFull) {
                        // void duplicate calls
                        pageProgressFull  = true;
                        if (pageLoadFinishCalled) {
                            //reset latch and move forward only if both indicators are true
                            resetLatch();
                        }
                    }
                }
            }

            /**
             * Dismisses and logs Javascript alerts.
             */
            @Override
            public boolean onJsAlert(WebView view, String url, String message,
                    JsResult result) {
                String logMsg = String.format("JS Alert '%s' received from %s", message, url);
                Log.w(TAG, logMsg);
                result.confirm();

                return true;
            }

            /**
             * Confirms and logs Javascript alerts.
             */
            @Override
            public boolean onJsConfirm(WebView view, String url, String message,
                    JsResult result) {
                String logMsg = String.format("JS Confirmation '%s' received from %s",
                        message, url);
                Log.w(TAG, logMsg);
                result.confirm();

                return true;
            }

            /**
             * Confirms and logs Javascript alerts, providing the default value.
             */
            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                    String defaultValue, JsPromptResult result) {
                String logMsg = String.format("JS Prompt '%s' received from %s; " +
                        "Giving default value '%s'", message, url, defaultValue);
                Log.w(TAG, logMsg);
                result.confirm(defaultValue);

                return true;
            }

            /*
             * Skip the unload confirmation
             */
            @Override
            public boolean onJsBeforeUnload(
                    WebView view, String url, String message, JsResult result) {
                result.confirm();
                return true;
            }
        });

        webView.setWebViewClient(new TestWebViewClient(
                WebViewClassic.fromWebView(webView).getWebViewClient()) {

            /**
             * Bypasses and logs errors.
             */
            @Override
            public void onReceivedError(WebView view, int errorCode,
                    String description, String failingUrl) {
                String message = String.format("Error '%s' (%d) loading url: %s",
                        description, errorCode, failingUrl);
                Log.w(TAG, message);
            }

            /**
             * Ignores and logs SSL errors.
             */
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                    SslError error) {
                Log.w(TAG, "SSL error: " + error);
                handler.proceed();
            }

            /**
             * Ignores and logs SSL client certificate requests.
             */
            @Override
            public void onReceivedClientCertRequest(WebView view, ClientCertRequestHandler handler,
                    String host_and_port) {
                Log.w(TAG, "SSL client certificate request: " + host_and_port);
                handler.cancel();
            }

            /**
             * Ignores http auth with dummy username and password
             */
            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                    String host, String realm) {
                handler.proceed("user", "passwd");
            }

            /* (non-Javadoc)
             * @see com.mogoweb.browser.TestWebViewClient#onPageFinished(android.webkit.WebView, java.lang.String)
             */
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!pageLoadFinishCalled) {
                    pageLoadFinishCalled = true;
                    if (pageProgressFull) {
                        //reset latch and move forward only if both indicators are true
                        resetLatch();
                    }
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                    Log.v(TAG, String.format("suppressing non-http url scheme: %s", url));
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        webView.setDownloadListener(new DownloadListener() {

            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                    String mimetype, long contentLength) {
                Log.v(TAG, String.format("Download request ignored: %s", url));
            }
        });
    }

    void resetLatch() {
        if (mLatch.getCount() != 1) {
            Log.w(TAG, "Expecting latch to be 1, but it's not!");
        } else {
            mLatch.countDown();
        }
    }

    void resetForNewPage() {
        mLatch = new CountDownLatch(1);
        pageLoadFinishCalled = false;
        pageProgressFull = false;
    }

    void waitForLoad() throws InterruptedException {
        boolean timedout = !mLatch.await(PAGE_LOAD_TIMEOUT, TimeUnit.MILLISECONDS);
        if (timedout) {
            Log.w(TAG, "page timeout. trying to stop.");
            // try to stop page load
            mInst.runOnMainSync(new Runnable(){
                public void run() {
                    mController.getTabControl().getCurrentTab().getWebView().stopLoading();
                }
            });
            // try to wait for count down latch again
            timedout = !mLatch.await(5000, TimeUnit.MILLISECONDS);
            if (timedout) {
                throw new RuntimeException("failed to stop timedout site, is browser pegged?");
            }
        }
    }

    private static class RunStatus {
        private File mFile;
        private int iteration;
        private int page;
        private String url;
        private boolean isRecovery;
        private boolean allClear;

        private RunStatus(File file) throws IOException {
            mFile = file;
            FileReader input = null;
            BufferedReader reader = null;
            isRecovery = false;
            allClear = false;
            iteration = 0;
            page = 0;
            try {
                input = new FileReader(mFile);
                isRecovery = true;
                reader = new BufferedReader(input);
                String line = reader.readLine();
                if (line == null)
                    return;
                iteration = Integer.parseInt(line);
                line = reader.readLine();
                if (line == null)
                    return;
                page = Integer.parseInt(line);
            } catch (FileNotFoundException ex) {
                return;
            } catch (NumberFormatException nfe) {
                Log.wtf(TAG, "unexpected data in status file, will start from begining");
                return;
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } finally {
                    if (input != null) {
                        input.close();
                    }
                }
            }
        }

        public static RunStatus load() throws IOException {
            return load(sStatusFile);
        }

        public static RunStatus load(String file) throws IOException {
            return new RunStatus(new File(sExternalStorage, file));
        }

        public void write() throws IOException {
            FileWriter output = null;
            if (mFile.exists()) {
                mFile.delete();
            }
            try {
                output = new FileWriter(mFile);
                output.write(iteration + newLine);
                output.write(page + newLine);
                output.write(url + newLine);
            } finally {
                if (output != null) {
                    output.close();
                }
            }
        }

        public void cleanUp() {
            // only perform cleanup when allClear flag is set
            // i.e. when the test was not interrupted by a Java crash
            if (mFile.exists() && allClear) {
                mFile.delete();
            }
        }

        public void resetPage() {
            page = 0;
        }

        public void incrementPage() {
            ++page;
            allClear = true;
        }

        public void incrementIteration() {
            ++iteration;
        }

        public int getPage() {
            return page;
        }

        public int getIteration() {
            return iteration;
        }

        public boolean getIsRecovery() {
            return isRecovery;
        }

        public void setUrl(String url) {
            this.url = url;
            allClear = false;
        }
    }

    /**
     * Loops over a list of URLs, points the browser to each one, and records the time elapsed.
     *
     * @param input the reader from which to get the URLs.
     * @param writer the writer to which to output the results.
     * @param clearCache determines whether the cache is cleared before loading each page
     * @param loopCount the number of times to loop through the list of pages
     * @throws IOException unable to read from input or write to writer.
     * @throws InterruptedException the thread was interrupted waiting for the page to load.
     */
    void loopUrls(BufferedReader input, OutputStreamWriter writer,
            boolean clearCache, int loopCount)
            throws IOException, InterruptedException {
        Tab tab = mController.getTabControl().getCurrentTab();
        WebView webView = tab.getWebView();

        List<String> pages = new LinkedList<String>();

        String page;
        while (null != (page = input.readLine())) {
            if (!TextUtils.isEmpty(page)) {
                pages.add(page);
            }
        }

        Iterator<String> iterator = pages.iterator();
        for (int i = 0; i < mStatus.getPage(); ++i) {
            iterator.next();
        }

        if (mStatus.getIsRecovery()) {
            Log.e(TAG, "Recovering after crash: " + iterator.next());
            mStatus.incrementPage();
        }

        while (mStatus.getIteration() < loopCount) {
            if (clearCache) {
                clearCacheUiThread(webView, true);
            }
            while(iterator.hasNext()) {
                page = iterator.next();
                mStatus.setUrl(page);
                mStatus.write();
                Log.i(TAG, "start: " + page);
                Uri uri = Uri.parse(page);
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.putExtra(Browser.EXTRA_APPLICATION_ID,
                    getInstrumentation().getTargetContext().getPackageName());

                long startTime = System.currentTimeMillis();
                resetForNewPage();
                mInst.runOnMainSync(new Runnable() {

                    public void run() {
                        mActivity.onNewIntent(intent);
                    }

                });
                waitForLoad();
                long stopTime = System.currentTimeMillis();

                String url = getUrlUiThread(webView);
                Log.i(TAG, "finish: " + url);

                if (writer != null) {
                    writer.write(page + "|" + (stopTime - startTime) + newLine);
                    writer.flush();
                }

                mStatus.incrementPage();
            }
            mStatus.incrementIteration();
            mStatus.resetPage();
            iterator = pages.iterator();
        }
    }

    public void testLoadPerformance() throws IOException, InterruptedException {
        setUpBrowser();

        OutputStreamWriter writer = getOutputStream();
        try {
            BufferedReader bufferedReader = getInputStream();
            try {
                loopUrls(bufferedReader, writer, true, PERF_LOOPCOUNT);
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, fnfe.getMessage(), fnfe);
            fail("Test environment not setup correctly");
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public void testStability() throws IOException, InterruptedException {
        setUpBrowser();

        BufferedReader bufferedReader = getInputStream();
        try {
            loopUrls(bufferedReader, null, true, STABILITY_LOOPCOUNT);
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, fnfe.getMessage(), fnfe);
            fail("Test environment not setup correctly");
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
    }

    private void clearCacheUiThread(final WebView webView, final boolean includeDiskFiles) {
        Runnable runner = new Runnable() {

            @Override
            public void run() {
                webView.clearCache(includeDiskFiles);
            }
        };
        getInstrumentation().runOnMainSync(runner);
    }

    private String getUrlUiThread(final WebView webView) {
        WebViewUrlGetter urlGetter = new WebViewUrlGetter(webView);
        getInstrumentation().runOnMainSync(urlGetter);
        return urlGetter.getUrl();
    }

    private class WebViewUrlGetter implements Runnable {

        private WebView mWebView;
        private String mUrl;

        public WebViewUrlGetter(WebView webView) {
            mWebView = webView;
        }

        @Override
        public void run() {
                mUrl = null;
                mUrl = mWebView.getUrl();
        }

        public String getUrl() {
            if (mUrl != null) {
                return mUrl;
            } else
                throw new IllegalStateException("url has not been fetched yet");
        }
    }
}

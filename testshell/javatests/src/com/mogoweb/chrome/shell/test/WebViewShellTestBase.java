package com.mogoweb.chrome.shell.test;

import java.util.concurrent.atomic.AtomicBoolean;

import org.chromium.content.browser.test.util.Criteria;
import org.chromium.content.browser.test.util.CriteriaHelper;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.text.TextUtils;

import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.shell.ShellActivity;

public class WebViewShellTestBase extends
        ActivityInstrumentationTestCase2<ShellActivity> {

    /** The maximum time the waitForWebViewToBeDoneLoading method will wait. */
    private static final long WAIT_FOR_WEBVIEW_LOADING_TIMEOUT = 10000;

    WebViewShellTestBase() {
        super(ShellActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
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
}

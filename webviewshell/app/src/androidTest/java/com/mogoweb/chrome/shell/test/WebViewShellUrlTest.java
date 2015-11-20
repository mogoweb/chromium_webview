package com.mogoweb.chrome.shell.test;

import android.test.suitebuilder.annotation.SmallTest;

import com.mogoweb.chrome.shell.ShellActivity;

public class WebViewShellUrlTest extends WebViewShellTestBase {
    // URL used for base tests.
    private static final String URL = "data:text";

    @SmallTest
    public void testBaseStartup() throws InterruptedException {
        ShellActivity activity = launchWebViewShellWithUrl(URL);
        waitForActiveShellToBeDoneLoading();

        // Make sure the activity was created as expected.
        assertNotNull(activity);
    }
}

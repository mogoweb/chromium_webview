/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Browser;
import android.test.ActivityInstrumentationTestCase2;
import android.text.TextUtils;
import android.webkit.WebView;

public class IntentHandlerTests extends ActivityInstrumentationTestCase2<BrowserActivity> {

    // How long to wait to receive onPageStarted
    static final int START_LOAD_TIMEOUT = 20000; // ms
    static final int POLL_INTERVAL = 50; // ms
    boolean mHasStarted = false;

    public IntentHandlerTests() {
        super(BrowserActivity.class);
    }

    public void testSwitchToTabWithUrl() throws Throwable {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://google.com/"));
        sendIntent(intent);
        Controller controller = getActivity().getController();
        Tab tabGoogle = controller.getCurrentTab();
        assertNotNull("Current tab (google.com", tabGoogle);
        assertEquals("http://google.com/", tabGoogle.getOriginalUrl());
        assertEquals(1, controller.getTabs().size());
        intent.setData(Uri.parse("http://maps.google.com/"));
        sendIntent(intent);
        Tab tabMaps = controller.getCurrentTab();
        assertNotSame(tabGoogle, tabMaps);
        assertNotNull("Current tab (maps.google.com)", tabMaps);
        assertEquals(2, controller.getTabs().size());
        intent.setData(Uri.parse("http://google.com/"));
        sendIntent(intent);
        assertEquals(tabGoogle, controller.getCurrentTab());
        assertEquals(2, controller.getTabs().size());
    }

    public void testShortcut() throws Throwable {
        Intent intent = BookmarkUtils.createShortcutIntent("http://google.com/");
        sendIntent(intent);
        Controller controller = getActivity().getController();
        Tab tabGoogle = controller.getCurrentTab();
        assertEquals("http://google.com/", tabGoogle.getOriginalUrl());
        assertEquals(1, controller.getTabs().size());
        sendIntent(intent);
        assertEquals(1, controller.getTabs().size());
        assertEquals(tabGoogle, controller.getCurrentTab());
        directlyLoadUrl(tabGoogle, "http://maps.google.com/");
        sendIntent(intent);
        if (BrowserActivity.isTablet(getActivity())) {
            assertEquals(2, controller.getTabs().size());
            assertNotSame(tabGoogle, controller.getCurrentTab());
            assertEquals("http://maps.google.com/", tabGoogle.getOriginalUrl());
            Tab currentTab = controller.getCurrentTab();
            assertEquals("http://google.com/", currentTab.getOriginalUrl());
        } else {
            assertEquals(1, controller.getTabs().size());
            assertEquals(tabGoogle, controller.getCurrentTab());
            assertEquals("http://google.com/", tabGoogle.getOriginalUrl());
        }
    }

    public void testApplication() throws Throwable {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://google.com/"));
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, getClass().getName());
        sendIntent(intent);
        Controller controller = getActivity().getController();
        Tab tabGoogle = controller.getCurrentTab();
        assertNotNull("Current tab (google.com", tabGoogle);
        assertEquals("http://google.com/", tabGoogle.getOriginalUrl());
        assertEquals(1, controller.getTabs().size());
        intent.setData(Uri.parse("http://maps.google.com/"));
        sendIntent(intent);
        Tab tabMaps = controller.getCurrentTab();
        assertEquals("http://maps.google.com/", tabMaps.getOriginalUrl());
        if (BrowserActivity.isTablet(getActivity())) {
            assertEquals(2, controller.getTabs().size());
            assertNotSame(tabGoogle, tabMaps);
            assertEquals("http://google.com/", tabGoogle.getOriginalUrl());
        } else {
            assertEquals(1, controller.getTabs().size());
            assertEquals(tabGoogle, tabMaps);
        }
    }

    /**
     * Simulate clicking a link by loading a URL directly on the WebView,
     * bypassing Tab, Controller, etc..
     * @throws Throwable
     */
    private void directlyLoadUrl(final Tab tab, final String url) throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                WebView web = tab.getWebView();
                web.loadUrl(url);
            }
        });
        waitForLoadStart(tab, url);
    }

    void waitForLoadStart(final Tab tab, final String url) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!TextUtils.equals(tab.getOriginalUrl(), url)) {
            if (start + START_LOAD_TIMEOUT < System.currentTimeMillis()) {
                throw new RuntimeException("Didn't receive onPageStarted!");
            }
            Thread.sleep(POLL_INTERVAL);
        }
    }

    private void sendIntent(final Intent intent) throws Throwable {
        sendIntent(intent, true);
    }

    private void sendIntent(final Intent intent, boolean waitForLoadStart) throws Throwable {
        if (!mHasStarted) {
            // Prevent crash recovery from happening
            intent.putExtra(Controller.NO_CRASH_RECOVERY, true);
            setActivityIntent(intent);
            getActivity();
        } else {
            final Activity activity = getActivity();
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getInstrumentation().callActivityOnNewIntent(activity, intent);
                }
            });
        }
        if (waitForLoadStart) {
            String url = intent.getDataString();
            Tab tab = getActivity().getController().getCurrentTab();
            waitForLoadStart(tab, url);
        }
    }

    @Override
    public BrowserActivity getActivity() {
        mHasStarted = true;
        return super.getActivity();
    }
}

// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome;

import org.chromium.base.PathUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.content.app.LibraryLoader;
import org.chromium.content.browser.BrowserStartupController;
import org.chromium.content.browser.DeviceUtils;
import org.chromium.content.browser.ResourceExtractor;
import org.chromium.content.common.CommandLine;
import org.chromium.content.common.ProcessInitException;

import android.content.Context;

/**
 * Chromium setup chores.
 */
public class ChromeInitializer {
    private static final String PRIVATE_DATA_DIRECTORY_SUFFIX = "chromiumtestshell";
    private static final String[] CHROME_MANDATORY_PAKS = {
        "en-US.pak",
        "resources.pak",
        "chrome_100_percent.pak",
    };
    private static final String COMMAND_LINE_FILE =
            "/data/local/tmp/chromium-testshell-command-line";

    /** Ensures that initialize() is only called once. */
    private static boolean sInitializeCalled = false;

    /**
     * The entry point to the initialization process.
     *
     * This is called by {@link ChromeView#initialize(Context)}.
     *
     * @param context Android context for the application using ChromeView
     */
    public static void initialize(Context context) {
        if (sInitializeCalled) {
            return;
        }
        sInitializeCalled = true;

        // Initialization lifted from
        //     chromium/src/chrome/android/testshell/java/src/org/chromium/chrome/testshell/ChromiumTestShellApplication.java

        ResourceExtractor.setMandatoryPaksToExtract(CHROME_MANDATORY_PAKS);
        PathUtils.setPrivateDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX);

        // Initialization lifted from
        //     chromium/src/chrome/android/testshell/java/src/org/chromium/chrome/testshell/ChromiumTestShellActivity.java
        CommandLine.initFromFile(COMMAND_LINE_FILE);
        DeviceUtils.addDeviceSpecificUserAgentSwitch(context);

        // Initialization lifted from
        //     chromium/src/android_webview/test/shell/src/org/chromium/android_webview/shell/AwShellApplication

        ResourceExtractor.setExtractImplicitLocaleForTesting(false);
        loadLibrary();
        start(context);
    }

    /**
     * Loads the native library, and performs basic static construction of objects needed
     * to run webview in this process. Does not create threads; safe to call from zygote.
     * Note: it is up to the caller to ensure this is only called once.
     */
    private static void loadLibrary() {
        try {
            LibraryLoader.loadNow();
        } catch (ProcessInitException e) {
            throw new RuntimeException("Cannot load WebView", e);
        }
    }

    /**
     * Starts the chromium browser process running within this process. Creates threads
     * and performs other per-app resource allocations; must not be called from zygote.
     * Note: it is up to the caller to ensure this is only called once.
     * @param context The Android application context
     */
    private static void start(final Context context) {
        // We must post to the UI thread to cover the case that the user
        // has invoked Chromium startup by using the (thread-safe)
        // CookieManager rather than creating a WebView.
        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                if( !BrowserStartupController.get(context).startBrowserProcessesSync(
                            BrowserStartupController.MAX_RENDERERS_SINGLE_PROCESS)) {
                    throw new RuntimeException("Cannot initialize WebView");
                }
            }
        });
    }
}

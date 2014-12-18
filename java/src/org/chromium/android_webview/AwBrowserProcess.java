// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.content.Context;
import android.util.Log;

import org.chromium.base.PathUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.content.browser.BrowserStartupController;
import org.chromium.media.MediaDrmBridge;

import java.util.UUID;

/**
 * Wrapper for the steps needed to initialize the java and native sides of webview chromium.
 */
public abstract class AwBrowserProcess {
    private static final String PRIVATE_DATA_DIRECTORY_SUFFIX = "webview";
    private static final String TAG = "AwBrowserProcess";

    /**
     * Loads the native library, and performs basic static construction of objects needed
     * to run webview in this process. Does not create threads; safe to call from zygote.
     * Note: it is up to the caller to ensure this is only called once.
     */
    public static void loadLibrary() {
        PathUtils.setPrivateDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX);
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
    public static void start(final Context context) {
        // We must post to the UI thread to cover the case that the user
        // has invoked Chromium startup by using the (thread-safe)
        // CookieManager rather than creating a WebView.
        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                try {
                    BrowserStartupController.get(context).startBrowserProcessesSync(
                                BrowserStartupController.MAX_RENDERERS_SINGLE_PROCESS);
                    initializePlatformKeySystem();
                } catch (ProcessInitException e) {
                    throw new RuntimeException("Cannot initialize WebView", e);
                }
            }
        });
    }

    private static void initializePlatformKeySystem() {
        String[] mappings = AwResource.getConfigKeySystemUuidMapping();
        for (String mapping : mappings) {
            try {
                String fragments[] = mapping.split(",");
                String keySystem = fragments[0].trim();
                UUID uuid = UUID.fromString(fragments[1]);
                MediaDrmBridge.addKeySystemUuidMapping(keySystem, uuid);
            } catch (java.lang.RuntimeException e) {
                Log.e(TAG, "Can't parse key-system mapping: " + mapping);
            }
        }
    }
}

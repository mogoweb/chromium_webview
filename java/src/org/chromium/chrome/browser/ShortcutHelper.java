// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.chrome.browser.BookmarkUtils;

import java.util.UUID;

/**
 * This is a helper class to create shortcuts on the Android home screen.
 */
public class ShortcutHelper {
    public static final String EXTRA_ID = "org.chromium.chrome.browser.webapp_id";
    public static final String EXTRA_MAC = "org.chromium.chrome.browser.webapp_mac";
    public static final String EXTRA_URL = "org.chromium.chrome.browser.webapp_url";

    private static String sFullScreenAction;

    /**
     * Sets the class names used when launching the shortcuts.
     * @param browserName Class name of the browser Activity.
     * @param fullScreenName Class name of the fullscreen Activity.
     */
    public static void setFullScreenAction(String fullScreenAction) {
        sFullScreenAction = fullScreenAction;
    }

    /**
     * Adds a shortcut for the current Tab.
     * @param appContext The application context.
     * @param tab Tab to create a shortcut for.
     * @param userRequestedTitle Updated title for the shortcut.
     */
    public static void addShortcut(Context appContext, TabBase tab, String userRequestedTitle) {
        if (TextUtils.isEmpty(sFullScreenAction)) {
            Log.e("ShortcutHelper", "ShortcutHelper is uninitialized.  Aborting.");
            return;
        }
        ActivityManager am = (ActivityManager) appContext.getSystemService(
                Context.ACTIVITY_SERVICE);
        nativeAddShortcut(tab.getNativePtr(), userRequestedTitle, am.getLauncherLargeIconSize());
    }

    /**
     * Called when we have to fire an Intent to add a shortcut to the homescreen.
     * If the webpage indicated that it was capable of functioning as a webapp, it is added as a
     * shortcut to a webapp Activity rather than as a general bookmark. User is sent to the
     * homescreen as soon as the shortcut is created.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private static void addShortcut(Context context, String url, String title, Bitmap favicon,
            int red, int green, int blue, boolean isWebappCapable) {
        assert sFullScreenAction != null;

        Intent shortcutIntent = null;
        if (isWebappCapable) {
            // Add the shortcut as a launcher icon for a full-screen Activity.
            shortcutIntent = new Intent();
            shortcutIntent.setAction(sFullScreenAction);
            shortcutIntent.putExtra(EXTRA_URL, url);
            shortcutIntent.putExtra(EXTRA_ID, UUID.randomUUID().toString());

            // The only reason we convert to a String here is because Android inexplicably eats a
            // byte[] when adding the shortcut -- the Bundle received by the launched Activity even
            // lacks the key for the extra.
            byte[] mac = WebappAuthenticator.getMacForUrl(context, url);
            String encodedMac = Base64.encodeToString(mac, Base64.DEFAULT);
            shortcutIntent.putExtra(EXTRA_MAC, encodedMac);
        } else {
            // Add the shortcut as a launcher icon to open in the browser Activity.
            shortcutIntent = BookmarkUtils.createShortcutIntent(context, url);
        }

        shortcutIntent.setPackage(context.getPackageName());
        context.sendBroadcast(BookmarkUtils.createAddToHomeIntent(context, shortcutIntent, title,
                favicon, red, green, blue));

        // User is sent to the homescreen as soon as the shortcut is created.
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(homeIntent);
    }

    private static native void nativeAddShortcut(int tabAndroidPtr, String userRequestedTitle,
            int launcherLargeIconSize);
}

// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;

import org.chromium.content.browser.SelectActionModeCallback.ActionHandler;

import java.net.URISyntaxException;

/**
 *  Main callback class used by ContentView.
 *
 *  This contains the superset of callbacks required to implement the browser UI and the callbacks
 *  required to implement the WebView API.
 *  The memory and reference ownership of this class is unusual - see the .cc file and ContentView
 *  for more details.
 *
 *  TODO(mkosiba): Rid this guy of default implementations. This class is used by both WebView and
 *  the browser and we don't want a the browser-specific default implementation to accidentally leak
 *  over to WebView.
 */
public class ContentViewClient {
    // Tag used for logging.
    private static final String TAG = "ContentViewClient";

    public void onUpdateTitle(String title) {
    }

    /**
     * Called whenever the background color of the page changes as notified by WebKit.
     * @param color The new ARGB color of the page background.
     */
    public void onBackgroundColorChanged(int color) {
    }

    /**
     * Notifies the client that the position of the top controls has changed.
     * @param topControlsOffsetYPix The Y offset of the top controls in physical pixels.
     * @param contentOffsetYPix The Y offset of the content in physical pixels.
     * @param overdrawBottomHeightPix The overdraw height.
     */
    public void onOffsetsForFullscreenChanged(
            float topControlsOffsetYPix, float contentOffsetYPix, float overdrawBottomHeightPix) {
    }

    public boolean shouldOverrideKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (!shouldPropagateKey(keyCode)) return true;

        // We also have to intercept some shortcuts before we send them to the ContentView.
        if (event.isCtrlPressed() && (
                keyCode == KeyEvent.KEYCODE_TAB ||
                keyCode == KeyEvent.KEYCODE_W ||
                keyCode == KeyEvent.KEYCODE_F4)) {
            return true;
        }

        return false;
    }

    /**
     * Called when an ImeEvent is sent to the page. Can be used to know when some text is entered
     * in a page.
     */
    public void onImeEvent() {
    }

    /**
     * Notified when a change to the IME was requested.
     *
     * @param requestShow Whether the IME was requested to be shown (may already be showing
     *                    though).
     */
    public void onImeStateChangeRequested(boolean requestShow) {
    }

    /**
     * Returns an ActionMode.Callback for in-page selection.
     */
    public ActionMode.Callback getSelectActionModeCallback(
            Context context, ActionHandler actionHandler, boolean incognito) {
        return new SelectActionModeCallback(context, actionHandler, incognito);
    }

    /**
     * Called when the contextual ActionBar is shown.
     */
    public void onContextualActionBarShown() {
    }

    /**
     * Called when the contextual ActionBar is hidden.
     */
    public void onContextualActionBarHidden() {
    }

    /**
     * Perform a search on {@code searchQuery}.  This method is only called if
     * {@link #doesPerformWebSearch()} returns {@code true}.
     * @param searchQuery The string to search for.
     */
    public void performWebSearch(String searchQuery) {
    }

    /**
     * If this returns {@code true} contextual web search attempts will be forwarded to
     * {@link #performWebSearch(String)}.
     * @return {@code true} iff this {@link ContentViewClient} wants to consume web search queries
     *         and override the default intent behavior.
     */
    public boolean doesPerformWebSearch() {
        return false;
    }

    /**
     * Notification that the selection has changed.
     * @param selection The newly established selection.
     */
    public void onSelectionChanged(String selection) {
    }

    /**
     * Called when a new content intent is requested to be started.
     */
    public void onStartContentIntent(Context context, String intentUrl) {
        Intent intent;
        // Perform generic parsing of the URI to turn it into an Intent.
        try {
            intent = Intent.parseUri(intentUrl, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException ex) {
            Log.w(TAG, "Bad URI " + intentUrl + ": " + ex.getMessage());
            return;
        }

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.w(TAG, "No application can handle " + intentUrl);
        }
    }

    public ContentVideoViewClient getContentVideoViewClient() {
        return null;
    }

    /**
     * Called when BrowserMediaPlayerManager wants to load a media resource.
     * @param url the URL of media resource to load.
     * @return true to prevent the resource from being loaded.
     */
    public boolean shouldBlockMediaRequest(String url) {
        return false;
    }

    /**
     * Check whether a key should be propagated to the embedder or not.
     * We need to send almost every key to Blink. However:
     * 1. We don't want to block the device on the renderer for
     * some keys like menu, home, call.
     * 2. There are no WebKit equivalents for some of these keys
     * (see app/keyboard_codes_win.h)
     * Note that these are not the same set as KeyEvent.isSystemKey:
     * for instance, AKEYCODE_MEDIA_* will be dispatched to webkit*.
     */
    public static boolean shouldPropagateKey(int keyCode) {
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
            return false;
        }
        return true;
    }
}

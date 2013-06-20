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

import android.os.Looper;
import android.util.Log;

import com.mogoweb.chrome.WebView;

/**
 * Centralised point for controlling WebView timers pausing and resuming.
 *
 * All methods on this class should only be called from the UI thread.
 */
public class WebViewTimersControl {

    private static final boolean LOGD_ENABLED = com.mogoweb.browser.Browser.LOGD_ENABLED;
    private static final String LOGTAG = "WebViewTimersControl";

    private static WebViewTimersControl sInstance;

    private boolean mBrowserActive;
    private boolean mPrerenderActive;

    /**
     * Get the static instance. Must be called from UI thread.
     */
    public static WebViewTimersControl getInstance() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("WebViewTimersControl.get() called on wrong thread");
        }
        if (sInstance == null) {
            sInstance = new WebViewTimersControl();
        }
        return sInstance;
    }

    private WebViewTimersControl() {
    }

    private void resumeTimers(WebView wv) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "Resuming webview timers, view=" + wv);
        if (wv != null) {
            wv.resumeTimers();
        }
    }

    private void maybePauseTimers(WebView wv) {
        if (!mBrowserActive && !mPrerenderActive && wv != null) {
            if (LOGD_ENABLED) Log.d(LOGTAG, "Pausing webview timers, view=" + wv);
            wv.pauseTimers();
        }
    }

    public void onBrowserActivityResume(WebView wv) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "onBrowserActivityResume");
        mBrowserActive = true;
        resumeTimers(wv);
    }

    public void onBrowserActivityPause(WebView wv) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "onBrowserActivityPause");
        mBrowserActive = false;
        maybePauseTimers(wv);
    }

    public void onPrerenderStart(WebView wv) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "onPrerenderStart");
        mPrerenderActive = true;
        resumeTimers(wv);
    }

    public void onPrerenderDone(WebView wv) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "onPrerenderDone");
        mPrerenderActive = false;
        maybePauseTimers(wv);
    }

}

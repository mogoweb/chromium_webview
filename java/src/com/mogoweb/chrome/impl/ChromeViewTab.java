// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.impl;

import android.content.Context;
import android.text.TextUtils;

import org.chromium.chrome.browser.TabBase;
import org.chromium.chrome.browser.infobar.AutoLoginProcessor;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.LoadUrlParams;
import org.chromium.content.common.CleanupReference;
import org.chromium.ui.WindowAndroid;

/**
 * ChromeView's implementation of a tab. This mirrors how Chrome for Android subclasses
 * and extends {@link TabBase}.
 */
public class ChromeViewTab extends TabBase {
    private int mNativeChromeViewTab;

    private CleanupReference mCleanupReference;

    // Tab state
    private boolean mIsLoading;

    /**
     * @param context  The Context the view is running in.
     * @param window   The WindowAndroid should represent this tab.
     */
    public ChromeViewTab(Context context, WindowAndroid window) {
        super(false, context, window);
        initialize();
        initContentView();
    }

    @Override
    public void initialize() {
        super.initialize();

        mNativeChromeViewTab = nativeInit();
        mCleanupReference = new CleanupReference(this, new DestroyRunnable(mNativeChromeViewTab));
    }

    @Override
    public void destroy() {
        super.destroy();

        if (mNativeChromeViewTab != 0) {
            mCleanupReference.cleanupNow();
            mNativeChromeViewTab = 0;
        }
    }

    /**
     * @return Whether or not the tab is currently loading.
     */
    public boolean isLoading() {
        return mIsLoading;
    }

    /**
     * Navigates this Tab's {@link ContentView} to a sanitized version of {@code url}.
     * @param url The potentially unsanitized URL to navigate to.
     * @param postData Optional data to be sent via POST.
     */
    public void loadUrlWithSanitization(String url, byte[] postData) {
        if (url == null) return;

        // Sanitize the URL.
        url = nativeFixupUrl(mNativeChromeViewTab, url);

        // Invalid URLs will just return empty.
        if (TextUtils.isEmpty(url)) return;

        ContentView contentView = getContentView();
        if (TextUtils.equals(url, contentView.getUrl())) {
            contentView.reload();
        } else {
            if (postData == null) {
                contentView.loadUrl(new LoadUrlParams(url));
            } else {
                contentView.loadUrl(LoadUrlParams.createLoadHttpPostParams(url, postData));
            }
        }
    }

    /**
     * Navigates this Tab's {@link ContentView} to a sanitized version of {@code url}.
     * @param url The potentially unsanitized URL to navigate to.
     */
    public void loadUrlWithSanitization(String url) {
        loadUrlWithSanitization(url, null);
    }

    @Override
    protected TabBaseChromeWebContentsDelegateAndroid createWebContentsDelegate() {
        return new ChromeViewTabBaseChromeWebContentsDelegateAndroid();
    }

    private static final class DestroyRunnable implements Runnable {
        private final int mNativeChromeViewTab;
        private DestroyRunnable(int nativeChromeViewTab) {
            mNativeChromeViewTab = nativeChromeViewTab;
        }
        @Override
        public void run() {
            nativeDestroy(mNativeChromeViewTab);
        }
    }

    @Override
    protected AutoLoginProcessor createAutoLoginProcessor() {
       return new AutoLoginProcessor() {
           @Override
           public void processAutoLoginResult(String accountName,
                   String authToken, boolean success, String result) {
               getInfoBarContainer().processAutoLogin(accountName, authToken,
                       success, result);
           }
       };
    }


    private class ChromeViewTabBaseChromeWebContentsDelegateAndroid
            extends TabBaseChromeWebContentsDelegateAndroid {
        @Override
        public void onLoadStarted() {
            mIsLoading = true;
        }

        @Override
        public void onLoadStopped() {
            mIsLoading = false;
        }
    }

    private native int nativeInit();
    private static native void nativeDestroy(int nativeChromeViewTab);
    private native String nativeFixupUrl(int nativeChromeViewTab, String url);
}
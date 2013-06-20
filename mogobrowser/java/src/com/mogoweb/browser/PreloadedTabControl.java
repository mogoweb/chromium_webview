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

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Class to manage the controlling of preloaded tab.
 */
public class PreloadedTabControl {
    private static final boolean LOGD_ENABLED = com.mogoweb.browser.Browser.LOGD_ENABLED;
    private static final String LOGTAG = "PreloadedTabControl";

    final Tab mTab;
    private String mLastQuery;
    private boolean mDestroyed;

    public PreloadedTabControl(Tab t) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "PreloadedTabControl.<init>");
        mTab = t;
    }

    public void setQuery(String query) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "Cannot set query: no searchbox interface");
    }

    public boolean searchBoxSubmit(final String query,
            final String fallbackUrl, final Map<String, String> fallbackHeaders) {
        return false;
    }

    public void searchBoxCancel() {
    }

    public void loadUrlIfChanged(String url, Map<String, String> headers) {
        String currentUrl = mTab.getUrl();
        if (!TextUtils.isEmpty(currentUrl)) {
            try {
                // remove fragment:
                currentUrl = Uri.parse(currentUrl).buildUpon().fragment(null).build().toString();
            } catch (UnsupportedOperationException e) {
                // carry on
            }
        }
        if (LOGD_ENABLED) Log.d(LOGTAG, "loadUrlIfChanged\nnew: " + url + "\nold: " +currentUrl);
        if (!TextUtils.equals(url, currentUrl)) {
            loadUrl(url, headers);
        }
    }

    public void loadUrl(String url, Map<String, String> headers) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "Preloading " + url);
        mTab.loadUrl(url, headers);
    }

    public void destroy() {
        if (LOGD_ENABLED) Log.d(LOGTAG, "PreloadedTabControl.destroy");
        mDestroyed = true;
        mTab.destroy();
    }

    public Tab getTab() {
        return mTab;
    }

}

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

package com.mogoweb.browser.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.mogoweb.browser.R;
import com.mogoweb.chrome.WebView;

public abstract class WebViewPreview extends Preference
        implements OnSharedPreferenceChangeListener {

    protected WebView mWebView;

    public WebViewPreview(
            Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public WebViewPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WebViewPreview(Context context) {
        super(context);
        init(context);
    }

    protected void init(Context context) {
        setLayoutResource(R.layout.webview_preview);
    }

    protected abstract void updatePreview(boolean forceReload);

    protected void setupWebView(WebView view) {}

    @Override
    protected View onCreateView(ViewGroup parent) {
        View root = super.onCreateView(parent);
        WebView webView = (WebView) root.findViewById(R.id.webview);
        // Tell WebView to really, truly ignore all touch events. No, seriously,
        // ignore them all. And don't show scrollbars.
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.setClickable(false);
        webView.setLongClickable(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        setupWebView(webView);
        return root;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mWebView = (WebView) view.findViewById(R.id.webview);
        updatePreview(true);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPrepareForRemoval() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPrepareForRemoval();
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {
        updatePreview(false);
    }

}

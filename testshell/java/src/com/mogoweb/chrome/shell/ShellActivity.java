// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.shell;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Picture;
import android.graphics.drawable.ClipDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.common.annotations.VisibleForTesting;
import com.mogoweb.chrome.WebChromeClient;
import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.WebViewClient;

/*
 * This is a lightweight activity for tests that only require WebView functionality.
 */
public class ShellActivity extends Activity {
    private final static String PREFERENCES_NAME = "AwShellPrefs";
    private final static String INITIAL_URL = "http://www.activeshare.cn/OEBPS/xhtml/p1.html";
    private WebView mWebView;
    private EditText mUrlTextView;
    private ImageButton mPrevButton;
    private ImageButton mNextButton;
    private ImageButton mCaptureButton;

    private LinearLayout mToolbar;
    private ClipDrawable mProgressDrawable;

    private boolean mIsLoading = false;

    private static final long COMPLETED_PROGRESS_TIMEOUT_MS = 200;

    private Runnable mClearProgressRunnable = new Runnable() {
        @Override
        public void run() {
            mProgressDrawable.setLevel(0);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.testshell_activity);

        mWebView = createWebView();

        mToolbar = (LinearLayout)findViewById(R.id.toolbar);
        mProgressDrawable = (ClipDrawable) mToolbar.getBackground();

        mWebView.getSettings().setJavaScriptEnabled(true);

        LinearLayout contentContainer = (LinearLayout) findViewById(R.id.content_container);
        mWebView.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1f));
        contentContainer.addView(mWebView);
        mWebView.requestFocus();

        initializeUrlField();
        initializeNavigationButtons();

        String startupUrl = getUrlFromIntent(getIntent());
        if (TextUtils.isEmpty(startupUrl)) {
            startupUrl = INITIAL_URL;
        }

        mWebView.loadUrl(startupUrl);
        mUrlTextView.setText(startupUrl);
    }

    private WebView createWebView() {
        WebView webView = new WebView(this);
        WebViewClient webViewClient = new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mIsLoading = true;
                if (mUrlTextView != null) {
                    mUrlTextView.setText(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mIsLoading = false;
            }
        };

        WebChromeClient webChromeClient = new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                mToolbar.removeCallbacks(mClearProgressRunnable);
                mProgressDrawable.setLevel(newProgress * 100);
                if (newProgress == 100) mToolbar.postDelayed(mClearProgressRunnable, COMPLETED_PROGRESS_TIMEOUT_MS);
            }
        };

        webView.setWebViewClient(webViewClient);
        webView.setWebChromeClient(webChromeClient);
        return webView;
    }

    private static String getUrlFromIntent(Intent intent) {
        return intent != null ? intent.getDataString() : null;
    }

    private void setKeyboardVisibilityForUrl(boolean visible) {
        InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (visible) {
            imm.showSoftInput(mUrlTextView, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(mUrlTextView.getWindowToken(), 0);
        }
    }

    private void initializeUrlField() {
        mUrlTextView = (EditText) findViewById(R.id.url);
        mUrlTextView.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((actionId != EditorInfo.IME_ACTION_GO) && (event == null ||
                        event.getKeyCode() != KeyEvent.KEYCODE_ENTER ||
                        event.getKeyCode() != KeyEvent.ACTION_DOWN)) {
                    return false;
                }

                mWebView.loadUrl(mUrlTextView.getText().toString());
                mUrlTextView.clearFocus();
                setKeyboardVisibilityForUrl(false);
                mWebView.requestFocus();
                return true;
            }
        });
        mUrlTextView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                setKeyboardVisibilityForUrl(hasFocus);
                mNextButton.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
                mPrevButton.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
                if (!hasFocus) {
                    mUrlTextView.setText(mWebView.getUrl());
                }
            }
        });
    }

    private void initializeNavigationButtons() {
        mPrevButton = (ImageButton) findViewById(R.id.prev);
        mPrevButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mWebView.canGoBack()) {
                    mWebView.goBack();
                }
            }
        });

        mNextButton = (ImageButton) findViewById(R.id.next);
        mNextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mWebView.canGoForward()) {
                    mWebView.goForward();
                }
            }
        });

        mCaptureButton = (ImageButton) findViewById(R.id.capture);
        mCaptureButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Picture picture = mWebView.capturePicture();
                if (picture != null) {
                    Log.d("ShellActivity", "capture success");
                }
            }
        });
    }

    @VisibleForTesting
    public WebView getWebView() {
        return mWebView;
    }

    @VisibleForTesting
    public boolean isLoading() {
        return mIsLoading;
    }
}

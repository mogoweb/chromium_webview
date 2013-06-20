/*
 * Copyright (C) 2010 The Android Open Source Project
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieSyncManager;

import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.WebViewClient;

public class GoogleAccountLogin implements Runnable,
        AccountManagerCallback<Bundle>, OnCancelListener {

    private static final String LOGTAG = "BrowserLogin";

    // Url for issuing the uber token.
    private Uri ISSUE_AUTH_TOKEN_URL = Uri.parse(
            "https://www.google.com/accounts/IssueAuthToken?service=gaia&Session=false");
    // Url for signing into a particular service.
    private static final Uri TOKEN_AUTH_URL = Uri.parse(
            "https://www.google.com/accounts/TokenAuth");
    // Google account type
    private static final String GOOGLE = "com.google";
    // Last auto login time
    public static final String PREF_AUTOLOGIN_TIME = "last_autologin_time";

    private final Activity mActivity;
    private final Account mAccount;
    private final WebView mWebView;
    private Runnable mRunnable;
    private ProgressDialog mProgressDialog;

    // SID and LSID retrieval process.
    private String mSid;
    private String mLsid;
    private int mState;  // {NONE(0), SID(1), LSID(2)}
    private boolean mTokensInvalidated;
    private String mUserAgent;

    private GoogleAccountLogin(Activity activity, Account account,
            Runnable runnable) {
        mActivity = activity;
        mAccount = account;
        mWebView = new WebView(mActivity);
        mRunnable = runnable;
        mUserAgent = mWebView.getSettings().getUserAgentString();

        // XXX: Doing pre-login causes onResume to skip calling
        // resumeWebViewTimers. So to avoid problems with timers not running, we
        // duplicate the work here using the off-screen WebView.
        CookieSyncManager.getInstance().startSync();
        WebViewTimersControl.getInstance().onBrowserActivityResume(mWebView);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                done();
            }
        });
    }

    private void saveLoginTime() {
        Editor ed = BrowserSettings.getInstance().getPreferences().edit();
        ed.putLong(PREF_AUTOLOGIN_TIME, System.currentTimeMillis());
        ed.apply();
    }

    // Runnable
    @Override
    public void run() {
        String url = ISSUE_AUTH_TOKEN_URL.buildUpon()
                .appendQueryParameter("SID", mSid)
                .appendQueryParameter("LSID", mLsid)
                .build().toString();
        // Intentionally not using Proxy.
        AndroidHttpClient client = AndroidHttpClient.newInstance(mUserAgent);
        HttpPost request = new HttpPost(url);

        String result = null;
        try {
            HttpResponse response = client.execute(request);
            int status = response.getStatusLine().getStatusCode();
            if (status != HttpStatus.SC_OK) {
                Log.d(LOGTAG, "LOGIN_FAIL: Bad status from auth url "
                      + status + ": "
                      + response.getStatusLine().getReasonPhrase());
                // Invalidate the tokens once just in case the 403 was for other
                // reasons.
                if (status == HttpStatus.SC_FORBIDDEN && !mTokensInvalidated) {
                    Log.d(LOGTAG, "LOGIN_FAIL: Invalidating tokens...");
                    // Need to regenerate the auth tokens and try again.
                    invalidateTokens();
                    // XXX: Do not touch any more member variables from this
                    // thread as a second thread will handle the next login
                    // attempt.
                    return;
                }
                done();
                return;
            }
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                Log.d(LOGTAG, "LOGIN_FAIL: Null entity in response");
                done();
                return;
            }
            result = EntityUtils.toString(entity, "UTF-8");
        } catch (Exception e) {
            Log.d(LOGTAG, "LOGIN_FAIL: Exception acquiring uber token " + e);
            request.abort();
            done();
            return;
        } finally {
            client.close();
        }
        final String newUrl = TOKEN_AUTH_URL.buildUpon()
                .appendQueryParameter("source", "android-browser")
                .appendQueryParameter("auth", result)
                .appendQueryParameter("continue",
                        BrowserSettings.getFactoryResetHomeUrl(mActivity))
                .build().toString();
        mActivity.runOnUiThread(new Runnable() {
            @Override public void run() {
                // Check mRunnable in case the request has been canceled.  This
                // is most likely not necessary as run() is the only non-UI
                // thread that calls done() but I am paranoid.
                synchronized (GoogleAccountLogin.this) {
                    if (mRunnable == null) {
                        return;
                    }
                    mWebView.loadUrl(newUrl);
                }
            }
        });
    }

    private void invalidateTokens() {
        AccountManager am = AccountManager.get(mActivity);
        am.invalidateAuthToken(GOOGLE, mSid);
        am.invalidateAuthToken(GOOGLE, mLsid);
        mTokensInvalidated = true;
        mState = 1;  // SID
        am.getAuthToken(mAccount, "SID", null, mActivity, this, null);
    }

    // AccountManager callbacks.
    @Override
    public void run(AccountManagerFuture<Bundle> value) {
        try {
            String id = value.getResult().getString(
                    AccountManager.KEY_AUTHTOKEN);
            switch (mState) {
                default:
                case 0:
                    throw new IllegalStateException(
                            "Impossible to get into this state");
                case 1:
                    mSid = id;
                    mState = 2;  // LSID
                    AccountManager.get(mActivity).getAuthToken(
                            mAccount, "LSID", null, mActivity, this, null);
                    break;
                case 2:
                    mLsid = id;
                    new Thread(this).start();
                    break;
            }
        } catch (Exception e) {
            Log.d(LOGTAG, "LOGIN_FAIL: Exception in state " + mState + " " + e);
            // For all exceptions load the original signin page.
            // TODO: toast login failed?
            done();
        }
    }

    // Start the login process if auto-login is enabled and the user is not
    // already logged in.
    public static void startLoginIfNeeded(Activity activity,
            Runnable runnable) {
        // Already logged in?
        if (isLoggedIn()) {
            runnable.run();
            return;
        }

        // No account found?
        Account[] accounts = getAccounts(activity);
        if (accounts == null || accounts.length == 0) {
            runnable.run();
            return;
        }

        GoogleAccountLogin login =
                new GoogleAccountLogin(activity, accounts[0], runnable);
        login.startLogin();
    }

    private void startLogin() {
        saveLoginTime();
        mProgressDialog = ProgressDialog.show(mActivity,
                mActivity.getString(R.string.pref_autologin_title),
                mActivity.getString(R.string.pref_autologin_progress,
                                    mAccount.name),
                true /* indeterminate */,
                true /* cancelable */,
                this);
        mState = 1;  // SID
        AccountManager.get(mActivity).getAuthToken(
                mAccount, "SID", null, mActivity, this, null);
    }

    private static Account[] getAccounts(Context ctx) {
        return AccountManager.get(ctx).getAccountsByType(GOOGLE);
    }

    // Checks if we already did pre-login.
    private static boolean isLoggedIn() {
        // See if we last logged in less than a week ago.
        long lastLogin = BrowserSettings.getInstance().getPreferences()
                .getLong(PREF_AUTOLOGIN_TIME, -1);
        if (lastLogin == -1) {
            return false;
        }
        return true;
    }

    // Used to indicate that the Browser should continue loading the main page.
    // This can happen on success, error, or timeout.
    private synchronized void done() {
        if (mRunnable != null) {
            Log.d(LOGTAG, "Finished login attempt for " + mAccount.name);
            mActivity.runOnUiThread(mRunnable);

            try {
                mProgressDialog.dismiss();
            } catch (Exception e) {
                // TODO: Switch to a managed dialog solution (DialogFragment?)
                // Also refactor this class, it doesn't
                // play nice with the activity lifecycle, leading to issues
                // with the dialog it manages
                Log.w(LOGTAG, "Failed to dismiss mProgressDialog: " + e.getMessage());
            }
            mRunnable = null;
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mWebView.destroy();
                }
            });
        }
    }

    // Called by the progress dialog on startup.
    public void onCancel(DialogInterface unused) {
        done();
    }

}

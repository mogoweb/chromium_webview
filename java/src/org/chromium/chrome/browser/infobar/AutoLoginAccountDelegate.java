// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import org.chromium.content.common.CommandLine;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

/**
 * Performs the actual login to the requried service.
 */
public class AutoLoginAccountDelegate implements AccountManagerCallback<Bundle> {

    private static final String WEB_LOGIN_PREFIX = "weblogin:";
    private final Activity mActivity;
    private final AutoLoginProcessor mAutoLoginProcessor;
    private final AccountManager mAccountManager;
    private final Account mAccount;
    private boolean mLogInRequested;
    private String mAuthTokenType;

    public AutoLoginAccountDelegate(Activity activity, AutoLoginProcessor autoLoginProcessor,
            String realm, String account, String accountArgs) {
        mActivity = activity;
        mAutoLoginProcessor = autoLoginProcessor;
        mAccountManager = AccountManager.get(activity);
        mAccount = ChromeSigninController.get(activity).getSignedInUser();
        mLogInRequested = false;
        mAuthTokenType = WEB_LOGIN_PREFIX + accountArgs;
    }

    public boolean logIn() {
        Log.i("AutoLoginAccountDelegate", "auto-login requested for "
                + (mAccount != null ? mAccount.toString() : "?"));

        Account currentAccount = ChromeSigninController.get(mActivity).getSignedInUser();
        if (mAccount == null || !mAccount.equals(currentAccount)) {
            Log.i("InfoBar", "auto-login failed because account is no longer valid");
            return false;
        }

        // The callback for this request comes in on a non-UI thread.
        mAccountManager.getAuthToken(mAccount, mAuthTokenType, null, mActivity, this, null);
        mLogInRequested = true;
        return true;
    }

    String getAuthToken() {
        return mAuthTokenType;
    }

    String getAccountName() {
        return mAccount != null ? mAccount.name : "";
    }

    boolean loginRequested() {
        return mLogInRequested;
    }

    boolean hasAccount() {
        return mAccount != null;
    }

    @Override
    public void run(AccountManagerFuture<Bundle> value) {
        String result = null;
        try {
            result = value.getResult().getString(AccountManager.KEY_AUTHTOKEN);
        } catch (Exception e) {
            result = null;
        }

        final boolean success = result != null;

        // Can't rely on the Bundle's auth token or account name as they might be null
        // if this was a failed attempt.
        if (mAutoLoginProcessor != null) {
            mAutoLoginProcessor.processAutoLoginResult(getAccountName(), getAuthToken(),
                    success, result);
        }
    }
}

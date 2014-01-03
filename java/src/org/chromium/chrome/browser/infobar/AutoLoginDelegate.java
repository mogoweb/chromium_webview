// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.accounts.Account;
import android.app.Activity;
import android.util.Log;
import android.util.Pair;

import org.chromium.chrome.R;

import org.chromium.base.CalledByNative;

/**
 * Java equivalent of the C++ AutoLoginDelegateAndroid.
 *
 * Offers functionality to log in using the account in the system and keeps track
 * of all the native autologin infobars and their respective accounts.
 */
public class AutoLoginDelegate {
    private final Activity mActivity;
    private final AutoLoginProcessor mAutoLoginProcessor;

    // nativeInfoBar -> AutoLoginAccountDelegate
    private Pair<Integer, AutoLoginAccountDelegate> mAccountHelper;

    public AutoLoginDelegate(AutoLoginProcessor autoLoginProcessor, Activity activity) {
        mActivity = activity;
        mAutoLoginProcessor = autoLoginProcessor;
        mAccountHelper = null;
    }

    /**
     * @return the account name of the device if any.
     */
    @CalledByNative
    String initializeAccount(int nativeInfoBar, String realm, String account, String args) {
        AutoLoginAccountDelegate accountHelper =
                new AutoLoginAccountDelegate(mActivity, mAutoLoginProcessor, realm, account, args);

        if (!accountHelper.hasAccount()) {
            return "";
        }

        mAccountHelper = new Pair<Integer, AutoLoginAccountDelegate>(nativeInfoBar, accountHelper);
        return accountHelper.getAccountName();
    }

    /**
     * Log in a user to a given google service.
     */
    @CalledByNative
    boolean logIn(int nativeInfoBar) {
        AutoLoginAccountDelegate account =
                mAccountHelper != null && mAccountHelper.first == nativeInfoBar ?
                        mAccountHelper.second : null;

        if (account == null || !account.logIn()) {
            nativeLoginFailed(nativeInfoBar);
            return false;
        }
        return true;
    }

    /**
     * Clear account information for cancelled login requests.
     */
    @CalledByNative
    boolean cancelLogIn(int nativeInfoBar) {
        mAccountHelper = null;
        return true;
    }

    /**
     * Clear all infobars in the same tab and tells native to proceed with login if successful.
     */
    public void dismissAutoLogins(String accountName, String authToken, boolean success,
            String result) {

        if (mAccountHelper != null) {
            int infoBar = mAccountHelper.first;
            AutoLoginAccountDelegate delegate = mAccountHelper.second;
            if (!delegate.loginRequested()) {
                nativeLoginDismiss(infoBar);
            } else {
                String accountAuthToken = delegate.getAuthToken();
                if (accountAuthToken != null && accountAuthToken.equals(authToken)
                        && delegate.loginRequested()) {
                    if (success) {
                        nativeLoginSuccess(infoBar, result);
                    } else {
                        nativeLoginFailed(infoBar);
                    }
                }
            }
            mAccountHelper = null;
        }
    }

    private native void nativeLoginSuccess(int nativeAutoLoginInfoBarDelegateAndroid,
            String result);
    private native void nativeLoginFailed(int nativeAutoLoginInfoBarDelegateAndroid);
    private native void nativeLoginDismiss(int nativeAutoLoginInfoBarDelegateAndroid);
}

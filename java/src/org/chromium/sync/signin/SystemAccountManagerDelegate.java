// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sync.signin;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorDescription;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;

import java.io.IOException;

/**
 * Default implementation of {@link AccountManagerDelegate} which delegates all calls to the
 * Android account manager.
 */
public class SystemAccountManagerDelegate implements AccountManagerDelegate {

    private final AccountManager mAccountManager;

    public SystemAccountManagerDelegate(Context context) {
        mAccountManager = AccountManager.get(context);
    }

    @Override
    public Account[] getAccountsByType(String type) {
        return mAccountManager.getAccountsByType(type);
    }

    @Override
    public AccountManagerFuture<Bundle> getAuthToken(Account account, String authTokenType,
            boolean notifyAuthFailure, AccountManagerCallback<Bundle> callback, Handler handler) {
        return mAccountManager.getAuthToken(account, authTokenType, null, notifyAuthFailure,
                callback, handler);
    }

    @Override
    public AccountManagerFuture<Bundle> getAuthToken(Account account, String authTokenType,
            Bundle options, Activity activity, AccountManagerCallback<Bundle> callback,
            Handler handler) {
        return mAccountManager.getAuthToken(account, authTokenType, options, activity, callback,
                handler);
    }

    @Override
    public void invalidateAuthToken(String accountType, String authToken) {
        mAccountManager.invalidateAuthToken(accountType, authToken);
    }

    @Override
    public String blockingGetAuthToken(Account account, String authTokenType,
                                       boolean notifyAuthFailure)
            throws OperationCanceledException, IOException, AuthenticatorException {
        return mAccountManager.blockingGetAuthToken(account, authTokenType, notifyAuthFailure);
    }

    @Override
    public Account[] getAccounts() {
        return mAccountManager.getAccounts();
    }

    @Override
    public boolean addAccountExplicitly(Account account, String password, Bundle userdata) {
        return mAccountManager.addAccountExplicitly(account, password, userdata);
    }

    @Override
    public AccountManagerFuture<Boolean> removeAccount(Account account,
            AccountManagerCallback<Boolean> callback, Handler handler) {
        return mAccountManager.removeAccount(account, callback, handler);
    }

    @Override
    public String getPassword(Account account) {
        return mAccountManager.getPassword(account);
    }

    @Override
    public void setPassword(Account account, String password) {
        mAccountManager.setPassword(account, password);
    }

    @Override
    public void clearPassword(Account account) {
        mAccountManager.clearPassword(account);
    }

    @Override
    public AccountManagerFuture<Bundle> confirmCredentials(Account account, Bundle bundle,
            Activity activity, AccountManagerCallback<Bundle> callback, Handler handler) {
        return mAccountManager.confirmCredentials(account, bundle, activity, callback, handler);
    }

    @Override
    public String peekAuthToken(Account account, String authTokenType) {
        return mAccountManager.peekAuthToken(account, authTokenType);
    }

    @Override
    public AuthenticatorDescription[] getAuthenticatorTypes() {
        return mAccountManager.getAuthenticatorTypes();
    }
}

// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sync.signin;

import android.accounts.Account;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorDescription;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;

import java.io.IOException;

/**
 * Wrapper around the Android account manager, to facilitate dependency injection during testing.
 */
public interface AccountManagerDelegate {
    Account[] getAccountsByType(String type);

    AccountManagerFuture<Bundle> getAuthToken(Account account, String authTokenType,
            boolean notifyAuthFailure, AccountManagerCallback<Bundle> callback, Handler handler);

    AccountManagerFuture<Bundle> getAuthToken(Account account, String authTokenType, Bundle options,
            Activity activity, AccountManagerCallback<Bundle> callback, Handler handler);

    void invalidateAuthToken(String accountType, String authToken);

    String blockingGetAuthToken(Account account, String authTokenType, boolean notifyAuthFailure)
            throws OperationCanceledException, IOException, AuthenticatorException;

    Account[] getAccounts();

    boolean addAccountExplicitly(Account account, String password, Bundle userdata);

    AccountManagerFuture<Boolean> removeAccount(Account account,
            AccountManagerCallback<Boolean> callback, Handler handler);

    String getPassword(Account account);

    void setPassword(Account account, String password);

    void clearPassword(Account account);

    AccountManagerFuture<Bundle> confirmCredentials(Account account, Bundle bundle,
            Activity activity, AccountManagerCallback<Bundle> callback, Handler handler);

    String peekAuthToken(Account account, String authTokenType);

    AuthenticatorDescription[] getAuthenticatorTypes();
}

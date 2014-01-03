// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sync.signin;


import com.google.common.annotations.VisibleForTesting;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorDescription;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import org.chromium.base.ThreadUtils;
import org.chromium.net.NetworkChangeNotifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import javax.annotation.Nullable;

/**
 * AccountManagerHelper wraps our access of AccountManager in Android.
 *
 * Use the AccountManagerHelper.get(someContext) to instantiate it
 */
public class AccountManagerHelper {

    private static final String TAG = "AccountManagerHelper";

    public static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    private static final Object lock = new Object();

    private static final int MAX_TRIES = 3;

    private static AccountManagerHelper sAccountManagerHelper;

    private final AccountManagerDelegate mAccountManager;

    private Context mApplicationContext;

    public interface GetAuthTokenCallback {
        /**
         * Invoked on the UI thread once a token has been provided by the AccountManager.
         * @param token Auth token, or null if no token is available (bad credentials,
         *      permission denied, etc).
         */
        void tokenAvailable(String token);
    }

    /**
     * @param context the Android context
     * @param accountManager the account manager to use as a backend service
     */
    private AccountManagerHelper(Context context,
                                 AccountManagerDelegate accountManager) {
        mApplicationContext = context.getApplicationContext();
        mAccountManager = accountManager;
    }

    /**
     * A factory method for the AccountManagerHelper.
     *
     * It is possible to override the AccountManager to use in tests for the instance of the
     * AccountManagerHelper by calling overrideAccountManagerHelperForTests(...) with
     * your MockAccountManager.
     *
     * @param context the applicationContext is retrieved from the context used as an argument.
     * @return a singleton instance of the AccountManagerHelper
     */
    public static AccountManagerHelper get(Context context) {
        synchronized (lock) {
            if (sAccountManagerHelper == null) {
                sAccountManagerHelper = new AccountManagerHelper(context,
                        new SystemAccountManagerDelegate(context));
            }
        }
        return sAccountManagerHelper;
    }

    @VisibleForTesting
    public static void overrideAccountManagerHelperForTests(Context context,
            AccountManagerDelegate accountManager) {
        synchronized (lock) {
            sAccountManagerHelper = new AccountManagerHelper(context, accountManager);
        }
    }

    /**
     * Creates an Account object for the given name.
     */
    public static Account createAccountFromName(String name) {
        return new Account(name, GOOGLE_ACCOUNT_TYPE);
    }

    public List<String> getGoogleAccountNames() {
        List<String> accountNames = new ArrayList<String>();
        Account[] accounts = mAccountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
        for (Account account : accounts) {
            accountNames.add(account.name);
        }
        return accountNames;
    }

    public Account[] getGoogleAccounts() {
        return mAccountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
    }

    public boolean hasGoogleAccounts() {
        return getGoogleAccounts().length > 0;
    }

    /**
     * Returns the account if it exists, null otherwise.
     */
    public Account getAccountFromName(String accountName) {
        Account[] accounts = mAccountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
        for (Account account : accounts) {
            if (account.name.equals(accountName)) {
                return account;
            }
        }
        return null;
    }

    /**
     * Returns whether the accounts exists.
     */
    public boolean hasAccountForName(String accountName) {
        return getAccountFromName(accountName) != null;
    }

    /**
     * @return Whether or not there is an account authenticator for Google accounts.
     */
    public boolean hasGoogleAccountAuthenticator() {
        AuthenticatorDescription[] descs = mAccountManager.getAuthenticatorTypes();
        for (AuthenticatorDescription desc : descs) {
            if (GOOGLE_ACCOUNT_TYPE.equals(desc.type)) return true;
        }
        return false;
    }

    /**
     * Gets the auth token synchronously.
     *
     * - Assumes that the account is a valid account.
     * - Should not be called on the main thread.
     */
    @Deprecated
    public String getAuthTokenFromBackground(Account account, String authTokenType) {
            AccountManagerFuture<Bundle> future = mAccountManager.getAuthToken(account,
                    authTokenType, false, null, null);
            AtomicBoolean errorEncountered = new AtomicBoolean(false);
            return getAuthTokenInner(future, errorEncountered);
    }

    /**
     * Gets the auth token and returns the response asynchronously.
     * This should be called when we have a foreground activity that needs an auth token.
     * If encountered an IO error, it will attempt to retry when the network is back.
     *
     * - Assumes that the account is a valid account.
     */
    public void getAuthTokenFromForeground(Activity activity, Account account, String authTokenType,
                GetAuthTokenCallback callback) {
        AtomicInteger numTries = new AtomicInteger(0);
        AtomicBoolean errorEncountered = new AtomicBoolean(false);
        getAuthTokenAsynchronously(activity, account, authTokenType, callback, numTries,
                errorEncountered, null);
    }

    private class ConnectionRetry implements NetworkChangeNotifier.ConnectionTypeObserver {
        private final Account mAccount;
        private final String mAuthTokenType;
        private final GetAuthTokenCallback mCallback;
        private final AtomicInteger mNumTries;
        private final AtomicBoolean mErrorEncountered;

        ConnectionRetry(Account account, String authTokenType, GetAuthTokenCallback callback,
                AtomicInteger numTries, AtomicBoolean errorEncountered) {
            mAccount = account;
            mAuthTokenType = authTokenType;
            mCallback = callback;
            mNumTries = numTries;
            mErrorEncountered = errorEncountered;
        }

        @Override
        public void onConnectionTypeChanged(int connectionType) {
            assert mNumTries.get() <= MAX_TRIES;
            if (mNumTries.get() == MAX_TRIES) {
                NetworkChangeNotifier.removeConnectionTypeObserver(this);
                return;
            }
            if (NetworkChangeNotifier.isOnline()) {
                NetworkChangeNotifier.removeConnectionTypeObserver(this);
                getAuthTokenAsynchronously(null, mAccount, mAuthTokenType, mCallback, mNumTries,
                        mErrorEncountered, this);
            }
        }
    }

    // Gets the auth token synchronously
    private String getAuthTokenInner(AccountManagerFuture<Bundle> future,
            AtomicBoolean errorEncountered) {
        try {
            Bundle result = future.getResult();
            if (result != null) {
                if (result.containsKey(AccountManager.KEY_INTENT)) {
                    Log.d(TAG, "Starting intent to get auth credentials");
                    // Need to start intent to get credentials
                    Intent intent = result.getParcelable(AccountManager.KEY_INTENT);
                    int flags = intent.getFlags();
                    flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
                    intent.setFlags(flags);
                    mApplicationContext.startActivity(intent);
                    return null;
                }
                return result.getString(AccountManager.KEY_AUTHTOKEN);
            } else {
                Log.w(TAG, "Auth token - getAuthToken returned null");
            }
        } catch (OperationCanceledException e) {
            Log.w(TAG, "Auth token - operation cancelled", e);
        } catch (AuthenticatorException e) {
            Log.w(TAG, "Auth token - authenticator exception", e);
        } catch (IOException e) {
            Log.w(TAG, "Auth token - IO exception", e);
            errorEncountered.set(true);
        }
        return null;
    }

    private void getAuthTokenAsynchronously(@Nullable Activity activity, final Account account,
            final String authTokenType, final GetAuthTokenCallback callback,
            final AtomicInteger numTries, final AtomicBoolean errorEncountered,
            final ConnectionRetry retry) {
        AccountManagerFuture<Bundle> future;
        if (numTries.get() == 0 && activity != null) {
            future = mAccountManager.getAuthToken(
                    account, authTokenType, null, activity, null, null);
        } else {
            future = mAccountManager.getAuthToken(
                    account, authTokenType, false, null, null);
        }
        final AccountManagerFuture<Bundle> finalFuture = future;
        errorEncountered.set(false);

        // On ICS onPostExecute is never called when running an AsyncTask from a different thread
        // than the UI thread.
        if (ThreadUtils.runningOnUiThread()) {
            new AsyncTask<Void, Void, String>() {
                @Override
                public String doInBackground(Void... params) {
                    return getAuthTokenInner(finalFuture, errorEncountered);
                }
                @Override
                public void onPostExecute(String authToken) {
                    onGotAuthTokenResult(account, authTokenType, authToken, callback, numTries,
                            errorEncountered, retry);
                }
            }.execute();
        } else {
            String authToken = getAuthTokenInner(finalFuture, errorEncountered);
            onGotAuthTokenResult(account, authTokenType, authToken, callback, numTries,
                    errorEncountered, retry);
        }
    }

    private void onGotAuthTokenResult(Account account, String authTokenType, String authToken,
            GetAuthTokenCallback callback, AtomicInteger numTries, AtomicBoolean errorEncountered,
            ConnectionRetry retry) {
        if (authToken != null || !errorEncountered.get() ||
                numTries.incrementAndGet() == MAX_TRIES ||
                !NetworkChangeNotifier.isInitialized()) {
            callback.tokenAvailable(authToken);
            return;
        }
        if (retry == null) {
            ConnectionRetry newRetry = new ConnectionRetry(account, authTokenType, callback,
                    numTries, errorEncountered);
            NetworkChangeNotifier.addConnectionTypeObserver(newRetry);
        } else {
            NetworkChangeNotifier.addConnectionTypeObserver(retry);
        }
    }

    /**
     * Invalidates the old token (if non-null/non-empty) and synchronously generates a new one.
     * Also notifies the user (via status bar) if any user action is required. The method will
     * return null if any user action is required to generate the new token.
     *
     * - Assumes that the account is a valid account.
     * - Should not be called on the main thread.
     */
    @Deprecated
    public String getNewAuthToken(Account account, String authToken, String authTokenType) {
        // TODO(dsmyers): consider reimplementing using an AccountManager function with an
        // explicit timeout.
        // Bug: https://code.google.com/p/chromium/issues/detail?id=172394.
        if (authToken != null && !authToken.isEmpty()) {
            mAccountManager.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, authToken);
        }

        try {
            return mAccountManager.blockingGetAuthToken(account, authTokenType, true);
        } catch (OperationCanceledException e) {
            Log.w(TAG, "Auth token - operation cancelled", e);
        } catch (AuthenticatorException e) {
            Log.w(TAG, "Auth token - authenticator exception", e);
        } catch (IOException e) {
            Log.w(TAG, "Auth token - IO exception", e);
        }
        return null;
    }

    /**
     * Invalidates the old token (if non-null/non-empty) and asynchronously generates a new one.
     *
     * - Assumes that the account is a valid account.
     */
    public void getNewAuthTokenFromForeground(Account account, String authToken,
                String authTokenType, GetAuthTokenCallback callback) {
        if (authToken != null && !authToken.isEmpty()) {
            mAccountManager.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, authToken);
        }
        AtomicInteger numTries = new AtomicInteger(0);
        AtomicBoolean errorEncountered = new AtomicBoolean(false);
        getAuthTokenAsynchronously(
            null, account, authTokenType, callback, numTries, errorEncountered, null);
    }

    /**
     * Removes an auth token from the AccountManager's cache.
     */
    public void invalidateAuthToken(String authToken) {
        mAccountManager.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, authToken);
    }
}

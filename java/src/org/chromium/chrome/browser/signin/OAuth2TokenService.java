// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * Java instance for the native OAuth2TokenService.
 * <p/>
 * This class forwards calls to request or invalidate access tokens made by native code to
 * AccountManagerHelper and forwards callbacks to native code.
 * <p/>
 */
public final class OAuth2TokenService {

    private static final String TAG = "OAuth2TokenService";

    public interface OAuth2TokenServiceObserver {
        void onRefreshTokenAvailable(Account account);
        void onRefreshTokenRevoked(Account account);
        void onRefreshTokensLoaded();
    }

    private static final String OAUTH2_SCOPE_PREFIX = "oauth2:";

    private final int mNativeProfileOAuth2TokenService;
    private final ObserverList<OAuth2TokenServiceObserver> mObservers;

    private OAuth2TokenService(int nativeOAuth2Service) {
        mNativeProfileOAuth2TokenService = nativeOAuth2Service;
        mObservers = new ObserverList<OAuth2TokenServiceObserver>();
    }

    public static OAuth2TokenService getForProfile(Profile profile) {
        ThreadUtils.assertOnUiThread();
        return (OAuth2TokenService) nativeGetForProfile(profile);
    }

    @CalledByNative
    private static OAuth2TokenService create(int nativeOAuth2Service) {
        ThreadUtils.assertOnUiThread();
        return new OAuth2TokenService(nativeOAuth2Service);
    }

    public void addObserver(OAuth2TokenServiceObserver observer) {
        ThreadUtils.assertOnUiThread();
        mObservers.addObserver(observer);
    }

    public void removeObserver(OAuth2TokenServiceObserver observer) {
        ThreadUtils.assertOnUiThread();
        mObservers.removeObserver(observer);
    }

    private static Account getAccountOrNullFromUsername(Context context, String username) {
        if (username == null) {
            Log.e(TAG, "Username is null");
            return null;
        }

        AccountManagerHelper accountManagerHelper = AccountManagerHelper.get(context);
        Account account = accountManagerHelper.getAccountFromName(username);
        if (account == null) {
            Log.e(TAG, "Account not found for provided username.");
            return null;
        }
        return account;
    }

    /**
     * Called by native to list the accounts with OAuth2 refresh tokens.
     */
    @CalledByNative
    public static String[] getAccounts(Context context) {
        AccountManagerHelper accountManagerHelper = AccountManagerHelper.get(context);
        java.util.List<String> accountNames = accountManagerHelper.getGoogleAccountNames();
        return accountNames.toArray(new String[accountNames.size()]);
    }

    /**
     * Called by native to retrieve OAuth2 tokens.
     *
     * @param username The native username (full address).
     * @param scope The scope to get an auth token for (without Android-style 'oauth2:' prefix).
     * @param nativeCallback The pointer to the native callback that should be run upon completion.
     */
    @CalledByNative
    public static void getOAuth2AuthToken(
            Context context, String username, String scope, final int nativeCallback) {
        Account account = getAccountOrNullFromUsername(context, username);
        if (account == null) {
            nativeOAuth2TokenFetched(null, false, nativeCallback);
            return;
        }
        String oauth2Scope = OAUTH2_SCOPE_PREFIX + scope;

        AccountManagerHelper accountManagerHelper = AccountManagerHelper.get(context);
        accountManagerHelper.getAuthTokenFromForeground(
            null, account, oauth2Scope, new AccountManagerHelper.GetAuthTokenCallback() {
                @Override
                public void tokenAvailable(String token) {
                    nativeOAuth2TokenFetched(
                        token, token != null, nativeCallback);
                }
            });
    }

    /**
     * Call this method to retrieve an OAuth2 access token for the given account and scope.
     *
     * @param activity the current activity. May be null.
     * @param account the account to get the access token for.
     * @param scope The scope to get an auth token for (without Android-style 'oauth2:' prefix).
     * @param callback called on successful and unsuccessful fetching of auth token.
     */
    public static void getOAuth2AccessToken(Context context, @Nullable Activity activity,
                                            Account account, String scope,
                                            AccountManagerHelper.GetAuthTokenCallback callback) {
        String oauth2Scope = OAUTH2_SCOPE_PREFIX + scope;
        AccountManagerHelper.get(context).getAuthTokenFromForeground(
                activity, account, oauth2Scope, callback);
    }

    /**
     * Call this method to retrieve an OAuth2 access token for the given account and scope. This
     * method times out after the specified timeout, and will return null if that happens.
     *
     * Given that this is a blocking method call, this should never be called from the UI thread.
     *
     * @param activity the current activity. May be null.
     * @param account the account to get the access token for.
     * @param scope The scope to get an auth token for (without Android-style 'oauth2:' prefix).
     * @param timeout the timeout.
     * @param unit the unit for |timeout|.
     */
    public static String getOAuth2AccessTokenWithTimeout(
            Context context, @Nullable Activity activity, Account account, String scope,
            long timeout, TimeUnit unit) {
        assert !ThreadUtils.runningOnUiThread();
        final AtomicReference<String> result = new AtomicReference<String>();
        final Semaphore semaphore = new Semaphore(0);
        getOAuth2AccessToken(
                context, activity, account, scope,
                new AccountManagerHelper.GetAuthTokenCallback() {
                    @Override
                    public void tokenAvailable(String token) {
                        result.set(token);
                        semaphore.release();
                    }
                });
        try {
            if (semaphore.tryAcquire(timeout, unit)) {
                return result.get();
            } else {
                Log.d(TAG, "Failed to retrieve auth token within timeout (" +
                        timeout + " + " + unit.name() + ")");
                return null;
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Got interrupted while waiting for auth token");
            return null;
        }
    }

    /**
     * Called by native to check wether the account has an OAuth2 refresh token.
     */
    @CalledByNative
    public static boolean hasOAuth2RefreshToken(Context context, String accountName) {
        return AccountManagerHelper.get(context).hasAccountForName(accountName);
    }

    /**
    * Called by native to invalidate an OAuth2 token.
    */
    @CalledByNative
    public static void invalidateOAuth2AuthToken(Context context, String accessToken) {
        if (accessToken != null) {
            AccountManagerHelper.get(context).invalidateAuthToken(accessToken);
        }
    }

    public void validateAccounts(Context context) {
        ThreadUtils.assertOnUiThread();
        String currentlySignedInAccount =
                ChromeSigninController.get(context).getSignedInAccountName();
        String[] accounts = getAccounts(context);
        nativeValidateAccounts(
                mNativeProfileOAuth2TokenService, accounts, currentlySignedInAccount);
    }

    /**
     * Triggers a notification to all observers of the native and Java instance of the
     * OAuth2TokenService that a refresh token is now available. This may cause observers to retry
     * operations that require authentication.
     */
    public void fireRefreshTokenAvailable(Account account) {
        ThreadUtils.assertOnUiThread();
        assert account != null;
        nativeFireRefreshTokenAvailableFromJava(mNativeProfileOAuth2TokenService, account.name);
    }

    @CalledByNative
    public void notifyRefreshTokenAvailable(String accountName) {
        assert accountName != null;
        Account account = AccountManagerHelper.createAccountFromName(accountName);
        for (OAuth2TokenServiceObserver observer : mObservers) {
            observer.onRefreshTokenAvailable(account);
        }
    }

    /**
     * Triggers a notification to all observers of the native and Java instance of the
     * OAuth2TokenService that a refresh token is now revoked.
     */
    public void fireRefreshTokenRevoked(Account account) {
        ThreadUtils.assertOnUiThread();
        assert account != null;
        nativeFireRefreshTokenRevokedFromJava(mNativeProfileOAuth2TokenService, account.name);
    }

    @CalledByNative
    public void notifyRefreshTokenRevoked(String accountName) {
        assert accountName != null;
        Account account = AccountManagerHelper.createAccountFromName(accountName);
        for (OAuth2TokenServiceObserver observer : mObservers) {
            observer.onRefreshTokenRevoked(account);
        }
    }

    /**
     * Triggers a notification to all observers of the native and Java instance of the
     * OAuth2TokenService that all refresh tokens now have been loaded.
     */
    public void fireRefreshTokensLoaded() {
        ThreadUtils.assertOnUiThread();
        nativeFireRefreshTokensLoadedFromJava(mNativeProfileOAuth2TokenService);
    }

    @CalledByNative
    public void notifyRefreshTokensLoaded() {
        for (OAuth2TokenServiceObserver observer : mObservers) {
            observer.onRefreshTokensLoaded();
        }
    }

    private static native Object nativeGetForProfile(Profile profile);
    private static native void nativeOAuth2TokenFetched(
            String authToken, boolean result, int nativeCallback);
    private native void nativeValidateAccounts(int nativeAndroidProfileOAuth2TokenService,
        String[] accounts, String currentlySignedInAccount);
    private native void nativeFireRefreshTokenAvailableFromJava(
            int nativeAndroidProfileOAuth2TokenService, String accountName);
    private native void nativeFireRefreshTokenRevokedFromJava(
            int nativeAndroidProfileOAuth2TokenService, String accountName);
    private native void nativeFireRefreshTokensLoadedFromJava(
            int nativeAndroidProfileOAuth2TokenService);
}

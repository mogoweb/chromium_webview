// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Handler;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.invalidation.InvalidationController;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.R;
import org.chromium.sync.internal_api.pub.base.ModelType;
import org.chromium.sync.notifier.SyncStatusHelper;
import org.chromium.sync.signin.ChromeSigninController;

import java.util.HashSet;

/**
 * Android wrapper of the SigninManager which provides access from the Java layer.
 * <p/>
 * This class handles common paths during the sign-in and sign-out flows.
 * <p/>
 * Only usable from the UI thread as the native SigninManager requires its access to be in the
 * UI thread.
 * <p/>
 * See chrome/browser/signin/signin_manager_android.h for more details.
 */
public class SigninManager {

    private static final String TAG = "SigninManager";

    private static SigninManager sSigninManager;

    private final Context mContext;
    private final int mNativeSigninManagerAndroid;

    /** Tracks whether the First Run check has been completed.
     *
     * A new sign-in can not be started while this is pending, to prevent the
     * pending check from eventually starting a 2nd sign-in.
     */
    private boolean mFirstRunCheckIsPending = true;
    private ObserverList<SignInAllowedObserver> mSignInAllowedObservers =
            new ObserverList<SignInAllowedObserver>();

    private Activity mSignInActivity;
    private Account mSignInAccount;
    private Observer mSignInObserver;
    private boolean mPassive = false;

    private ProgressDialog mSignOutProgressDialog;
    private Runnable mSignOutCallback;

    private AlertDialog mPolicyConfirmationDialog;

    /**
     * SignInAllowedObservers will be notified once signing-in becomes allowed or disallowed.
     */
    public static interface SignInAllowedObserver {
        /**
         * Invoked once all startup checks are done and signing-in becomes allowed, or disallowed.
         */
        public void onSignInAllowedChanged();
    }

    /**
     * The Observer of startSignIn() will be notified when sign-in completes.
     */
    public static interface Observer {
        /**
         * Invoked after sign-in completed successfully.
         */
        public void onSigninComplete();

        /**
         * Invoked when the sign-in process was cancelled by the user.
         *
         * The user should have the option of going back and starting the process again,
         * if possible.
         */
        public void onSigninCancelled();
    }

    /**
     * A helper method for retrieving the application-wide SigninManager.
     * <p/>
     * Can only be accessed on the main thread.
     *
     * @param context the ApplicationContext is retrieved from the context used as an argument.
     * @return a singleton instance of the SigninManager.
     */
    public static SigninManager get(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sSigninManager == null) {
            sSigninManager = new SigninManager(context);
        }
        return sSigninManager;
    }

    private SigninManager(Context context) {
        ThreadUtils.assertOnUiThread();
        mContext = context.getApplicationContext();
        mNativeSigninManagerAndroid = nativeInit();
    }

    /**
     * Notifies the SigninManager that the First Run check has completed.
     *
     * The user will be allowed to sign-in once this is signaled.
     */
    public void onFirstRunCheckDone() {
        mFirstRunCheckIsPending = false;

        if (isSignInAllowed()) {
            notifySignInAllowedChanged();
        }
    }

    /**
     * Returns true if signin can be started now.
     */
    public boolean isSignInAllowed() {
        return !mFirstRunCheckIsPending &&
                mSignInAccount == null &&
                ChromeSigninController.get(mContext).getSignedInUser() == null;
    }

    public void addSignInAllowedObserver(SignInAllowedObserver observer) {
        mSignInAllowedObservers.addObserver(observer);
    }

    public void removeSignInAllowedObserver(SignInAllowedObserver observer) {
        mSignInAllowedObservers.removeObserver(observer);
    }

    private void notifySignInAllowedChanged() {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                for (SignInAllowedObserver observer : mSignInAllowedObservers) {
                    observer.onSignInAllowedChanged();
                }
            }
        });
    }

    /**
     * Starts the sign-in flow, and executes the callback when ready to proceed.
     * <p/>
     * This method checks with the native side whether the account has management enabled, and may
     * present a dialog to the user to confirm sign-in. The callback is invoked once these processes
     * and the common sign-in initialization complete.
     *
     * @param activity The context to use for the operation.
     * @param account The account to sign in to.
     * @param passive If passive is true then this operation should not interact with the user.
     * @param callback The Observer to notify when the sign-in process is finished.
     */
    public void startSignIn(
            Activity activity, final Account account, boolean passive, final Observer observer) {
        assert mSignInActivity == null;
        assert mSignInAccount == null;
        assert mSignInObserver == null;

        if (mFirstRunCheckIsPending) {
            Log.w(TAG, "Ignoring sign-in request until the First Run check completes.");
            return;
        }

        mSignInActivity = activity;
        mSignInAccount = account;
        mSignInObserver = observer;
        mPassive = passive;

        notifySignInAllowedChanged();

        if (!nativeShouldLoadPolicyForUser(account.name)) {
            // Proceed with the sign-in flow without checking for policy if it can be determined
            // that this account can't have management enabled based on the username.
            doSignIn();
            return;
        }

        Log.d(TAG, "Checking if account has policy management enabled");
        // This will call back to onPolicyCheckedBeforeSignIn.
        nativeCheckPolicyBeforeSignIn(mNativeSigninManagerAndroid, account.name);
    }

    @CalledByNative
    private void onPolicyCheckedBeforeSignIn(String managementDomain) {
        if (managementDomain == null) {
            Log.d(TAG, "Account doesn't have policy");
            doSignIn();
            return;
        }

        if (mSignInActivity.isDestroyed()) {
            // The activity is no longer running, cancel sign in.
            cancelSignIn();
            return;
        }

        if (mPassive) {
            // If this is a passive interaction (e.g. auto signin) then don't show the confirmation
            // dialog.
            nativeFetchPolicyBeforeSignIn(mNativeSigninManagerAndroid);
            return;
        }

        Log.d(TAG, "Account has policy management");
        AlertDialog.Builder builder = new AlertDialog.Builder(mSignInActivity);
        builder.setTitle(R.string.policy_dialog_title);
        builder.setMessage(mContext.getResources().getString(R.string.policy_dialog_message,
                                                             managementDomain));
        builder.setPositiveButton(
                R.string.policy_dialog_proceed,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Log.d(TAG, "Accepted policy management, proceeding with sign-in");
                        // This will call back to onPolicyFetchedBeforeSignIn.
                        nativeFetchPolicyBeforeSignIn(mNativeSigninManagerAndroid);
                        mPolicyConfirmationDialog = null;
                    }
                });
        builder.setNegativeButton(
                R.string.policy_dialog_cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Log.d(TAG, "Cancelled sign-in");
                        cancelSignIn();
                        mPolicyConfirmationDialog = null;
                    }
                });
        builder.setOnDismissListener(
                new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (mPolicyConfirmationDialog != null) {
                            Log.d(TAG, "Policy dialog dismissed, cancelling sign-in.");
                            cancelSignIn();
                            mPolicyConfirmationDialog = null;
                        }
                    }
                });
        mPolicyConfirmationDialog = builder.create();
        mPolicyConfirmationDialog.show();
    }

    @CalledByNative
    private void onPolicyFetchedBeforeSignIn() {
        // Policy has been fetched for the user and is being enforced; features like sync may now
        // be disabled by policy, and the rest of the sign-in flow can be resumed.
        doSignIn();
    }

    private void doSignIn() {
        Log.d(TAG, "Committing the sign-in process now");
        assert mSignInAccount != null;

        // Cache the signed-in account name.
        ChromeSigninController.get(mContext).setSignedInAccountName(mSignInAccount.name);

        // Tell the native side that sign-in has completed.
        // This will trigger NOTIFICATION_GOOGLE_SIGNIN_SUCCESSFUL.
        nativeOnSignInCompleted(mNativeSigninManagerAndroid, mSignInAccount.name);

        // Register for invalidations.
        InvalidationController invalidationController = InvalidationController.get(mContext);
        invalidationController.setRegisteredTypes(mSignInAccount, true, new HashSet<ModelType>());

        // Sign-in to sync.
        ProfileSyncService profileSyncService = ProfileSyncService.get(mContext);
        if (SyncStatusHelper.get(mContext).isSyncEnabled(mSignInAccount) &&
                !profileSyncService.hasSyncSetupCompleted()) {
            profileSyncService.setSetupInProgress(true);
            profileSyncService.syncSignIn();
        }

        if (mSignInObserver != null)
            mSignInObserver.onSigninComplete();

        // All done, cleanup.
        Log.d(TAG, "Signin done");
        mSignInActivity = null;
        mSignInAccount = null;
        mSignInObserver = null;
        notifySignInAllowedChanged();
    }

    /**
     * Signs out of Chrome.
     * <p/>
     * This method clears the signed-in username, stops sync and sends out a
     * sign-out notification on the native side.
     *
     * @param activity If not null then a progress dialog is shown over the activity until signout
     * completes, in case the account had management enabled. The activity must be valid until the
     * callback is invoked.
     * @param callback Will be invoked after signout completes, if not null.
     */
    public void signOut(Activity activity, Runnable callback) {
        mSignOutCallback = callback;

        boolean wipeData = getManagementDomain() != null;
        Log.d(TAG, "Signing out, wipe data? " + wipeData);

        ChromeSigninController.get(mContext).clearSignedInUser();
        ProfileSyncService.get(mContext).signOut();
        nativeSignOut(mNativeSigninManagerAndroid);

        if (wipeData) {
            wipeProfileData(activity);
        } else {
            onSignOutDone();
        }
    }

    /**
     * Returns the management domain if the signed in account is managed, otherwise returns null.
     */
    public String getManagementDomain() {
        return nativeGetManagementDomain(mNativeSigninManagerAndroid);
    }

    public void logInSignedInUser() {
        nativeLogInSignedInUser(mNativeSigninManagerAndroid);
    }

    private void cancelSignIn() {
        if (mSignInObserver != null)
            mSignInObserver.onSigninCancelled();
        mSignInActivity = null;
        mSignInObserver = null;
        mSignInAccount = null;
        notifySignInAllowedChanged();
    }

    private void wipeProfileData(Activity activity) {
        if (activity != null) {
            // We don't have progress update, so this takes an indeterminate amount of time.
            boolean indeterminate = true;
            // This dialog is not cancelable by the user.
            boolean cancelable = false;
            mSignOutProgressDialog = ProgressDialog.show(
                activity,
                activity.getString(R.string.wiping_profile_data_title),
                activity.getString(R.string.wiping_profile_data_message),
                indeterminate, cancelable);
        }
        // This will call back to onProfileDataWiped().
        nativeWipeProfileData(mNativeSigninManagerAndroid);
    }

    @CalledByNative
    private void onProfileDataWiped() {
        if (mSignOutProgressDialog != null && mSignOutProgressDialog.isShowing())
            mSignOutProgressDialog.dismiss();
        onSignOutDone();
    }

    private void onSignOutDone() {
        if (mSignOutCallback != null) {
            new Handler().post(mSignOutCallback);
            mSignOutCallback = null;
        }
    }

    // Native methods.
    private native int nativeInit();
    private native boolean nativeShouldLoadPolicyForUser(String username);
    private native void nativeCheckPolicyBeforeSignIn(
            int nativeSigninManagerAndroid, String username);
    private native void nativeFetchPolicyBeforeSignIn(int nativeSigninManagerAndroid);
    private native void nativeOnSignInCompleted(int nativeSigninManagerAndroid, String username);
    private native void nativeSignOut(int nativeSigninManagerAndroid);
    private native String nativeGetManagementDomain(int nativeSigninManagerAndroid);
    private native void nativeWipeProfileData(int nativeSigninManagerAndroid);
    private native void nativeLogInSignedInUser(int nativeSigninManagerAndroid);
}

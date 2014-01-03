// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.sync;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.ActivityStatus;
import org.chromium.sync.notifier.SyncStatusHelper;
import org.chromium.sync.signin.AccountManagerHelper;

/**
 * A class for controlling when a sync should be performed immediately, and when it should be
 * delayed until Chrome comes to the foreground again.
 */
public class DelayedSyncController {
    private static final String TAG = "DelayedSyncController";
    private static final String DELAYED_ACCOUNT_NAME = "delayed_account";

    private static class LazyHolder {
        private static final DelayedSyncController INSTANCE = new DelayedSyncController();
    }

    public static DelayedSyncController getInstance() {
        return LazyHolder.INSTANCE;
    }

    @VisibleForTesting
    DelayedSyncController() {}

    /**
     * Resume any syncs that were delayed while Chromium was backgrounded.
     */
    public boolean resumeDelayedSyncs(final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String accountName = prefs.getString(DELAYED_ACCOUNT_NAME, null);
        if (accountName == null) {
            Log.d(TAG, "No delayed sync.");
            return false;
        } else {
            Log.d(TAG, "Handling delayed sync.");
            Account account = AccountManagerHelper.createAccountFromName(accountName);
            requestSyncOnBackgroundThread(context, account);
            return true;
        }
    }

    /**
     * Calls ContentResolver.requestSync() in a separate thread as it performs some blocking
     * IO operations.
     */
    @VisibleForTesting
    void requestSyncOnBackgroundThread(final Context context, final Account account) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                String contractAuthority =
                        SyncStatusHelper.get(context).getContractAuthority();
                ContentResolver.requestSync(account, contractAuthority, new Bundle());
                return null;
            }
        }.execute();
    }

    /**
     * Stores preferences to indicate that an invalidation has arrived, but dropped on the floor.
     */
    void setDelayedSync(Context ctx, String accountName) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(ctx).edit();
        editor.putString(DELAYED_ACCOUNT_NAME, accountName);
        editor.apply();
    }

    /**
     * If there is a delayed sync, it will be cleared.
     */
    @VisibleForTesting
    void clearDelayedSyncs(Context context) {
        setDelayedSync(context, null);
    }

    @VisibleForTesting
    boolean shouldPerformSync(Context ctx, Bundle extras, Account account) {
        boolean manualSync = isManualSync(extras);

        if (manualSync || ActivityStatus.isApplicationVisible()) {
            clearDelayedSyncs(ctx);
            return true;
        } else {
            Log.d(TAG, "Delaying sync.");
            setDelayedSync(ctx, account.name);
            return false;
        }
    }

    private static boolean isManualSync(Bundle extras) {
        boolean manualSync = false;
        if (extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false)) {
            manualSync = true;
            Log.d(TAG, "Manual sync requested.");
        }
        return manualSync;
    }
}

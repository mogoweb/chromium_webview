// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.invalidation;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import org.chromium.base.ActivityStatus;
import org.chromium.base.CalledByNative;
import org.chromium.sync.internal_api.pub.base.ModelType;
import org.chromium.sync.notifier.InvalidationIntentProtocol;
import org.chromium.sync.notifier.InvalidationPreferences;
import org.chromium.sync.notifier.InvalidationService;
import org.chromium.sync.notifier.SyncStatusHelper;

import java.util.Set;

/**
 * Controller used to send start, stop, and registration-change commands to the invalidation
 * client library used by Sync.
 */
public class InvalidationController implements ActivityStatus.StateListener {
    private static final Object LOCK = new Object();

    private static InvalidationController sInstance;

    private final Context mContext;

    /**
     * Sets the types for which the client should register for notifications.
     *
     * @param account  Account of the user.
     * @param allTypes If {@code true}, registers for all types, and {@code types} is ignored
     * @param types    Set of types for which to register. Ignored if {@code allTypes == true}.
     */
    public void setRegisteredTypes(Account account, boolean allTypes, Set<ModelType> types) {
        Intent registerIntent =
                InvalidationIntentProtocol.createRegisterIntent(account, allTypes, types);
        registerIntent.setClass(mContext, InvalidationService.class);
        mContext.startService(registerIntent);
    }

    /**
     * Reads all stored preferences and calls
     * {@link #setRegisteredTypes(android.accounts.Account, boolean, java.util.Set)} with the stored
     * values, refreshing the set of types with {@code types}. It can be used on startup of Chrome
     * to ensure we always have a set of registrations consistent with the native code.
     * @param types    Set of types for which to register.
     */
    public void refreshRegisteredTypes(Set<ModelType> types) {
        InvalidationPreferences invalidationPreferences = new InvalidationPreferences(mContext);
        Set<String> savedSyncedTypes = invalidationPreferences.getSavedSyncedTypes();
        Account account = invalidationPreferences.getSavedSyncedAccount();
        boolean allTypes = savedSyncedTypes != null &&
                savedSyncedTypes.contains(ModelType.ALL_TYPES_TYPE);
        setRegisteredTypes(account, allTypes, types);
    }

    /**
     * Sets object ids for which the client should register for notification. This is intended for
     * registering non-Sync types; Sync types are registered with {@code setRegisteredTypes}.
     *
     * @param objectSources The sources of the objects.
     * @param objectNames   The names of the objects.
     */
    @CalledByNative
    public void setRegisteredObjectIds(int[] objectSources, String[] objectNames) {
        InvalidationPreferences invalidationPreferences = new InvalidationPreferences(mContext);
        Account account = invalidationPreferences.getSavedSyncedAccount();
        Intent registerIntent =
                InvalidationIntentProtocol.createRegisterIntent(
                        account, objectSources, objectNames);
        registerIntent.setClass(mContext, InvalidationService.class);
        mContext.startService(registerIntent);
    }

    /**
     * Starts the invalidation client.
     */
    public void start() {
        Intent intent = new Intent(mContext, InvalidationService.class);
        mContext.startService(intent);
    }

    /**
     * Stops the invalidation client.
     */
    public void stop() {
        Intent intent = new Intent(mContext, InvalidationService.class);
        intent.putExtra(InvalidationIntentProtocol.EXTRA_STOP, true);
        mContext.startService(intent);
    }

    /**
     * Returns the instance that will use {@code context} to issue intents.
     *
     * Calling this method will create the instance if it does not yet exist.
     */
    @CalledByNative
    public static InvalidationController get(Context context) {
        synchronized (LOCK) {
            if (sInstance == null) {
                sInstance = new InvalidationController(context);
            }
            return sInstance;
        }
    }

    /**
     * Creates an instance using {@code context} to send intents.
     */
    @VisibleForTesting
    InvalidationController(Context context) {
        mContext = Preconditions.checkNotNull(context.getApplicationContext());
        ActivityStatus.registerStateListener(this);
    }

    @Override
    public void onActivityStateChange(int newState) {
        if (SyncStatusHelper.get(mContext).isSyncEnabled()) {
            if (newState == ActivityStatus.PAUSED) {
                stop();
            } else if (newState == ActivityStatus.RESUMED) {
                start();
            }
        }
    }
}

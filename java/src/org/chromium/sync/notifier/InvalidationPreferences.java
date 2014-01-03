// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sync.notifier;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.ipc.invalidation.external.client.types.ObjectId;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Class to manage the preferences used by the invalidation client.
 * <p>
 * This class provides methods to read and write the preferences used by the invalidation client.
 * <p>
 * To read a preference, call the appropriate {@code get...} method.
 * <p>
 * To write a preference, first call {@link #edit} to obtain a {@link EditContext}. Then, make
 * one or more calls to a {@code set...} method, providing the same edit context to each call.
 * Finally, call {@link #commit(EditContext)} to save the changes to stable storage.
 *
 * @author dsmyers@google.com (Daniel Myers)
 */
public class InvalidationPreferences {
    /**
     * Wrapper around a {@link android.content.SharedPreferences.Editor} for the preferences.
     * Used to avoid exposing raw preference objects to users of this class.
     */
    public class EditContext {
        private final SharedPreferences.Editor editor;

        EditContext() {
            this.editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        }
    }

    @VisibleForTesting
    public static class PrefKeys {
        /**
         * Shared preference key to store the invalidation types that we want to register
         * for.
         */
        @VisibleForTesting
        public static final String SYNC_TANGO_TYPES = "sync_tango_types";

        /**
         * Shared preference key to store tango object ids for additional objects that we want to
         * register for.
         */
        @VisibleForTesting
        public static final String TANGO_OBJECT_IDS = "tango_object_ids";

        /** Shared preference key to store the name of the account in use. */
        @VisibleForTesting
        public static final String SYNC_ACCT_NAME = "sync_acct_name";

        /** Shared preference key to store the type of account in use. */
        static final String SYNC_ACCT_TYPE = "sync_acct_type";

        /** Shared preference key to store internal notification client library state. */
        static final String SYNC_TANGO_INTERNAL_STATE = "sync_tango_internal_state";
    }

    private static final String TAG = "InvalidationPreferences";

    private final Context mContext;

    public InvalidationPreferences(Context context) {
        this.mContext = Preconditions.checkNotNull(context.getApplicationContext());
    }

    /** Returns a new {@link EditContext} to modify the preferences managed by this class. */
    public EditContext edit() {
        return new EditContext();
    }

    /**
     * Applies the changes accumulated in {@code editContext}. Returns whether they were
     * successfully written.
     * <p>
     * NOTE: this method performs blocking I/O and must not be called from the UI thread.
     */
    public boolean commit(EditContext editContext) {
        if (!editContext.editor.commit()) {
            Log.w(TAG, "Failed to commit invalidation preferences");
            return false;
        }
        return true;
    }

    /** Returns the saved sync types, or {@code null} if none exist. */
    @Nullable public Set<String> getSavedSyncedTypes() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getStringSet(PrefKeys.SYNC_TANGO_TYPES, null);
    }

    /** Sets the saved sync types to {@code syncTypes} in {@code editContext}. */
    public void setSyncTypes(EditContext editContext, Collection<String> syncTypes) {
        Preconditions.checkNotNull(syncTypes);
        Set<String> selectedTypesSet = new HashSet<String>(syncTypes);
        editContext.editor.putStringSet(PrefKeys.SYNC_TANGO_TYPES, selectedTypesSet);
    }

    /** Returns the saved non-sync object ids, or {@code null} if none exist. */
    @Nullable
    public Set<ObjectId> getSavedObjectIds() {
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
      Set<String> objectIdStrings = preferences.getStringSet(PrefKeys.TANGO_OBJECT_IDS, null);
      if (objectIdStrings == null) {
          return null;
      }
      Set<ObjectId> objectIds = new HashSet<ObjectId>(objectIdStrings.size());
      for (String objectIdString : objectIdStrings) {
          ObjectId objectId = getObjectId(objectIdString);
          if (objectId != null) {
              objectIds.add(objectId);
          }
      }
      return objectIds;
    }

    /** Sets the saved non-sync object ids */
    public void setObjectIds(EditContext editContext, Collection<ObjectId> objectIds) {
      Preconditions.checkNotNull(objectIds);
      Set<String> objectIdStrings = new HashSet<String>(objectIds.size());
      for (ObjectId objectId : objectIds) {
          objectIdStrings.add(getObjectIdString(objectId));
      }
      editContext.editor.putStringSet(PrefKeys.TANGO_OBJECT_IDS, objectIdStrings);
    }

    /** Returns the saved account, or {@code null} if none exists. */
    @Nullable public Account getSavedSyncedAccount() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String accountName = preferences.getString(PrefKeys.SYNC_ACCT_NAME, null);
        String accountType = preferences.getString(PrefKeys.SYNC_ACCT_TYPE, null);
        if (accountName == null || accountType == null) {
            return null;
        }
        return new Account(accountName, accountType);
    }

    /** Sets the saved account to {@code account} in {@code editContext}. */
    public void setAccount(EditContext editContext, Account account) {
        editContext.editor.putString(PrefKeys.SYNC_ACCT_NAME, account.name);
        editContext.editor.putString(PrefKeys.SYNC_ACCT_TYPE, account.type);
    }

    /** Returns the notification client internal state. */
    @Nullable public byte[] getInternalNotificationClientState() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String base64State = preferences.getString(PrefKeys.SYNC_TANGO_INTERNAL_STATE, null);
        if (base64State == null) {
            return null;
        }
        return Base64.decode(base64State, Base64.DEFAULT);
    }

    /** Sets the notification client internal state to {@code state}. */
    public void setInternalNotificationClientState(EditContext editContext, byte[] state) {
        editContext.editor.putString(PrefKeys.SYNC_TANGO_INTERNAL_STATE,
                Base64.encodeToString(state, Base64.DEFAULT));
    }

    /** Converts the given object id to a string for storage in preferences. */
    private String getObjectIdString(ObjectId objectId) {
        return objectId.getSource() + ":" + new String(objectId.getName());
    }

    /**
     * Converts the given object id string stored in preferences to an object id.
     * Returns null if the string does not represent a valid object id.
     */
    private ObjectId getObjectId(String objectIdString) {
        int separatorPos = objectIdString.indexOf(':');
        // Ensure that the separator is surrounded by at least one character on each side.
        if (separatorPos < 1 || separatorPos == objectIdString.length() - 1) {
            return null;
        }
        int objectSource;
        try {
            objectSource = Integer.parseInt(objectIdString.substring(0, separatorPos));
        } catch (NumberFormatException e) {
            return null;
        }
        byte[] objectName = objectIdString.substring(separatorPos + 1).getBytes();
        return ObjectId.newInstance(objectSource, objectName);
    }
}

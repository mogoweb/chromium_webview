/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogoweb.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages the interaction between the secure system setting for default geolocation
 * permissions and the browser.
 */
class SystemAllowGeolocationOrigins {

    // Preference key for the value of the system setting last read by the browser
    private final static String LAST_READ_ALLOW_GEOLOCATION_ORIGINS =
            "last_read_allow_geolocation_origins";

    // The application context
    private final Context mContext;

    // The observer used to listen to the system setting.
    private final SettingObserver mSettingObserver;

    public SystemAllowGeolocationOrigins(Context context) {
        mContext = context.getApplicationContext();
        mSettingObserver = new SettingObserver();
    }

    /**
     * Checks whether the setting has changed and installs an observer to listen for
     * future changes. Must be called on the application main thread.
     */
    public void start() {
        // Register to receive notifications when the system settings change.
        Uri uri = Settings.Secure.getUriFor(Settings.Secure.ALLOWED_GEOLOCATION_ORIGINS);
        mContext.getContentResolver().registerContentObserver(uri, false, mSettingObserver);

        // Read and apply the setting if needed.
        maybeApplySettingAsync();
    }

    /**
     * Stops the manager.
     */
    public void stop() {
        mContext.getContentResolver().unregisterContentObserver(mSettingObserver);
    }

    void maybeApplySettingAsync() {
        BackgroundHandler.execute(mMaybeApplySetting);
    }

    /**
     * Checks to see if the system setting has changed and if so,
     * updates the Geolocation permissions accordingly.
     */
    private Runnable mMaybeApplySetting = new Runnable() {

        @Override
        public void run() {
         // Get the new value
            String newSetting = getSystemSetting();

            // Get the last read value
            SharedPreferences preferences = BrowserSettings.getInstance()
                    .getPreferences();
            String lastReadSetting =
                    preferences.getString(LAST_READ_ALLOW_GEOLOCATION_ORIGINS, "");

            // If the new value is the same as the last one we read, we're done.
            if (TextUtils.equals(lastReadSetting, newSetting)) {
                return;
            }

            // Save the new value as the last read value
            preferences.edit()
                    .putString(LAST_READ_ALLOW_GEOLOCATION_ORIGINS, newSetting)
                    .apply();

            Set<String> oldOrigins = parseAllowGeolocationOrigins(lastReadSetting);
            Set<String> newOrigins = parseAllowGeolocationOrigins(newSetting);
            Set<String> addedOrigins = setMinus(newOrigins, oldOrigins);
            Set<String> removedOrigins = setMinus(oldOrigins, newOrigins);

            // Remove the origins in the last read value
            removeOrigins(removedOrigins);

            // Add the origins in the new value
            addOrigins(addedOrigins);
        }
    };

    /**
     * Parses the value of the default geolocation permissions setting.
     *
     * @param setting A space-separated list of origins.
     * @return A mutable set of origins.
     */
    private static HashSet<String> parseAllowGeolocationOrigins(String setting) {
        HashSet<String> origins = new HashSet<String>();
        if (!TextUtils.isEmpty(setting)) {
            for (String origin : setting.split("\\s+")) {
                if (!TextUtils.isEmpty(origin)) {
                    origins.add(origin);
                }
            }
        }
        return origins;
    }

    /**
     * Gets the difference between two sets. Does not modify any of the arguments.
     *
     * @return A set containing all elements in {@code x} that are not in {@code y}.
     */
    private <A> Set<A> setMinus(Set<A> x, Set<A> y) {
        HashSet<A> z = new HashSet<A>(x.size());
        for (A a : x) {
            if (!y.contains(a)) {
                z.add(a);
            }
        }
        return z;
    }

    /**
     * Gets the current system setting for default allowed geolocation origins.
     *
     * @return The default allowed origins. Returns {@code ""} if not set.
     */
    private String getSystemSetting() {
        String value = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ALLOWED_GEOLOCATION_ORIGINS);
        return value == null ? "" : value;
    }

    /**
     * Adds geolocation permissions for the given origins.
     */
    private void addOrigins(Set<String> origins) {
        for (String origin : origins) {
            GeolocationPermissions.getInstance().allow(origin);
        }
    }

    /**
     * Removes geolocation permissions for the given origins, if they are allowed.
     * If they are denied or not set, nothing is done.
     */
    private void removeOrigins(Set<String> origins) {
        for (final String origin : origins) {
            GeolocationPermissions.getInstance().getAllowed(origin, new ValueCallback<Boolean>() {
                public void onReceiveValue(Boolean value) {
                    if (value != null && value.booleanValue()) {
                        GeolocationPermissions.getInstance().clear(origin);
                    }
                }
            });
        }
    }

    /**
     * Listens for changes to the system setting.
     */
    private class SettingObserver extends ContentObserver {

        SettingObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            maybeApplySettingAsync();
        }
    }

}

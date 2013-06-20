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
 * limitations under the License
 */

package com.mogoweb.browser.preferences;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;

import com.mogoweb.browser.BrowserSettings;
import com.mogoweb.browser.GoogleAccountLogin;
import com.mogoweb.browser.PreferenceKeys;
import com.mogoweb.browser.R;

public class DebugPreferencesFragment extends PreferenceFragment
        implements OnPreferenceClickListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML preferences file
        addPreferencesFromResource(R.xml.debug_preferences);

        Preference e = findPreference(PreferenceKeys.PREF_RESET_PRELOGIN);
        e.setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (PreferenceKeys.PREF_RESET_PRELOGIN.equals(preference.getKey())) {
            BrowserSettings.getInstance().getPreferences().edit()
                    .remove(GoogleAccountLogin.PREF_AUTOLOGIN_TIME)
                    .apply();
            return true;
        }
        return false;
    }
}

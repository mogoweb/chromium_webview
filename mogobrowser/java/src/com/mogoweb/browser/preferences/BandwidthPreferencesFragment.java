/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import com.mogoweb.browser.BrowserSettings;
import com.mogoweb.browser.PreferenceKeys;
import com.mogoweb.browser.R;

public class BandwidthPreferencesFragment extends PreferenceFragment {

    static final String TAG = "BandwidthPreferencesFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the XML preferences file
        addPreferencesFromResource(R.xml.bandwidth_preferences);
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceScreen prefScreen = getPreferenceScreen();
        SharedPreferences sharedPrefs = prefScreen.getSharedPreferences();
        if (!sharedPrefs.contains(PreferenceKeys.PREF_DATA_PRELOAD)) {
            // set default value for preload setting
            ListPreference preload = (ListPreference) prefScreen.findPreference(
                    PreferenceKeys.PREF_DATA_PRELOAD);
            if (preload != null) {
                preload.setValue(BrowserSettings.getInstance().getDefaultPreloadSetting());
            }
        }
        if (!sharedPrefs.contains(PreferenceKeys.PREF_LINK_PREFETCH)) {
            // set default value for link prefetch setting
            ListPreference prefetch = (ListPreference) prefScreen.findPreference(
                    PreferenceKeys.PREF_LINK_PREFETCH);
            if (prefetch != null) {
                prefetch.setValue(BrowserSettings.getInstance().getDefaultLinkPrefetchSetting());
            }
        }
    }

}

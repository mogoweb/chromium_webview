///*
// * Copyright (C) 2010 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.mogoweb.browser.search;
//
//import com.mogoweb.browser.R;
//
//import android.app.SearchManager;
//import android.content.ComponentName;
//import android.content.Context;
//import android.content.pm.ActivityInfo;
//import android.content.pm.PackageManager;
//import android.content.res.Resources;
//import android.preference.ListPreference;
//import android.util.AttributeSet;
//import android.util.Log;
//
//import java.util.ArrayList;
//
//class SearchEnginePreference extends ListPreference {
//
//    private static final String TAG = "SearchEnginePreference";
//
//    public SearchEnginePreference(Context context, AttributeSet attrs) {
//        super(context, attrs);
//
//        ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();
//        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
//
//        SearchEngine defaultSearchEngine = SearchEngines.getDefaultSearchEngine(context);
//        String defaultSearchEngineName = null;
//        if (defaultSearchEngine != null) {
//            defaultSearchEngineName = defaultSearchEngine.getName();
//            entryValues.add(defaultSearchEngineName);
//            entries.add(defaultSearchEngine.getLabel());
//        }
//        for (SearchEngineInfo searchEngineInfo : SearchEngines.getSearchEngineInfos(context)) {
//            String name = searchEngineInfo.getName();
//            // Skip entry with same name as default provider
//            if (!name.equals(defaultSearchEngineName)) {
//                entryValues.add(name);
//                entries.add(searchEngineInfo.getLabel());
//            }
//        }
//
//        setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));
//        setEntries(entries.toArray(new CharSequence[entries.size()]));
//    }
//
//}

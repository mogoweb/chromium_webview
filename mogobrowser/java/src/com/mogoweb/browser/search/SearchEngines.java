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
//import android.content.Context;
//import android.content.res.Resources;
//import android.text.TextUtils;
//import android.util.Log;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class SearchEngines {
//
//    private static final String TAG = "SearchEngines";
//
//    public static SearchEngine getDefaultSearchEngine(Context context) {
//        return DefaultSearchEngine.create(context);
//    }
//
//    public static List<SearchEngineInfo> getSearchEngineInfos(Context context) {
//        ArrayList<SearchEngineInfo> searchEngineInfos = new ArrayList<SearchEngineInfo>();
//        Resources res = context.getResources();
//        String[] searchEngines = res.getStringArray(R.array.search_engines);
//        for (int i = 0; i < searchEngines.length; i++) {
//            String name = searchEngines[i];
//            SearchEngineInfo info = new SearchEngineInfo(context, name);
//            searchEngineInfos.add(info);
//        }
//        return searchEngineInfos;
//    }
//
//    public static SearchEngine get(Context context, String name) {
//        // TODO: cache
//        SearchEngine defaultSearchEngine = getDefaultSearchEngine(context);
//        if (TextUtils.isEmpty(name)
//                || (defaultSearchEngine != null && name.equals(defaultSearchEngine.getName()))) {
//            return defaultSearchEngine;
//        }
//        SearchEngineInfo searchEngineInfo = getSearchEngineInfo(context, name);
//        if (searchEngineInfo == null) return defaultSearchEngine;
//        return new OpenSearchSearchEngine(context, searchEngineInfo);
//    }
//
//    public static SearchEngineInfo getSearchEngineInfo(Context context, String name) {
//        try {
//            return new SearchEngineInfo(context, name);
//        } catch (IllegalArgumentException exception) {
//            Log.e(TAG, "Cannot load search engine " + name, exception);
//            return null;
//        }
//    }
//
//}

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
// * limitations under the License
// */
//
//package com.mogoweb.browser;
//
//import android.content.Context;
//import android.content.CursorLoader;
//import android.net.Uri;
//import android.provider.BrowserContract.Bookmarks;
//
//public class BookmarksLoader extends CursorLoader {
//    public static final String ARG_ACCOUNT_TYPE = "acct_type";
//    public static final String ARG_ACCOUNT_NAME = "acct_name";
//
//    public static final int COLUMN_INDEX_ID = 0;
//    public static final int COLUMN_INDEX_URL = 1;
//    public static final int COLUMN_INDEX_TITLE = 2;
//    public static final int COLUMN_INDEX_FAVICON = 3;
//    public static final int COLUMN_INDEX_THUMBNAIL = 4;
//    public static final int COLUMN_INDEX_TOUCH_ICON = 5;
//    public static final int COLUMN_INDEX_IS_FOLDER = 6;
//    public static final int COLUMN_INDEX_PARENT = 8;
//    public static final int COLUMN_INDEX_TYPE = 9;
//
//    public static final String[] PROJECTION = new String[] {
//        Bookmarks._ID, // 0
//        Bookmarks.URL, // 1
//        Bookmarks.TITLE, // 2
//        Bookmarks.FAVICON, // 3
//        Bookmarks.THUMBNAIL, // 4
//        Bookmarks.TOUCH_ICON, // 5
//        Bookmarks.IS_FOLDER, // 6
//        Bookmarks.POSITION, // 7
//        Bookmarks.PARENT, // 8
//        Bookmarks.TYPE, // 9
//    };
//
//    String mAccountType;
//    String mAccountName;
//
//    public BookmarksLoader(Context context, String accountType, String accountName) {
//        super(context, addAccount(Bookmarks.CONTENT_URI_DEFAULT_FOLDER, accountType, accountName),
//                PROJECTION, null, null, null);
//        mAccountType = accountType;
//        mAccountName = accountName;
//    }
//
//    @Override
//    public void setUri(Uri uri) {
//        super.setUri(addAccount(uri, mAccountType, mAccountName));
//    }
//
//    static Uri addAccount(Uri uri, String accountType, String accountName) {
//        return uri.buildUpon().appendQueryParameter(Bookmarks.PARAM_ACCOUNT_TYPE, accountType).
//                appendQueryParameter(Bookmarks.PARAM_ACCOUNT_NAME, accountName).build();
//    }
//}

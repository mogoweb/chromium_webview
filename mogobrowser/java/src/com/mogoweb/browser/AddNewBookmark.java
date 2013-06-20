///*
// * Copyright (C) 2008 The Android Open Source Project
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
//
//package com.mogoweb.browser;
//
//import android.content.Context;
//import android.view.LayoutInflater;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
///**
// *  Custom layout for an item representing a bookmark in the browser.
// */
// // FIXME: Remove BrowserBookmarkItem
//class AddNewBookmark extends LinearLayout {
//
//    private TextView    mUrlText;
//
//    /**
//     *  Instantiate a bookmark item, including a default favicon.
//     *
//     *  @param context  The application context for the item.
//     */
//    AddNewBookmark(Context context) {
//        super(context);
//
//        setWillNotDraw(false);
//        LayoutInflater factory = LayoutInflater.from(context);
//        factory.inflate(R.layout.add_new_bookmark, this);
//        mUrlText = (TextView) findViewById(R.id.url);
//    }
//
//    /**
//     *  Set the new url for the bookmark item.
//     *  @param url  The new url for the bookmark item.
//     */
//    /* package */ void setUrl(String url) {
//        mUrlText.setText(url);
//    }
//}

//
///*
// * Copyright (C) 2011 The Android Open Source Project
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
//package com.mogoweb.browser.homepages;
//
//import android.content.ContentProvider;
//import android.content.ContentValues;
//import android.content.Context;
//import android.content.res.AssetFileDescriptor;
//import android.database.Cursor;
//import android.net.Uri;
//import android.os.ParcelFileDescriptor;
//import android.util.Log;
//import android.webkit.WebResourceResponse;
//
//import com.mogoweb.browser.BrowserSettings;
//
//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.PipedInputStream;
//import java.io.PipedOutputStream;
//
//public class HomeProvider extends ContentProvider {
//
//    private static final String TAG = "HomeProvider";
//    public static final String AUTHORITY = "com.mogoweb.browser.home";
//    public static final String MOST_VISITED = "content://" + AUTHORITY + "/";
//
//    @Override
//    public int delete(Uri uri, String selection, String[] selectionArgs) {
//        return 0;
//    }
//
//    @Override
//    public String getType(Uri uri) {
//        return null;
//    }
//
//    @Override
//    public Uri insert(Uri uri, ContentValues values) {
//        return null;
//    }
//
//    @Override
//    public boolean onCreate() {
//        return false;
//    }
//
//    @Override
//    public Cursor query(Uri uri, String[] projection, String selection,
//            String[] selectionArgs, String sortOrder) {
//        return null;
//    }
//
//    @Override
//    public int update(Uri uri, ContentValues values, String selection,
//            String[] selectionArgs) {
//        return 0;
//    }
//
//    @Override
//    public ParcelFileDescriptor openFile(Uri uri, String mode) {
//        try {
//            ParcelFileDescriptor[] pipes = ParcelFileDescriptor.createPipe();
//            final ParcelFileDescriptor write = pipes[1];
//            AssetFileDescriptor afd = new AssetFileDescriptor(write, 0, -1);
//            new RequestHandler(getContext(), uri, afd.createOutputStream()).start();
//            return pipes[0];
//        } catch (IOException e) {
//            Log.e(TAG, "Failed to handle request: " + uri, e);
//            return null;
//        }
//    }
//
//    public static WebResourceResponse shouldInterceptRequest(Context context,
//            String url) {
//        try {
//            boolean useMostVisited = BrowserSettings.getInstance().useMostVisitedHomepage();
//            if (useMostVisited && url.startsWith("content://")) {
//                Uri uri = Uri.parse(url);
//                if (AUTHORITY.equals(uri.getAuthority())) {
//                    InputStream ins = context.getContentResolver()
//                            .openInputStream(uri);
//                    return new WebResourceResponse("text/html", "utf-8", ins);
//                }
//            }
//            boolean listFiles = BrowserSettings.getInstance().isDebugEnabled();
//            if (listFiles && interceptFile(url)) {
//                PipedInputStream ins = new PipedInputStream();
//                PipedOutputStream outs = new PipedOutputStream(ins);
//                new RequestHandler(context, Uri.parse(url), outs).start();
//                return new WebResourceResponse("text/html", "utf-8", ins);
//            }
//        } catch (Exception e) {}
//        return null;
//    }
//
//    private static boolean interceptFile(String url) {
//        if (!url.startsWith("file:///")) {
//            return false;
//        }
//        String fpath = url.substring(7);
//        File f = new File(fpath);
//        if (!f.isDirectory()) {
//            return false;
//        }
//        return true;
//    }
//
//}

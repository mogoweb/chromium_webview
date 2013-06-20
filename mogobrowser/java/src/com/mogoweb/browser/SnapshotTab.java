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
//package com.mogoweb.browser;
//
//import android.content.ContentResolver;
//import android.content.ContentUris;
//import android.content.ContentValues;
//import android.content.Context;
//import android.database.Cursor;
//import android.graphics.BitmapFactory;
//import android.net.Uri;
//import android.os.AsyncTask;
//import android.os.Bundle;
//import android.text.TextUtils;
//import android.util.Log;
//import android.webkit.WebView;
//import android.webkit.WebViewClassic;
//
//import com.mogoweb.browser.provider.SnapshotProvider.Snapshots;
//
//import java.io.ByteArrayInputStream;
//import java.io.FileNotFoundException;
//import java.io.InputStream;
//import java.util.Map;
//import java.util.zip.GZIPInputStream;
//
//
//public class SnapshotTab extends Tab {
//
//    private static final String LOGTAG = "SnapshotTab";
//
//    private long mSnapshotId;
//    private LoadData mLoadTask;
//    private WebViewFactory mWebViewFactory;
//    private int mBackgroundColor;
//    private long mDateCreated;
//    private boolean mIsLive;
//
//    public SnapshotTab(WebViewController wvcontroller, long snapshotId) {
//        super(wvcontroller, null, null);
//        mSnapshotId = snapshotId;
//        mWebViewFactory = mWebViewController.getWebViewFactory();
//        WebView web = mWebViewFactory.createWebView(false);
//        setWebView(web);
//        loadData();
//    }
//
//    @Override
//    void putInForeground() {
//        if (getWebView() == null) {
//            WebView web = mWebViewFactory.createWebView(false);
//            if (mBackgroundColor != 0) {
//                web.setBackgroundColor(mBackgroundColor);
//            }
//            setWebView(web);
//            loadData();
//        }
//        super.putInForeground();
//    }
//
//    @Override
//    void putInBackground() {
//        if (getWebView() == null) return;
//        super.putInBackground();
//    }
//
//    void loadData() {
//        if (mLoadTask == null) {
//            mLoadTask = new LoadData(this, mContext);
//            mLoadTask.execute();
//        }
//    }
//
//    @Override
//    void addChildTab(Tab child) {
//        if (mIsLive) {
//            super.addChildTab(child);
//        } else {
//            throw new IllegalStateException("Snapshot tabs cannot have child tabs!");
//        }
//    }
//
//    @Override
//    public boolean isSnapshot() {
//        return !mIsLive;
//    }
//
//    public long getSnapshotId() {
//        return mSnapshotId;
//    }
//
//    @Override
//    public ContentValues createSnapshotValues() {
//        if (mIsLive) {
//            return super.createSnapshotValues();
//        }
//        return null;
//    }
//
//    @Override
//    public Bundle saveState() {
//        if (mIsLive) {
//            return super.saveState();
//        }
//        return null;
//    }
//
//    public long getDateCreated() {
//        return mDateCreated;
//    }
//
//    @Override
//    public void loadUrl(String url, Map<String, String> headers) {
//        if (!mIsLive) {
//            mIsLive = true;
//            getWebViewClassic().clearViewState();
//        }
//        super.loadUrl(url, headers);
//    }
//
//    @Override
//    public boolean canGoBack() {
//        return super.canGoBack() || mIsLive;
//    }
//
//    @Override
//    public boolean canGoForward() {
//        return mIsLive && super.canGoForward();
//    }
//
//    @Override
//    public void goBack() {
//        if (super.canGoBack()) {
//            super.goBack();
//        } else {
//            mIsLive = false;
//            getWebView().stopLoading();
//            loadData();
//        }
//    }
//
//    static class LoadData extends AsyncTask<Void, Void, Cursor> {
//
//        static final String[] PROJECTION = new String[] {
//            Snapshots._ID, // 0
//            Snapshots.URL, // 1
//            Snapshots.TITLE, // 2
//            Snapshots.FAVICON, // 3
//            Snapshots.VIEWSTATE, // 4
//            Snapshots.BACKGROUND, // 5
//            Snapshots.DATE_CREATED, // 6
//            Snapshots.VIEWSTATE_PATH, // 7
//        };
//        static final int SNAPSHOT_ID = 0;
//        static final int SNAPSHOT_URL = 1;
//        static final int SNAPSHOT_TITLE = 2;
//        static final int SNAPSHOT_FAVICON = 3;
//        static final int SNAPSHOT_VIEWSTATE = 4;
//        static final int SNAPSHOT_BACKGROUND = 5;
//        static final int SNAPSHOT_DATE_CREATED = 6;
//        static final int SNAPSHOT_VIEWSTATE_PATH = 7;
//
//        private SnapshotTab mTab;
//        private ContentResolver mContentResolver;
//        private Context mContext;
//
//        public LoadData(SnapshotTab t, Context context) {
//            mTab = t;
//            mContentResolver = context.getContentResolver();
//            mContext = context;
//        }
//
//        @Override
//        protected Cursor doInBackground(Void... params) {
//            long id = mTab.mSnapshotId;
//            Uri uri = ContentUris.withAppendedId(Snapshots.CONTENT_URI, id);
//            return mContentResolver.query(uri, PROJECTION, null, null, null);
//        }
//
//        private InputStream getInputStream(Cursor c) throws FileNotFoundException {
//            String path = c.getString(SNAPSHOT_VIEWSTATE_PATH);
//            if (!TextUtils.isEmpty(path)) {
//                return mContext.openFileInput(path);
//            }
//            byte[] data = c.getBlob(SNAPSHOT_VIEWSTATE);
//            ByteArrayInputStream bis = new ByteArrayInputStream(data);
//            return bis;
//        }
//
//        @Override
//        protected void onPostExecute(Cursor result) {
//            try {
//                if (result.moveToFirst()) {
//                    mTab.mCurrentState.mTitle = result.getString(SNAPSHOT_TITLE);
//                    mTab.mCurrentState.mUrl = result.getString(SNAPSHOT_URL);
//                    byte[] favicon = result.getBlob(SNAPSHOT_FAVICON);
//                    if (favicon != null) {
//                        mTab.mCurrentState.mFavicon = BitmapFactory
//                                .decodeByteArray(favicon, 0, favicon.length);
//                    }
//                    WebViewClassic web = mTab.getWebViewClassic();
//                    if (web != null) {
//                        InputStream ins = getInputStream(result);
//                        GZIPInputStream stream = new GZIPInputStream(ins);
//                        web.loadViewState(stream);
//                    }
//                    mTab.mBackgroundColor = result.getInt(SNAPSHOT_BACKGROUND);
//                    mTab.mDateCreated = result.getLong(SNAPSHOT_DATE_CREATED);
//                    mTab.mWebViewController.onPageFinished(mTab);
//                }
//            } catch (Exception e) {
//                Log.w(LOGTAG, "Failed to load view state, closing tab", e);
//                mTab.mWebViewController.closeTab(mTab);
//            } finally {
//                if (result != null) {
//                    result.close();
//                }
//                mTab.mLoadTask = null;
//            }
//        }
//
//    }
//
//    @Override
//    protected void persistThumbnail() {
//        if (mIsLive) {
//            super.persistThumbnail();
//        }
//    }
//}

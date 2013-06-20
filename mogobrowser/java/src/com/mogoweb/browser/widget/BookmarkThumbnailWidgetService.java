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
//
//package com.mogoweb.browser.widget;
//
//import android.appwidget.AppWidgetManager;
//import android.content.ContentUris;
//import android.content.Context;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.database.Cursor;
//import android.database.MergeCursor;
//import android.graphics.Bitmap;
//import android.graphics.Bitmap.Config;
//import android.graphics.BitmapFactory;
//import android.graphics.BitmapFactory.Options;
//import android.net.Uri;
//import android.os.Binder;
//import android.provider.BrowserContract;
//import android.provider.BrowserContract.Bookmarks;
//import android.text.TextUtils;
//import android.util.Log;
//import android.widget.RemoteViews;
//import android.widget.RemoteViewsService;
//
//import com.mogoweb.browser.BrowserActivity;
//import com.mogoweb.browser.R;
//import com.mogoweb.browser.provider.BrowserProvider2;
//
//import java.io.File;
//import java.io.FilenameFilter;
//import java.util.HashSet;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//public class BookmarkThumbnailWidgetService extends RemoteViewsService {
//
//    static final String TAG = "BookmarkThumbnailWidgetService";
//    static final String ACTION_CHANGE_FOLDER
//            = "com.mogoweb.browser.widget.CHANGE_FOLDER";
//
//    static final String STATE_CURRENT_FOLDER = "current_folder";
//    static final String STATE_ROOT_FOLDER = "root_folder";
//
//    private static final String[] PROJECTION = new String[] {
//            BrowserContract.Bookmarks._ID,
//            BrowserContract.Bookmarks.TITLE,
//            BrowserContract.Bookmarks.URL,
//            BrowserContract.Bookmarks.FAVICON,
//            BrowserContract.Bookmarks.IS_FOLDER,
//            BrowserContract.Bookmarks.POSITION, /* needed for order by */
//            BrowserContract.Bookmarks.THUMBNAIL,
//            BrowserContract.Bookmarks.PARENT};
//    private static final int BOOKMARK_INDEX_ID = 0;
//    private static final int BOOKMARK_INDEX_TITLE = 1;
//    private static final int BOOKMARK_INDEX_URL = 2;
//    private static final int BOOKMARK_INDEX_FAVICON = 3;
//    private static final int BOOKMARK_INDEX_IS_FOLDER = 4;
//    private static final int BOOKMARK_INDEX_THUMBNAIL = 6;
//    private static final int BOOKMARK_INDEX_PARENT_ID = 7;
//
//    @Override
//    public RemoteViewsFactory onGetViewFactory(Intent intent) {
//        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
//        if (widgetId < 0) {
//            Log.w(TAG, "Missing EXTRA_APPWIDGET_ID!");
//            return null;
//        }
//        return new BookmarkFactory(getApplicationContext(), widgetId);
//    }
//
//    static SharedPreferences getWidgetState(Context context, int widgetId) {
//        return context.getSharedPreferences(
//                String.format("widgetState-%d", widgetId),
//                Context.MODE_PRIVATE);
//    }
//
//    static void deleteWidgetState(Context context, int widgetId) {
//        File file = context.getSharedPrefsFile(
//                String.format("widgetState-%d", widgetId));
//        if (file.exists()) {
//            if (!file.delete()) {
//                file.deleteOnExit();
//            }
//        }
//    }
//
//    static void changeFolder(Context context, Intent intent) {
//        int wid = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
//        long fid = intent.getLongExtra(Bookmarks._ID, -1);
//        if (wid >= 0 && fid >= 0) {
//            SharedPreferences prefs = getWidgetState(context, wid);
//            prefs.edit().putLong(STATE_CURRENT_FOLDER, fid).commit();
//            AppWidgetManager.getInstance(context)
//                    .notifyAppWidgetViewDataChanged(wid, R.id.bookmarks_list);
//        }
//    }
//
//    static void setupWidgetState(Context context, int widgetId, long rootFolder) {
//        SharedPreferences pref = getWidgetState(context, widgetId);
//        pref.edit()
//            .putLong(STATE_CURRENT_FOLDER, rootFolder)
//            .putLong(STATE_ROOT_FOLDER, rootFolder)
//            .apply();
//    }
//
//    /**
//     *  Checks for any state files that may have not received onDeleted
//     */
//    static void removeOrphanedStates(Context context, int[] widgetIds) {
//        File prefsDirectory = context.getSharedPrefsFile("null").getParentFile();
//        File[] widgetStates = prefsDirectory.listFiles(new StateFilter(widgetIds));
//        if (widgetStates != null) {
//            for (File f : widgetStates) {
//                Log.w(TAG, "Found orphaned state: " + f.getName());
//                if (!f.delete()) {
//                    f.deleteOnExit();
//                }
//            }
//        }
//    }
//
//    static class StateFilter implements FilenameFilter {
//
//        static final Pattern sStatePattern = Pattern.compile("widgetState-(\\d+)\\.xml");
//        HashSet<Integer> mWidgetIds;
//
//        StateFilter(int[] ids) {
//            mWidgetIds = new HashSet<Integer>();
//            for (int id : ids) {
//                mWidgetIds.add(id);
//            }
//        }
//
//        @Override
//        public boolean accept(File dir, String filename) {
//            Matcher m = sStatePattern.matcher(filename);
//            if (m.matches()) {
//                int id = Integer.parseInt(m.group(1));
//                if (!mWidgetIds.contains(id)) {
//                    return true;
//                }
//            }
//            return false;
//        }
//
//    }
//
//    static class BookmarkFactory implements RemoteViewsService.RemoteViewsFactory {
//        private Cursor mBookmarks;
//        private Context mContext;
//        private int mWidgetId;
//        private long mCurrentFolder = -1;
//        private long mRootFolder = -1;
//        private SharedPreferences mPreferences = null;
//
//        public BookmarkFactory(Context context, int widgetId) {
//            mContext = context.getApplicationContext();
//            mWidgetId = widgetId;
//        }
//
//        void syncState() {
//            if (mPreferences == null) {
//                mPreferences = getWidgetState(mContext, mWidgetId);
//            }
//            long currentFolder = mPreferences.getLong(STATE_CURRENT_FOLDER, -1);
//            mRootFolder = mPreferences.getLong(STATE_ROOT_FOLDER, -1);
//            if (currentFolder != mCurrentFolder) {
//                resetBookmarks();
//                mCurrentFolder = currentFolder;
//            }
//        }
//
//        void saveState() {
//            if (mPreferences == null) {
//                mPreferences = getWidgetState(mContext, mWidgetId);
//            }
//            mPreferences.edit()
//                .putLong(STATE_CURRENT_FOLDER, mCurrentFolder)
//                .putLong(STATE_ROOT_FOLDER, mRootFolder)
//                .commit();
//        }
//
//        @Override
//        public int getCount() {
//            if (mBookmarks == null)
//                return 0;
//            return mBookmarks.getCount();
//        }
//
//        @Override
//        public long getItemId(int position) {
//            return position;
//        }
//
//        @Override
//        public RemoteViews getLoadingView() {
//            return new RemoteViews(
//                    mContext.getPackageName(), R.layout.bookmarkthumbnailwidget_item);
//        }
//
//        @Override
//        public RemoteViews getViewAt(int position) {
//            if (!mBookmarks.moveToPosition(position)) {
//                return null;
//            }
//
//            long id = mBookmarks.getLong(BOOKMARK_INDEX_ID);
//            String title = mBookmarks.getString(BOOKMARK_INDEX_TITLE);
//            String url = mBookmarks.getString(BOOKMARK_INDEX_URL);
//            boolean isFolder = mBookmarks.getInt(BOOKMARK_INDEX_IS_FOLDER) != 0;
//
//            RemoteViews views;
//            // Two layouts are needed because of b/5387153
//            if (isFolder) {
//                views = new RemoteViews(mContext.getPackageName(),
//                        R.layout.bookmarkthumbnailwidget_item_folder);
//            } else {
//                views = new RemoteViews(mContext.getPackageName(),
//                        R.layout.bookmarkthumbnailwidget_item);
//            }
//            // Set the title of the bookmark. Use the url as a backup.
//            String displayTitle = title;
//            if (TextUtils.isEmpty(displayTitle)) {
//                // The browser always requires a title for bookmarks, but jic...
//                displayTitle = url;
//            }
//            views.setTextViewText(R.id.label, displayTitle);
//            if (isFolder) {
//                if (id == mCurrentFolder) {
//                    id = mBookmarks.getLong(BOOKMARK_INDEX_PARENT_ID);
//                    views.setImageViewResource(R.id.thumb, R.drawable.thumb_bookmark_widget_folder_back_holo);
//                } else {
//                    views.setImageViewResource(R.id.thumb, R.drawable.thumb_bookmark_widget_folder_holo);
//                }
//                views.setImageViewResource(R.id.favicon, R.drawable.ic_bookmark_widget_bookmark_holo_dark);
//                views.setDrawableParameters(R.id.thumb, true, 0, -1, null, -1);
//            } else {
//                // RemoteViews require a valid bitmap config
//                Options options = new Options();
//                options.inPreferredConfig = Config.ARGB_8888;
//                Bitmap thumbnail = null, favicon = null;
//                byte[] blob = mBookmarks.getBlob(BOOKMARK_INDEX_THUMBNAIL);
//                views.setDrawableParameters(R.id.thumb, true, 255, -1, null, -1);
//                if (blob != null && blob.length > 0) {
//                    thumbnail = BitmapFactory.decodeByteArray(
//                            blob, 0, blob.length, options);
//                    views.setImageViewBitmap(R.id.thumb, thumbnail);
//                } else {
//                    views.setImageViewResource(R.id.thumb,
//                            R.drawable.browser_thumbnail);
//                }
//                blob = mBookmarks.getBlob(BOOKMARK_INDEX_FAVICON);
//                if (blob != null && blob.length > 0) {
//                    favicon = BitmapFactory.decodeByteArray(
//                            blob, 0, blob.length, options);
//                    views.setImageViewBitmap(R.id.favicon, favicon);
//                } else {
//                    views.setImageViewResource(R.id.favicon,
//                            R.drawable.app_web_browser_sm);
//                }
//            }
//            Intent fillin;
//            if (isFolder) {
//                fillin = new Intent(ACTION_CHANGE_FOLDER)
//                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
//                        .putExtra(Bookmarks._ID, id);
//            } else {
//                if (!TextUtils.isEmpty(url)) {
//                    fillin = new Intent(Intent.ACTION_VIEW)
//                            .addCategory(Intent.CATEGORY_BROWSABLE)
//                            .setData(Uri.parse(url));
//                } else {
//                    fillin = new Intent(BrowserActivity.ACTION_SHOW_BROWSER);
//                }
//            }
//            views.setOnClickFillInIntent(R.id.list_item, fillin);
//            return views;
//        }
//
//        @Override
//        public int getViewTypeCount() {
//            return 2;
//        }
//
//        @Override
//        public boolean hasStableIds() {
//            return false;
//        }
//
//        @Override
//        public void onCreate() {
//        }
//
//        @Override
//        public void onDestroy() {
//            if (mBookmarks != null) {
//                mBookmarks.close();
//                mBookmarks = null;
//            }
//            deleteWidgetState(mContext, mWidgetId);
//        }
//
//        @Override
//        public void onDataSetChanged() {
//            long token = Binder.clearCallingIdentity();
//            syncState();
//            if (mRootFolder < 0 || mCurrentFolder < 0) {
//                // This shouldn't happen, but JIC default to the local account
//                mRootFolder = BrowserProvider2.FIXED_ID_ROOT;
//                mCurrentFolder = mRootFolder;
//                saveState();
//            }
//            loadBookmarks();
//            Binder.restoreCallingIdentity(token);
//        }
//
//        private void resetBookmarks() {
//            if (mBookmarks != null) {
//                mBookmarks.close();
//                mBookmarks = null;
//            }
//        }
//
//        void loadBookmarks() {
//            resetBookmarks();
//
//            Uri uri = ContentUris.withAppendedId(
//                    BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER,
//                    mCurrentFolder);
//            mBookmarks = mContext.getContentResolver().query(uri, PROJECTION,
//                    null, null, null);
//            if (mCurrentFolder != mRootFolder) {
//                uri = ContentUris.withAppendedId(
//                        BrowserContract.Bookmarks.CONTENT_URI,
//                        mCurrentFolder);
//                Cursor c = mContext.getContentResolver().query(uri, PROJECTION,
//                        null, null, null);
//                mBookmarks = new MergeCursor(new Cursor[] { c, mBookmarks });
//            }
//        }
//    }
//
//}

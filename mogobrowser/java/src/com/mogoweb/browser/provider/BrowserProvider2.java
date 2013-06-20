///*
// * Copyright (C) 2010 he Android Open Source Project
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
//package com.mogoweb.browser.provider;
//
//import android.accounts.Account;
//import android.accounts.AccountManager;
//import android.app.SearchManager;
//import android.content.ContentResolver;
//import android.content.ContentUris;
//import android.content.ContentValues;
//import android.content.Context;
//import android.content.Intent;
//import android.content.UriMatcher;
//import android.content.res.Resources;
//import android.content.res.TypedArray;
//import android.database.AbstractCursor;
//import android.database.ContentObserver;
//import android.database.Cursor;
//import android.database.DatabaseUtils;
//import android.database.MatrixCursor;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteOpenHelper;
//import android.database.sqlite.SQLiteQueryBuilder;
//import android.net.Uri;
//import android.provider.BaseColumns;
//import android.provider.Browser;
//import android.provider.Browser.BookmarkColumns;
//import android.provider.BrowserContract;
//import android.provider.BrowserContract.Accounts;
//import android.provider.BrowserContract.Bookmarks;
//import android.provider.BrowserContract.ChromeSyncColumns;
//import android.provider.BrowserContract.Combined;
//import android.provider.BrowserContract.History;
//import android.provider.BrowserContract.Images;
//import android.provider.BrowserContract.Searches;
//import android.provider.BrowserContract.Settings;
//import android.provider.BrowserContract.SyncState;
//import android.provider.ContactsContract.RawContacts;
//import android.provider.SyncStateContract;
//import android.text.TextUtils;
//
//import com.android.common.content.SyncStateContentProviderHelper;
//import com.google.common.annotations.VisibleForTesting;
//import com.mogoweb.browser.R;
//import com.mogoweb.browser.UrlUtils;
//import com.mogoweb.browser.widget.BookmarkThumbnailWidgetProvider;
//
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.Arrays;
//import java.util.HashMap;
//
//public class BrowserProvider2 extends SQLiteContentProvider {
//
//    public static final String PARAM_GROUP_BY = "groupBy";
//    public static final String PARAM_ALLOW_EMPTY_ACCOUNTS = "allowEmptyAccounts";
//
//    public static final String LEGACY_AUTHORITY = "browser";
//    static final Uri LEGACY_AUTHORITY_URI = new Uri.Builder()
//            .authority(LEGACY_AUTHORITY).scheme("content").build();
//
//    public static interface Thumbnails {
//        public static final Uri CONTENT_URI = Uri.withAppendedPath(
//                BrowserContract.AUTHORITY_URI, "thumbnails");
//        public static final String _ID = "_id";
//        public static final String THUMBNAIL = "thumbnail";
//    }
//
//    public static interface OmniboxSuggestions {
//        public static final Uri CONTENT_URI = Uri.withAppendedPath(
//                BrowserContract.AUTHORITY_URI, "omnibox_suggestions");
//        public static final String _ID = "_id";
//        public static final String URL = "url";
//        public static final String TITLE = "title";
//        public static final String IS_BOOKMARK = "bookmark";
//    }
//
//    static final String TABLE_BOOKMARKS = "bookmarks";
//    static final String TABLE_HISTORY = "history";
//    static final String TABLE_IMAGES = "images";
//    static final String TABLE_SEARCHES = "searches";
//    static final String TABLE_SYNC_STATE = "syncstate";
//    static final String TABLE_SETTINGS = "settings";
//    static final String TABLE_SNAPSHOTS = "snapshots";
//    static final String TABLE_THUMBNAILS = "thumbnails";
//
//    static final String TABLE_BOOKMARKS_JOIN_IMAGES = "bookmarks LEFT OUTER JOIN images " +
//            "ON bookmarks.url = images." + Images.URL;
//    static final String TABLE_HISTORY_JOIN_IMAGES = "history LEFT OUTER JOIN images " +
//            "ON history.url = images." + Images.URL;
//
//    static final String VIEW_ACCOUNTS = "v_accounts";
//    static final String VIEW_SNAPSHOTS_COMBINED = "v_snapshots_combined";
//    static final String VIEW_OMNIBOX_SUGGESTIONS = "v_omnibox_suggestions";
//
//    static final String FORMAT_COMBINED_JOIN_SUBQUERY_JOIN_IMAGES =
//            "history LEFT OUTER JOIN (%s) bookmarks " +
//            "ON history.url = bookmarks.url LEFT OUTER JOIN images " +
//            "ON history.url = images.url_key";
//
//    static final String DEFAULT_SORT_HISTORY = History.DATE_LAST_VISITED + " DESC";
//    static final String DEFAULT_SORT_ACCOUNTS =
//            Accounts.ACCOUNT_NAME + " IS NOT NULL DESC, "
//            + Accounts.ACCOUNT_NAME + " ASC";
//
//    private static final String TABLE_BOOKMARKS_JOIN_HISTORY =
//        "history LEFT OUTER JOIN bookmarks ON history.url = bookmarks.url";
//
//    private static final String[] SUGGEST_PROJECTION = new String[] {
//            qualifyColumn(TABLE_HISTORY, History._ID),
//            qualifyColumn(TABLE_HISTORY, History.URL),
//            bookmarkOrHistoryColumn(Combined.TITLE),
//            bookmarkOrHistoryLiteral(Combined.URL,
//                    Integer.toString(R.drawable.ic_bookmark_off_holo_dark),
//                    Integer.toString(R.drawable.ic_history_holo_dark)),
//            qualifyColumn(TABLE_HISTORY, History.DATE_LAST_VISITED)};
//
//    private static final String SUGGEST_SELECTION =
//            "history.url LIKE ? OR history.url LIKE ? OR history.url LIKE ? OR history.url LIKE ?"
//            + " OR history.title LIKE ? OR bookmarks.title LIKE ?";
//
//    private static final String ZERO_QUERY_SUGGEST_SELECTION =
//            TABLE_HISTORY + "." + History.DATE_LAST_VISITED + " != 0";
//
//    private static final String IMAGE_PRUNE =
//            "url_key NOT IN (SELECT url FROM bookmarks " +
//            "WHERE url IS NOT NULL AND deleted == 0) AND url_key NOT IN " +
//            "(SELECT url FROM history WHERE url IS NOT NULL)";
//
//    static final int THUMBNAILS = 10;
//    static final int THUMBNAILS_ID = 11;
//    static final int OMNIBOX_SUGGESTIONS = 20;
//
//    static final int BOOKMARKS = 1000;
//    static final int BOOKMARKS_ID = 1001;
//    static final int BOOKMARKS_FOLDER = 1002;
//    static final int BOOKMARKS_FOLDER_ID = 1003;
//    static final int BOOKMARKS_SUGGESTIONS = 1004;
//    static final int BOOKMARKS_DEFAULT_FOLDER_ID = 1005;
//
//    static final int HISTORY = 2000;
//    static final int HISTORY_ID = 2001;
//
//    static final int SEARCHES = 3000;
//    static final int SEARCHES_ID = 3001;
//
//    static final int SYNCSTATE = 4000;
//    static final int SYNCSTATE_ID = 4001;
//
//    static final int IMAGES = 5000;
//
//    static final int COMBINED = 6000;
//    static final int COMBINED_ID = 6001;
//
//    static final int ACCOUNTS = 7000;
//
//    static final int SETTINGS = 8000;
//
//    static final int LEGACY = 9000;
//    static final int LEGACY_ID = 9001;
//
//    public static final long FIXED_ID_ROOT = 1;
//
//    // Default sort order for unsync'd bookmarks
//    static final String DEFAULT_BOOKMARKS_SORT_ORDER =
//            Bookmarks.IS_FOLDER + " DESC, position ASC, _id ASC";
//
//    // Default sort order for sync'd bookmarks
//    static final String DEFAULT_BOOKMARKS_SORT_ORDER_SYNC = "position ASC, _id ASC";
//
//    static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
//
//    static final HashMap<String, String> ACCOUNTS_PROJECTION_MAP = new HashMap<String, String>();
//    static final HashMap<String, String> BOOKMARKS_PROJECTION_MAP = new HashMap<String, String>();
//    static final HashMap<String, String> OTHER_BOOKMARKS_PROJECTION_MAP =
//            new HashMap<String, String>();
//    static final HashMap<String, String> HISTORY_PROJECTION_MAP = new HashMap<String, String>();
//    static final HashMap<String, String> SYNC_STATE_PROJECTION_MAP = new HashMap<String, String>();
//    static final HashMap<String, String> IMAGES_PROJECTION_MAP = new HashMap<String, String>();
//    static final HashMap<String, String> COMBINED_HISTORY_PROJECTION_MAP = new HashMap<String, String>();
//    static final HashMap<String, String> COMBINED_BOOKMARK_PROJECTION_MAP = new HashMap<String, String>();
//    static final HashMap<String, String> SEARCHES_PROJECTION_MAP = new HashMap<String, String>();
//    static final HashMap<String, String> SETTINGS_PROJECTION_MAP = new HashMap<String, String>();
//
//    static {
//        final UriMatcher matcher = URI_MATCHER;
//        final String authority = BrowserContract.AUTHORITY;
//        matcher.addURI(authority, "accounts", ACCOUNTS);
//        matcher.addURI(authority, "bookmarks", BOOKMARKS);
//        matcher.addURI(authority, "bookmarks/#", BOOKMARKS_ID);
//        matcher.addURI(authority, "bookmarks/folder", BOOKMARKS_FOLDER);
//        matcher.addURI(authority, "bookmarks/folder/#", BOOKMARKS_FOLDER_ID);
//        matcher.addURI(authority, "bookmarks/folder/id", BOOKMARKS_DEFAULT_FOLDER_ID);
//        matcher.addURI(authority,
//                SearchManager.SUGGEST_URI_PATH_QUERY,
//                BOOKMARKS_SUGGESTIONS);
//        matcher.addURI(authority,
//                "bookmarks/" + SearchManager.SUGGEST_URI_PATH_QUERY,
//                BOOKMARKS_SUGGESTIONS);
//        matcher.addURI(authority, "history", HISTORY);
//        matcher.addURI(authority, "history/#", HISTORY_ID);
//        matcher.addURI(authority, "searches", SEARCHES);
//        matcher.addURI(authority, "searches/#", SEARCHES_ID);
//        matcher.addURI(authority, "syncstate", SYNCSTATE);
//        matcher.addURI(authority, "syncstate/#", SYNCSTATE_ID);
//        matcher.addURI(authority, "images", IMAGES);
//        matcher.addURI(authority, "combined", COMBINED);
//        matcher.addURI(authority, "combined/#", COMBINED_ID);
//        matcher.addURI(authority, "settings", SETTINGS);
//        matcher.addURI(authority, "thumbnails", THUMBNAILS);
//        matcher.addURI(authority, "thumbnails/#", THUMBNAILS_ID);
//        matcher.addURI(authority, "omnibox_suggestions", OMNIBOX_SUGGESTIONS);
//
//        // Legacy
//        matcher.addURI(LEGACY_AUTHORITY, "searches", SEARCHES);
//        matcher.addURI(LEGACY_AUTHORITY, "searches/#", SEARCHES_ID);
//        matcher.addURI(LEGACY_AUTHORITY, "bookmarks", LEGACY);
//        matcher.addURI(LEGACY_AUTHORITY, "bookmarks/#", LEGACY_ID);
//        matcher.addURI(LEGACY_AUTHORITY,
//                SearchManager.SUGGEST_URI_PATH_QUERY,
//                BOOKMARKS_SUGGESTIONS);
//        matcher.addURI(LEGACY_AUTHORITY,
//                "bookmarks/" + SearchManager.SUGGEST_URI_PATH_QUERY,
//                BOOKMARKS_SUGGESTIONS);
//
//        // Projection maps
//        HashMap<String, String> map;
//
//        // Accounts
//        map = ACCOUNTS_PROJECTION_MAP;
//        map.put(Accounts.ACCOUNT_TYPE, Accounts.ACCOUNT_TYPE);
//        map.put(Accounts.ACCOUNT_NAME, Accounts.ACCOUNT_NAME);
//        map.put(Accounts.ROOT_ID, Accounts.ROOT_ID);
//
//        // Bookmarks
//        map = BOOKMARKS_PROJECTION_MAP;
//        map.put(Bookmarks._ID, qualifyColumn(TABLE_BOOKMARKS, Bookmarks._ID));
//        map.put(Bookmarks.TITLE, Bookmarks.TITLE);
//        map.put(Bookmarks.URL, Bookmarks.URL);
//        map.put(Bookmarks.FAVICON, Bookmarks.FAVICON);
//        map.put(Bookmarks.THUMBNAIL, Bookmarks.THUMBNAIL);
//        map.put(Bookmarks.TOUCH_ICON, Bookmarks.TOUCH_ICON);
//        map.put(Bookmarks.IS_FOLDER, Bookmarks.IS_FOLDER);
//        map.put(Bookmarks.PARENT, Bookmarks.PARENT);
//        map.put(Bookmarks.POSITION, Bookmarks.POSITION);
//        map.put(Bookmarks.INSERT_AFTER, Bookmarks.INSERT_AFTER);
//        map.put(Bookmarks.IS_DELETED, Bookmarks.IS_DELETED);
//        map.put(Bookmarks.ACCOUNT_NAME, Bookmarks.ACCOUNT_NAME);
//        map.put(Bookmarks.ACCOUNT_TYPE, Bookmarks.ACCOUNT_TYPE);
//        map.put(Bookmarks.SOURCE_ID, Bookmarks.SOURCE_ID);
//        map.put(Bookmarks.VERSION, Bookmarks.VERSION);
//        map.put(Bookmarks.DATE_CREATED, Bookmarks.DATE_CREATED);
//        map.put(Bookmarks.DATE_MODIFIED, Bookmarks.DATE_MODIFIED);
//        map.put(Bookmarks.DIRTY, Bookmarks.DIRTY);
//        map.put(Bookmarks.SYNC1, Bookmarks.SYNC1);
//        map.put(Bookmarks.SYNC2, Bookmarks.SYNC2);
//        map.put(Bookmarks.SYNC3, Bookmarks.SYNC3);
//        map.put(Bookmarks.SYNC4, Bookmarks.SYNC4);
//        map.put(Bookmarks.SYNC5, Bookmarks.SYNC5);
//        map.put(Bookmarks.PARENT_SOURCE_ID, "(SELECT " + Bookmarks.SOURCE_ID +
//                " FROM " + TABLE_BOOKMARKS + " A WHERE " +
//                "A." + Bookmarks._ID + "=" + TABLE_BOOKMARKS + "." + Bookmarks.PARENT +
//                ") AS " + Bookmarks.PARENT_SOURCE_ID);
//        map.put(Bookmarks.INSERT_AFTER_SOURCE_ID, "(SELECT " + Bookmarks.SOURCE_ID +
//                " FROM " + TABLE_BOOKMARKS + " A WHERE " +
//                "A." + Bookmarks._ID + "=" + TABLE_BOOKMARKS + "." + Bookmarks.INSERT_AFTER +
//                ") AS " + Bookmarks.INSERT_AFTER_SOURCE_ID);
//        map.put(Bookmarks.TYPE, "CASE "
//                + " WHEN " + Bookmarks.IS_FOLDER + "=0 THEN "
//                    + Bookmarks.BOOKMARK_TYPE_BOOKMARK
//                + " WHEN " + ChromeSyncColumns.SERVER_UNIQUE + "='"
//                    + ChromeSyncColumns.FOLDER_NAME_BOOKMARKS_BAR + "' THEN "
//                    + Bookmarks.BOOKMARK_TYPE_BOOKMARK_BAR_FOLDER
//                + " WHEN " + ChromeSyncColumns.SERVER_UNIQUE + "='"
//                    + ChromeSyncColumns.FOLDER_NAME_OTHER_BOOKMARKS + "' THEN "
//                    + Bookmarks.BOOKMARK_TYPE_OTHER_FOLDER
//                + " ELSE " + Bookmarks.BOOKMARK_TYPE_FOLDER
//                + " END AS " + Bookmarks.TYPE);
//
//        // Other bookmarks
//        OTHER_BOOKMARKS_PROJECTION_MAP.putAll(BOOKMARKS_PROJECTION_MAP);
//        OTHER_BOOKMARKS_PROJECTION_MAP.put(Bookmarks.POSITION,
//                Long.toString(Long.MAX_VALUE) + " AS " + Bookmarks.POSITION);
//
//        // History
//        map = HISTORY_PROJECTION_MAP;
//        map.put(History._ID, qualifyColumn(TABLE_HISTORY, History._ID));
//        map.put(History.TITLE, History.TITLE);
//        map.put(History.URL, History.URL);
//        map.put(History.FAVICON, History.FAVICON);
//        map.put(History.THUMBNAIL, History.THUMBNAIL);
//        map.put(History.TOUCH_ICON, History.TOUCH_ICON);
//        map.put(History.DATE_CREATED, History.DATE_CREATED);
//        map.put(History.DATE_LAST_VISITED, History.DATE_LAST_VISITED);
//        map.put(History.VISITS, History.VISITS);
//        map.put(History.USER_ENTERED, History.USER_ENTERED);
//
//        // Sync state
//        map = SYNC_STATE_PROJECTION_MAP;
//        map.put(SyncState._ID, SyncState._ID);
//        map.put(SyncState.ACCOUNT_NAME, SyncState.ACCOUNT_NAME);
//        map.put(SyncState.ACCOUNT_TYPE, SyncState.ACCOUNT_TYPE);
//        map.put(SyncState.DATA, SyncState.DATA);
//
//        // Images
//        map = IMAGES_PROJECTION_MAP;
//        map.put(Images.URL, Images.URL);
//        map.put(Images.FAVICON, Images.FAVICON);
//        map.put(Images.THUMBNAIL, Images.THUMBNAIL);
//        map.put(Images.TOUCH_ICON, Images.TOUCH_ICON);
//
//        // Combined history half
//        map = COMBINED_HISTORY_PROJECTION_MAP;
//        map.put(Combined._ID, bookmarkOrHistoryColumn(Combined._ID));
//        map.put(Combined.TITLE, bookmarkOrHistoryColumn(Combined.TITLE));
//        map.put(Combined.URL, qualifyColumn(TABLE_HISTORY, Combined.URL));
//        map.put(Combined.DATE_CREATED, qualifyColumn(TABLE_HISTORY, Combined.DATE_CREATED));
//        map.put(Combined.DATE_LAST_VISITED, Combined.DATE_LAST_VISITED);
//        map.put(Combined.IS_BOOKMARK, "CASE WHEN " +
//                TABLE_BOOKMARKS + "." + Bookmarks._ID +
//                " IS NOT NULL THEN 1 ELSE 0 END AS " + Combined.IS_BOOKMARK);
//        map.put(Combined.VISITS, Combined.VISITS);
//        map.put(Combined.FAVICON, Combined.FAVICON);
//        map.put(Combined.THUMBNAIL, Combined.THUMBNAIL);
//        map.put(Combined.TOUCH_ICON, Combined.TOUCH_ICON);
//        map.put(Combined.USER_ENTERED, "NULL AS " + Combined.USER_ENTERED);
//
//        // Combined bookmark half
//        map = COMBINED_BOOKMARK_PROJECTION_MAP;
//        map.put(Combined._ID, Combined._ID);
//        map.put(Combined.TITLE, Combined.TITLE);
//        map.put(Combined.URL, Combined.URL);
//        map.put(Combined.DATE_CREATED, Combined.DATE_CREATED);
//        map.put(Combined.DATE_LAST_VISITED, "NULL AS " + Combined.DATE_LAST_VISITED);
//        map.put(Combined.IS_BOOKMARK, "1 AS " + Combined.IS_BOOKMARK);
//        map.put(Combined.VISITS, "0 AS " + Combined.VISITS);
//        map.put(Combined.FAVICON, Combined.FAVICON);
//        map.put(Combined.THUMBNAIL, Combined.THUMBNAIL);
//        map.put(Combined.TOUCH_ICON, Combined.TOUCH_ICON);
//        map.put(Combined.USER_ENTERED, "NULL AS " + Combined.USER_ENTERED);
//
//        // Searches
//        map = SEARCHES_PROJECTION_MAP;
//        map.put(Searches._ID, Searches._ID);
//        map.put(Searches.SEARCH, Searches.SEARCH);
//        map.put(Searches.DATE, Searches.DATE);
//
//        // Settings
//        map = SETTINGS_PROJECTION_MAP;
//        map.put(Settings.KEY, Settings.KEY);
//        map.put(Settings.VALUE, Settings.VALUE);
//    }
//
//    static final String bookmarkOrHistoryColumn(String column) {
//        return "CASE WHEN bookmarks." + column + " IS NOT NULL THEN " +
//                "bookmarks." + column + " ELSE history." + column + " END AS " + column;
//    }
//
//    static final String bookmarkOrHistoryLiteral(String column, String bookmarkValue,
//            String historyValue) {
//        return "CASE WHEN bookmarks." + column + " IS NOT NULL THEN \"" + bookmarkValue +
//                "\" ELSE \"" + historyValue + "\" END";
//    }
//
//    static final String qualifyColumn(String table, String column) {
//        return table + "." + column + " AS " + column;
//    }
//
//    DatabaseHelper mOpenHelper;
//    SyncStateContentProviderHelper mSyncHelper = new SyncStateContentProviderHelper();
//    // This is so provider tests can intercept widget updating
//    ContentObserver mWidgetObserver = null;
//    boolean mUpdateWidgets = false;
//    boolean mSyncToNetwork = true;
//
//    final class DatabaseHelper extends SQLiteOpenHelper {
//        static final String DATABASE_NAME = "browser2.db";
//        static final int DATABASE_VERSION = 32;
//        public DatabaseHelper(Context context) {
//            super(context, DATABASE_NAME, null, DATABASE_VERSION);
//            setWriteAheadLoggingEnabled(true);
//        }
//
//        @Override
//        public void onCreate(SQLiteDatabase db) {
//            db.execSQL("CREATE TABLE " + TABLE_BOOKMARKS + "(" +
//                    Bookmarks._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
//                    Bookmarks.TITLE + " TEXT," +
//                    Bookmarks.URL + " TEXT," +
//                    Bookmarks.IS_FOLDER + " INTEGER NOT NULL DEFAULT 0," +
//                    Bookmarks.PARENT + " INTEGER," +
//                    Bookmarks.POSITION + " INTEGER NOT NULL," +
//                    Bookmarks.INSERT_AFTER + " INTEGER," +
//                    Bookmarks.IS_DELETED + " INTEGER NOT NULL DEFAULT 0," +
//                    Bookmarks.ACCOUNT_NAME + " TEXT," +
//                    Bookmarks.ACCOUNT_TYPE + " TEXT," +
//                    Bookmarks.SOURCE_ID + " TEXT," +
//                    Bookmarks.VERSION + " INTEGER NOT NULL DEFAULT 1," +
//                    Bookmarks.DATE_CREATED + " INTEGER," +
//                    Bookmarks.DATE_MODIFIED + " INTEGER," +
//                    Bookmarks.DIRTY + " INTEGER NOT NULL DEFAULT 0," +
//                    Bookmarks.SYNC1 + " TEXT," +
//                    Bookmarks.SYNC2 + " TEXT," +
//                    Bookmarks.SYNC3 + " TEXT," +
//                    Bookmarks.SYNC4 + " TEXT," +
//                    Bookmarks.SYNC5 + " TEXT" +
//                    ");");
//
//            // TODO indices
//
//            db.execSQL("CREATE TABLE " + TABLE_HISTORY + "(" +
//                    History._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
//                    History.TITLE + " TEXT," +
//                    History.URL + " TEXT NOT NULL," +
//                    History.DATE_CREATED + " INTEGER," +
//                    History.DATE_LAST_VISITED + " INTEGER," +
//                    History.VISITS + " INTEGER NOT NULL DEFAULT 0," +
//                    History.USER_ENTERED + " INTEGER" +
//                    ");");
//
//            db.execSQL("CREATE TABLE " + TABLE_IMAGES + " (" +
//                    Images.URL + " TEXT UNIQUE NOT NULL," +
//                    Images.FAVICON + " BLOB," +
//                    Images.THUMBNAIL + " BLOB," +
//                    Images.TOUCH_ICON + " BLOB" +
//                    ");");
//            db.execSQL("CREATE INDEX imagesUrlIndex ON " + TABLE_IMAGES +
//                    "(" + Images.URL + ")");
//
//            db.execSQL("CREATE TABLE " + TABLE_SEARCHES + " (" +
//                    Searches._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
//                    Searches.SEARCH + " TEXT," +
//                    Searches.DATE + " LONG" +
//                    ");");
//
//            db.execSQL("CREATE TABLE " + TABLE_SETTINGS + " (" +
//                    Settings.KEY + " TEXT PRIMARY KEY," +
//                    Settings.VALUE + " TEXT NOT NULL" +
//                    ");");
//
//            createAccountsView(db);
//            createThumbnails(db);
//
//            mSyncHelper.createDatabase(db);
//
//            if (!importFromBrowserProvider(db)) {
//                createDefaultBookmarks(db);
//            }
//
//            enableSync(db);
//            createOmniboxSuggestions(db);
//        }
//
//        void createOmniboxSuggestions(SQLiteDatabase db) {
//            db.execSQL(SQL_CREATE_VIEW_OMNIBOX_SUGGESTIONS);
//        }
//
//        void createThumbnails(SQLiteDatabase db) {
//            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_THUMBNAILS + " (" +
//                    Thumbnails._ID + " INTEGER PRIMARY KEY," +
//                    Thumbnails.THUMBNAIL + " BLOB NOT NULL" +
//                    ");");
//        }
//
//        void enableSync(SQLiteDatabase db) {
//            ContentValues values = new ContentValues();
//            values.put(Settings.KEY, Settings.KEY_SYNC_ENABLED);
//            values.put(Settings.VALUE, 1);
//            insertSettingsInTransaction(db, values);
//            // Enable bookmark sync on all accounts
//            AccountManager am = (AccountManager) getContext().getSystemService(
//                    Context.ACCOUNT_SERVICE);
//            if (am == null) {
//                return;
//            }
//            Account[] accounts = am.getAccountsByType("com.google");
//            if (accounts == null || accounts.length == 0) {
//                return;
//            }
//            for (Account account : accounts) {
//                if (ContentResolver.getIsSyncable(
//                        account, BrowserContract.AUTHORITY) == 0) {
//                    // Account wasn't syncable, enable it
//                    ContentResolver.setIsSyncable(
//                            account, BrowserContract.AUTHORITY, 1);
//                    ContentResolver.setSyncAutomatically(
//                            account, BrowserContract.AUTHORITY, true);
//                }
//            }
//        }
//
//        boolean importFromBrowserProvider(SQLiteDatabase db) {
//            Context context = getContext();
//            File oldDbFile = context.getDatabasePath(BrowserProvider.sDatabaseName);
//            if (oldDbFile.exists()) {
//                BrowserProvider.DatabaseHelper helper =
//                        new BrowserProvider.DatabaseHelper(context);
//                SQLiteDatabase oldDb = helper.getWritableDatabase();
//                Cursor c = null;
//                try {
//                    String table = BrowserProvider.TABLE_NAMES[BrowserProvider.URI_MATCH_BOOKMARKS];
//                    // Import bookmarks
//                    c = oldDb.query(table,
//                            new String[] {
//                            BookmarkColumns.URL, // 0
//                            BookmarkColumns.TITLE, // 1
//                            BookmarkColumns.FAVICON, // 2
//                            BookmarkColumns.TOUCH_ICON, // 3
//                            BookmarkColumns.CREATED, // 4
//                            }, BookmarkColumns.BOOKMARK + "!=0", null,
//                            null, null, null);
//                    if (c != null) {
//                        while (c.moveToNext()) {
//                            String url = c.getString(0);
//                            if (TextUtils.isEmpty(url))
//                                continue; // We require a valid URL
//                            ContentValues values = new ContentValues();
//                            values.put(Bookmarks.URL, url);
//                            values.put(Bookmarks.TITLE, c.getString(1));
//                            values.put(Bookmarks.DATE_CREATED, c.getInt(4));
//                            values.put(Bookmarks.POSITION, 0);
//                            values.put(Bookmarks.PARENT, FIXED_ID_ROOT);
//                            ContentValues imageValues = new ContentValues();
//                            imageValues.put(Images.URL, url);
//                            imageValues.put(Images.FAVICON, c.getBlob(2));
//                            imageValues.put(Images.TOUCH_ICON, c.getBlob(3));
//                            db.insert(TABLE_IMAGES, Images.THUMBNAIL, imageValues);
//                            db.insert(TABLE_BOOKMARKS, Bookmarks.DIRTY, values);
//                        }
//                        c.close();
//                    }
//                    // Import history
//                    c = oldDb.query(table,
//                            new String[] {
//                            BookmarkColumns.URL, // 0
//                            BookmarkColumns.TITLE, // 1
//                            BookmarkColumns.VISITS, // 2
//                            BookmarkColumns.DATE, // 3
//                            BookmarkColumns.CREATED, // 4
//                            }, BookmarkColumns.VISITS + " > 0 OR "
//                            + BookmarkColumns.BOOKMARK + " = 0",
//                            null, null, null, null);
//                    if (c != null) {
//                        while (c.moveToNext()) {
//                            ContentValues values = new ContentValues();
//                            String url = c.getString(0);
//                            if (TextUtils.isEmpty(url))
//                                continue; // We require a valid URL
//                            values.put(History.URL, url);
//                            values.put(History.TITLE, c.getString(1));
//                            values.put(History.VISITS, c.getInt(2));
//                            values.put(History.DATE_LAST_VISITED, c.getLong(3));
//                            values.put(History.DATE_CREATED, c.getLong(4));
//                            db.insert(TABLE_HISTORY, History.FAVICON, values);
//                        }
//                        c.close();
//                    }
//                    // Wipe the old DB, in case the delete fails.
//                    oldDb.delete(table, null, null);
//                } finally {
//                    if (c != null) c.close();
//                    oldDb.close();
//                    helper.close();
//                }
//                if (!oldDbFile.delete()) {
//                    oldDbFile.deleteOnExit();
//                }
//                return true;
//            }
//            return false;
//        }
//
//        void createAccountsView(SQLiteDatabase db) {
//            db.execSQL("CREATE VIEW IF NOT EXISTS v_accounts AS "
//                    + "SELECT NULL AS " + Accounts.ACCOUNT_NAME
//                    + ", NULL AS " + Accounts.ACCOUNT_TYPE
//                    + ", " + FIXED_ID_ROOT + " AS " + Accounts.ROOT_ID
//                    + " UNION ALL SELECT " + Accounts.ACCOUNT_NAME
//                    + ", " + Accounts.ACCOUNT_TYPE + ", "
//                    + Bookmarks._ID + " AS " + Accounts.ROOT_ID
//                    + " FROM " + TABLE_BOOKMARKS + " WHERE "
//                    + ChromeSyncColumns.SERVER_UNIQUE + " = \""
//                    + ChromeSyncColumns.FOLDER_NAME_BOOKMARKS_BAR + "\" AND "
//                    + Bookmarks.IS_DELETED + " = 0");
//        }
//
//        @Override
//        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
//            if (oldVersion < 32) {
//                createOmniboxSuggestions(db);
//            }
//            if (oldVersion < 31) {
//                createThumbnails(db);
//            }
//            if (oldVersion < 30) {
//                db.execSQL("DROP VIEW IF EXISTS " + VIEW_SNAPSHOTS_COMBINED);
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SNAPSHOTS);
//            }
//            if (oldVersion < 28) {
//                enableSync(db);
//            }
//            if (oldVersion < 27) {
//                createAccountsView(db);
//            }
//            if (oldVersion < 26) {
//                db.execSQL("DROP VIEW IF EXISTS combined");
//            }
//            if (oldVersion < 25) {
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SEARCHES);
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_IMAGES);
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
//                mSyncHelper.onAccountsChanged(db, new Account[] {}); // remove all sync info
//                onCreate(db);
//            }
//        }
//
//        public void onOpen(SQLiteDatabase db) {
//            mSyncHelper.onDatabaseOpened(db);
//        }
//
//        private void createDefaultBookmarks(SQLiteDatabase db) {
//            ContentValues values = new ContentValues();
//            // TODO figure out how to deal with localization for the defaults
//
//            // Bookmarks folder
//            values.put(Bookmarks._ID, FIXED_ID_ROOT);
//            values.put(ChromeSyncColumns.SERVER_UNIQUE, ChromeSyncColumns.FOLDER_NAME_BOOKMARKS);
//            values.put(Bookmarks.TITLE, "Bookmarks");
//            values.putNull(Bookmarks.PARENT);
//            values.put(Bookmarks.POSITION, 0);
//            values.put(Bookmarks.IS_FOLDER, true);
//            values.put(Bookmarks.DIRTY, true);
//            db.insertOrThrow(TABLE_BOOKMARKS, null, values);
//
//            addDefaultBookmarks(db, FIXED_ID_ROOT);
//        }
//
//        private void addDefaultBookmarks(SQLiteDatabase db, long parentId) {
//            Resources res = getContext().getResources();
//            final CharSequence[] bookmarks = res.getTextArray(
//                    R.array.bookmarks);
//            int size = bookmarks.length;
//            TypedArray preloads = res.obtainTypedArray(R.array.bookmark_preloads);
//            try {
//                String parent = Long.toString(parentId);
//                String now = Long.toString(System.currentTimeMillis());
//                for (int i = 0; i < size; i = i + 2) {
//                    CharSequence bookmarkDestination = replaceSystemPropertyInString(getContext(),
//                            bookmarks[i + 1]);
//                    db.execSQL("INSERT INTO bookmarks (" +
//                            Bookmarks.TITLE + ", " +
//                            Bookmarks.URL + ", " +
//                            Bookmarks.IS_FOLDER + "," +
//                            Bookmarks.PARENT + "," +
//                            Bookmarks.POSITION + "," +
//                            Bookmarks.DATE_CREATED +
//                        ") VALUES (" +
//                            "'" + bookmarks[i] + "', " +
//                            "'" + bookmarkDestination + "', " +
//                            "0," +
//                            parent + "," +
//                            Integer.toString(i) + "," +
//                            now +
//                            ");");
//
//                    int faviconId = preloads.getResourceId(i, 0);
//                    int thumbId = preloads.getResourceId(i + 1, 0);
//                    byte[] thumb = null, favicon = null;
//                    try {
//                        thumb = readRaw(res, thumbId);
//                    } catch (IOException e) {
//                    }
//                    try {
//                        favicon = readRaw(res, faviconId);
//                    } catch (IOException e) {
//                    }
//                    if (thumb != null || favicon != null) {
//                        ContentValues imageValues = new ContentValues();
//                        imageValues.put(Images.URL, bookmarkDestination.toString());
//                        if (favicon != null) {
//                            imageValues.put(Images.FAVICON, favicon);
//                        }
//                        if (thumb != null) {
//                            imageValues.put(Images.THUMBNAIL, thumb);
//                        }
//                        db.insert(TABLE_IMAGES, Images.FAVICON, imageValues);
//                    }
//                }
//            } catch (ArrayIndexOutOfBoundsException e) {
//            } finally {
//                preloads.recycle();
//            }
//        }
//
//        private byte[] readRaw(Resources res, int id) throws IOException {
//            if (id == 0) {
//                return null;
//            }
//            InputStream is = res.openRawResource(id);
//            try {
//                ByteArrayOutputStream bos = new ByteArrayOutputStream();
//                byte[] buf = new byte[4096];
//                int read;
//                while ((read = is.read(buf)) > 0) {
//                    bos.write(buf, 0, read);
//                }
//                bos.flush();
//                return bos.toByteArray();
//            } finally {
//                is.close();
//            }
//        }
//
//        // XXX: This is a major hack to remove our dependency on gsf constants and
//        // its content provider. http://b/issue?id=2425179
//        private String getClientId(ContentResolver cr) {
//            String ret = "android-google";
//            Cursor c = null;
//            try {
//                c = cr.query(Uri.parse("content://com.google.settings/partner"),
//                        new String[] { "value" }, "name='client_id'", null, null);
//                if (c != null && c.moveToNext()) {
//                    ret = c.getString(0);
//                }
//            } catch (RuntimeException ex) {
//                // fall through to return the default
//            } finally {
//                if (c != null) {
//                    c.close();
//                }
//            }
//            return ret;
//        }
//
//        private CharSequence replaceSystemPropertyInString(Context context, CharSequence srcString) {
//            StringBuffer sb = new StringBuffer();
//            int lastCharLoc = 0;
//
//            final String client_id = getClientId(context.getContentResolver());
//
//            for (int i = 0; i < srcString.length(); ++i) {
//                char c = srcString.charAt(i);
//                if (c == '{') {
//                    sb.append(srcString.subSequence(lastCharLoc, i));
//                    lastCharLoc = i;
//              inner:
//                    for (int j = i; j < srcString.length(); ++j) {
//                        char k = srcString.charAt(j);
//                        if (k == '}') {
//                            String propertyKeyValue = srcString.subSequence(i + 1, j).toString();
//                            if (propertyKeyValue.equals("CLIENT_ID")) {
//                                sb.append(client_id);
//                            } else {
//                                sb.append("unknown");
//                            }
//                            lastCharLoc = j + 1;
//                            i = j;
//                            break inner;
//                        }
//                    }
//                }
//            }
//            if (srcString.length() - lastCharLoc > 0) {
//                // Put on the tail, if there is one
//                sb.append(srcString.subSequence(lastCharLoc, srcString.length()));
//            }
//            return sb;
//        }
//    }
//
//    @Override
//    public SQLiteOpenHelper getDatabaseHelper(Context context) {
//        synchronized (this) {
//            if (mOpenHelper == null) {
//                mOpenHelper = new DatabaseHelper(context);
//            }
//            return mOpenHelper;
//        }
//    }
//
//    @Override
//    public boolean isCallerSyncAdapter(Uri uri) {
//        return uri.getBooleanQueryParameter(BrowserContract.CALLER_IS_SYNCADAPTER, false);
//    }
//
//    @VisibleForTesting
//    public void setWidgetObserver(ContentObserver obs) {
//        mWidgetObserver = obs;
//    }
//
//    void refreshWidgets() {
//        mUpdateWidgets = true;
//    }
//
//    @Override
//    protected void onEndTransaction(boolean callerIsSyncAdapter) {
//        super.onEndTransaction(callerIsSyncAdapter);
//        if (mUpdateWidgets) {
//            if (mWidgetObserver == null) {
//                BookmarkThumbnailWidgetProvider.refreshWidgets(getContext());
//            } else {
//                mWidgetObserver.dispatchChange(false);
//            }
//            mUpdateWidgets = false;
//        }
//        mSyncToNetwork = true;
//    }
//
//    @Override
//    public String getType(Uri uri) {
//        final int match = URI_MATCHER.match(uri);
//        switch (match) {
//            case LEGACY:
//            case BOOKMARKS:
//                return Bookmarks.CONTENT_TYPE;
//            case LEGACY_ID:
//            case BOOKMARKS_ID:
//                return Bookmarks.CONTENT_ITEM_TYPE;
//            case HISTORY:
//                return History.CONTENT_TYPE;
//            case HISTORY_ID:
//                return History.CONTENT_ITEM_TYPE;
//            case SEARCHES:
//                return Searches.CONTENT_TYPE;
//            case SEARCHES_ID:
//                return Searches.CONTENT_ITEM_TYPE;
//        }
//        return null;
//    }
//
//    boolean isNullAccount(String account) {
//        if (account == null) return true;
//        account = account.trim();
//        return account.length() == 0 || account.equals("null");
//    }
//
//    Object[] getSelectionWithAccounts(Uri uri, String selection, String[] selectionArgs) {
//        // Look for account info
//        String accountType = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_TYPE);
//        String accountName = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_NAME);
//        boolean hasAccounts = false;
//        if (accountType != null && accountName != null) {
//            if (!isNullAccount(accountType) && !isNullAccount(accountName)) {
//                selection = DatabaseUtils.concatenateWhere(selection,
//                        Bookmarks.ACCOUNT_TYPE + "=? AND " + Bookmarks.ACCOUNT_NAME + "=? ");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { accountType, accountName });
//                hasAccounts = true;
//            } else {
//                selection = DatabaseUtils.concatenateWhere(selection,
//                        Bookmarks.ACCOUNT_NAME + " IS NULL AND " +
//                        Bookmarks.ACCOUNT_TYPE + " IS NULL");
//            }
//        }
//        return new Object[] { selection, selectionArgs, hasAccounts };
//    }
//
//    @Override
//    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
//            String sortOrder) {
//        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
//        final int match = URI_MATCHER.match(uri);
//        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
//        String limit = uri.getQueryParameter(BrowserContract.PARAM_LIMIT);
//        String groupBy = uri.getQueryParameter(PARAM_GROUP_BY);
//        switch (match) {
//            case ACCOUNTS: {
//                qb.setTables(VIEW_ACCOUNTS);
//                qb.setProjectionMap(ACCOUNTS_PROJECTION_MAP);
//                String allowEmpty = uri.getQueryParameter(PARAM_ALLOW_EMPTY_ACCOUNTS);
//                if ("false".equals(allowEmpty)) {
//                    selection = DatabaseUtils.concatenateWhere(selection,
//                            SQL_WHERE_ACCOUNT_HAS_BOOKMARKS);
//                }
//                if (sortOrder == null) {
//                    sortOrder = DEFAULT_SORT_ACCOUNTS;
//                }
//                break;
//            }
//
//            case BOOKMARKS_FOLDER_ID:
//            case BOOKMARKS_ID:
//            case BOOKMARKS: {
//                // Only show deleted bookmarks if requested to do so
//                if (!uri.getBooleanQueryParameter(Bookmarks.QUERY_PARAMETER_SHOW_DELETED, false)) {
//                    selection = DatabaseUtils.concatenateWhere(
//                            Bookmarks.IS_DELETED + "=0", selection);
//                }
//
//                if (match == BOOKMARKS_ID) {
//                    // Tack on the ID of the specific bookmark requested
//                    selection = DatabaseUtils.concatenateWhere(selection,
//                            TABLE_BOOKMARKS + "." + Bookmarks._ID + "=?");
//                    selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                            new String[] { Long.toString(ContentUris.parseId(uri)) });
//                } else if (match == BOOKMARKS_FOLDER_ID) {
//                    // Tack on the ID of the specific folder requested
//                    selection = DatabaseUtils.concatenateWhere(selection,
//                            TABLE_BOOKMARKS + "." + Bookmarks.PARENT + "=?");
//                    selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                            new String[] { Long.toString(ContentUris.parseId(uri)) });
//                }
//
//                Object[] withAccount = getSelectionWithAccounts(uri, selection, selectionArgs);
//                selection = (String) withAccount[0];
//                selectionArgs = (String[]) withAccount[1];
//                boolean hasAccounts = (Boolean) withAccount[2];
//
//                // Set a default sort order if one isn't specified
//                if (TextUtils.isEmpty(sortOrder)) {
//                    if (hasAccounts) {
//                        sortOrder = DEFAULT_BOOKMARKS_SORT_ORDER_SYNC;
//                    } else {
//                        sortOrder = DEFAULT_BOOKMARKS_SORT_ORDER;
//                    }
//                }
//
//                qb.setProjectionMap(BOOKMARKS_PROJECTION_MAP);
//                qb.setTables(TABLE_BOOKMARKS_JOIN_IMAGES);
//                break;
//            }
//
//            case BOOKMARKS_FOLDER: {
//                // Look for an account
//                boolean useAccount = false;
//                String accountType = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_TYPE);
//                String accountName = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_NAME);
//                if (!isNullAccount(accountType) && !isNullAccount(accountName)) {
//                    useAccount = true;
//                }
//
//                qb.setTables(TABLE_BOOKMARKS_JOIN_IMAGES);
//                String[] args;
//                String query;
//                // Set a default sort order if one isn't specified
//                if (TextUtils.isEmpty(sortOrder)) {
//                    if (useAccount) {
//                        sortOrder = DEFAULT_BOOKMARKS_SORT_ORDER_SYNC;
//                    } else {
//                        sortOrder = DEFAULT_BOOKMARKS_SORT_ORDER;
//                    }
//                }
//                if (!useAccount) {
//                    qb.setProjectionMap(BOOKMARKS_PROJECTION_MAP);
//                    String where = Bookmarks.PARENT + "=? AND " + Bookmarks.IS_DELETED + "=0";
//                    where = DatabaseUtils.concatenateWhere(where, selection);
//                    args = new String[] { Long.toString(FIXED_ID_ROOT) };
//                    if (selectionArgs != null) {
//                        args = DatabaseUtils.appendSelectionArgs(args, selectionArgs);
//                    }
//                    query = qb.buildQuery(projection, where, null, null, sortOrder, null);
//                } else {
//                    qb.setProjectionMap(BOOKMARKS_PROJECTION_MAP);
//                    String where = Bookmarks.ACCOUNT_TYPE + "=? AND " +
//                            Bookmarks.ACCOUNT_NAME + "=? " +
//                            "AND parent = " +
//                            "(SELECT _id FROM " + TABLE_BOOKMARKS + " WHERE " +
//                            ChromeSyncColumns.SERVER_UNIQUE + "=" +
//                            "'" + ChromeSyncColumns.FOLDER_NAME_BOOKMARKS_BAR + "' " +
//                            "AND account_type = ? AND account_name = ?) " +
//                            "AND " + Bookmarks.IS_DELETED + "=0";
//                    where = DatabaseUtils.concatenateWhere(where, selection);
//                    String bookmarksBarQuery = qb.buildQuery(projection,
//                            where, null, null, null, null);
//                    args = new String[] {accountType, accountName,
//                            accountType, accountName};
//                    if (selectionArgs != null) {
//                        args = DatabaseUtils.appendSelectionArgs(args, selectionArgs);
//                    }
//
//                    where = Bookmarks.ACCOUNT_TYPE + "=? AND " + Bookmarks.ACCOUNT_NAME + "=?" +
//                            " AND " + ChromeSyncColumns.SERVER_UNIQUE + "=?";
//                    where = DatabaseUtils.concatenateWhere(where, selection);
//                    qb.setProjectionMap(OTHER_BOOKMARKS_PROJECTION_MAP);
//                    String otherBookmarksQuery = qb.buildQuery(projection,
//                            where, null, null, null, null);
//
//                    query = qb.buildUnionQuery(
//                            new String[] { bookmarksBarQuery, otherBookmarksQuery },
//                            sortOrder, limit);
//
//                    args = DatabaseUtils.appendSelectionArgs(args, new String[] {
//                            accountType, accountName, ChromeSyncColumns.FOLDER_NAME_OTHER_BOOKMARKS,
//                            });
//                    if (selectionArgs != null) {
//                        args = DatabaseUtils.appendSelectionArgs(args, selectionArgs);
//                    }
//                }
//
//                Cursor cursor = db.rawQuery(query, args);
//                if (cursor != null) {
//                    cursor.setNotificationUri(getContext().getContentResolver(),
//                            BrowserContract.AUTHORITY_URI);
//                }
//                return cursor;
//            }
//
//            case BOOKMARKS_DEFAULT_FOLDER_ID: {
//                String accountName = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_NAME);
//                String accountType = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_TYPE);
//                long id = queryDefaultFolderId(accountName, accountType);
//                MatrixCursor c = new MatrixCursor(new String[] {Bookmarks._ID});
//                c.newRow().add(id);
//                return c;
//            }
//
//            case BOOKMARKS_SUGGESTIONS: {
//                return doSuggestQuery(selection, selectionArgs, limit);
//            }
//
//            case HISTORY_ID: {
//                selection = DatabaseUtils.concatenateWhere(selection, TABLE_HISTORY + "._id=?");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case HISTORY: {
//                filterSearchClient(selectionArgs);
//                if (sortOrder == null) {
//                    sortOrder = DEFAULT_SORT_HISTORY;
//                }
//                qb.setProjectionMap(HISTORY_PROJECTION_MAP);
//                qb.setTables(TABLE_HISTORY_JOIN_IMAGES);
//                break;
//            }
//
//            case SEARCHES_ID: {
//                selection = DatabaseUtils.concatenateWhere(selection, TABLE_SEARCHES + "._id=?");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case SEARCHES: {
//                qb.setTables(TABLE_SEARCHES);
//                qb.setProjectionMap(SEARCHES_PROJECTION_MAP);
//                break;
//            }
//
//            case SYNCSTATE: {
//                return mSyncHelper.query(db, projection, selection, selectionArgs, sortOrder);
//            }
//
//            case SYNCSTATE_ID: {
//                selection = appendAccountToSelection(uri, selection);
//                String selectionWithId =
//                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
//                        + (selection == null ? "" : " AND (" + selection + ")");
//                return mSyncHelper.query(db, projection, selectionWithId, selectionArgs, sortOrder);
//            }
//
//            case IMAGES: {
//                qb.setTables(TABLE_IMAGES);
//                qb.setProjectionMap(IMAGES_PROJECTION_MAP);
//                break;
//            }
//
//            case LEGACY_ID:
//            case COMBINED_ID: {
//                selection = DatabaseUtils.concatenateWhere(
//                        selection, Combined._ID + " = CAST(? AS INTEGER)");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case LEGACY:
//            case COMBINED: {
//                if ((match == LEGACY || match == LEGACY_ID)
//                        && projection == null) {
//                    projection = Browser.HISTORY_PROJECTION;
//                }
//                String[] args = createCombinedQuery(uri, projection, qb);
//                if (selectionArgs == null) {
//                    selectionArgs = args;
//                } else {
//                    selectionArgs = DatabaseUtils.appendSelectionArgs(args, selectionArgs);
//                }
//                break;
//            }
//
//            case SETTINGS: {
//                qb.setTables(TABLE_SETTINGS);
//                qb.setProjectionMap(SETTINGS_PROJECTION_MAP);
//                break;
//            }
//
//            case THUMBNAILS_ID: {
//                selection = DatabaseUtils.concatenateWhere(
//                        selection, Thumbnails._ID + " = ?");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case THUMBNAILS: {
//                qb.setTables(TABLE_THUMBNAILS);
//                break;
//            }
//
//            case OMNIBOX_SUGGESTIONS: {
//                qb.setTables(VIEW_OMNIBOX_SUGGESTIONS);
//                break;
//            }
//
//            default: {
//                throw new UnsupportedOperationException("Unknown URL " + uri.toString());
//            }
//        }
//
//        Cursor cursor = qb.query(db, projection, selection, selectionArgs, groupBy,
//                null, sortOrder, limit);
//        cursor.setNotificationUri(getContext().getContentResolver(), BrowserContract.AUTHORITY_URI);
//        return cursor;
//    }
//
//    private Cursor doSuggestQuery(String selection, String[] selectionArgs, String limit) {
//        if (TextUtils.isEmpty(selectionArgs[0])) {
//            selection = ZERO_QUERY_SUGGEST_SELECTION;
//            selectionArgs = null;
//        } else {
//            String like = selectionArgs[0] + "%";
//            if (selectionArgs[0].startsWith("http")
//                    || selectionArgs[0].startsWith("file")) {
//                selectionArgs[0] = like;
//            } else {
//                selectionArgs = new String[6];
//                selectionArgs[0] = "http://" + like;
//                selectionArgs[1] = "http://www." + like;
//                selectionArgs[2] = "https://" + like;
//                selectionArgs[3] = "https://www." + like;
//                // To match against titles.
//                selectionArgs[4] = like;
//                selectionArgs[5] = like;
//                selection = SUGGEST_SELECTION;
//            }
//            selection = DatabaseUtils.concatenateWhere(selection,
//                    Bookmarks.IS_DELETED + "=0 AND " + Bookmarks.IS_FOLDER + "=0");
//
//        }
//        Cursor c = mOpenHelper.getReadableDatabase().query(TABLE_BOOKMARKS_JOIN_HISTORY,
//                SUGGEST_PROJECTION, selection, selectionArgs, null, null,
//                null, null);
//
//        return new SuggestionsCursor(c);
//    }
//
//    private String[] createCombinedQuery(
//            Uri uri, String[] projection, SQLiteQueryBuilder qb) {
//        String[] args = null;
//        StringBuilder whereBuilder = new StringBuilder(128);
//        whereBuilder.append(Bookmarks.IS_DELETED);
//        whereBuilder.append(" = 0");
//        // Look for account info
//        Object[] withAccount = getSelectionWithAccounts(uri, null, null);
//        String selection = (String) withAccount[0];
//        String[] selectionArgs = (String[]) withAccount[1];
//        if (selection != null) {
//            whereBuilder.append(" AND " + selection);
//            if (selectionArgs != null) {
//                // We use the selection twice, hence we need to duplicate the args
//                args = new String[selectionArgs.length * 2];
//                System.arraycopy(selectionArgs, 0, args, 0, selectionArgs.length);
//                System.arraycopy(selectionArgs, 0, args, selectionArgs.length,
//                        selectionArgs.length);
//            }
//        }
//        String where = whereBuilder.toString();
//        // Build the bookmark subquery for history union subquery
//        qb.setTables(TABLE_BOOKMARKS);
//        String subQuery = qb.buildQuery(null, where, null, null, null, null);
//        // Build the history union subquery
//        qb.setTables(String.format(FORMAT_COMBINED_JOIN_SUBQUERY_JOIN_IMAGES, subQuery));
//        qb.setProjectionMap(COMBINED_HISTORY_PROJECTION_MAP);
//        String historySubQuery = qb.buildQuery(null,
//                null, null, null, null, null);
//        // Build the bookmark union subquery
//        qb.setTables(TABLE_BOOKMARKS_JOIN_IMAGES);
//        qb.setProjectionMap(COMBINED_BOOKMARK_PROJECTION_MAP);
//        where += String.format(" AND %s NOT IN (SELECT %s FROM %s)",
//                Combined.URL, History.URL, TABLE_HISTORY);
//        String bookmarksSubQuery = qb.buildQuery(null, where,
//                null, null, null, null);
//        // Put it all together
//        String query = qb.buildUnionQuery(
//                new String[] {historySubQuery, bookmarksSubQuery},
//                null, null);
//        qb.setTables("(" + query + ")");
//        qb.setProjectionMap(null);
//        return args;
//    }
//
//    int deleteBookmarks(String selection, String[] selectionArgs,
//            boolean callerIsSyncAdapter) {
//        //TODO cascade deletes down from folders
//        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
//        if (callerIsSyncAdapter) {
//            return db.delete(TABLE_BOOKMARKS, selection, selectionArgs);
//        }
//        ContentValues values = new ContentValues();
//        values.put(Bookmarks.DATE_MODIFIED, System.currentTimeMillis());
//        values.put(Bookmarks.IS_DELETED, 1);
//        return updateBookmarksInTransaction(values, selection, selectionArgs,
//                callerIsSyncAdapter);
//    }
//
//    @Override
//    public int deleteInTransaction(Uri uri, String selection, String[] selectionArgs,
//            boolean callerIsSyncAdapter) {
//        final int match = URI_MATCHER.match(uri);
//        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
//        int deleted = 0;
//        switch (match) {
//            case BOOKMARKS_ID: {
//                selection = DatabaseUtils.concatenateWhere(selection,
//                        TABLE_BOOKMARKS + "._id=?");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case BOOKMARKS: {
//                // Look for account info
//                Object[] withAccount = getSelectionWithAccounts(uri, selection, selectionArgs);
//                selection = (String) withAccount[0];
//                selectionArgs = (String[]) withAccount[1];
//                deleted = deleteBookmarks(selection, selectionArgs, callerIsSyncAdapter);
//                pruneImages();
//                if (deleted > 0) {
//                    refreshWidgets();
//                }
//                break;
//            }
//
//            case HISTORY_ID: {
//                selection = DatabaseUtils.concatenateWhere(selection, TABLE_HISTORY + "._id=?");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case HISTORY: {
//                filterSearchClient(selectionArgs);
//                deleted = db.delete(TABLE_HISTORY, selection, selectionArgs);
//                pruneImages();
//                break;
//            }
//
//            case SEARCHES_ID: {
//                selection = DatabaseUtils.concatenateWhere(selection, TABLE_SEARCHES + "._id=?");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case SEARCHES: {
//                deleted = db.delete(TABLE_SEARCHES, selection, selectionArgs);
//                break;
//            }
//
//            case SYNCSTATE: {
//                deleted = mSyncHelper.delete(db, selection, selectionArgs);
//                break;
//            }
//            case SYNCSTATE_ID: {
//                String selectionWithId =
//                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
//                        + (selection == null ? "" : " AND (" + selection + ")");
//                deleted = mSyncHelper.delete(db, selectionWithId, selectionArgs);
//                break;
//            }
//            case LEGACY_ID: {
//                selection = DatabaseUtils.concatenateWhere(
//                        selection, Combined._ID + " = CAST(? AS INTEGER)");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case LEGACY: {
//                String[] projection = new String[] { Combined._ID,
//                        Combined.IS_BOOKMARK, Combined.URL };
//                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
//                String[] args = createCombinedQuery(uri, projection, qb);
//                if (selectionArgs == null) {
//                    selectionArgs = args;
//                } else {
//                    selectionArgs = DatabaseUtils.appendSelectionArgs(
//                            args, selectionArgs);
//                }
//                Cursor c = qb.query(db, projection, selection, selectionArgs,
//                        null, null, null);
//                while (c.moveToNext()) {
//                    long id = c.getLong(0);
//                    boolean isBookmark = c.getInt(1) != 0;
//                    String url = c.getString(2);
//                    if (isBookmark) {
//                        deleted += deleteBookmarks(Bookmarks._ID + "=?",
//                                new String[] { Long.toString(id) },
//                                callerIsSyncAdapter);
//                        db.delete(TABLE_HISTORY, History.URL + "=?",
//                                new String[] { url });
//                    } else {
//                        deleted += db.delete(TABLE_HISTORY,
//                                Bookmarks._ID + "=?",
//                                new String[] { Long.toString(id) });
//                    }
//                }
//                c.close();
//                break;
//            }
//            case THUMBNAILS_ID: {
//                selection = DatabaseUtils.concatenateWhere(
//                        selection, Thumbnails._ID + " = ?");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case THUMBNAILS: {
//                deleted = db.delete(TABLE_THUMBNAILS, selection, selectionArgs);
//                break;
//            }
//            default: {
//                throw new UnsupportedOperationException("Unknown delete URI " + uri);
//            }
//        }
//        if (deleted > 0) {
//            postNotifyUri(uri);
//            if (shouldNotifyLegacy(uri)) {
//                postNotifyUri(LEGACY_AUTHORITY_URI);
//            }
//        }
//        return deleted;
//    }
//
//    long queryDefaultFolderId(String accountName, String accountType) {
//        if (!isNullAccount(accountName) && !isNullAccount(accountType)) {
//            final SQLiteDatabase db = mOpenHelper.getReadableDatabase();
//            Cursor c = db.query(TABLE_BOOKMARKS, new String[] { Bookmarks._ID },
//                    ChromeSyncColumns.SERVER_UNIQUE + " = ?" +
//                    " AND account_type = ? AND account_name = ?",
//                    new String[] { ChromeSyncColumns.FOLDER_NAME_BOOKMARKS_BAR,
//                    accountType, accountName }, null, null, null);
//            try {
//                if (c.moveToFirst()) {
//                    return c.getLong(0);
//                }
//            } finally {
//                c.close();
//            }
//        }
//        return FIXED_ID_ROOT;
//    }
//
//    @Override
//    public Uri insertInTransaction(Uri uri, ContentValues values, boolean callerIsSyncAdapter) {
//        int match = URI_MATCHER.match(uri);
//        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
//        long id = -1;
//        if (match == LEGACY) {
//            // Intercept and route to the correct table
//            Integer bookmark = values.getAsInteger(BookmarkColumns.BOOKMARK);
//            values.remove(BookmarkColumns.BOOKMARK);
//            if (bookmark == null || bookmark == 0) {
//                match = HISTORY;
//            } else {
//                match = BOOKMARKS;
//                values.remove(BookmarkColumns.DATE);
//                values.remove(BookmarkColumns.VISITS);
//                values.remove(BookmarkColumns.USER_ENTERED);
//                values.put(Bookmarks.IS_FOLDER, 0);
//            }
//        }
//        switch (match) {
//            case BOOKMARKS: {
//                // Mark rows dirty if they're not coming from a sync adapter
//                if (!callerIsSyncAdapter) {
//                    long now = System.currentTimeMillis();
//                    values.put(Bookmarks.DATE_CREATED, now);
//                    values.put(Bookmarks.DATE_MODIFIED, now);
//                    values.put(Bookmarks.DIRTY, 1);
//
//                    boolean hasAccounts = values.containsKey(Bookmarks.ACCOUNT_TYPE)
//                            || values.containsKey(Bookmarks.ACCOUNT_NAME);
//                    String accountType = values
//                            .getAsString(Bookmarks.ACCOUNT_TYPE);
//                    String accountName = values
//                            .getAsString(Bookmarks.ACCOUNT_NAME);
//                    boolean hasParent = values.containsKey(Bookmarks.PARENT);
//                    if (hasParent && hasAccounts) {
//                        // Let's make sure it's valid
//                        long parentId = values.getAsLong(Bookmarks.PARENT);
//                        hasParent = isValidParent(
//                                accountType, accountName, parentId);
//                    } else if (hasParent && !hasAccounts) {
//                        long parentId = values.getAsLong(Bookmarks.PARENT);
//                        hasParent = setParentValues(parentId, values);
//                    }
//
//                    // If no parent is set default to the "Bookmarks Bar" folder
//                    if (!hasParent) {
//                        values.put(Bookmarks.PARENT,
//                                queryDefaultFolderId(accountName, accountType));
//                    }
//                }
//
//                // If no position is requested put the bookmark at the beginning of the list
//                if (!values.containsKey(Bookmarks.POSITION)) {
//                    values.put(Bookmarks.POSITION, Long.toString(Long.MIN_VALUE));
//                }
//
//                // Extract out the image values so they can be inserted into the images table
//                String url = values.getAsString(Bookmarks.URL);
//                ContentValues imageValues = extractImageValues(values, url);
//                Boolean isFolder = values.getAsBoolean(Bookmarks.IS_FOLDER);
//                if ((isFolder == null || !isFolder)
//                        && imageValues != null && !TextUtils.isEmpty(url)) {
//                    int count = db.update(TABLE_IMAGES, imageValues, Images.URL + "=?",
//                            new String[] { url });
//                    if (count == 0) {
//                        db.insertOrThrow(TABLE_IMAGES, Images.FAVICON, imageValues);
//                    }
//                }
//
//                id = db.insertOrThrow(TABLE_BOOKMARKS, Bookmarks.DIRTY, values);
//                refreshWidgets();
//                break;
//            }
//
//            case HISTORY: {
//                // If no created time is specified set it to now
//                if (!values.containsKey(History.DATE_CREATED)) {
//                    values.put(History.DATE_CREATED, System.currentTimeMillis());
//                }
//                String url = values.getAsString(History.URL);
//                url = filterSearchClient(url);
//                values.put(History.URL, url);
//
//                // Extract out the image values so they can be inserted into the images table
//                ContentValues imageValues = extractImageValues(values,
//                        values.getAsString(History.URL));
//                if (imageValues != null) {
//                    db.insertOrThrow(TABLE_IMAGES, Images.FAVICON, imageValues);
//                }
//
//                id = db.insertOrThrow(TABLE_HISTORY, History.VISITS, values);
//                break;
//            }
//
//            case SEARCHES: {
//                id = insertSearchesInTransaction(db, values);
//                break;
//            }
//
//            case SYNCSTATE: {
//                id = mSyncHelper.insert(db, values);
//                break;
//            }
//
//            case SETTINGS: {
//                id = 0;
//                insertSettingsInTransaction(db, values);
//                break;
//            }
//
//            case THUMBNAILS: {
//                id = db.replaceOrThrow(TABLE_THUMBNAILS, null, values);
//                break;
//            }
//
//            default: {
//                throw new UnsupportedOperationException("Unknown insert URI " + uri);
//            }
//        }
//
//        if (id >= 0) {
//            postNotifyUri(uri);
//            if (shouldNotifyLegacy(uri)) {
//                postNotifyUri(LEGACY_AUTHORITY_URI);
//            }
//            return ContentUris.withAppendedId(uri, id);
//        } else {
//            return null;
//        }
//    }
//
//    private String[] getAccountNameAndType(long id) {
//        if (id <= 0) {
//            return null;
//        }
//        Uri uri = ContentUris.withAppendedId(Bookmarks.CONTENT_URI, id);
//        Cursor c = query(uri,
//                new String[] { Bookmarks.ACCOUNT_NAME, Bookmarks.ACCOUNT_TYPE },
//                null, null, null);
//        try {
//            if (c.moveToFirst()) {
//                String parentName = c.getString(0);
//                String parentType = c.getString(1);
//                return new String[] { parentName, parentType };
//            }
//            return null;
//        } finally {
//            c.close();
//        }
//    }
//
//    private boolean setParentValues(long parentId, ContentValues values) {
//        String[] parent = getAccountNameAndType(parentId);
//        if (parent == null) {
//            return false;
//        }
//        values.put(Bookmarks.ACCOUNT_NAME, parent[0]);
//        values.put(Bookmarks.ACCOUNT_TYPE, parent[1]);
//        return true;
//    }
//
//    private boolean isValidParent(String accountType, String accountName,
//            long parentId) {
//        String[] parent = getAccountNameAndType(parentId);
//        if (parent != null
//                && TextUtils.equals(accountName, parent[0])
//                && TextUtils.equals(accountType, parent[1])) {
//            return true;
//        }
//        return false;
//    }
//
//    private void filterSearchClient(String[] selectionArgs) {
//        if (selectionArgs != null) {
//            for (int i = 0; i < selectionArgs.length; i++) {
//                selectionArgs[i] = filterSearchClient(selectionArgs[i]);
//            }
//        }
//    }
//
//    // Filters out the client= param for search urls
//    private String filterSearchClient(String url) {
//        // remove "client" before updating it to the history so that it wont
//        // show up in the auto-complete list.
//        int index = url.indexOf("client=");
//        if (index > 0 && url.contains(".google.")) {
//            int end = url.indexOf('&', index);
//            if (end > 0) {
//                url = url.substring(0, index)
//                        .concat(url.substring(end + 1));
//            } else {
//                // the url.charAt(index-1) should be either '?' or '&'
//                url = url.substring(0, index-1);
//            }
//        }
//        return url;
//    }
//
//    /**
//     * Searches are unique, so perform an UPSERT manually since SQLite doesn't support them.
//     */
//    private long insertSearchesInTransaction(SQLiteDatabase db, ContentValues values) {
//        String search = values.getAsString(Searches.SEARCH);
//        if (TextUtils.isEmpty(search)) {
//            throw new IllegalArgumentException("Must include the SEARCH field");
//        }
//        Cursor cursor = null;
//        try {
//            cursor = db.query(TABLE_SEARCHES, new String[] { Searches._ID },
//                    Searches.SEARCH + "=?", new String[] { search }, null, null, null);
//            if (cursor.moveToNext()) {
//                long id = cursor.getLong(0);
//                db.update(TABLE_SEARCHES, values, Searches._ID + "=?",
//                        new String[] { Long.toString(id) });
//                return id;
//            } else {
//                return db.insertOrThrow(TABLE_SEARCHES, Searches.SEARCH, values);
//            }
//        } finally {
//            if (cursor != null) cursor.close();
//        }
//    }
//
//    /**
//     * Settings are unique, so perform an UPSERT manually since SQLite doesn't support them.
//     */
//    private long insertSettingsInTransaction(SQLiteDatabase db, ContentValues values) {
//        String key = values.getAsString(Settings.KEY);
//        if (TextUtils.isEmpty(key)) {
//            throw new IllegalArgumentException("Must include the KEY field");
//        }
//        String[] keyArray = new String[] { key };
//        Cursor cursor = null;
//        try {
//            cursor = db.query(TABLE_SETTINGS, new String[] { Settings.KEY },
//                    Settings.KEY + "=?", keyArray, null, null, null);
//            if (cursor.moveToNext()) {
//                long id = cursor.getLong(0);
//                db.update(TABLE_SETTINGS, values, Settings.KEY + "=?", keyArray);
//                return id;
//            } else {
//                return db.insertOrThrow(TABLE_SETTINGS, Settings.VALUE, values);
//            }
//        } finally {
//            if (cursor != null) cursor.close();
//        }
//    }
//
//    @Override
//    public int updateInTransaction(Uri uri, ContentValues values, String selection,
//            String[] selectionArgs, boolean callerIsSyncAdapter) {
//        int match = URI_MATCHER.match(uri);
//        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
//        if (match == LEGACY || match == LEGACY_ID) {
//            // Intercept and route to the correct table
//            Integer bookmark = values.getAsInteger(BookmarkColumns.BOOKMARK);
//            values.remove(BookmarkColumns.BOOKMARK);
//            if (bookmark == null || bookmark == 0) {
//                if (match == LEGACY) {
//                    match = HISTORY;
//                } else {
//                    match = HISTORY_ID;
//                }
//            } else {
//                if (match == LEGACY) {
//                    match = BOOKMARKS;
//                } else {
//                    match = BOOKMARKS_ID;
//                }
//                values.remove(BookmarkColumns.DATE);
//                values.remove(BookmarkColumns.VISITS);
//                values.remove(BookmarkColumns.USER_ENTERED);
//            }
//        }
//        int modified = 0;
//        switch (match) {
//            case BOOKMARKS_ID: {
//                selection = DatabaseUtils.concatenateWhere(selection,
//                        TABLE_BOOKMARKS + "._id=?");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case BOOKMARKS: {
//                Object[] withAccount = getSelectionWithAccounts(uri, selection, selectionArgs);
//                selection = (String) withAccount[0];
//                selectionArgs = (String[]) withAccount[1];
//                modified = updateBookmarksInTransaction(values, selection, selectionArgs,
//                        callerIsSyncAdapter);
//                if (modified > 0) {
//                    refreshWidgets();
//                }
//                break;
//            }
//
//            case HISTORY_ID: {
//                selection = DatabaseUtils.concatenateWhere(selection, TABLE_HISTORY + "._id=?");
//                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                        new String[] { Long.toString(ContentUris.parseId(uri)) });
//                // fall through
//            }
//            case HISTORY: {
//                modified = updateHistoryInTransaction(values, selection, selectionArgs);
//                break;
//            }
//
//            case SYNCSTATE: {
//                modified = mSyncHelper.update(mDb, values,
//                        appendAccountToSelection(uri, selection), selectionArgs);
//                break;
//            }
//
//            case SYNCSTATE_ID: {
//                selection = appendAccountToSelection(uri, selection);
//                String selectionWithId =
//                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
//                        + (selection == null ? "" : " AND (" + selection + ")");
//                modified = mSyncHelper.update(mDb, values,
//                        selectionWithId, selectionArgs);
//                break;
//            }
//
//            case IMAGES: {
//                String url = values.getAsString(Images.URL);
//                if (TextUtils.isEmpty(url)) {
//                    throw new IllegalArgumentException("Images.URL is required");
//                }
//                if (!shouldUpdateImages(db, url, values)) {
//                    return 0;
//                }
//                int count = db.update(TABLE_IMAGES, values, Images.URL + "=?",
//                        new String[] { url });
//                if (count == 0) {
//                    db.insertOrThrow(TABLE_IMAGES, Images.FAVICON, values);
//                    count = 1;
//                }
//                // Only favicon is exposed in the public API. If we updated
//                // the thumbnail or touch icon don't bother notifying the
//                // legacy authority since it can't read it anyway.
//                boolean updatedLegacy = false;
//                if (getUrlCount(db, TABLE_BOOKMARKS, url) > 0) {
//                    postNotifyUri(Bookmarks.CONTENT_URI);
//                    updatedLegacy = values.containsKey(Images.FAVICON);
//                    refreshWidgets();
//                }
//                if (getUrlCount(db, TABLE_HISTORY, url) > 0) {
//                    postNotifyUri(History.CONTENT_URI);
//                    updatedLegacy = values.containsKey(Images.FAVICON);
//                }
//                if (pruneImages() > 0 || updatedLegacy) {
//                    postNotifyUri(LEGACY_AUTHORITY_URI);
//                }
//                // Even though we may be calling notifyUri on Bookmarks, don't
//                // sync to network as images aren't synced. Otherwise this
//                // unnecessarily triggers a bookmark sync.
//                mSyncToNetwork = false;
//                return count;
//            }
//
//            case SEARCHES: {
//                modified = db.update(TABLE_SEARCHES, values, selection, selectionArgs);
//                break;
//            }
//
//            case ACCOUNTS: {
//                Account[] accounts = AccountManager.get(getContext()).getAccounts();
//                mSyncHelper.onAccountsChanged(mDb, accounts);
//                break;
//            }
//
//            case THUMBNAILS: {
//                modified = db.update(TABLE_THUMBNAILS, values,
//                        selection, selectionArgs);
//                break;
//            }
//
//            default: {
//                throw new UnsupportedOperationException("Unknown update URI " + uri);
//            }
//        }
//        pruneImages();
//        if (modified > 0) {
//            postNotifyUri(uri);
//            if (shouldNotifyLegacy(uri)) {
//                postNotifyUri(LEGACY_AUTHORITY_URI);
//            }
//        }
//        return modified;
//    }
//
//    // We want to avoid sending out more URI notifications than we have to
//    // Thus, we check to see if the images we are about to store are already there
//    // This is used because things like a site's favion or touch icon is rarely
//    // changed, but the browser tries to update it every time the page loads.
//    // Without this, we will always send out 3 URI notifications per page load.
//    // With this, that drops to 0 or 1, depending on if the thumbnail changed.
//    private boolean shouldUpdateImages(
//            SQLiteDatabase db, String url, ContentValues values) {
//        final String[] projection = new String[] {
//                Images.FAVICON,
//                Images.THUMBNAIL,
//                Images.TOUCH_ICON,
//        };
//        Cursor cursor = db.query(TABLE_IMAGES, projection, Images.URL + "=?",
//                new String[] { url }, null, null, null);
//        byte[] nfavicon = values.getAsByteArray(Images.FAVICON);
//        byte[] nthumb = values.getAsByteArray(Images.THUMBNAIL);
//        byte[] ntouch = values.getAsByteArray(Images.TOUCH_ICON);
//        byte[] cfavicon = null;
//        byte[] cthumb = null;
//        byte[] ctouch = null;
//        try {
//            if (cursor.getCount() <= 0) {
//                return nfavicon != null || nthumb != null || ntouch != null;
//            }
//            while (cursor.moveToNext()) {
//                if (nfavicon != null) {
//                    cfavicon = cursor.getBlob(0);
//                    if (!Arrays.equals(nfavicon, cfavicon)) {
//                        return true;
//                    }
//                }
//                if (nthumb != null) {
//                    cthumb = cursor.getBlob(1);
//                    if (!Arrays.equals(nthumb, cthumb)) {
//                        return true;
//                    }
//                }
//                if (ntouch != null) {
//                    ctouch = cursor.getBlob(2);
//                    if (!Arrays.equals(ntouch, ctouch)) {
//                        return true;
//                    }
//                }
//            }
//        } finally {
//            cursor.close();
//        }
//        return false;
//    }
//
//    int getUrlCount(SQLiteDatabase db, String table, String url) {
//        Cursor c = db.query(table, new String[] { "COUNT(*)" },
//                "url = ?", new String[] { url }, null, null, null);
//        try {
//            int count = 0;
//            if (c.moveToFirst()) {
//                count = c.getInt(0);
//            }
//            return count;
//        } finally {
//            c.close();
//        }
//    }
//
//    /**
//     * Does a query to find the matching bookmarks and updates each one with the provided values.
//     */
//    int updateBookmarksInTransaction(ContentValues values, String selection,
//            String[] selectionArgs, boolean callerIsSyncAdapter) {
//        int count = 0;
//        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
//        final String[] bookmarksProjection = new String[] {
//                Bookmarks._ID, // 0
//                Bookmarks.VERSION, // 1
//                Bookmarks.URL, // 2
//                Bookmarks.TITLE, // 3
//                Bookmarks.IS_FOLDER, // 4
//                Bookmarks.ACCOUNT_NAME, // 5
//                Bookmarks.ACCOUNT_TYPE, // 6
//        };
//        Cursor cursor = db.query(TABLE_BOOKMARKS, bookmarksProjection,
//                selection, selectionArgs, null, null, null);
//        boolean updatingParent = values.containsKey(Bookmarks.PARENT);
//        String parentAccountName = null;
//        String parentAccountType = null;
//        if (updatingParent) {
//            long parent = values.getAsLong(Bookmarks.PARENT);
//            Cursor c = db.query(TABLE_BOOKMARKS, new String[] {
//                    Bookmarks.ACCOUNT_NAME, Bookmarks.ACCOUNT_TYPE},
//                    "_id = ?", new String[] { Long.toString(parent) },
//                    null, null, null);
//            if (c.moveToFirst()) {
//                parentAccountName = c.getString(0);
//                parentAccountType = c.getString(1);
//            }
//            c.close();
//        } else if (values.containsKey(Bookmarks.ACCOUNT_NAME)
//                || values.containsKey(Bookmarks.ACCOUNT_TYPE)) {
//            // TODO: Implement if needed (no one needs this yet)
//        }
//        try {
//            String[] args = new String[1];
//            // Mark the bookmark dirty if the caller isn't a sync adapter
//            if (!callerIsSyncAdapter) {
//                values.put(Bookmarks.DATE_MODIFIED, System.currentTimeMillis());
//                values.put(Bookmarks.DIRTY, 1);
//            }
//
//            boolean updatingUrl = values.containsKey(Bookmarks.URL);
//            String url = null;
//            if (updatingUrl) {
//                url = values.getAsString(Bookmarks.URL);
//            }
//            ContentValues imageValues = extractImageValues(values, url);
//
//            while (cursor.moveToNext()) {
//                long id = cursor.getLong(0);
//                args[0] = Long.toString(id);
//                String accountName = cursor.getString(5);
//                String accountType = cursor.getString(6);
//                // If we are updating the parent and either the account name or
//                // type do not match that of the new parent
//                if (updatingParent
//                        && (!TextUtils.equals(accountName, parentAccountName)
//                        || !TextUtils.equals(accountType, parentAccountType))) {
//                    // Parent is a different account
//                    // First, insert a new bookmark/folder with the new account
//                    // Then, if this is a folder, reparent all it's children
//                    // Finally, delete the old bookmark/folder
//                    ContentValues newValues = valuesFromCursor(cursor);
//                    newValues.putAll(values);
//                    newValues.remove(Bookmarks._ID);
//                    newValues.remove(Bookmarks.VERSION);
//                    newValues.put(Bookmarks.ACCOUNT_NAME, parentAccountName);
//                    newValues.put(Bookmarks.ACCOUNT_TYPE, parentAccountType);
//                    Uri insertUri = insertInTransaction(Bookmarks.CONTENT_URI,
//                            newValues, callerIsSyncAdapter);
//                    long newId = ContentUris.parseId(insertUri);
//                    if (cursor.getInt(4) != 0) {
//                        // This is a folder, reparent
//                        ContentValues updateChildren = new ContentValues(1);
//                        updateChildren.put(Bookmarks.PARENT, newId);
//                        count += updateBookmarksInTransaction(updateChildren,
//                                Bookmarks.PARENT + "=?", new String[] {
//                                Long.toString(id)}, callerIsSyncAdapter);
//                    }
//                    // Now, delete the old one
//                    Uri uri = ContentUris.withAppendedId(Bookmarks.CONTENT_URI, id);
//                    deleteInTransaction(uri, null, null, callerIsSyncAdapter);
//                    count += 1;
//                } else {
//                    if (!callerIsSyncAdapter) {
//                        // increase the local version for non-sync changes
//                        values.put(Bookmarks.VERSION, cursor.getLong(1) + 1);
//                    }
//                    count += db.update(TABLE_BOOKMARKS, values, "_id=?", args);
//                }
//
//                // Update the images over in their table
//                if (imageValues != null) {
//                    if (!updatingUrl) {
//                        url = cursor.getString(2);
//                        imageValues.put(Images.URL, url);
//                    }
//
//                    if (!TextUtils.isEmpty(url)) {
//                        args[0] = url;
//                        if (db.update(TABLE_IMAGES, imageValues, Images.URL + "=?", args) == 0) {
//                            db.insert(TABLE_IMAGES, Images.FAVICON, imageValues);
//                        }
//                    }
//                }
//            }
//        } finally {
//            if (cursor != null) cursor.close();
//        }
//        return count;
//    }
//
//    ContentValues valuesFromCursor(Cursor c) {
//        int count = c.getColumnCount();
//        ContentValues values = new ContentValues(count);
//        String[] colNames = c.getColumnNames();
//        for (int i = 0; i < count; i++) {
//            switch (c.getType(i)) {
//            case Cursor.FIELD_TYPE_BLOB:
//                values.put(colNames[i], c.getBlob(i));
//                break;
//            case Cursor.FIELD_TYPE_FLOAT:
//                values.put(colNames[i], c.getFloat(i));
//                break;
//            case Cursor.FIELD_TYPE_INTEGER:
//                values.put(colNames[i], c.getLong(i));
//                break;
//            case Cursor.FIELD_TYPE_STRING:
//                values.put(colNames[i], c.getString(i));
//                break;
//            }
//        }
//        return values;
//    }
//
//    /**
//     * Does a query to find the matching bookmarks and updates each one with the provided values.
//     */
//    int updateHistoryInTransaction(ContentValues values, String selection, String[] selectionArgs) {
//        int count = 0;
//        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
//        filterSearchClient(selectionArgs);
//        Cursor cursor = query(History.CONTENT_URI,
//                new String[] { History._ID, History.URL },
//                selection, selectionArgs, null);
//        try {
//            String[] args = new String[1];
//
//            boolean updatingUrl = values.containsKey(History.URL);
//            String url = null;
//            if (updatingUrl) {
//                url = filterSearchClient(values.getAsString(History.URL));
//                values.put(History.URL, url);
//            }
//            ContentValues imageValues = extractImageValues(values, url);
//
//            while (cursor.moveToNext()) {
//                args[0] = cursor.getString(0);
//                count += db.update(TABLE_HISTORY, values, "_id=?", args);
//
//                // Update the images over in their table
//                if (imageValues != null) {
//                    if (!updatingUrl) {
//                        url = cursor.getString(1);
//                        imageValues.put(Images.URL, url);
//                    }
//                    args[0] = url;
//                    if (db.update(TABLE_IMAGES, imageValues, Images.URL + "=?", args) == 0) {
//                        db.insert(TABLE_IMAGES, Images.FAVICON, imageValues);
//                    }
//                }
//            }
//        } finally {
//            if (cursor != null) cursor.close();
//        }
//        return count;
//    }
//
//    String appendAccountToSelection(Uri uri, String selection) {
//        final String accountName = uri.getQueryParameter(RawContacts.ACCOUNT_NAME);
//        final String accountType = uri.getQueryParameter(RawContacts.ACCOUNT_TYPE);
//
//        final boolean partialUri = TextUtils.isEmpty(accountName) ^ TextUtils.isEmpty(accountType);
//        if (partialUri) {
//            // Throw when either account is incomplete
//            throw new IllegalArgumentException(
//                    "Must specify both or neither of ACCOUNT_NAME and ACCOUNT_TYPE for " + uri);
//        }
//
//        // Accounts are valid by only checking one parameter, since we've
//        // already ruled out partial accounts.
//        final boolean validAccount = !TextUtils.isEmpty(accountName);
//        if (validAccount) {
//            StringBuilder selectionSb = new StringBuilder(RawContacts.ACCOUNT_NAME + "="
//                    + DatabaseUtils.sqlEscapeString(accountName) + " AND "
//                    + RawContacts.ACCOUNT_TYPE + "="
//                    + DatabaseUtils.sqlEscapeString(accountType));
//            if (!TextUtils.isEmpty(selection)) {
//                selectionSb.append(" AND (");
//                selectionSb.append(selection);
//                selectionSb.append(')');
//            }
//            return selectionSb.toString();
//        } else {
//            return selection;
//        }
//    }
//
//    ContentValues extractImageValues(ContentValues values, String url) {
//        ContentValues imageValues = null;
//        // favicon
//        if (values.containsKey(Bookmarks.FAVICON)) {
//            imageValues = new ContentValues();
//            imageValues.put(Images.FAVICON, values.getAsByteArray(Bookmarks.FAVICON));
//            values.remove(Bookmarks.FAVICON);
//        }
//
//        // thumbnail
//        if (values.containsKey(Bookmarks.THUMBNAIL)) {
//            if (imageValues == null) {
//                imageValues = new ContentValues();
//            }
//            imageValues.put(Images.THUMBNAIL, values.getAsByteArray(Bookmarks.THUMBNAIL));
//            values.remove(Bookmarks.THUMBNAIL);
//        }
//
//        // touch icon
//        if (values.containsKey(Bookmarks.TOUCH_ICON)) {
//            if (imageValues == null) {
//                imageValues = new ContentValues();
//            }
//            imageValues.put(Images.TOUCH_ICON, values.getAsByteArray(Bookmarks.TOUCH_ICON));
//            values.remove(Bookmarks.TOUCH_ICON);
//        }
//
//        if (imageValues != null) {
//            imageValues.put(Images.URL,  url);
//        }
//        return imageValues;
//    }
//
//    int pruneImages() {
//        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
//        return db.delete(TABLE_IMAGES, IMAGE_PRUNE, null);
//    }
//
//    boolean shouldNotifyLegacy(Uri uri) {
//        if (uri.getPathSegments().contains("history")
//                || uri.getPathSegments().contains("bookmarks")
//                || uri.getPathSegments().contains("searches")) {
//            return true;
//        }
//        return false;
//    }
//
//    @Override
//    protected boolean syncToNetwork(Uri uri) {
//        if (BrowserContract.AUTHORITY.equals(uri.getAuthority())
//                && uri.getPathSegments().contains("bookmarks")) {
//            return mSyncToNetwork;
//        }
//        if (LEGACY_AUTHORITY.equals(uri.getAuthority())) {
//            // Allow for 3rd party sync adapters
//            return true;
//        }
//        return false;
//    }
//
//    static class SuggestionsCursor extends AbstractCursor {
//        private static final int ID_INDEX = 0;
//        private static final int URL_INDEX = 1;
//        private static final int TITLE_INDEX = 2;
//        private static final int ICON_INDEX = 3;
//        private static final int LAST_ACCESS_TIME_INDEX = 4;
//        // shared suggestion array index, make sure to match COLUMNS
//        private static final int SUGGEST_COLUMN_INTENT_ACTION_ID = 1;
//        private static final int SUGGEST_COLUMN_INTENT_DATA_ID = 2;
//        private static final int SUGGEST_COLUMN_TEXT_1_ID = 3;
//        private static final int SUGGEST_COLUMN_TEXT_2_TEXT_ID = 4;
//        private static final int SUGGEST_COLUMN_TEXT_2_URL_ID = 5;
//        private static final int SUGGEST_COLUMN_ICON_1_ID = 6;
//        private static final int SUGGEST_COLUMN_LAST_ACCESS_HINT_ID = 7;
//
//        // shared suggestion columns
//        private static final String[] COLUMNS = new String[] {
//                BaseColumns._ID,
//                SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
//                SearchManager.SUGGEST_COLUMN_INTENT_DATA,
//                SearchManager.SUGGEST_COLUMN_TEXT_1,
//                SearchManager.SUGGEST_COLUMN_TEXT_2,
//                SearchManager.SUGGEST_COLUMN_TEXT_2_URL,
//                SearchManager.SUGGEST_COLUMN_ICON_1,
//                SearchManager.SUGGEST_COLUMN_LAST_ACCESS_HINT};
//
//        private final Cursor mSource;
//
//        public SuggestionsCursor(Cursor cursor) {
//            mSource = cursor;
//        }
//
//        @Override
//        public String[] getColumnNames() {
//            return COLUMNS;
//        }
//
//        @Override
//        public String getString(int columnIndex) {
//            switch (columnIndex) {
//            case ID_INDEX:
//                return mSource.getString(columnIndex);
//            case SUGGEST_COLUMN_INTENT_ACTION_ID:
//                return Intent.ACTION_VIEW;
//            case SUGGEST_COLUMN_INTENT_DATA_ID:
//                return mSource.getString(URL_INDEX);
//            case SUGGEST_COLUMN_TEXT_2_TEXT_ID:
//            case SUGGEST_COLUMN_TEXT_2_URL_ID:
//                return UrlUtils.stripUrl(mSource.getString(URL_INDEX));
//            case SUGGEST_COLUMN_TEXT_1_ID:
//                return mSource.getString(TITLE_INDEX);
//            case SUGGEST_COLUMN_ICON_1_ID:
//                return mSource.getString(ICON_INDEX);
//            case SUGGEST_COLUMN_LAST_ACCESS_HINT_ID:
//                return mSource.getString(LAST_ACCESS_TIME_INDEX);
//            }
//            return null;
//        }
//
//        @Override
//        public int getCount() {
//            return mSource.getCount();
//        }
//
//        @Override
//        public double getDouble(int column) {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public float getFloat(int column) {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public int getInt(int column) {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public long getLong(int column) {
//            switch (column) {
//            case ID_INDEX:
//                return mSource.getLong(ID_INDEX);
//            case SUGGEST_COLUMN_LAST_ACCESS_HINT_ID:
//                return mSource.getLong(LAST_ACCESS_TIME_INDEX);
//            }
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public short getShort(int column) {
//            throw new UnsupportedOperationException();
//        }
//
//        @Override
//        public boolean isNull(int column) {
//            return mSource.isNull(column);
//        }
//
//        @Override
//        public boolean onMove(int oldPosition, int newPosition) {
//            return mSource.moveToPosition(newPosition);
//        }
//    }
//
//    // ---------------------------------------------------
//    //  SQL below, be warned
//    // ---------------------------------------------------
//
//    private static final String SQL_CREATE_VIEW_OMNIBOX_SUGGESTIONS =
//            "CREATE VIEW IF NOT EXISTS v_omnibox_suggestions "
//            + " AS "
//            + "  SELECT _id, url, title, 1 AS bookmark, 0 AS visits, 0 AS date"
//            + "  FROM bookmarks "
//            + "  WHERE deleted = 0 AND folder = 0 "
//            + "  UNION ALL "
//            + "  SELECT _id, url, title, 0 AS bookmark, visits, date "
//            + "  FROM history "
//            + "  WHERE url NOT IN (SELECT url FROM bookmarks"
//            + "    WHERE deleted = 0 AND folder = 0) "
//            + "  ORDER BY bookmark DESC, visits DESC, date DESC ";
//
//    private static final String SQL_WHERE_ACCOUNT_HAS_BOOKMARKS =
//            "0 < ( "
//            + "SELECT count(*) "
//            + "FROM bookmarks "
//            + "WHERE deleted = 0 AND folder = 0 "
//            + "  AND ( "
//            + "    v_accounts.account_name = bookmarks.account_name "
//            + "    OR (v_accounts.account_name IS NULL AND bookmarks.account_name IS NULL) "
//            + "  ) "
//            + "  AND ( "
//            + "    v_accounts.account_type = bookmarks.account_type "
//            + "    OR (v_accounts.account_type IS NULL AND bookmarks.account_type IS NULL) "
//            + "  ) "
//            + ")";
//}

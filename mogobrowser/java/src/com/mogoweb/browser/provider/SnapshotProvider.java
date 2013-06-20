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
//package com.mogoweb.browser.provider;
//
//import android.content.ContentProvider;
//import android.content.ContentUris;
//import android.content.ContentValues;
//import android.content.Context;
//import android.content.UriMatcher;
//import android.database.Cursor;
//import android.database.DatabaseUtils;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteOpenHelper;
//import android.database.sqlite.SQLiteQueryBuilder;
//import android.net.Uri;
//import android.os.FileUtils;
//import android.provider.BrowserContract;
//import android.text.TextUtils;
//
//import java.io.File;
//
//public class SnapshotProvider extends ContentProvider {
//
//    public static interface Snapshots {
//
//        public static final Uri CONTENT_URI = Uri.withAppendedPath(
//                SnapshotProvider.AUTHORITY_URI, "snapshots");
//        public static final String _ID = "_id";
//        @Deprecated
//        public static final String VIEWSTATE = "view_state";
//        public static final String BACKGROUND = "background";
//        public static final String TITLE = "title";
//        public static final String URL = "url";
//        public static final String FAVICON = "favicon";
//        public static final String THUMBNAIL = "thumbnail";
//        public static final String DATE_CREATED = "date_created";
//        public static final String VIEWSTATE_PATH = "viewstate_path";
//        public static final String VIEWSTATE_SIZE = "viewstate_size";
//    }
//
//    public static final String AUTHORITY = "com.mogoweb.browser.snapshots";
//    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);
//
//    static final String TABLE_SNAPSHOTS = "snapshots";
//    static final int SNAPSHOTS = 10;
//    static final int SNAPSHOTS_ID = 11;
//    static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
//    // Workaround that we can't remove the "NOT NULL" constraint on VIEWSTATE
//    static final byte[] NULL_BLOB_HACK = new byte[0];
//
//    SnapshotDatabaseHelper mOpenHelper;
//
//    static {
//        URI_MATCHER.addURI(AUTHORITY, "snapshots", SNAPSHOTS);
//        URI_MATCHER.addURI(AUTHORITY, "snapshots/#", SNAPSHOTS_ID);
//    }
//
//    final static class SnapshotDatabaseHelper extends SQLiteOpenHelper {
//
//        static final String DATABASE_NAME = "snapshots.db";
//        static final int DATABASE_VERSION = 3;
//
//        public SnapshotDatabaseHelper(Context context) {
//            super(context, DATABASE_NAME, null, DATABASE_VERSION);
//        }
//
//        @Override
//        public void onCreate(SQLiteDatabase db) {
//            db.execSQL("CREATE TABLE " + TABLE_SNAPSHOTS + "(" +
//                    Snapshots._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
//                    Snapshots.TITLE + " TEXT," +
//                    Snapshots.URL + " TEXT NOT NULL," +
//                    Snapshots.DATE_CREATED + " INTEGER," +
//                    Snapshots.FAVICON + " BLOB," +
//                    Snapshots.THUMBNAIL + " BLOB," +
//                    Snapshots.BACKGROUND + " INTEGER," +
//                    Snapshots.VIEWSTATE + " BLOB NOT NULL," +
//                    Snapshots.VIEWSTATE_PATH + " TEXT," +
//                    Snapshots.VIEWSTATE_SIZE + " INTEGER" +
//                    ");");
//        }
//
//        @Override
//        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
//            if (oldVersion < 2) {
//                db.execSQL("DROP TABLE " + TABLE_SNAPSHOTS);
//                onCreate(db);
//            }
//            if (oldVersion < 3) {
//                db.execSQL("ALTER TABLE " + TABLE_SNAPSHOTS + " ADD COLUMN "
//                        + Snapshots.VIEWSTATE_PATH + " TEXT");
//                db.execSQL("ALTER TABLE " + TABLE_SNAPSHOTS + " ADD COLUMN "
//                        + Snapshots.VIEWSTATE_SIZE + " INTEGER");
//                db.execSQL("UPDATE " + TABLE_SNAPSHOTS + " SET "
//                        + Snapshots.VIEWSTATE_SIZE + " = length("
//                        + Snapshots.VIEWSTATE + ")");
//            }
//        }
//
//    }
//
//    static File getOldDatabasePath(Context context) {
//        File dir = context.getExternalFilesDir(null);
//        return new File(dir, SnapshotDatabaseHelper.DATABASE_NAME);
//    }
//
//    private void migrateToDataFolder() {
//        File dbPath = getContext().getDatabasePath(SnapshotDatabaseHelper.DATABASE_NAME);
//        if (dbPath.exists()) return;
//        File oldPath = getOldDatabasePath(getContext());
//        if (oldPath.exists()) {
//            // Try to move
//            if (!oldPath.renameTo(dbPath)) {
//                // Failed, do a copy
//                FileUtils.copyFile(oldPath, dbPath);
//            }
//            // Cleanup
//            oldPath.delete();
//        }
//    }
//
//    @Override
//    public boolean onCreate() {
//        migrateToDataFolder();
//        mOpenHelper = new SnapshotDatabaseHelper(getContext());
//        return true;
//    }
//
//    SQLiteDatabase getWritableDatabase() {
//        return mOpenHelper.getWritableDatabase();
//    }
//
//    SQLiteDatabase getReadableDatabase() {
//        return mOpenHelper.getReadableDatabase();
//    }
//
//    @Override
//    public Cursor query(Uri uri, String[] projection, String selection,
//            String[] selectionArgs, String sortOrder) {
//        SQLiteDatabase db = getReadableDatabase();
//        if (db == null) {
//            return null;
//        }
//        final int match = URI_MATCHER.match(uri);
//        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
//        String limit = uri.getQueryParameter(BrowserContract.PARAM_LIMIT);
//        switch (match) {
//        case SNAPSHOTS_ID:
//            selection = DatabaseUtils.concatenateWhere(selection, "_id=?");
//            selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                    new String[] { Long.toString(ContentUris.parseId(uri)) });
//            // fall through
//        case SNAPSHOTS:
//            qb.setTables(TABLE_SNAPSHOTS);
//            break;
//
//        default:
//            throw new UnsupportedOperationException("Unknown URL " + uri.toString());
//        }
//        Cursor cursor = qb.query(db, projection, selection, selectionArgs,
//                null, null, sortOrder, limit);
//        cursor.setNotificationUri(getContext().getContentResolver(),
//                AUTHORITY_URI);
//        return cursor;
//    }
//
//    @Override
//    public String getType(Uri uri) {
//        return null;
//    }
//
//    @Override
//    public Uri insert(Uri uri, ContentValues values) {
//        SQLiteDatabase db = getWritableDatabase();
//        if (db == null) {
//            return null;
//        }
//        int match = URI_MATCHER.match(uri);
//        long id = -1;
//        switch (match) {
//        case SNAPSHOTS:
//            if (!values.containsKey(Snapshots.VIEWSTATE)) {
//                values.put(Snapshots.VIEWSTATE, NULL_BLOB_HACK);
//            }
//            id = db.insert(TABLE_SNAPSHOTS, Snapshots.TITLE, values);
//            break;
//        default:
//            throw new UnsupportedOperationException("Unknown insert URI " + uri);
//        }
//        if (id < 0) {
//            return null;
//        }
//        Uri inserted = ContentUris.withAppendedId(uri, id);
//        getContext().getContentResolver().notifyChange(inserted, null, false);
//        return inserted;
//    }
//
//    static final String[] DELETE_PROJECTION = new String[] {
//        Snapshots.VIEWSTATE_PATH,
//    };
//    private void deleteDataFiles(SQLiteDatabase db, String selection,
//            String[] selectionArgs) {
//        Cursor c = db.query(TABLE_SNAPSHOTS, DELETE_PROJECTION, selection,
//                selectionArgs, null, null, null);
//        final Context context = getContext();
//        while (c.moveToNext()) {
//            String filename = c.getString(0);
//            if (TextUtils.isEmpty(filename)) {
//                continue;
//            }
//            File f = context.getFileStreamPath(filename);
//            if (f.exists()) {
//                if (!f.delete()) {
//                    f.deleteOnExit();
//                }
//            }
//        }
//        c.close();
//    }
//
//    @Override
//    public int delete(Uri uri, String selection, String[] selectionArgs) {
//        SQLiteDatabase db = getWritableDatabase();
//        if (db == null) {
//            return 0;
//        }
//        int match = URI_MATCHER.match(uri);
//        int deleted = 0;
//        switch (match) {
//        case SNAPSHOTS_ID: {
//            selection = DatabaseUtils.concatenateWhere(selection, TABLE_SNAPSHOTS + "._id=?");
//            selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
//                    new String[] { Long.toString(ContentUris.parseId(uri)) });
//            // fall through
//        }
//        case SNAPSHOTS:
//            deleteDataFiles(db, selection, selectionArgs);
//            deleted = db.delete(TABLE_SNAPSHOTS, selection, selectionArgs);
//            break;
//        default:
//            throw new UnsupportedOperationException("Unknown delete URI " + uri);
//        }
//        if (deleted > 0) {
//            getContext().getContentResolver().notifyChange(uri, null, false);
//        }
//        return deleted;
//    }
//
//    @Override
//    public int update(Uri uri, ContentValues values, String selection,
//            String[] selectionArgs) {
//        throw new UnsupportedOperationException("not implemented");
//    }
//
//}

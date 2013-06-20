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
//package com.mogoweb.browser;
//
//import android.content.Context;
//import android.database.Cursor;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteOpenHelper;
//import android.provider.BaseColumns;
//import android.util.Log;
//import android.webkit.WebSettingsClassic.AutoFillProfile;
//
//public class AutoFillProfileDatabase {
//
//    static final String LOGTAG = "AutoFillProfileDatabase";
//
//    static final String DATABASE_NAME = "autofill.db";
//    static final int DATABASE_VERSION = 2;
//    static final String PROFILES_TABLE_NAME = "profiles";
//    private AutoFillProfileDatabaseHelper mOpenHelper;
//    private static AutoFillProfileDatabase sInstance;
//
//    public static final class Profiles implements BaseColumns {
//        private Profiles() { }
//
//        static final String FULL_NAME = "fullname";
//        static final String EMAIL_ADDRESS = "email";
//        static final String COMPANY_NAME = "companyname";
//        static final String ADDRESS_LINE_1 = "addressline1";
//        static final String ADDRESS_LINE_2 = "addressline2";
//        static final String CITY = "city";
//        static final String STATE = "state";
//        static final String ZIP_CODE = "zipcode";
//        static final String COUNTRY = "country";
//        static final String PHONE_NUMBER = "phone";
//    }
//
//    private static class AutoFillProfileDatabaseHelper extends SQLiteOpenHelper {
//        AutoFillProfileDatabaseHelper(Context context) {
//             super(context, DATABASE_NAME, null, DATABASE_VERSION);
//        }
//
//        @Override
//        public void onCreate(SQLiteDatabase db) {
//            db.execSQL("CREATE TABLE " + PROFILES_TABLE_NAME + " ("
//                    + Profiles._ID + " INTEGER PRIMARY KEY,"
//                    + Profiles.FULL_NAME + " TEXT,"
//                    + Profiles.EMAIL_ADDRESS + " TEXT,"
//                    + Profiles.COMPANY_NAME + " TEXT,"
//                    + Profiles.ADDRESS_LINE_1 + " TEXT,"
//                    + Profiles.ADDRESS_LINE_2 + " TEXT,"
//                    + Profiles.CITY + " TEXT,"
//                    + Profiles.STATE + " TEXT,"
//                    + Profiles.ZIP_CODE + " TEXT,"
//                    + Profiles.COUNTRY + " TEXT,"
//                    + Profiles.PHONE_NUMBER + " TEXT"
//                    + " );");
//        }
//
//        @Override
//        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
//            Log.w(LOGTAG, "Upgrading database from version " + oldVersion + " to "
//                    + newVersion + ", which will destroy all old data");
//            db.execSQL("DROP TABLE IF EXISTS " + PROFILES_TABLE_NAME);
//            onCreate(db);
//        }
//    }
//
//    private AutoFillProfileDatabase(Context context) {
//        mOpenHelper = new AutoFillProfileDatabaseHelper(context);
//    }
//
//    public static AutoFillProfileDatabase getInstance(Context context) {
//        if (sInstance == null) {
//            sInstance = new AutoFillProfileDatabase(context);
//        }
//        return sInstance;
//    }
//
//    private SQLiteDatabase getDatabase(boolean writable) {
//        return writable ? mOpenHelper.getWritableDatabase() : mOpenHelper.getReadableDatabase();
//    }
//
//    public void addOrUpdateProfile(final int id, AutoFillProfile profile) {
//        final String sql = "INSERT OR REPLACE INTO " + PROFILES_TABLE_NAME + " ("
//                + Profiles._ID + ","
//                + Profiles.FULL_NAME + ","
//                + Profiles.EMAIL_ADDRESS + ","
//                + Profiles.COMPANY_NAME + ","
//                + Profiles.ADDRESS_LINE_1 + ","
//                + Profiles.ADDRESS_LINE_2 + ","
//                + Profiles.CITY + ","
//                + Profiles.STATE + ","
//                + Profiles.ZIP_CODE + ","
//                + Profiles.COUNTRY + ","
//                + Profiles.PHONE_NUMBER
//                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?);";
//        final Object[] params = { id,
//                profile.getFullName(),
//                profile.getEmailAddress(),
//                profile.getCompanyName(),
//                profile.getAddressLine1(),
//                profile.getAddressLine2(),
//                profile.getCity(),
//                profile.getState(),
//                profile.getZipCode(),
//                profile.getCountry(),
//                profile.getPhoneNumber() };
//        getDatabase(true).execSQL(sql, params);
//    }
//
//    public Cursor getProfile(int id) {
//        final String[] cols = {
//                Profiles.FULL_NAME,
//                Profiles.EMAIL_ADDRESS,
//                Profiles.COMPANY_NAME,
//                Profiles.ADDRESS_LINE_1,
//                Profiles.ADDRESS_LINE_2,
//                Profiles.CITY,
//                Profiles.STATE,
//                Profiles.ZIP_CODE,
//                Profiles.COUNTRY,
//                Profiles.PHONE_NUMBER
//        };
//
//        final String[] selectArgs = { Integer.toString(id) };
//        return getDatabase(false).query(PROFILES_TABLE_NAME, cols, Profiles._ID + "=?", selectArgs,
//                null, null, null, "1");
//    }
//
//    public void dropProfile(int id) {
//        final String sql = "DELETE FROM " + PROFILES_TABLE_NAME +" WHERE " + Profiles._ID + " = ?;";
//        final Object[] params = { id };
//        getDatabase(true).execSQL(sql, params);
//    }
//
//    public void close() {
//        mOpenHelper.close();
//    }
//}

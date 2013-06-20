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
//import android.accounts.Account;
//import android.accounts.AccountManager;
//import android.content.BroadcastReceiver;
//import android.content.ContentResolver;
//import android.content.Context;
//import android.content.Intent;
//import android.database.Cursor;
//import android.net.Uri;
//import android.provider.BrowserContract;
//import android.provider.BrowserContract.Accounts;
//import android.provider.BrowserContract.Bookmarks;
//import android.text.TextUtils;
//
//public class AccountsChangedReceiver extends BroadcastReceiver {
//
//    private static final String[] PROJECTION = new String[] {
//        Accounts.ACCOUNT_NAME,
//        Accounts.ACCOUNT_TYPE,
//    };
//    private static final String SELECTION = Accounts.ACCOUNT_NAME + " IS NOT NULL";
//    private static final String DELETE_SELECTION = Accounts.ACCOUNT_NAME + "=? AND "
//            + Accounts.ACCOUNT_TYPE + "=?";
//
//    @Override
//    public void onReceive(Context context, Intent intent) {
//        new DeleteRemovedAccounts(context).start();
//    }
//
//    static class DeleteRemovedAccounts extends Thread {
//        Context mContext;
//        public DeleteRemovedAccounts(Context context) {
//            mContext = context.getApplicationContext();
//        }
//
//        @Override
//        public void run() {
//            Account[] accounts = AccountManager.get(mContext).getAccounts();
//            ContentResolver cr = mContext.getContentResolver();
//            Cursor c = cr.query(Accounts.CONTENT_URI, PROJECTION,
//                    SELECTION, null, null);
//            while (c.moveToNext()) {
//                String name = c.getString(0);
//                String type = c.getString(1);
//                if (!contains(accounts, name, type)) {
//                    delete(cr, name, type);
//                }
//            }
//            cr.update(Accounts.CONTENT_URI, null, null, null);
//            c.close();
//        }
//
//        void delete(ContentResolver cr, String name, String type) {
//            // Pretend to be a sync adapter to delete the data and not mark
//            // it for deletion. Without this, the bookmarks will be marked to
//            // be deleted, which will propagate to the server if the account
//            // is added back.
//            Uri uri = Bookmarks.CONTENT_URI.buildUpon()
//                    .appendQueryParameter(BrowserContract.CALLER_IS_SYNCADAPTER, "true")
//                    .build();
//            cr.delete(uri, DELETE_SELECTION, new String[] { name, type });
//        }
//
//        boolean contains(Account[] accounts, String name, String type) {
//            for (Account a : accounts) {
//                if (TextUtils.equals(a.name, name)
//                        && TextUtils.equals(a.type, type)) {
//                    return true;
//                }
//            }
//            return false;
//        }
//    }
//}

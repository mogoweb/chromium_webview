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
//
//package com.mogoweb.browser.widget;
//
//import android.app.ListActivity;
//import android.app.LoaderManager.LoaderCallbacks;
//import android.appwidget.AppWidgetManager;
//import android.content.Context;
//import android.content.CursorLoader;
//import android.content.Intent;
//import android.content.Loader;
//import android.database.Cursor;
//import android.os.Bundle;
//import android.provider.BrowserContract.Accounts;
//import android.view.View;
//import android.view.View.OnClickListener;
//import android.widget.ArrayAdapter;
//import android.widget.ListView;
//
//import com.mogoweb.browser.R;
//import com.mogoweb.browser.AddBookmarkPage.BookmarkAccount;
//import com.mogoweb.browser.provider.BrowserProvider2;
//
//public class BookmarkWidgetConfigure extends ListActivity
//        implements OnClickListener, LoaderCallbacks<Cursor> {
//
//    static final int LOADER_ACCOUNTS = 1;
//
//    private ArrayAdapter<BookmarkAccount> mAccountAdapter;
//    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setResult(RESULT_CANCELED);
//        setVisible(false);
//        setContentView(R.layout.widget_account_selection);
//        findViewById(R.id.cancel).setOnClickListener(this);
//        mAccountAdapter = new ArrayAdapter<BookmarkAccount>(this,
//                android.R.layout.simple_list_item_1);
//        setListAdapter(mAccountAdapter);
//        Intent intent = getIntent();
//        Bundle extras = intent.getExtras();
//        if (extras != null) {
//            mAppWidgetId = extras.getInt(
//                    AppWidgetManager.EXTRA_APPWIDGET_ID,
//                    AppWidgetManager.INVALID_APPWIDGET_ID);
//        }
//        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
//            finish();
//        } else {
//            getLoaderManager().initLoader(LOADER_ACCOUNTS, null, this);
//        }
//    }
//
//    @Override
//    public void onClick(View v) {
//        finish();
//    }
//
//    @Override
//    protected void onListItemClick(ListView l, View v, int position, long id) {
//        BookmarkAccount account = mAccountAdapter.getItem(position);
//        pickAccount(account.rootFolderId);
//    }
//
//    @Override
//    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
//        return new AccountsLoader(this);
//    }
//
//    void pickAccount(long rootId) {
//        BookmarkThumbnailWidgetService.setupWidgetState(this, mAppWidgetId, rootId);
//        Intent result = new Intent();
//        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
//        setResult(RESULT_OK, result);
//        finish();
//    }
//
//    @Override
//    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
//        if (cursor == null || cursor.getCount() < 1) {
//            // We always have the local account, so fall back to that
//            pickAccount(BrowserProvider2.FIXED_ID_ROOT);
//        } else if (cursor.getCount() == 1) {
//            cursor.moveToFirst();
//            pickAccount(cursor.getLong(AccountsLoader.COLUMN_INDEX_ROOT_ID));
//        } else {
//            mAccountAdapter.clear();
//            while (cursor.moveToNext()) {
//                mAccountAdapter.add(new BookmarkAccount(this, cursor));
//            }
//            setVisible(true);
//        }
//        getLoaderManager().destroyLoader(LOADER_ACCOUNTS);
//    }
//
//    @Override
//    public void onLoaderReset(Loader<Cursor> loader) {
//        // Don't care
//    }
//
//    static class AccountsLoader extends CursorLoader {
//
//        static final String[] PROJECTION = new String[] {
//            Accounts.ACCOUNT_NAME,
//            Accounts.ACCOUNT_TYPE,
//            Accounts.ROOT_ID,
//        };
//
//        static final int COLUMN_INDEX_ACCOUNT_NAME = 0;
//        static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
//        static final int COLUMN_INDEX_ROOT_ID = 2;
//
//        public AccountsLoader(Context context) {
//            super(context, Accounts.CONTENT_URI
//                    .buildUpon()
//                    .appendQueryParameter(BrowserProvider2.PARAM_ALLOW_EMPTY_ACCOUNTS, "false")
//                    .build(), PROJECTION, null, null, null);
//        }
//
//    }
//
//}

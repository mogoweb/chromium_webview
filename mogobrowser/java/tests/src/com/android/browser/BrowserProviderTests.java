/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogoweb.browser;

import com.mogoweb.browser.provider.BrowserProvider;
import com.mogoweb.browser.provider.BrowserProvider2;
import com.mogoweb.browser.tests.utils.ProviderTestCase3;

import android.app.SearchManager;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BrowserContract;
import android.test.suitebuilder.annotation.MediumTest;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Unit tests for {@link BrowserProvider}.
 */
@MediumTest
public class BrowserProviderTests extends ProviderTestCase3<BrowserProvider2> {

    private ArrayList<Uri> mDeleteUris;

    public BrowserProviderTests() {
        super(BrowserProvider2.class,
                BrowserContract.AUTHORITY, BrowserProvider2.LEGACY_AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        mDeleteUris = new ArrayList<Uri>();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        for (Uri uri : mDeleteUris) {
            deleteUri(uri);
        }
        super.tearDown();
    }

    public void testHasDefaultBookmarks() {
        Cursor c = getBookmarksSuggest("");
        try {
            assertTrue("No default bookmarks", c.getCount() > 0);
        } finally {
            c.close();
        }
    }

    public void testPartialFirstTitleWord() {
        assertInsertQuery("http://www.example.com/rasdfe", "nfgjra sdfywe", "nfgj");
    }

    public void testFullFirstTitleWord() {
        assertInsertQuery("http://www.example.com/", "nfgjra dfger", "nfgjra");
    }

    public void testFullFirstTitleWordPartialSecond() {
        assertInsertQuery("http://www.example.com/", "nfgjra dfger", "nfgjra df");
    }

    public void testFullTitle() {
        assertInsertQuery("http://www.example.com/", "nfgjra dfger", "nfgjra dfger");
    }

// Not implemented in BrowserProvider
//    public void testFullSecondTitleWord() {
//        assertInsertQuery("http://www.example.com/rasdfe", "nfgjra sdfywe", "sdfywe");
//    }

    public void testFullTitleJapanese() {
        String title = "\u30ae\u30e3\u30e9\u30ea\u30fc\u30fcGoogle\u691c\u7d22";
        assertInsertQuery("http://www.example.com/sdaga", title, title);
    }

    public void testPartialTitleJapanese() {
        String title = "\u30ae\u30e3\u30e9\u30ea\u30fc\u30fcGoogle\u691c\u7d22";
        String query = "\u30ae\u30e3\u30e9\u30ea\u30fc";
        assertInsertQuery("http://www.example.com/sdaga", title, query);
    }

    // Test for http://b/issue?id=2152749
    public void testSoundmarkTitleJapanese() {
        String title = "\u30ae\u30e3\u30e9\u30ea\u30fc\u30fcGoogle\u691c\u7d22";
        String query = "\u30ad\u30e3\u30e9\u30ea\u30fc";
        assertInsertQuery("http://www.example.com/sdaga", title, query);
    }

    //
    // Utilities
    //

    private void assertInsertQuery(String url, String title, String query) {
        addBookmark(url, title);
        assertQueryReturns(url, title, query);
    }

    private void assertQueryReturns(String url, String title, String query) {
        Cursor c = getBookmarksSuggest(query);
        try {
            assertTrue(title + " not matched by " + query, c.getCount() > 0);
            assertTrue("More than one result for " + query, c.getCount() == 1);
            while (c.moveToNext()) {
                String text1 = getCol(c, SearchManager.SUGGEST_COLUMN_TEXT_1);
                assertNotNull(text1);
                assertEquals("Bad title", title, text1);
                String text2 = getCol(c, SearchManager.SUGGEST_COLUMN_TEXT_2);
                assertNotNull(text2);
                String data = getCol(c, SearchManager.SUGGEST_COLUMN_INTENT_DATA);
                assertNotNull(data);
                assertEquals("Bad URL", url, data);
            }
        } finally {
            c.close();
        }
    }

    private Cursor getBookmarksSuggest(String query) {
        Uri suggestUri = Uri.parse("content://browser/bookmarks/search_suggest_query");
        String[] selectionArgs = { query };
        Cursor c = getMockContentResolver().query(suggestUri, null, "url LIKE ?",
                selectionArgs, null);
        assertNotNull(c);
        return c;
    }

    private void addBookmark(String url, String title) {
        Uri uri = insertBookmark(url, title);
        assertNotNull(uri);
        assertFalse(android.provider.Browser.BOOKMARKS_URI.equals(uri));
        mDeleteUris.add(uri);
    }

    private Uri insertBookmark(String url, String title) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("url", url);
        values.put("visits", 0);
        values.put("date", 0);
        values.put("created", 0);
        values.put("bookmark", 1);
        return getMockContentResolver().insert(android.provider.Browser.BOOKMARKS_URI,
                values);
    }

    private void deleteUri(Uri uri) {
        int count = getMockContentResolver().delete(uri, null, null);
        assertEquals("Failed to delete " + uri, 1, count);
    }

    private static String getCol(Cursor c, String name) {
        int col = c.getColumnIndex(name);
        String msg = "Column " + name + " not found, columns: "
                + Arrays.toString(c.getColumnNames());
        assertTrue(msg, col >= 0);
        return c.getString(col);
    }
}

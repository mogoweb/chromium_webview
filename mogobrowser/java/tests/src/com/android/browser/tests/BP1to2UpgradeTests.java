/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.mogoweb.browser.tests;

import com.mogoweb.browser.provider.BrowserProvider;
import com.mogoweb.browser.tests.utils.BP2TestCaseHelper;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.provider.Browser;
import android.provider.Browser.BookmarkColumns;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Bookmarks;
import android.provider.BrowserContract.History;
import android.provider.BrowserContract.Images;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

@SmallTest
public class BP1to2UpgradeTests extends BP2TestCaseHelper {

    BrowserProvider mBp1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBp1 = new BrowserProvider();
        mBp1.attachInfo(getMockContext(), null);
    }

    /**
     * Test that simply makes sure BP1->BP2 with no changes works as intended
     */
    public void testStockUpgrade() {
        Cursor c = mBp1.query(Browser.BOOKMARKS_URI,
                new String[] { BookmarkColumns.URL }, null, null,
                BookmarkColumns.URL + " DESC");
        ArrayList<String> urls = new ArrayList<String>(c.getCount());
        while (c.moveToNext()) {
            urls.add(c.getString(0));
        }
        c.close();
        // First, test the public API (which will hit BP2)
        c = getMockContentResolver().query(Browser.BOOKMARKS_URI,
                new String[] { BookmarkColumns.URL }, null, null,
                BookmarkColumns.URL + " DESC");
        assertEquals(urls.size(), c.getCount());
        int i = 0;
        while (c.moveToNext()) {
            assertEquals(urls.get(i++), c.getString(0));
        }
        c.close();
        // Next, test BP2's new API (not a public API)
        c = getMockContentResolver().query(Bookmarks.CONTENT_URI,
                new String[] { Bookmarks.URL }, null, null,
                Bookmarks.URL + " DESC");
        assertEquals(urls.size(), c.getCount());
        i = 0;
        while (c.moveToNext()) {
            assertEquals(urls.get(i++), c.getString(0));
        }
        c.close();
    }

    public void testPreserveHistory() {
        ContentValues values = new ContentValues();
        values.put(BookmarkColumns.URL, "http://slashdot.org/");
        values.put(BookmarkColumns.BOOKMARK, 0);
        values.put(BookmarkColumns.DATE, 123456);
        mBp1.insert(Browser.BOOKMARKS_URI, values);
        // First, test internal API
        Cursor c = getMockContentResolver().query(History.CONTENT_URI,
                new String[] { History.URL, History.DATE_LAST_VISITED },
                null, null, null);
        assertEquals(1, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals("http://slashdot.org/", c.getString(0));
        assertEquals(123456, c.getInt(1));
        c.close();
        // Next, test public API
        c = getMockContentResolver().query(Browser.BOOKMARKS_URI,
                Browser.HISTORY_PROJECTION, BookmarkColumns.BOOKMARK + " = 0",
                null, null);
        assertEquals("public API", 1, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals("http://slashdot.org/",
                c.getString(Browser.HISTORY_PROJECTION_URL_INDEX));
        assertEquals(123456, c.getInt(Browser.HISTORY_PROJECTION_DATE_INDEX));
        c.close();
    }

    public void testPreserveBookmarks() {
        // First, nuke 'er (deletes stock bookmarks)
        mBp1.delete(Browser.BOOKMARKS_URI, null, null);
        ContentValues values = new ContentValues();
        values.put(BookmarkColumns.URL, "http://slashdot.org/");
        values.put(BookmarkColumns.BOOKMARK, 1);
        values.put(BookmarkColumns.CREATED, 123456);
        mBp1.insert(Browser.BOOKMARKS_URI, values);
        // First, test internal API
        Cursor c = getMockContentResolver().query(Bookmarks.CONTENT_URI,
                new String[] { Bookmarks.URL, Bookmarks.DATE_CREATED },
                null, null, null);
        assertEquals(1, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals("http://slashdot.org/", c.getString(0));
        assertEquals(123456, c.getInt(1));
        c.close();
        // Next, test public API
        c = getMockContentResolver().query(Browser.BOOKMARKS_URI,
                new String[] { BookmarkColumns.URL, BookmarkColumns.CREATED },
                BookmarkColumns.BOOKMARK + " = 1", null, null);
        assertEquals("public API", 1, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals("http://slashdot.org/", c.getString(0));
        assertEquals(123456, c.getInt(1));
        c.close();
    }

    public void testEmptyUpgrade() {
        mBp1.delete(Browser.BOOKMARKS_URI, null, null);
        Cursor c = getMockContentResolver().query(Bookmarks.CONTENT_URI,
                null, null, null, null);
        assertEquals(0, c.getCount());
        c.close();
    }

}

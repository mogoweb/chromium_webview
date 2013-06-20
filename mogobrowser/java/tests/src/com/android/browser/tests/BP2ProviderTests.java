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

import com.mogoweb.browser.tests.utils.BP2TestCaseHelper;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Images;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.ByteArrayOutputStream;

@SmallTest
public class BP2ProviderTests extends BP2TestCaseHelper {

    static final String[] PROJECTION = new String[] {
            BrowserContract.Bookmarks.PARENT,
            BrowserContract.Bookmarks.ACCOUNT_NAME,
            BrowserContract.Bookmarks.ACCOUNT_TYPE,
    };
    static final int INDEX_PARENT = 0;
    static final int INDEX_ACCOUNT_NAME = 1;
    static final int INDEX_ACCOUNT_TYPE = 2;

    public void testUpdateImage() {
        String url = "http://stub1.com";
        insertBookmark(url, "stub 1");
        ContentValues values = new ContentValues();
        values.put(Images.URL, url);
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Config.ARGB_8888);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
        values.put(Images.THUMBNAIL, os.toByteArray());
        // Use updateBookmarks because the bookmarks URI observer should
        // be triggered, even though we aren't giving it a bookmarks URI
        assertTrue(updateBookmark(Images.CONTENT_URI, values));
    }

    public void testIsValidParentNullAccount() {
        doTestIsValidParent(null, null);
    }

    public void testIsValidParentWithAccount() {
        doTestIsValidParent("test@gmail.com", "com.google");
    }

    private void doTestIsValidParent(String accountName, String accountType) {
        // Create the folder
        ContentValues values = new ContentValues();
        values.put(BrowserContract.Bookmarks.TITLE, "New Folder");
        values.put(BrowserContract.Bookmarks.IS_FOLDER, 1);
        values.put(BrowserContract.Bookmarks.ACCOUNT_NAME, accountName);
        values.put(BrowserContract.Bookmarks.ACCOUNT_TYPE, accountType);
        Uri folderUri = insertBookmark(values);
        assertNotNull(folderUri);
        long folderId = ContentUris.parseId(folderUri);
        assertTrue("Failed to parse folder id!", folderId > 0);
        // Insert a bookmark with the same ACCOUNT_* info as parent
        values.put(BrowserContract.Bookmarks.TITLE, "google");
        values.put(BrowserContract.Bookmarks.URL, "http://google.com");
        values.put(BrowserContract.Bookmarks.IS_FOLDER, 0);
        values.put(BrowserContract.Bookmarks.PARENT, folderId);
        Uri insertedUri = insertBookmark(values);
        assertNotNull(insertedUri);
        Cursor c = getMockContentResolver().query(insertedUri,
                PROJECTION, null, null, null);
        try {
            assertNotNull(c);
            assertTrue(c.moveToFirst());
            long insertedParentId = c.getLong(INDEX_PARENT);
            String insertedAccountName = c.getString(INDEX_ACCOUNT_NAME);
            String insertedAccountType = c.getString(INDEX_ACCOUNT_TYPE);
            assertEquals(folderId, insertedParentId);
            assertEquals(accountName, insertedAccountName);
            assertEquals(accountType, insertedAccountType);

            // Insert a bookmark with no ACCOUNT_* set, BUT with a valid parent
            // The inserted should end up with the ACCOUNT_* of the parent
            values.remove(BrowserContract.Bookmarks.ACCOUNT_NAME);
            values.remove(BrowserContract.Bookmarks.ACCOUNT_TYPE);
            insertedUri = insertBookmark(values);
            assertNotNull(insertedUri);
            c.close();
            c = getMockContentResolver().query(insertedUri,
                    PROJECTION, null, null, null);
            assertNotNull(c);
            assertTrue(c.moveToFirst());
            insertedParentId = c.getLong(INDEX_PARENT);
            insertedAccountName = c.getString(INDEX_ACCOUNT_NAME);
            insertedAccountType = c.getString(INDEX_ACCOUNT_TYPE);
            assertEquals(folderId, insertedParentId);
            assertEquals(accountName, insertedAccountName);
            assertEquals(accountType, insertedAccountType);

            // Insert a bookmark with a different ACCOUNT_* than it's parent
            // ACCOUNT_* should override parent
            accountName = accountName + "@something.else";
            accountType = "com.google";
            values.put(BrowserContract.Bookmarks.ACCOUNT_NAME, accountName);
            values.put(BrowserContract.Bookmarks.ACCOUNT_TYPE, accountType);
            insertedUri = insertBookmark(values);
            assertNotNull(insertedUri);
            c.close();
            c = getMockContentResolver().query(insertedUri,
                    PROJECTION, null, null, null);
            assertNotNull(c);
            assertTrue(c.moveToFirst());
            insertedParentId = c.getLong(INDEX_PARENT);
            insertedAccountName = c.getString(INDEX_ACCOUNT_NAME);
            insertedAccountType = c.getString(INDEX_ACCOUNT_TYPE);
            assertNotSame(folderId, insertedParentId);
            assertEquals(accountName, insertedAccountName);
            assertEquals(accountType, insertedAccountType);
        } finally {
            c.close();
        }
    }
}

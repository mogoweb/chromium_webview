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

package com.mogoweb.browser.tests.utils;

import com.mogoweb.browser.provider.BrowserProvider2;

import java.io.File;
import java.io.FilenameFilter;

import android.content.ContentValues;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Browser;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Bookmarks;
import android.provider.BrowserContract.History;
import android.util.Log;

/**
 *  This is a replacement for ProviderTestCase2 that can handle notifyChange testing.
 *  It also has helper methods specifically for testing BrowserProvider2
 */
public abstract class BP2TestCaseHelper extends ProviderTestCase3<BrowserProvider2> {

    // Tag for potential performance impacts
    private static final String PERFTAG = "BP2-PerfCheck";

    private TriggeredObserver mLegacyObserver;
    private TriggeredObserver mRootObserver;
    private TriggeredObserver mBookmarksObserver;
    private TriggeredObserver mHistoryObserver;
    private TriggeredObserver mWidgetObserver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mLegacyObserver = new TriggeredObserver(Browser.BOOKMARKS_URI);
        mRootObserver = new TriggeredObserver(BrowserContract.AUTHORITY_URI);
        mBookmarksObserver = new TriggeredObserver(Bookmarks.CONTENT_URI);
        mHistoryObserver = new TriggeredObserver(History.CONTENT_URI);
        mWidgetObserver = new TriggeredObserver();
        // We don't need to worry about setting this back to null since this
        // is a private instance local to the MockContentResolver
        getProvider().setWidgetObserver(mWidgetObserver);
    }

    public BP2TestCaseHelper() {
        super(BrowserProvider2.class,
                BrowserContract.AUTHORITY, BrowserProvider2.LEGACY_AUTHORITY);
    }

    public void perfIdeallyUntriggered(TriggeredObserver... obs) {
        for (TriggeredObserver ob : obs) {
            if (ob.checkTriggered()) {
                // Not ideal, unnecessary notification
                Log.i(PERFTAG, ob.mUri + " onChange called but content unaltered!");
            }
        }
    }

    public void assertObserversTriggered(boolean triggered,
            TriggeredObserver... observers) {
        for (TriggeredObserver obs : observers) {
            assertEquals(obs.mUri + ", descendents:" + obs.mNotifyForDescendents,
                    triggered, obs.checkTriggered());
        }
    }

    public class TriggeredObserver extends ContentObserver {
        private boolean mTriggered;
        Uri mUri;
        boolean mNotifyForDescendents;

        /**
         * Creates an unmanaged TriggeredObserver
         */
        public TriggeredObserver() {
            super(null);
        }

        /**
         * Same as TriggeredObserver(uri, true);
         */
        public TriggeredObserver(Uri uri) {
            this(uri, true);
        }

        /**
         * Creates a managed TriggeredObserver that self-registers with the
         * mock ContentResolver
         */
        public TriggeredObserver(Uri uri, boolean notifyForDescendents) {
            super(null);
            mUri = uri;
            mNotifyForDescendents = notifyForDescendents;
            registerContentObserver(uri, notifyForDescendents, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mTriggered = true;
        }

        public boolean checkTriggered() {
            boolean ret = mTriggered;
            mTriggered = false;
            return ret;
        }
    }

    Uri mockInsert(Uri uri, ContentValues values) {
        assertObserversTriggered(false, mLegacyObserver, mRootObserver);
        Uri ret = getMockContentResolver().insert(uri, values);
        assertObserversTriggered(true, mLegacyObserver, mRootObserver);
        return ret;
    }

    int mockUpdate(Uri uri, ContentValues values, String where,
            String[] selectionArgs) {
        assertObserversTriggered(false, mLegacyObserver, mRootObserver);
        int ret = getMockContentResolver().update(uri, values, where, selectionArgs);
        if (ret > 0) {
            assertObserversTriggered(true, mLegacyObserver, mRootObserver);
        } else {
            perfIdeallyUntriggered(mLegacyObserver);
            perfIdeallyUntriggered(mRootObserver);
        }
        return ret;
    }

    public Uri insertBookmark(String url, String title) {
        ContentValues values = new ContentValues();
        values.put(BrowserContract.Bookmarks.TITLE, title);
        values.put(BrowserContract.Bookmarks.URL, url);
        values.put(BrowserContract.Bookmarks.IS_FOLDER, 0);
        return insertBookmark(values);
    }

    public Uri insertBookmark(ContentValues values) {
        assertObserversTriggered(false, mBookmarksObserver, mWidgetObserver);
        Uri ret = mockInsert(Bookmarks.CONTENT_URI, values);
        assertObserversTriggered(true, mBookmarksObserver, mWidgetObserver);
        perfIdeallyUntriggered(mHistoryObserver);
        return ret;
    }

    public boolean updateBookmark(Uri uri, String url, String title) {
        ContentValues values = new ContentValues();
        values.put(BrowserContract.Bookmarks.TITLE, title);
        values.put(BrowserContract.Bookmarks.URL, url);
        return updateBookmark(uri, values);
    }

    public boolean updateBookmark(Uri uri, ContentValues values) {
        assertObserversTriggered(false, mBookmarksObserver, mWidgetObserver);
        int modifyCount = mockUpdate(uri, values, null, null);
        assertTrue("UpdatedBookmark modified too much! " + uri, modifyCount <= 1);
        boolean updated = modifyCount == 1;
        if (updated) {
            assertObserversTriggered(updated, mBookmarksObserver, mWidgetObserver);
        } else {
            perfIdeallyUntriggered(mBookmarksObserver, mWidgetObserver);
        }
        perfIdeallyUntriggered(mHistoryObserver);
        return updated;
    }

    public Uri insertHistory(String url, String title) {
        ContentValues values = new ContentValues();
        values.put(BrowserContract.History.TITLE, title);
        values.put(BrowserContract.History.URL, url);
        assertObserversTriggered(false, mHistoryObserver);
        Uri ret = mockInsert(History.CONTENT_URI, values);
        assertObserversTriggered(true, mHistoryObserver);
        perfIdeallyUntriggered(mBookmarksObserver, mWidgetObserver);
        return ret;
    }

    public boolean updateHistory(Uri uri, String url, String title) {
        ContentValues values = new ContentValues();
        values.put(BrowserContract.History.TITLE, title);
        values.put(BrowserContract.History.URL, url);
        return updateHistory(uri, values);
    }

    public boolean updateHistory(Uri uri, ContentValues values) {
        assertObserversTriggered(false, mHistoryObserver);
        int modifyCount = mockUpdate(uri, values, null, null);
        assertTrue("UpdatedHistory modified too much! " + uri, modifyCount <= 1);
        boolean updated = modifyCount == 1;
        if (updated) {
            assertObserversTriggered(updated, mHistoryObserver);
        } else {
            perfIdeallyUntriggered(mHistoryObserver);
        }
        perfIdeallyUntriggered(mBookmarksObserver, mWidgetObserver);
        return updated;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // Delete the test databases so that subsequent runs have a clean slate
        File f = getMockContext().getDatabasePath("test");
        File dir = f.getParentFile();
        File testFiles[] = dir.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                return filename.startsWith(ProviderTestCase3.FILENAME_PREFIX);
            }
        });
        for (File testFile : testFiles) {
            testFile.delete();
        }
    }
}

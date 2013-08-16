/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
//import android.provider.BrowserContract;
//import android.provider.BrowserContract.Bookmarks;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.CRC32;

/**
 * Settings backup agent for the Android browser.  Currently the only thing
 * stored is the set of bookmarks.  It's okay if I/O exceptions are thrown
 * out of the agent; the calling code handles it and the backup operation
 * simply fails.
 *
 * @hide
 */
public class BrowserBackupAgent extends BackupAgent {
    static final String TAG = "BrowserBackupAgent";
    static final boolean DEBUG = false;

    static final String BOOKMARK_KEY = "_bookmarks_";
    /** this version num MUST be incremented if the flattened-file schema ever changes */
    static final int BACKUP_AGENT_VERSION = 0;

    /**
     * This simply preserves the existing state as we now prefer Chrome Sync
     * to handle bookmark backup.
     */
    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        long savedFileSize = -1;
        long savedCrc = -1;
        int savedVersion = -1;

        // Extract the previous bookmark file size & CRC from the saved state
        DataInputStream in = new DataInputStream(
                new FileInputStream(oldState.getFileDescriptor()));
        try {
            savedFileSize = in.readLong();
            savedCrc = in.readLong();
            savedVersion = in.readInt();
        } catch (EOFException e) {
            // It means we had no previous state; that's fine
            return;
        } finally {
            if (in != null) {
                in.close();
            }
        }
        // Write the existing state
        writeBackupState(savedFileSize, savedCrc, newState);
    }

    /**
     * Restore from backup -- reads in the flattened bookmark file as supplied from
     * the backup service, parses that out, and rebuilds the bookmarks table in the
     * browser database from it.
     */
    @Override
    public void onRestore(BackupDataInput data, int appVersionCode,
            ParcelFileDescriptor newState) throws IOException {
//        long crc = -1;
//        File tmpfile = File.createTempFile("rst", null, getFilesDir());
//        try {
//            while (data.readNextHeader()) {
//                if (BOOKMARK_KEY.equals(data.getKey())) {
//                    // Read the flattened bookmark data into a temp file
//                    crc = copyBackupToFile(data, tmpfile, data.getDataSize());
//
//                    FileInputStream infstream = new FileInputStream(tmpfile);
//                    DataInputStream in = new DataInputStream(infstream);
//
//                    try {
//                        int count = in.readInt();
//                        ArrayList<Bookmark> bookmarks = new ArrayList<Bookmark>(count);
//
//                        // Read all the bookmarks, then process later -- if we can't read
//                        // all the data successfully, we don't touch the bookmarks table
//                        for (int i = 0; i < count; i++) {
//                            Bookmark mark = new Bookmark();
//                            mark.url = in.readUTF();
//                            mark.visits = in.readInt();
//                            mark.date = in.readLong();
//                            mark.created = in.readLong();
//                            mark.title = in.readUTF();
//                            bookmarks.add(mark);
//                        }
//
//                        // Okay, we have all the bookmarks -- now see if we need to add
//                        // them to the browser's database
//                        int N = bookmarks.size();
//                        int nUnique = 0;
//                        if (DEBUG) Log.v(TAG, "Restoring " + N + " bookmarks");
//                        String[] urlCol = new String[] { Bookmarks.URL };
//                        for (int i = 0; i < N; i++) {
//                            Bookmark mark = bookmarks.get(i);
//
//                            // Does this URL exist in the bookmark table?
//                            Cursor cursor = getContentResolver().query(
//                                    Bookmarks.CONTENT_URI, urlCol,
//                                    Bookmarks.URL + " == ?",
//                                    new String[] { mark.url }, null);
//                            // if not, insert it
//                            if (cursor.getCount() <= 0) {
//                                if (DEBUG) Log.v(TAG, "Did not see url: " + mark.url);
//                                addBookmark(mark);
//                                nUnique++;
//                            } else {
//                                if (DEBUG) Log.v(TAG, "Skipping extant url: " + mark.url);
//                            }
//                            cursor.close();
//                        }
//                        Log.i(TAG, "Restored " + nUnique + " of " + N + " bookmarks");
//                    } catch (IOException ioe) {
//                        Log.w(TAG, "Bad backup data; not restoring");
//                        crc = -1;
//                    } finally {
//                        if (in != null) {
//                            in.close();
//                        }
//                    }
//                }
//
//                // Last, write the state we just restored from so we can discern
//                // changes whenever we get invoked for backup in the future
//                writeBackupState(tmpfile.length(), crc, newState);
//            }
//        } finally {
//            // Whatever happens, delete the temp file
//            tmpfile.delete();
//        }
    }

    void addBookmark(Bookmark mark) {
//        ContentValues values = new ContentValues();
//        values.put(Bookmarks.TITLE, mark.title);
//        values.put(Bookmarks.URL, mark.url);
//        values.put(Bookmarks.IS_FOLDER, 0);
//        values.put(Bookmarks.DATE_CREATED, mark.created);
//        values.put(Bookmarks.DATE_MODIFIED, mark.date);
//        getContentResolver().insert(Bookmarks.CONTENT_URI, values);
    }

    static class Bookmark {
        public String url;
        public int visits;
        public long date;
        public long created;
        public String title;
    }
    /*
     * Utility functions
     */

    // Read the given file from backup to a file, calculating a CRC32 along the way
    private long copyBackupToFile(BackupDataInput data, File file, int toRead)
            throws IOException {
        final int CHUNK = 8192;
        byte[] buf = new byte[CHUNK];
        CRC32 crc = new CRC32();
        FileOutputStream out = new FileOutputStream(file);

        try {
            while (toRead > 0) {
                int numRead = data.readEntityData(buf, 0, CHUNK);
                crc.update(buf, 0, numRead);
                out.write(buf, 0, numRead);
                toRead -= numRead;
            }
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return crc.getValue();
    }

    // Write the given metrics to the new state file
    private void writeBackupState(long fileSize, long crc, ParcelFileDescriptor stateFile)
            throws IOException {
        DataOutputStream out = new DataOutputStream(
                new FileOutputStream(stateFile.getFileDescriptor()));
        try {
            out.writeLong(fileSize);
            out.writeLong(crc);
            out.writeInt(BACKUP_AGENT_VERSION);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}

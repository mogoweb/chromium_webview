// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/**
 * This class provides methods to access content URI schemes.
 */
abstract class ContentUriUtils {
    private static final String TAG = "ContentUriUtils";

    // Prevent instantiation.
    private ContentUriUtils() {}

    /**
     * Opens the content URI for reading, and returns the file descriptor to
     * the caller. The caller is responsible for closing the file desciptor.
     *
     * @param context {@link Context} in interest
     * @param uriString the content URI to open
     * @returns file desciptor upon sucess, or -1 otherwise.
     */
    @CalledByNative
    public static int openContentUriForRead(Context context, String uriString) {
        ParcelFileDescriptor pfd = getParcelFileDescriptor(context, uriString);
        if (pfd != null) {
            return pfd.detachFd();
        }
        return -1;
    }

    /**
     * Check whether a content URI exists.
     *
     * @param context {@link Context} in interest.
     * @param uriString the content URI to query.
     * @returns true if the uri exists, or false otherwise.
     */
    @CalledByNative
    public static boolean contentUriExists(Context context, String uriString) {
        ParcelFileDescriptor pfd = getParcelFileDescriptor(context, uriString);
        if (pfd == null) {
            return false;
        }
        return true;
    }

    /**
     * Helper method to open a content URI and return the ParcelFileDescriptor.
     *
     * @param context {@link Context} in interest.
     * @param uriString the content URI to open.
     * @returns ParcelFileDescriptor of the content URI, or NULL if the file does not exist.
     */
    private static ParcelFileDescriptor getParcelFileDescriptor(Context context, String uriString) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = Uri.parse(uriString);

        ParcelFileDescriptor pfd = null;
        try {
            pfd = resolver.openFileDescriptor(uri, "r");
        } catch (java.io.FileNotFoundException e) {
            Log.w(TAG, "Cannot find content uri: " + uriString, e);
        }
        return pfd;
    }
}

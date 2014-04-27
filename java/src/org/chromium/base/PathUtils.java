// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Environment;

/**
 * This class provides the path related methods for the native library.
 */
public abstract class PathUtils {

    private static String sDataDirectorySuffix;

    // Prevent instantiation.
    private PathUtils() {}

    /**
     * Sets the suffix that should be used for the directory where private data is to be stored
     * by the application.
     * @param suffix The private data directory suffix.
     * @see Context#getDir(String, int)
     */
    public static void setPrivateDataDirectorySuffix(String suffix) {
        sDataDirectorySuffix = suffix;
    }

    /**
     * @return the private directory that is used to store application data.
     */
    @CalledByNative
    public static String getDataDirectory(Context appContext) {
        if (sDataDirectorySuffix == null) {
            throw new IllegalStateException(
                    "setDataDirectorySuffix must be called before getDataDirectory");
        }
        return appContext.getDir(sDataDirectorySuffix, Context.MODE_PRIVATE).getPath();
    }

    /**
     * @return the private directory that is used to store application database.
     */
    @CalledByNative
    public static String getDatabaseDirectory(Context appContext) {
        // Context.getDatabasePath() returns path for the provided filename.
        return appContext.getDatabasePath("foo").getParent();
    }

    /**
     * @return the cache directory.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    public static String getCacheDirectory(Context appContext) {
        return appContext.getCacheDir().getPath();
    }

    /**
     * @return the public downloads directory.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private static String getDownloadsDirectory(Context appContext) {
        return Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getPath();
    }

    /**
     * @return the path to native libraries.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private static String getNativeLibraryDirectory(Context appContext) {
        ApplicationInfo ai = appContext.getApplicationInfo();
        if ((ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
            (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            return ai.nativeLibraryDir;
        }

        return "/system/lib/";
    }

    /**
     * @return the external storage directory.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    public static String getExternalStorageDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }
}

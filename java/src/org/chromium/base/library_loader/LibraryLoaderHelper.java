// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


package org.chromium.base.library_loader;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The class provides helper functions to extract native libraries from APK,
 * and load libraries from there.
 *
 * The class should be package-visible only, but made public for testing
 * purpose.
 */
public class LibraryLoaderHelper {
    private static final String TAG = "LibraryLoaderHelper";

    private static final String LIB_DIR = "lib";

    /**
     * One-way switch becomes true if native libraries were unpacked
     * from APK.
     */
    private static boolean sLibrariesWereUnpacked = false;

    /**
     * Loads native libraries using workaround only, skip the library in system
     * lib path. The method exists only for testing purpose.
     * Caller must ensure thread safety of this method.
     * @param context
     */
    public static boolean loadNativeLibrariesUsingWorkaroundForTesting(Context context) {
        // Although tryLoadLibraryUsingWorkaround might be called multiple times,
        // libraries should only be unpacked once, this is guaranteed by
        // sLibrariesWereUnpacked.
        for (String library : NativeLibraries.LIBRARIES) {
            if (!tryLoadLibraryUsingWorkaround(context, library)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Try to load a native library using a workaround of
     *   http://b/13216167.
     *
     * Workaround for b/13216167 was adapted from code in
     * https://googleplex-android-review.git.corp.google.com/#/c/433061
     *
     * More details about http://b/13216167:
     *   PackageManager may fail to update shared library.
     *
     * Native library directory in an updated package is a symbolic link
     * to a directory in /data/app-lib/<package name>, for example:
     * /data/data/com.android.chrome/lib -> /data/app-lib/com.android.chrome[-1].
     * When updating the application, the PackageManager create a new directory,
     * e.g., /data/app-lib/com.android.chrome-2, and remove the old symlink and
     * recreate one to the new directory. However, on some devices (e.g. Sony Xperia),
     * the symlink was updated, but fails to extract new native libraries from
     * the new apk.

     * We make the following changes to alleviate the issue:
     *  1) name the native library with apk version code, e.g.,
     *     libchrome.1750.136.so, 1750.136 is Chrome version number;
     *  2) first try to load the library using System.loadLibrary,
     *     if that failed due to the library file was not found,
     *     search the named library in a /data/data/com.android.chrome/app_lib
     *     directory. Because of change 1), each version has a different native
     *     library name, so avoid mistakenly using the old native library.
     *
     *  If named library is not in /data/data/com.android.chrome/app_lib directory,
     *  extract native libraries from apk and cache in the directory.
     *
     * This function doesn't throw UnsatisfiedLinkError, the caller needs to
     * check the return value.
     */
    static boolean tryLoadLibraryUsingWorkaround(Context context, String library) {
        assert context != null;
        File libFile = getWorkaroundLibFile(context, library);
        if (!libFile.exists() && !unpackLibrariesOnce(context)) {
            return false;
        }
        try {
            System.load(libFile.getAbsolutePath());
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * Returns the directory for holding extracted native libraries.
     * It may create the directory if it doesn't exist.
     *
     * @param context
     * @return the directory file object
     */
    public static File getWorkaroundLibDir(Context context) {
        return context.getDir(LIB_DIR, Context.MODE_PRIVATE);
    }

    private static File getWorkaroundLibFile(Context context, String library) {
        String libName = System.mapLibraryName(library);
        return new File(getWorkaroundLibDir(context), libName);
    }

    /**
     * Unpack native libraries from the APK file. The method is supposed to
     * be called only once. It deletes existing files in unpacked directory
     * before unpacking.
     *
     * @param context
     * @return true when unpacking was successful, false when failed or called
     *         more than once.
     */
    private static boolean unpackLibrariesOnce(Context context) {
        if (sLibrariesWereUnpacked) {
            return false;
        }
        sLibrariesWereUnpacked = true;

        File libDir = getWorkaroundLibDir(context);
        deleteDirectorySync(libDir);

        try {
            ApplicationInfo appInfo = context.getApplicationInfo();
            ZipFile file = new ZipFile(new File(appInfo.sourceDir), ZipFile.OPEN_READ);
            for (String libName : NativeLibraries.LIBRARIES) {
                String jniNameInApk = "lib/" + Build.CPU_ABI + "/" +
                    System.mapLibraryName(libName);

                final ZipEntry entry = file.getEntry(jniNameInApk);
                if (entry == null) {
                    Log.e(TAG, appInfo.sourceDir + " doesn't have file " + jniNameInApk);
                    file.close();
                    deleteDirectorySync(libDir);
                    return false;
                }

                File outputFile = getWorkaroundLibFile(context, libName);

                Log.i(TAG, "Extracting native libraries into " + outputFile.getAbsolutePath());

                assert !outputFile.exists();

                try {
                    if (!outputFile.createNewFile()) {
                        throw new IOException();
                    }

                    InputStream is = null;
                    FileOutputStream os = null;
                    try {
                        is = file.getInputStream(entry);
                        os = new FileOutputStream(outputFile);
                        int count = 0;
                        byte[] buffer = new byte[16 * 1024];
                        while ((count = is.read(buffer)) > 0) {
                            os.write(buffer, 0, count);
                        }
                    } finally {
                        try {
                            if (is != null) is.close();
                        } finally {
                            if (os != null) os.close();
                        }
                    }
                    // Change permission to rwxr-xr-x
                    outputFile.setReadable(true, false);
                    outputFile.setExecutable(true, false);
                    outputFile.setWritable(true);
                } catch (IOException e) {
                    if (outputFile.exists()) {
                        if (!outputFile.delete()) {
                            Log.e(TAG, "Failed to delete " + outputFile.getAbsolutePath());
                        }
                    }
                    file.close();
                    throw e;
                }
            }
            file.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to unpack native libraries", e);
            deleteDirectorySync(libDir);
            return false;
        }
    }

    /**
     * Delete old library files in the backup directory.
     *  The actual deletion is done in a background thread.
     *
     * @param context
     */
    static void deleteWorkaroundLibrariesAsynchronously(final Context context) {
        // Child process should not reach here.
        new Thread() {
            @Override
            public void run() {
                deleteWorkaroundLibrariesSynchronously(context);
            }
        }.start();
    }

    /**
     * Delete the workaround libraries and directory synchronously.
     * For testing purpose only.
     * @param context
     */
    public static void deleteWorkaroundLibrariesSynchronously(Context context) {
        File libDir = getWorkaroundLibDir(context);
        deleteDirectorySync(libDir);
    }

    private static void deleteDirectorySync(File dir) {
        try {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    if (!file.delete()) {
                      Log.e(TAG, "Failed to remove " + file.getAbsolutePath());
                    }
                }
            }
            if (!dir.delete()) {
                Log.w(TAG, "Failed to remove " + dir.getAbsolutePath());
            }
            return;
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove old libs, ", e);
        }
    }
}

// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base.library_loader;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.chromium.base.CommandLine;
import org.chromium.base.JNINamespace;
import org.chromium.base.SysUtils;
import org.chromium.base.TraceEvent;

/**
 * This class provides functionality to load and register the native libraries.
 * Callers are allowed to separate loading the libraries from initializing them.
 * This may be an advantage for Android Webview, where the libraries can be loaded
 * by the zygote process, but then needs per process initialization after the
 * application processes are forked from the zygote process.
 *
 * The libraries may be loaded and initialized from any thread. Synchronization
 * primitives are used to ensure that overlapping requests from different
 * threads are handled sequentially.
 *
 * See also base/android/library_loader/library_loader_hooks.cc, which contains
 * the native counterpart to this class.
 */
@JNINamespace("base::android")
public class LibraryLoader {
    private static final String TAG = "LibraryLoader";

    // Guards all access to the libraries
    private static final Object sLock = new Object();

    // One-way switch becomes true when the libraries are loaded.
    private static boolean sLoaded = false;

    // One-way switch becomes true when the libraries are initialized (
    // by calling nativeLibraryLoaded, which forwards to LibraryLoaded(...) in
    // library_loader_hooks.cc).
    private static boolean sInitialized = false;

    // One-way switch becomes true if the system library loading failed,
    // and the right native library was found and loaded by the hack.
    // The flag is used to report UMA stats later.
    private static boolean sNativeLibraryHackWasUsed = false;

    /**
     * The same as ensureInitialized(null, false), should only be called
     * by non-browser processes.
     *
     * @throws ProcessInitException
     */
    public static void ensureInitialized() throws ProcessInitException {
        ensureInitialized(null, false);
    }

    /**
     *  This method blocks until the library is fully loaded and initialized.
     *
     *  @param context The context in which the method is called, the caller
     *    may pass in a null context if it doesn't know in which context it
     *    is running, or it doesn't need to work around the issue
     *    http://b/13216167.
     *
     *    When the context is not null and native library was not extracted
     *    by Android package manager, the LibraryLoader class
     *    will extract the native libraries from APK. This is a hack used to
     *    work around some Sony devices with the following platform bug:
     *    http://b/13216167.
     *
     *  @param shouldDeleteOldWorkaroundLibraries The flag tells whether the method
     *    should delete the old workaround libraries or not.
     */
    public static void ensureInitialized(
            Context context, boolean shouldDeleteOldWorkaroundLibraries)
            throws ProcessInitException {
        synchronized (sLock) {
            if (sInitialized) {
                // Already initialized, nothing to do.
                return;
            }
            loadAlreadyLocked(context, shouldDeleteOldWorkaroundLibraries);
            initializeAlreadyLocked(CommandLine.getJavaSwitchesOrNull());
        }
    }

    /**
     * Checks if library is fully loaded and initialized.
     */
    public static boolean isInitialized() {
        synchronized (sLock) {
            return sInitialized;
        }
    }

    /**
     * The same as loadNow(null, false), should only be called by
     * non-browser process.
     *
     * @throws ProcessInitException
     */
    public static void loadNow() throws ProcessInitException {
        loadNow(null, false);
    }

    /**
     * Loads the library and blocks until the load completes. The caller is responsible
     * for subsequently calling ensureInitialized().
     * May be called on any thread, but should only be called once. Note the thread
     * this is called on will be the thread that runs the native code's static initializers.
     * See the comment in doInBackground() for more considerations on this.
     *
     * @param context The context the code is running, or null if it doesn't have one.
     * @param shouldDeleteOldWorkaroundLibraries The flag tells whether the method
     *   should delete the old workaround libraries or not.
     *
     * @throws ProcessInitException if the native library failed to load.
     */
    public static void loadNow(Context context, boolean shouldDeleteOldWorkaroundLibraries)
            throws ProcessInitException {
        synchronized (sLock) {
            loadAlreadyLocked(context, shouldDeleteOldWorkaroundLibraries);
        }
    }

    /**
     * initializes the library here and now: must be called on the thread that the
     * native will call its "main" thread. The library must have previously been
     * loaded with loadNow.
     * @param initCommandLine The command line arguments that native command line will
     * be initialized with.
     */
    public static void initialize(String[] initCommandLine) throws ProcessInitException {
        synchronized (sLock) {
            initializeAlreadyLocked(initCommandLine);
        }
    }

    // Invoke System.loadLibrary(...), triggering JNI_OnLoad in native code
    private static void loadAlreadyLocked(
            Context context, boolean shouldDeleteOldWorkaroundLibraries)
            throws ProcessInitException {
        try {
            if (!sLoaded) {
                assert !sInitialized;

                long startTime = SystemClock.uptimeMillis();
                boolean useChromiumLinker = Linker.isUsed();

                if (useChromiumLinker) Linker.prepareLibraryLoad();

                for (String library : NativeLibraries.LIBRARIES) {
                    Log.i(TAG, "Loading: " + library);
                    if (useChromiumLinker) {
                        Linker.loadLibrary(library);
                    } else {
                        try {
                            System.loadLibrary(library);
                        } catch (UnsatisfiedLinkError e) {
                            if (context != null
                                && LibraryLoaderHelper.tryLoadLibraryUsingWorkaround(context,
                                                                                     library)) {
                                sNativeLibraryHackWasUsed = true;
                            } else {
                                throw e;
                            }
                        }
                    }
                }
                if (useChromiumLinker) Linker.finishLibraryLoad();

                if (context != null
                    && shouldDeleteOldWorkaroundLibraries
                    && !sNativeLibraryHackWasUsed) {
                    LibraryLoaderHelper.deleteWorkaroundLibrariesAsynchronously(
                        context);
                }

                long stopTime = SystemClock.uptimeMillis();
                Log.i(TAG, String.format("Time to load native libraries: %d ms (timestamps %d-%d)",
                        stopTime - startTime,
                        startTime % 10000,
                        stopTime % 10000));
                sLoaded = true;
            }
        } catch (UnsatisfiedLinkError e) {
            throw new ProcessInitException(LoaderErrors.LOADER_ERROR_NATIVE_LIBRARY_LOAD_FAILED, e);
        }
        // Check that the version of the library we have loaded matches the version we expect
        Log.i(TAG, String.format(
                "Expected native library version number \"%s\"," +
                        "actual native library version number \"%s\"",
                NativeLibraries.VERSION_NUMBER,
                nativeGetVersionNumber()));
        if (!NativeLibraries.VERSION_NUMBER.equals(nativeGetVersionNumber())) {
            throw new ProcessInitException(LoaderErrors.LOADER_ERROR_NATIVE_LIBRARY_WRONG_VERSION);
        }
    }

    // Invoke base::android::LibraryLoaded in library_loader_hooks.cc
    private static void initializeAlreadyLocked(String[] initCommandLine)
            throws ProcessInitException {
        if (sInitialized) {
            return;
        }
        if (!nativeLibraryLoaded(initCommandLine)) {
            Log.e(TAG, "error calling nativeLibraryLoaded");
            throw new ProcessInitException(LoaderErrors.LOADER_ERROR_FAILED_TO_REGISTER_JNI);
        }
        // From this point on, native code is ready to use and checkIsReady()
        // shouldn't complain from now on (and in fact, it's used by the
        // following calls).
        sInitialized = true;
        CommandLine.enableNativeProxy();

        // From now on, keep tracing in sync with native.
        TraceEvent.registerNativeEnabledObserver();

        // Record histogram for the Chromium linker.
        if (Linker.isUsed()) {
            nativeRecordChromiumAndroidLinkerHistogram(Linker.loadAtFixedAddressFailed(),
                    SysUtils.isLowEndDevice());
        }

        nativeRecordNativeLibraryHack(sNativeLibraryHackWasUsed);
    }

    // Only methods needed before or during normal JNI registration are during System.OnLoad.
    // nativeLibraryLoaded is then called to register everything else.  This process is called
    // "initialization".  This method will be mapped (by generated code) to the LibraryLoaded
    // definition in base/android/library_loader/library_loader_hooks.cc.
    //
    // Return true on success and false on failure.
    private static native boolean nativeLibraryLoaded(String[] initCommandLine);

    // Method called to record statistics about the Chromium linker operation,
    // i.e. whether the library failed to be loaded at a fixed address, and
    // whether the device is 'low-memory'.
    private static native void nativeRecordChromiumAndroidLinkerHistogram(
            boolean loadedAtFixedAddressFailed,
            boolean isLowMemoryDevice);

    // Get the version of the native library. This is needed so that we can check we
    // have the right version before initializing the (rest of the) JNI.
    private static native String nativeGetVersionNumber();

    private static native void nativeRecordNativeLibraryHack(boolean usedHack);
}

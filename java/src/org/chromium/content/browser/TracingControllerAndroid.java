// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.TraceEvent;
import org.chromium.content.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Controller for Chrome's tracing feature.
 *
 * We don't have any UI per se. Just call startTracing() to start and
 * stopTracing() to stop. We'll report progress to the user with Toasts.
 *
 * If the host application registers this class's BroadcastReceiver, you can
 * also start and stop the tracer with a broadcast intent, as follows:
 * <ul>
 * <li>To start tracing: am broadcast -a org.chromium.content_shell_apk.GPU_PROFILER_START
 * <li>Add "-e file /foo/bar/xyzzy" to log trace data to a specific file.
 * <li>To stop tracing: am broadcast -a org.chromium.content_shell_apk.GPU_PROFILER_STOP
 * </ul>
 * Note that the name of these intents change depending on which application
 * is being traced, but the general form is [app package name].GPU_PROFILER_{START,STOP}.
 */
@JNINamespace("content")
public class TracingControllerAndroid {

    private static final String TAG = "TracingControllerAndroid";

    private static final String ACTION_START = "GPU_PROFILER_START";
    private static final String ACTION_STOP = "GPU_PROFILER_STOP";
    private static final String FILE_EXTRA = "file";
    private static final String CATEGORIES_EXTRA = "categories";
    private static final String RECORD_CONTINUOUSLY_EXTRA = "continuous";
    private static final String DEFAULT_CHROME_CATEGORIES_PLACE_HOLDER =
            "_DEFAULT_CHROME_CATEGORIES";

    private final Context mContext;
    private final TracingBroadcastReceiver mBroadcastReceiver;
    private final TracingIntentFilter mIntentFilter;
    private boolean mIsTracing;

    // We might not want to always show toasts when we start the profiler, especially if
    // showing the toast impacts performance.  This gives us the chance to disable them.
    private boolean mShowToasts = true;

    private String mFilename;

    public TracingControllerAndroid(Context context) {
        mContext = context;
        mBroadcastReceiver = new TracingBroadcastReceiver();
        mIntentFilter = new TracingIntentFilter(context);
    }

    /**
     * Get a BroadcastReceiver that can handle profiler intents.
     */
    public BroadcastReceiver getBroadcastReceiver() {
        return mBroadcastReceiver;
    }

    /**
     * Get an IntentFilter for profiler intents.
     */
    public IntentFilter getIntentFilter() {
        return mIntentFilter;
    }

    /**
     * Register a BroadcastReceiver in the given context.
     */
    public void registerReceiver(Context context) {
        context.registerReceiver(getBroadcastReceiver(), getIntentFilter());
    }

    /**
     * Unregister the GPU BroadcastReceiver in the given context.
     * @param context
     */
    public void unregisterReceiver(Context context) {
        context.unregisterReceiver(getBroadcastReceiver());
    }

    /**
     * Returns true if we're currently profiling.
     */
    public boolean isTracing() {
        return mIsTracing;
    }

    /**
     * Returns the path of the current output file. Null if isTracing() false.
     */
    public String getOutputPath() {
        return mFilename;
    }

    /**
     * Start profiling to a new file in the Downloads directory.
     *
     * Calls #startTracing(String, boolean, String, boolean) with a new timestamped filename.
     * @see #startTracing(String, boolean, String, boolean)
     */
    public boolean startTracing(boolean showToasts, String categories,
            boolean recordContinuously) {
        mShowToasts = showToasts;
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            logAndToastError(
                    mContext.getString(R.string.profiler_no_storage_toast));
            return false;
        }

        // Generate a hopefully-unique filename using the UTC timestamp.
        // (Not a huge problem if it isn't unique, we'll just append more data.)
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy-MM-dd-HHmmss", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        File dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        File file = new File(
                dir, "chrome-profile-results-" + formatter.format(new Date()));

        return startTracing(file.getPath(), showToasts, categories, recordContinuously);
    }

    /**
     * Start profiling to the specified file. Returns true on success.
     *
     * Only one TracingControllerAndroid can be running at the same time. If another profiler
     * is running when this method is called, it will be cancelled. If this
     * profiler is already running, this method does nothing and returns false.
     *
     * @param filename The name of the file to output the profile data to.
     * @param showToasts Whether or not we want to show toasts during this profiling session.
     * When we are timing the profile run we might not want to incur extra draw overhead of showing
     * notifications about the profiling system.
     * @param categories Which categories to trace. See TracingControllerAndroid::BeginTracing()
     * (in content/public/browser/trace_controller.h) for the format.
     * @param recordContinuously Record until the user ends the trace. The trace buffer is fixed
     * size and we use it as a ring buffer during recording.
     */
    public boolean startTracing(String filename, boolean showToasts, String categories,
            boolean recordContinuously) {
        mShowToasts = showToasts;
        if (isTracing()) {
            // Don't need a toast because this shouldn't happen via the UI.
            Log.e(TAG, "Received startTracing, but we're already tracing");
            return false;
        }
        // Lazy initialize the native side, to allow construction before the library is loaded.
        if (mNativeTracingControllerAndroid == 0) {
            mNativeTracingControllerAndroid = nativeInit();
        }
        if (!nativeStartTracing(mNativeTracingControllerAndroid, filename, categories,
                recordContinuously)) {
            logAndToastError(mContext.getString(R.string.profiler_error_toast));
            return false;
        }

        logAndToastInfo(mContext.getString(R.string.profiler_started_toast) + ": " + categories);
        TraceEvent.setEnabledToMatchNative();
        mFilename = filename;
        mIsTracing = true;
        return true;
    }

    /**
     * Stop profiling. This won't take effect until Chrome has flushed its file.
     */
    public void stopTracing() {
        if (isTracing()) {
            nativeStopTracing(mNativeTracingControllerAndroid);
        }
    }

    /**
     * Called by native code when the profiler's output file is closed.
     */
    @CalledByNative
    protected void onTracingStopped() {
        if (!isTracing()) {
            // Don't need a toast because this shouldn't happen via the UI.
            Log.e(TAG, "Received onTracingStopped, but we aren't tracing");
            return;
        }

        logAndToastInfo(
                mContext.getString(R.string.profiler_stopped_toast, mFilename));
        TraceEvent.setEnabledToMatchNative();
        mIsTracing = false;
        mFilename = null;
    }

    @Override
    protected void finalize() {
        if (mNativeTracingControllerAndroid != 0) {
            nativeDestroy(mNativeTracingControllerAndroid);
            mNativeTracingControllerAndroid = 0;
        }
    }

    void logAndToastError(String str) {
        Log.e(TAG, str);
        if (mShowToasts) Toast.makeText(mContext, str, Toast.LENGTH_SHORT).show();
    }

    void logAndToastInfo(String str) {
        Log.i(TAG, str);
        if (mShowToasts) Toast.makeText(mContext, str, Toast.LENGTH_SHORT).show();
    }

    private static class TracingIntentFilter extends IntentFilter {
        TracingIntentFilter(Context context) {
            addAction(context.getPackageName() + "." + ACTION_START);
            addAction(context.getPackageName() + "." + ACTION_STOP);
        }
    }

    class TracingBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().endsWith(ACTION_START)) {
                String categories = intent.getStringExtra(CATEGORIES_EXTRA);
                if (TextUtils.isEmpty(categories)) {
                    categories = nativeGetDefaultCategories();
                } else {
                    categories = categories.replaceFirst(
                            DEFAULT_CHROME_CATEGORIES_PLACE_HOLDER, nativeGetDefaultCategories());
                }
                boolean recordContinuously =
                        intent.getStringExtra(RECORD_CONTINUOUSLY_EXTRA) != null;
                String filename = intent.getStringExtra(FILE_EXTRA);
                if (filename != null) {
                    startTracing(filename, true, categories, recordContinuously);
                } else {
                    startTracing(true, categories, recordContinuously);
                }
            } else if (intent.getAction().endsWith(ACTION_STOP)) {
                stopTracing();
            } else {
                Log.e(TAG, "Unexpected intent: " + intent);
            }
        }
    }

    private long mNativeTracingControllerAndroid;
    private native long nativeInit();
    private native void nativeDestroy(long nativeTracingControllerAndroid);
    private native boolean nativeStartTracing(long nativeTracingControllerAndroid, String filename,
            String categories, boolean recordContinuously);
    private native void nativeStopTracing(long nativeTracingControllerAndroid);
    private native String nativeGetDefaultCategories();
}

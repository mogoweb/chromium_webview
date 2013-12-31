// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * The window base class that has the minimum functionality.
 */
@JNINamespace("ui")
public class WindowAndroid {

    private static final String TAG = "WindowAndroid";

    // Native pointer to the c++ WindowAndroid object.
    private int mNativeWindowAndroid = 0;

    // Constants used for intent request code bounding.
    private static final int REQUEST_CODE_PREFIX = 1000;
    private static final int REQUEST_CODE_RANGE_SIZE = 100;
    // A string used as a key to store intent errors in a bundle
    static final String WINDOW_CALLBACK_ERRORS = "window_callback_errors";

    private int mNextRequestCode = 0;
    protected Activity mActivity;
    protected Context mApplicationContext;
    protected SparseArray<IntentCallback> mOutstandingIntents;
    protected HashMap<Integer, String> mIntentErrors;

    /**
     * @param activity
     */
    public WindowAndroid(Activity activity) {
        mActivity = activity;
        mApplicationContext = mActivity.getApplicationContext();
        mOutstandingIntents = new SparseArray<IntentCallback>();
        mIntentErrors = new HashMap<Integer, String>();

    }

    /**
     * Shows an intent and returns the results to the callback object.
     * @param intent The intent that needs to be showed.
     * @param callback The object that will receive the results for the intent.
     * @param errorId The ID of error string to be show if activity is paused before intent
     *        results.
     * @return Whether the intent was shown.
     */
    public boolean showIntent(Intent intent, IntentCallback callback, int errorId) {
        int requestCode = REQUEST_CODE_PREFIX + mNextRequestCode;
        mNextRequestCode = (mNextRequestCode + 1) % REQUEST_CODE_RANGE_SIZE;

        try {
            mActivity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            return false;
        }

        mOutstandingIntents.put(requestCode, callback);
        mIntentErrors.put(requestCode, mActivity.getString(errorId));

        return true;
    }

    /**
     * Displays an error message with a provided error message string.
     * @param error The error message string to be displayed.
     */
    public void showError(String error) {
        if (error != null) {
            Toast.makeText(mActivity, error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Displays an error message from the given resource id.
     * @param resId The error message string's resource id.
     */
    public void showError(int resId) {
        showError(mActivity.getString(resId));
    }

    /**
     * Displays an error message for a nonexistent callback.
     * @param error The error message string to be displayed.
     */
    protected void showCallbackNonExistentError(String error) {
        showError(error);
    }

    /**
     * Broadcasts the given intent to all interested BroadcastReceivers.
     */
    public void sendBroadcast(Intent intent) {
        mActivity.sendBroadcast(intent);
    }

    /**
     * TODO(nileshagrawal): Stop returning Activity Context crbug.com/233440.
     * @return Activity context.
     * @see #getApplicationContext()
     */
    @Deprecated
    public Context getContext() {
        return mActivity;
    }

    /**
     * @return The application context for this activity.
     */
    public Context getApplicationContext() {
        return mApplicationContext;
    }

    /**
     * Saves the error messages that should be shown if any pending intents would return
     * after the application has been put onPause.
     * @param bundle The bundle to save the information in onPause
     */
    public void saveInstanceState(Bundle bundle) {
        bundle.putSerializable(WINDOW_CALLBACK_ERRORS, mIntentErrors);
    }

    /**
     * Restores the error messages that should be shown if any pending intents would return
     * after the application has been put onPause.
     * @param bundle The bundle to restore the information from onResume
     */
    public void restoreInstanceState(Bundle bundle) {
        if (bundle == null) return;

        Object errors = bundle.getSerializable(WINDOW_CALLBACK_ERRORS);
        if (errors instanceof HashMap) {
            @SuppressWarnings("unchecked")
            HashMap<Integer, String> intentErrors = (HashMap<Integer, String>) errors;
            mIntentErrors = intentErrors;
        }
    }

    /**
     * Responds to the intent result if the intent was created by the native window.
     * @param requestCode Request code of the requested intent.
     * @param resultCode Result code of the requested intent.
     * @param data The data returned by the intent.
     * @return Boolean value of whether the intent was started by the native window.
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentCallback callback = mOutstandingIntents.get(requestCode);
        mOutstandingIntents.delete(requestCode);
        String errorMessage = mIntentErrors.remove(requestCode);

        if (callback != null) {
            callback.onIntentCompleted(this, resultCode,
                    mActivity.getContentResolver(), data);
            return true;
        } else {
            if (errorMessage != null) {
                showCallbackNonExistentError(errorMessage);
                return true;
            }
        }
        return false;
    }

    /**
     * An interface that intent callback objects have to implement.
     */
    public interface IntentCallback {
        /**
         * Handles the data returned by the requested intent.
         * @param window A window reference.
         * @param resultCode Result code of the requested intent.
         * @param contentResolver An instance of ContentResolver class for accessing returned data.
         * @param data The data returned by the intent.
         */
        public void onIntentCompleted(WindowAndroid window, int resultCode,
                ContentResolver contentResolver, Intent data);
    }

    /**
     * Destroys the c++ WindowAndroid object if one has been created.
     */
    public void destroy() {
        if (mNativeWindowAndroid != 0) {
            nativeDestroy(mNativeWindowAndroid);
            mNativeWindowAndroid = 0;
        }
    }

    /**
     * Returns a pointer to the c++ AndroidWindow object and calls the initializer if
     * the object has not been previously initialized.
     * @return A pointer to the c++ AndroidWindow.
     */
    public int getNativePointer() {
        if (mNativeWindowAndroid == 0) {
            mNativeWindowAndroid = nativeInit();
        }
        return mNativeWindowAndroid;
    }

    /**
     * Returns a PNG-encoded screenshot of the the window region at (|windowX|,
     * |windowY|) with the size |width| by |height| pixels.
     */
    @CalledByNative
    public byte[] grabSnapshot(int windowX, int windowY, int width, int height) {
        try {
            // Take a screenshot of the content view. This generally includes UI
            // controls such as the URL bar.
            View contentView = mActivity.findViewById(android.R.id.content);
            if (contentView == null) return null;
            Bitmap bitmap =
                    UiUtils.generateScaledScreenshot(contentView, 0, Bitmap.Config.ARGB_8888);
            if (bitmap == null) return null;

            // Clip the result into the requested region.
            if (windowX > 0 || windowY > 0 || width != bitmap.getWidth() ||
                    height != bitmap.getHeight()) {
                Rect clip = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                clip.intersect(windowX, windowY, windowX + width, windowY + height);
                bitmap = Bitmap.createBitmap(
                        bitmap, clip.left, clip.top, clip.width(), clip.height());
            }

            // Compress the result into a PNG.
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, result)) return null;
            bitmap.recycle();
            return result.toByteArray();
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory while grabbing window snapshot.", e);
            return null;
        }
    }

    private native int nativeInit();
    private native void nativeDestroy(int nativeWindowAndroid);

}

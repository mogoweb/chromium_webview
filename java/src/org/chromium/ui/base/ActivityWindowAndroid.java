// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.base;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;

import org.chromium.ui.UiUtils;

import java.io.ByteArrayOutputStream;

/**
 * The class provides the WindowAndroid's implementation which requires
 * Activity Instance.
 * Only instantiate this class when you need the implemented features.
 */
public class ActivityWindowAndroid extends WindowAndroid {
    // Constants used for intent request code bounding.
    private static final int REQUEST_CODE_PREFIX = 1000;
    private static final int REQUEST_CODE_RANGE_SIZE = 100;
    private static final String TAG = "ActivityWindowAndroid";

    private final Activity mActivity;
    private int mNextRequestCode = 0;

    public ActivityWindowAndroid(Activity activity) {
        super(activity.getApplicationContext());
        mActivity = activity;
    }

    @Override
    public int showCancelableIntent(PendingIntent intent, IntentCallback callback, int errorId) {
        int requestCode = generateNextRequestCode();

        try {
            mActivity.startIntentSenderForResult(
                    intent.getIntentSender(), requestCode, new Intent(), 0, 0, 0);
        } catch (SendIntentException e) {
            return START_INTENT_FAILURE;
        }

        storeCallbackData(requestCode, callback, errorId);
        return requestCode;
    }

    @Override
    public int showCancelableIntent(Intent intent, IntentCallback callback, int errorId) {
        int requestCode = generateNextRequestCode();

        try {
            mActivity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            return START_INTENT_FAILURE;
        }

        storeCallbackData(requestCode, callback, errorId);
        return requestCode;
    }

    @Override
    public void cancelIntent(int requestCode) {
        mActivity.finishActivity(requestCode);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentCallback callback = mOutstandingIntents.get(requestCode);
        mOutstandingIntents.delete(requestCode);
        String errorMessage = mIntentErrors.remove(requestCode);

        if (callback != null) {
            callback.onIntentCompleted(this, resultCode,
                    mApplicationContext.getContentResolver(), data);
            return true;
        } else {
            if (errorMessage != null) {
                showCallbackNonExistentError(errorMessage);
                return true;
            }
        }
        return false;
    }

    @Override
    @Deprecated
    public Context getContext() {
        return mActivity;
    }

    /**
     * Returns a PNG-encoded screenshot of the the window region at (|windowX|,
     * |windowY|) with the size |width| by |height| pixels.
     */
    @Override
    public byte[] grabSnapshot(int windowX, int windowY, int width, int height) {
        try {
            // Take a screenshot of the root activity view. This generally includes UI
            // controls such as the URL bar and OS windows such as the status bar.
            View rootView = mActivity.findViewById(android.R.id.content).getRootView();
            Bitmap bitmap = UiUtils.generateScaledScreenshot(rootView, 0, Bitmap.Config.ARGB_8888);
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

    private int generateNextRequestCode() {
        int requestCode = REQUEST_CODE_PREFIX + mNextRequestCode;
        mNextRequestCode = (mNextRequestCode + 1) % REQUEST_CODE_RANGE_SIZE;
        return requestCode;
    }

    private void storeCallbackData(int requestCode, IntentCallback callback, int errorId) {
        mOutstandingIntents.put(requestCode, callback);
        mIntentErrors.put(requestCode, mApplicationContext.getString(errorId));
    }
}

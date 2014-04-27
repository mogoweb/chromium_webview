// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Vibrator;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * This is the implementation of the C++ counterpart VibrationProvider.
 */
@JNINamespace("content")
class VibrationProvider {
    private static final String TAG = "VibrationProvider";

    private final AudioManager mAudioManager;
    private final Vibrator mVibrator;
    private final boolean mHasVibratePermission;

    @CalledByNative
    private static VibrationProvider create(Context context) {
        return new VibrationProvider(context);
    }

    @CalledByNative
    private void vibrate(long milliseconds) {
        if (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT &&
                mHasVibratePermission) {
            mVibrator.vibrate(milliseconds);
        }
    }

    @CalledByNative
    private void cancelVibration() {
        if (mHasVibratePermission) mVibrator.cancel();
    }

    private VibrationProvider(Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibratePermission = context.checkCallingOrSelfPermission(
                android.Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED;
        if (!mHasVibratePermission) {
            Log.w(TAG, "Failed to use vibrate API, requires VIBRATE permission.");
        }
    }
}

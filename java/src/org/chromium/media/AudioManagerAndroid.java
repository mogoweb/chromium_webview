// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

@JNINamespace("media")
class AudioManagerAndroid {
    private static final String TAG = AudioManagerAndroid.class.getSimpleName();

    private final AudioManager mAudioManager;
    private final Context mContext;

    private BroadcastReceiver mReceiver;
    private boolean mOriginalSpeakerStatus;

    @CalledByNative
    public void setMode(int mode) {
        try {
            mAudioManager.setMode(mode);
        } catch (SecurityException e) {
            Log.e(TAG, "setMode exception: " + e.getMessage());
            logDeviceInfo();
        }
    }

    @CalledByNative
    private static AudioManagerAndroid createAudioManagerAndroid(Context context) {
        return new AudioManagerAndroid(context);
    }

    private AudioManagerAndroid(Context context) {
        mContext = context;
        mAudioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    @CalledByNative
    public void registerHeadsetReceiver() {
        if (mReceiver != null) {
            return;
        }

        mOriginalSpeakerStatus = mAudioManager.isSpeakerphoneOn();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                    try {
                        mAudioManager.setSpeakerphoneOn(
                                intent.getIntExtra("state", 0) == 0);
                    } catch (SecurityException e) {
                        Log.e(TAG, "setMode exception: " + e.getMessage());
                        logDeviceInfo();
                    }
                }
            }
        };
        mContext.registerReceiver(mReceiver, filter);
    }

    @CalledByNative
    public void unregisterHeadsetReceiver() {
        mContext.unregisterReceiver(mReceiver);
        mReceiver = null;
        mAudioManager.setSpeakerphoneOn(mOriginalSpeakerStatus);
    }

    private void logDeviceInfo() {
        Log.i(TAG, "Manufacturer:" + Build.MANUFACTURER +
                  " Board: " + Build.BOARD + " Device: " + Build.DEVICE +
                  " Model: " + Build.MODEL + " PRODUCT: " + Build.PRODUCT);
    }
}

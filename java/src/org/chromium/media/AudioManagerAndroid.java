// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@JNINamespace("media")
class AudioManagerAndroid {
    private static final String TAG = "AudioManagerAndroid";

    // Set to true to enable debug logs. Avoid in production builds.
    // NOTE: always check in as false.
    private static final boolean DEBUG = false;

    private static boolean runningOnJellyBeanOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    private static boolean runningOnJellyBeanMR1OrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    private static boolean runningOnJellyBeanMR2OrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    }

    /** Simple container for device information. */
    private static class AudioDeviceName {
        private final int mId;
        private final String mName;

        private AudioDeviceName(int id, String name) {
            mId = id;
            mName = name;
        }

        @CalledByNative("AudioDeviceName")
        private String id() { return String.valueOf(mId); }

        @CalledByNative("AudioDeviceName")
        private String name() { return mName; }
    }

    // Supported audio device types.
    private static final int DEVICE_DEFAULT = -2;
    private static final int DEVICE_INVALID = -1;
    private static final int DEVICE_SPEAKERPHONE = 0;
    private static final int DEVICE_WIRED_HEADSET = 1;
    private static final int DEVICE_EARPIECE = 2;
    private static final int DEVICE_BLUETOOTH_HEADSET = 3;
    private static final int DEVICE_COUNT = 4;

    // Maps audio device types to string values. This map must be in sync
    // with the device types above.
    // TODO(henrika): add support for proper detection of device names and
    // localize the name strings by using resource strings.
    // See http://crbug.com/333208 for details.
    private static final String[] DEVICE_NAMES = new String[] {
        "Speakerphone",
        "Wired headset",      // With or without microphone.
        "Headset earpiece",   // Only available on mobile phones.
        "Bluetooth headset",  // Requires BLUETOOTH permission.
    };

    // List of valid device types.
    private static final Integer[] VALID_DEVICES = new Integer[] {
        DEVICE_SPEAKERPHONE,
        DEVICE_WIRED_HEADSET,
        DEVICE_EARPIECE,
        DEVICE_BLUETOOTH_HEADSET,
    };

    // Bluetooth audio SCO states. Example of valid state sequence:
    // SCO_INVALID -> SCO_TURNING_ON -> SCO_ON -> SCO_TURNING_OFF -> SCO_OFF.
    private static final int STATE_BLUETOOTH_SCO_INVALID = -1;
    private static final int STATE_BLUETOOTH_SCO_OFF = 0;
    private static final int STATE_BLUETOOTH_SCO_ON = 1;
    private static final int STATE_BLUETOOTH_SCO_TURNING_ON = 2;
    private static final int STATE_BLUETOOTH_SCO_TURNING_OFF = 3;

    // Use 44.1kHz as the default sampling rate.
    private static final int DEFAULT_SAMPLING_RATE = 44100;
    // Randomly picked up frame size which is close to return value on N4.
    // Return this value when getProperty(PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
    // fails.
    private static final int DEFAULT_FRAME_PER_BUFFER = 256;

    private final AudioManager mAudioManager;
    private final Context mContext;
    private final long mNativeAudioManagerAndroid;

    // Enabled during initialization if BLUETOOTH permission is granted.
    private boolean mHasBluetoothPermission = false;

    private int mSavedAudioMode = AudioManager.MODE_INVALID;

    // Stores the audio states related to Bluetooth SCO audio, where some
    // states are needed to keep track of intermediate states while the SCO
    // channel is enabled or disabled (switching state can take a few seconds).
    private int mBluetoothScoState = STATE_BLUETOOTH_SCO_INVALID;

    private boolean mIsInitialized = false;
    private boolean mSavedIsSpeakerphoneOn;
    private boolean mSavedIsMicrophoneMute;

    // Id of the requested audio device. Can only be modified by
    // call to setDevice().
    private int mRequestedAudioDevice = DEVICE_INVALID;

    // Lock to protect |mAudioDevices| and |mRequestedAudioDevice| which can
    // be accessed from the main thread and the audio manager thread.
    private final Object mLock = new Object();

    // Contains a list of currently available audio devices.
    private boolean[] mAudioDevices = new boolean[DEVICE_COUNT];

    private final ContentResolver mContentResolver;
    private SettingsObserver mSettingsObserver = null;
    private HandlerThread mSettingsObserverThread = null;
    private int mCurrentVolume;

    // Broadcast receiver for wired headset intent broadcasts.
    private BroadcastReceiver mWiredHeadsetReceiver;

    // Broadcast receiver for Bluetooth headset intent broadcasts.
    // Utilized to detect changes in Bluetooth headset availability.
    private BroadcastReceiver mBluetoothHeadsetReceiver;

    // Broadcast receiver for Bluetooth SCO broadcasts.
    // Utilized to detect if BT SCO streaming is on or off.
    private BroadcastReceiver mBluetoothScoReceiver;

    /** Construction */
    @CalledByNative
    private static AudioManagerAndroid createAudioManagerAndroid(
            Context context,
            long nativeAudioManagerAndroid) {
        return new AudioManagerAndroid(context, nativeAudioManagerAndroid);
    }

    private AudioManagerAndroid(Context context, long nativeAudioManagerAndroid) {
        mContext = context;
        mNativeAudioManagerAndroid = nativeAudioManagerAndroid;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mContentResolver = mContext.getContentResolver();
    }

    /**
     * Saves the initial speakerphone and microphone state.
     * Populates the list of available audio devices and registers receivers
     * for broadcast intents related to wired headset and Bluetooth devices.
     */
    @CalledByNative
    private void init() {
        if (DEBUG) logd("init");
        if (mIsInitialized)
            return;

        for (int i = 0; i < DEVICE_COUNT; ++i) {
            mAudioDevices[i] = false;
        }

        // Initialize audio device list with things we know is always available.
        if (hasEarpiece()) {
            mAudioDevices[DEVICE_EARPIECE] = true;
        }
        mAudioDevices[DEVICE_SPEAKERPHONE] = true;

        // Register receivers for broadcast intents related to Bluetooth device
        // and Bluetooth SCO notifications. Requires BLUETOOTH permission.
        registerBluetoothIntentsIfNeeded();

        // Register receiver for broadcast intents related to adding/
        // removing a wired headset (Intent.ACTION_HEADSET_PLUG).
        registerForWiredHeadsetIntentBroadcast();

        // Start observer for volume changes.
        // TODO(henrika): try-catch parts below are added as a test to see if
        // it avoids the crash in init() reported in http://crbug.com/336600.
        // Should be removed if possible when we understand the reason better.
        try {
            mSettingsObserverThread = new HandlerThread("SettingsObserver");
            mSettingsObserverThread.start();
            mSettingsObserver = new SettingsObserver(
                new Handler(mSettingsObserverThread.getLooper()));
        } catch (Exception e) {
            // It is fine to rely on code below here to detect failure by
            // observing mSettingsObserver==null.
            Log.wtf(TAG, "SettingsObserver exception: ", e);
        }

        mIsInitialized = true;
        if (DEBUG) reportUpdate();
    }

    /**
     * Unregister all previously registered intent receivers and restore
     * the stored state (stored in {@link #init()}).
     */
    @CalledByNative
    private void close() {
        if (DEBUG) logd("close");
        if (!mIsInitialized)
            return;

        if (mSettingsObserverThread != null) {
            mSettingsObserverThread.quit();
            try {
                mSettingsObserverThread.join();
            } catch (Exception e) {
                Log.wtf(TAG, "HandlerThread.join() exception: ", e);
            }
            mSettingsObserverThread = null;
        }
        if (mContentResolver != null) {
            mContentResolver.unregisterContentObserver(mSettingsObserver);
            mSettingsObserver = null;
        }

        unregisterForWiredHeadsetIntentBroadcast();
        unregisterBluetoothIntentsIfNeeded();

        mIsInitialized = false;
    }

    /**
     * Saves current audio mode and sets audio mode to MODE_IN_COMMUNICATION
     * if input parameter is true. Restores saved audio mode if input parameter
     * is false.
     */
    @CalledByNative
    private void setCommunicationAudioModeOn(boolean on) {
        if (DEBUG) logd("setCommunicationAudioModeOn(" + on + ")");

        if (on) {
            if (mSavedAudioMode != AudioManager.MODE_INVALID) {
                Log.wtf(TAG, "Audio mode has already been set!");
                return;
            }

            // Store the current audio mode the first time we try to
            // switch to communication mode.
            try {
                mSavedAudioMode = mAudioManager.getMode();
            } catch (SecurityException e) {
                Log.wtf(TAG, "getMode exception: ", e);
                logDeviceInfo();
            }

            // Store microphone mute state and speakerphone state so it can
            // be restored when closing.
            mSavedIsSpeakerphoneOn = mAudioManager.isSpeakerphoneOn();
            mSavedIsMicrophoneMute = mAudioManager.isMicrophoneMute();

            try {
                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } catch (SecurityException e) {
                Log.wtf(TAG, "setMode exception: ", e);
                logDeviceInfo();
            }
        } else {
            if (mSavedAudioMode == AudioManager.MODE_INVALID) {
                Log.wtf(TAG, "Audio mode has not yet been set!");
                return;
            }

            // Restore previously stored audio states.
            setMicrophoneMute(mSavedIsMicrophoneMute);
            setSpeakerphoneOn(mSavedIsSpeakerphoneOn);

            // Restore the mode that was used before we switched to
            // communication mode.
            try {
                mAudioManager.setMode(mSavedAudioMode);
            } catch (SecurityException e) {
                Log.wtf(TAG, "setMode exception: ", e);
                logDeviceInfo();
            }
            mSavedAudioMode = AudioManager.MODE_INVALID;
        }
    }

    /**
     * Activates, i.e., starts routing audio to, the specified audio device.
     *
     * @param deviceId Unique device ID (integer converted to string)
     * representing the selected device. This string is empty if the so-called
     * default device is requested.
     */
    @CalledByNative
    private boolean setDevice(String deviceId) {
        if (DEBUG) logd("setDevice: " + deviceId);
        if (!mIsInitialized)
            return false;
        int intDeviceId = deviceId.isEmpty() ?
            DEVICE_DEFAULT : Integer.parseInt(deviceId);

        if (intDeviceId == DEVICE_DEFAULT) {
            boolean devices[] = null;
            synchronized (mLock) {
                devices = mAudioDevices.clone();
                mRequestedAudioDevice = DEVICE_DEFAULT;
            }
            int defaultDevice = selectDefaultDevice(devices);
            setAudioDevice(defaultDevice);
            return true;
        }

        // A non-default device is specified. Verify that it is valid
        // device, and if so, start using it.
        List<Integer> validIds = Arrays.asList(VALID_DEVICES);
        if (!validIds.contains(intDeviceId) || !mAudioDevices[intDeviceId]) {
            return false;
        }
        synchronized (mLock) {
            mRequestedAudioDevice = intDeviceId;
        }
        setAudioDevice(intDeviceId);
        return true;
    }

    /**
     * @return the current list of available audio devices.
     * Note that this call does not trigger any update of the list of devices,
     * it only copies the current state in to the output array.
     */
    @CalledByNative
    private AudioDeviceName[] getAudioInputDeviceNames() {
        if (!mIsInitialized)
            return null;
        boolean devices[] = null;
        synchronized (mLock) {
            devices = mAudioDevices.clone();
        }
        List<String> list = new ArrayList<String>();
        AudioDeviceName[] array =
            new AudioDeviceName[getNumOfAudioDevices(devices)];
        int i = 0;
        for (int id = 0; id < DEVICE_COUNT; ++id) {
            if (devices[id]) {
                array[i] = new AudioDeviceName(id, DEVICE_NAMES[id]);
                list.add(DEVICE_NAMES[id]);
                i++;
            }
        }
        if (DEBUG) logd("getAudioInputDeviceNames: " + list);
        return array;
    }

    @CalledByNative
    private int getNativeOutputSampleRate() {
        if (runningOnJellyBeanMR1OrHigher()) {
            String sampleRateString = mAudioManager.getProperty(
                    AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            return (sampleRateString == null ?
                    DEFAULT_SAMPLING_RATE : Integer.parseInt(sampleRateString));
        } else {
            return DEFAULT_SAMPLING_RATE;
        }
    }

  /**
   * Returns the minimum frame size required for audio input.
   *
   * @param sampleRate sampling rate
   * @param channels number of channels
   */
    @CalledByNative
    private static int getMinInputFrameSize(int sampleRate, int channels) {
        int channelConfig;
        if (channels == 1) {
            channelConfig = AudioFormat.CHANNEL_IN_MONO;
        } else if (channels == 2) {
            channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        } else {
            return -1;
        }
        return AudioRecord.getMinBufferSize(
                sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) / 2 / channels;
    }

  /**
   * Returns the minimum frame size required for audio output.
   *
   * @param sampleRate sampling rate
   * @param channels number of channels
   */
    @CalledByNative
    private static int getMinOutputFrameSize(int sampleRate, int channels) {
        int channelConfig;
        if (channels == 1) {
            channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        } else if (channels == 2) {
            channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        } else {
            return -1;
        }
        return AudioTrack.getMinBufferSize(
                sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) / 2 / channels;
    }

    @CalledByNative
    private boolean isAudioLowLatencySupported() {
        return mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUDIO_LOW_LATENCY);
    }

    @CalledByNative
    private int getAudioLowLatencyOutputFrameSize() {
        String framesPerBuffer =
                mAudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        return (framesPerBuffer == null ?
                DEFAULT_FRAME_PER_BUFFER : Integer.parseInt(framesPerBuffer));
    }

    @CalledByNative
    public static boolean shouldUseAcousticEchoCanceler() {
        // AcousticEchoCanceler was added in API level 16 (Jelly Bean).
        if (!runningOnJellyBeanOrHigher()) {
            return false;
        }

        // Next is a list of device models which have been vetted for good
        // quality platform echo cancellation.
        if (!Build.MODEL.equals("SM-T310R") &&  // Galaxy Tab 3 7.0
            !Build.MODEL.equals("GT-I9300") &&  // Galaxy S3
            !Build.MODEL.equals("GT-I9500") &&  // Galaxy S4
            !Build.MODEL.equals("GT-N7105") &&  // Galaxy Note 2
            !Build.MODEL.equals("SM-N9005") &&  // Galaxy Note 3
            !Build.MODEL.equals("Nexus 4") &&
            !Build.MODEL.equals("Nexus 5") &&
            !Build.MODEL.equals("Nexus 7")) {
            return false;
        }

        // As a final check, verify that the device supports acoustic echo
        // cancellation.
        return AcousticEchoCanceler.isAvailable();
    }

    /**
     * Register for BT intents if we have the BLUETOOTH permission.
     * Also extends the list of available devices with a BT device if one exists.
     */
    private void registerBluetoothIntentsIfNeeded() {
        // Check if this process has the BLUETOOTH permission or not.
        mHasBluetoothPermission = hasBluetoothPermission();

        // Add a Bluetooth headset to the list of available devices if a BT
        // headset is detected and if we have the BLUETOOTH permission.
        // We must do this initial check using a dedicated method since the
        // broadcasted intent BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED
        // is not sticky and will only be received if a BT headset is connected
        // after this method has been called.
        if (!mHasBluetoothPermission) {
            return;
        }
        if (hasBluetoothHeadset()) {
            mAudioDevices[DEVICE_BLUETOOTH_HEADSET] = true;
        }

        // Register receivers for broadcast intents related to changes in
        // Bluetooth headset availability and usage of the SCO channel.
        registerForBluetoothHeadsetIntentBroadcast();
        registerForBluetoothScoIntentBroadcast();
    }

    /** Unregister for BT intents if a registration has been made. */
    private void unregisterBluetoothIntentsIfNeeded() {
        if (mHasBluetoothPermission) {
            mAudioManager.stopBluetoothSco();
            unregisterForBluetoothHeadsetIntentBroadcast();
            unregisterForBluetoothScoIntentBroadcast();
        }
    }

    /** Sets the speaker phone mode. */
    private void setSpeakerphoneOn(boolean on) {
        boolean wasOn = mAudioManager.isSpeakerphoneOn();
        if (wasOn == on) {
            return;
        }
        mAudioManager.setSpeakerphoneOn(on);
    }

    /** Sets the microphone mute state. */
    private void setMicrophoneMute(boolean on) {
        boolean wasMuted = mAudioManager.isMicrophoneMute();
        if (wasMuted == on) {
            return;
        }
        mAudioManager.setMicrophoneMute(on);
    }

    /** Gets  the current microphone mute state. */
    private boolean isMicrophoneMute() {
        return mAudioManager.isMicrophoneMute();
    }

    /** Gets the current earpice state. */
    private boolean hasEarpiece() {
        return mContext.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_TELEPHONY);
    }

    /** Checks if the process has BLUETOOTH permission or not. */
    private boolean hasBluetoothPermission() {
        boolean hasBluetooth = mContext.checkPermission(
            android.Manifest.permission.BLUETOOTH,
            Process.myPid(),
            Process.myUid()) == PackageManager.PERMISSION_GRANTED;
        if (DEBUG && !hasBluetooth) {
            logd("BLUETOOTH permission is missing!");
        }
        return hasBluetooth;
    }

    /**
     * Gets the current Bluetooth headset state.
     * android.bluetooth.BluetoothAdapter.getProfileConnectionState() requires
     * the BLUETOOTH permission.
     */
    private boolean hasBluetoothHeadset() {
        if (!mHasBluetoothPermission) {
            Log.wtf(TAG, "hasBluetoothHeadset() requires BLUETOOTH permission!");
            return false;
        }

        // To get a BluetoothAdapter representing the local Bluetooth adapter,
        // when running on JELLY_BEAN_MR1 (4.2) and below, call the static
        // getDefaultAdapter() method; when running on JELLY_BEAN_MR2 (4.3) and
        // higher, retrieve it through getSystemService(String) with
        // BLUETOOTH_SERVICE.
        BluetoothAdapter btAdapter = null;
        if (runningOnJellyBeanMR2OrHigher()) {
            // Use BluetoothManager to get the BluetoothAdapter for
            // Android 4.3 and above.
            try {
                BluetoothManager btManager =
                        (BluetoothManager)mContext.getSystemService(
                                Context.BLUETOOTH_SERVICE);
                btAdapter = btManager.getAdapter();
            } catch (Exception e) {
                Log.wtf(TAG, "BluetoothManager.getAdapter exception", e);
                return false;
            }
        } else {
            // Use static method for Android 4.2 and below to get the
            // BluetoothAdapter.
            try {
                btAdapter = BluetoothAdapter.getDefaultAdapter();
            } catch (Exception e) {
                Log.wtf(TAG, "BluetoothAdapter.getDefaultAdapter exception", e);
                return false;
            }
        }

        int profileConnectionState;
        try {
            profileConnectionState = btAdapter.getProfileConnectionState(
                android.bluetooth.BluetoothProfile.HEADSET);
        } catch (Exception e) {
            Log.wtf(TAG, "BluetoothAdapter.getProfileConnectionState exception", e);
            profileConnectionState =
                android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
        }

        // Ensure that Bluetooth is enabled and that a device which supports the
        // headset and handsfree profile is connected.
        // TODO(henrika): it is possible that btAdapter.isEnabled() is
        // redundant. It might be sufficient to only check the profile state.
        return btAdapter.isEnabled() && profileConnectionState ==
            android.bluetooth.BluetoothProfile.STATE_CONNECTED;
    }

    /**
     * Registers receiver for the broadcasted intent when a wired headset is
     * plugged in or unplugged. The received intent will have an extra
     * 'state' value where 0 means unplugged, and 1 means plugged.
     */
    private void registerForWiredHeadsetIntentBroadcast() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);

        /** Receiver which handles changes in wired headset availability. */
        mWiredHeadsetReceiver = new BroadcastReceiver() {
            private static final int STATE_UNPLUGGED = 0;
            private static final int STATE_PLUGGED = 1;
            private static final int HAS_NO_MIC = 0;
            private static final int HAS_MIC = 1;

            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra("state", STATE_UNPLUGGED);
                if (DEBUG) {
                    int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
                    String name = intent.getStringExtra("name");
                    logd("BroadcastReceiver.onReceive: a=" + intent.getAction() +
                        ", s=" + state +
                        ", m=" + microphone +
                        ", n=" + name +
                        ", sb=" + isInitialStickyBroadcast());
                }
                switch (state) {
                    case STATE_UNPLUGGED:
                        synchronized (mLock) {
                            // Wired headset and earpiece are mutually exclusive.
                            mAudioDevices[DEVICE_WIRED_HEADSET] = false;
                            if (hasEarpiece()) {
                                mAudioDevices[DEVICE_EARPIECE] = true;
                            }
                        }
                        break;
                    case STATE_PLUGGED:
                        synchronized (mLock) {
                            // Wired headset and earpiece are mutually exclusive.
                            mAudioDevices[DEVICE_WIRED_HEADSET] = true;
                            mAudioDevices[DEVICE_EARPIECE] = false;
                        }
                        break;
                    default:
                        loge("Invalid state!");
                        break;
                }

                // Update the existing device selection, but only if a specific
                // device has already been selected explicitly.
                if (deviceHasBeenRequested()) {
                    updateDeviceActivation();
                } else if (DEBUG) {
                    reportUpdate();
                }
            }
        };

        // Note: the intent we register for here is sticky, so it'll tell us
        // immediately what the last action was (plugged or unplugged).
        // It will enable us to set the speakerphone correctly.
        mContext.registerReceiver(mWiredHeadsetReceiver, filter);
    }

    /** Unregister receiver for broadcasted ACTION_HEADSET_PLUG intent. */
    private void unregisterForWiredHeadsetIntentBroadcast() {
        mContext.unregisterReceiver(mWiredHeadsetReceiver);
        mWiredHeadsetReceiver = null;
    }

    /**
     * Registers receiver for the broadcasted intent related to BT headset
     * availability or a change in connection state of the local Bluetooth
     * adapter. Example: triggers when the BT device is turned on or off.
     * BLUETOOTH permission is required to receive this one.
     */
    private void registerForBluetoothHeadsetIntentBroadcast() {
        IntentFilter filter = new IntentFilter(
            android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);

        /** Receiver which handles changes in BT headset availability. */
        mBluetoothHeadsetReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // A change in connection state of the Headset profile has
                // been detected, e.g. BT headset has been connected or
                // disconnected. This broadcast is *not* sticky.
                int profileState = intent.getIntExtra(
                    android.bluetooth.BluetoothHeadset.EXTRA_STATE,
                    android.bluetooth.BluetoothHeadset.STATE_DISCONNECTED);
                if (DEBUG) {
                    logd("BroadcastReceiver.onReceive: a=" + intent.getAction() +
                        ", s=" + profileState +
                        ", sb=" + isInitialStickyBroadcast());
                }

                switch (profileState) {
                    case android.bluetooth.BluetoothProfile.STATE_DISCONNECTED:
                        // We do not have to explicitly call stopBluetoothSco()
                        // since BT SCO will be disconnected automatically when
                        // the BT headset is disabled.
                        synchronized (mLock) {
                            // Remove the BT device from the list of devices.
                            mAudioDevices[DEVICE_BLUETOOTH_HEADSET] = false;
                        }
                        break;
                    case android.bluetooth.BluetoothProfile.STATE_CONNECTED:
                        synchronized (mLock) {
                            // Add the BT device to the list of devices.
                            mAudioDevices[DEVICE_BLUETOOTH_HEADSET] = true;
                        }
                        break;
                    case android.bluetooth.BluetoothProfile.STATE_CONNECTING:
                        // Bluetooth service is switching from off to on.
                        break;
                    case android.bluetooth.BluetoothProfile.STATE_DISCONNECTING:
                        // Bluetooth service is switching from on to off.
                        break;
                    default:
                        loge("Invalid state!");
                        break;
                }

                // Update the existing device selection, but only if a specific
                // device has already been selected explicitly.
                if (deviceHasBeenRequested()) {
                    updateDeviceActivation();
                } else if (DEBUG) {
                    reportUpdate();
                }
           }
        };

        mContext.registerReceiver(mBluetoothHeadsetReceiver, filter);
    }

    private void unregisterForBluetoothHeadsetIntentBroadcast() {
        mContext.unregisterReceiver(mBluetoothHeadsetReceiver);
        mBluetoothHeadsetReceiver = null;
    }

    /**
     * Registers receiver for the broadcasted intent related the existence
     * of a BT SCO channel. Indicates if BT SCO streaming is on or off.
     */
    private void registerForBluetoothScoIntentBroadcast() {
        IntentFilter filter = new IntentFilter(
            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);

        /** BroadcastReceiver implementation which handles changes in BT SCO. */
        mBluetoothScoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                if (DEBUG) {
                    logd("BroadcastReceiver.onReceive: a=" + intent.getAction() +
                        ", s=" + state +
                        ", sb=" + isInitialStickyBroadcast());
                }

                switch (state) {
                    case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                        mBluetoothScoState = STATE_BLUETOOTH_SCO_ON;
                        break;
                    case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                        mBluetoothScoState = STATE_BLUETOOTH_SCO_OFF;
                        break;
                    case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                        // do nothing
                        break;
                    default:
                        loge("Invalid state!");
                }
                if (DEBUG) {
                    reportUpdate();
                }
           }
        };

        mContext.registerReceiver(mBluetoothScoReceiver, filter);
    }

    private void unregisterForBluetoothScoIntentBroadcast() {
        mContext.unregisterReceiver(mBluetoothScoReceiver);
        mBluetoothScoReceiver = null;
    }

    /** Enables BT audio using the SCO audio channel. */
    private void startBluetoothSco() {
        if (!mHasBluetoothPermission) {
            return;
        }
        if (mBluetoothScoState == STATE_BLUETOOTH_SCO_ON ||
            mBluetoothScoState == STATE_BLUETOOTH_SCO_TURNING_ON) {
            // Unable to turn on BT in this state.
            return;
        }

        // Check if audio is already routed to BT SCO; if so, just update
        // states but don't try to enable it again.
        if (mAudioManager.isBluetoothScoOn()) {
            mBluetoothScoState = STATE_BLUETOOTH_SCO_ON;
            return;
        }

        if (DEBUG) logd("startBluetoothSco: turning BT SCO on...");
        mBluetoothScoState = STATE_BLUETOOTH_SCO_TURNING_ON;
        mAudioManager.startBluetoothSco();
    }

    /** Disables BT audio using the SCO audio channel. */
    private void stopBluetoothSco() {
        if (!mHasBluetoothPermission) {
            return;
        }
        if (mBluetoothScoState != STATE_BLUETOOTH_SCO_ON &&
            mBluetoothScoState != STATE_BLUETOOTH_SCO_TURNING_ON) {
            // No need to turn off BT in this state.
            return;
        }
        if (!mAudioManager.isBluetoothScoOn()) {
            // TODO(henrika): can we do anything else than logging here?
            loge("Unable to stop BT SCO since it is already disabled!");
            return;
        }

        if (DEBUG) logd("stopBluetoothSco: turning BT SCO off...");
        mBluetoothScoState = STATE_BLUETOOTH_SCO_TURNING_OFF;
        mAudioManager.stopBluetoothSco();
    }

    /**
     * Changes selection of the currently active audio device.
     *
     * @param device Specifies the selected audio device.
     */
    private void setAudioDevice(int device) {
        if (DEBUG) logd("setAudioDevice(device=" + device + ")");

        // Ensure that the Bluetooth SCO audio channel is always disabled
        // unless the BT headset device is selected.
        if (device == DEVICE_BLUETOOTH_HEADSET) {
            startBluetoothSco();
        } else {
            stopBluetoothSco();
        }

        switch (device) {
            case DEVICE_BLUETOOTH_HEADSET:
                break;
            case DEVICE_SPEAKERPHONE:
                setSpeakerphoneOn(true);
                break;
            case DEVICE_WIRED_HEADSET:
                setSpeakerphoneOn(false);
                break;
            case DEVICE_EARPIECE:
                setSpeakerphoneOn(false);
                break;
            default:
                loge("Invalid audio device selection!");
                break;
        }
        reportUpdate();
    }

    /**
     * Use a special selection scheme if the default device is selected.
     * The "most unique" device will be selected; Wired headset first,
     * then Bluetooth and last the speaker phone.
     */
    private static int selectDefaultDevice(boolean[] devices) {
        if (devices[DEVICE_WIRED_HEADSET]) {
            return DEVICE_WIRED_HEADSET;
        } else if (devices[DEVICE_BLUETOOTH_HEADSET]) {
            // TODO(henrika): possibly need improvements here if we are
            // in a state where Bluetooth is turning off.
            return DEVICE_BLUETOOTH_HEADSET;
        }
        return DEVICE_SPEAKERPHONE;
    }

    /** Returns true if setDevice() has been called with a valid device id. */
    private boolean deviceHasBeenRequested() {
        synchronized (mLock) {
            return (mRequestedAudioDevice != DEVICE_INVALID);
        }
    }

    /**
     * Updates the active device given the current list of devices and
     * information about if a specific device has been selected or if
     * the default device is selected.
     */
    private void updateDeviceActivation() {
        boolean devices[] = null;
        int requested = DEVICE_INVALID;
        synchronized (mLock) {
            requested = mRequestedAudioDevice;
            devices = mAudioDevices.clone();
        }
        if (requested == DEVICE_INVALID) {
            loge("Unable to activate device since no device is selected!");
            return;
        }

        // Update default device if it has been selected explicitly, or
        // the selected device has been removed from the list.
        if (requested == DEVICE_DEFAULT || !devices[requested]) {
            // Get default device given current list and activate the device.
            int defaultDevice = selectDefaultDevice(devices);
            setAudioDevice(defaultDevice);
        } else {
            // Activate the selected device since we know that it exists in
            // the list.
            setAudioDevice(requested);
        }
    }

    /** Returns number of available devices */
    private static int getNumOfAudioDevices(boolean[] devices) {
        int count = 0;
        for (int i = 0; i < DEVICE_COUNT; ++i) {
            if (devices[i])
                ++count;
        }
        return count;
    }

    /**
     * For now, just log the state change but the idea is that we should
     * notify a registered state change listener (if any) that there has
     * been a change in the state.
     * TODO(henrika): add support for state change listener.
     */
    private void reportUpdate() {
        synchronized (mLock) {
            List<String> devices = new ArrayList<String>();
            for (int i = 0; i < DEVICE_COUNT; ++i) {
                if (mAudioDevices[i])
                    devices.add(DEVICE_NAMES[i]);
            }
            if (DEBUG) {
                logd("reportUpdate: requested=" + mRequestedAudioDevice +
                    ", btSco=" + mBluetoothScoState +
                    ", devices=" + devices);
            }
        }
    }

    private void logDeviceInfo() {
        Log.i(TAG, "Manufacturer:" + Build.MANUFACTURER +
                " Board: " + Build.BOARD + " Device: " + Build.DEVICE +
                " Model: " + Build.MODEL + " PRODUCT: " + Build.PRODUCT);
    }

    /** Trivial helper method for debug logging */
    private static void logd(String msg) {
        Log.d(TAG, msg);
    }

    /** Trivial helper method for error logging */
    private static void loge(String msg) {
        Log.e(TAG, msg);
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
            mContentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DEBUG) logd("SettingsObserver.onChange: " + selfChange);
            super.onChange(selfChange);
            int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            if (DEBUG) logd("nativeSetMute: " + (volume == 0));
            nativeSetMute(mNativeAudioManagerAndroid, (volume == 0));
        }
    }

    private native void nativeSetMute(long nativeAudioManagerAndroid, boolean muted);
}

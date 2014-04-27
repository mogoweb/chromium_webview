// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owned by its native counterpart declared in
 * usb_midi_device_factory_android.h. Refer to that class for general comments.
 */
@JNINamespace("media")
class UsbMidiDeviceFactoryAndroid {
    /**
     * The UsbManager of this system.
     */
    private UsbManager mUsbManager;

    /**
     * A BroadcastReceiver for USB device permission requests.
     */
    private BroadcastReceiver mReceiver;

    /**
     * Accessible USB-MIDI devices got so far.
     */
    private final List<UsbMidiDeviceAndroid> mDevices = new ArrayList<UsbMidiDeviceAndroid>();

    /**
     * Devices whose access permission requested but not resolved so far.
     */
    private Set<UsbDevice> mRequestedDevices;

    /**
     * The identifier of this factory.
     */
    private long mNativePointer;

    private static final String ACTION_USB_PERMISSION =
        "org.chromium.media.USB_PERMISSION";

    /**
     * Constructs a UsbMidiDeviceAndroid.
     * @param natviePointer The native pointer to which the created factory is associated.
     */
    UsbMidiDeviceFactoryAndroid(long nativePointer) {
        mNativePointer = nativePointer;
    }

    /**
     * Constructs a UsbMidiDeviceAndroid.
     * @param nativePointer The native pointer to which the created factory is associated.
     */
    @CalledByNative
    static UsbMidiDeviceFactoryAndroid create(long nativePointer) {
        return new UsbMidiDeviceFactoryAndroid(nativePointer);
    }

    /**
     * Enumerates USB-MIDI devices.
     * If there are devices having USB-MIDI interfaces, this function requests permission for
     * accessing the device to the user.
     * When the permission request is accepted or rejected onRequestDone will be called.
     *
     * If there are no USB-MIDI interfaces, this function returns false.
     * @return true if some permission requests are in progress.
     */
    @CalledByNative
    boolean enumerateDevices(Context context) {
        mUsbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        Map<String, UsbDevice> devices = mUsbManager.getDeviceList();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mRequestedDevices = new HashSet<UsbDevice>();
        for (UsbDevice device : devices.values()) {
            boolean found = false;
            for (int i = 0; i < device.getInterfaceCount() && !found; ++i) {
                UsbInterface iface = device.getInterface(i);
                if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO &&
                    iface.getInterfaceSubclass() == UsbMidiDeviceAndroid.MIDI_SUBCLASS) {
                    found = true;
                }
            }
            if (found) {
                mUsbManager.requestPermission(device, pendingIntent);
                mRequestedDevices.add(device);
            }
        }
        if (mRequestedDevices.isEmpty()) {
            // No USB-MIDI devices are found.
            return false;
        }

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    onRequestDone(context, intent);
                }
            }
        };
        context.registerReceiver(mReceiver, filter);
        return true;
    }

    /**
     * Called when the user accepts or rejects the permission request requested by
     * EnumerateDevices.
     * If all permission requests are responded, this function calls
     * nativeOnUsbMidiDeviceRequestDone with the accessible USB-MIDI devices.
     */
    private void onRequestDone(Context context, Intent intent) {
        UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (!mRequestedDevices.contains(device)) {
            // We are not interested in the device.
            return;
        }
        mRequestedDevices.remove(device);
        if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            // The request was rejected.
            device = null;
        }
        if (device != null) {
            // Now we can add the device.
            mDevices.add(new UsbMidiDeviceAndroid(mUsbManager, device));
        }
        if (mRequestedDevices.isEmpty()) {
            // All requests are done.
            context.unregisterReceiver(mReceiver);
            if (mNativePointer != 0) {
                nativeOnUsbMidiDeviceRequestDone(mNativePointer, mDevices.toArray());
            }
        }
    }

    /**
     * Disconnects the native object.
     */
    @CalledByNative
    void close() {
        mNativePointer = 0;
    }

    private static native void nativeOnUsbMidiDeviceRequestDone(
            long nativeUsbMidiDeviceFactoryAndroid, Object[] devices);
}

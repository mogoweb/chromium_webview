// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Owned by its native counterpart declared in usb_midi_device_android.h.
 * Refer to that class for general comments.
 */
@JNINamespace("media")
class UsbMidiDeviceAndroid {
    /**
     * A connection handle for this device.
     */
    private UsbDeviceConnection mConnection;

    /**
     * A map from endpoint number to UsbEndpoint.
     */
    private final Map<Integer, UsbEndpoint> mEndpointMap;

    /**
     * A map from UsbEndpoint to UsbRequest associated to it.
     */
    private final Map<UsbEndpoint, UsbRequest> mRequestMap;

    /**
     * Audio interface subclass code for MIDI.
     */
    static final int MIDI_SUBCLASS = 3;

    /**
     * Constructs a UsbMidiDeviceAndroid.
     * @param manager
     * @param device The USB device which this object is assocated with.
     */
    UsbMidiDeviceAndroid(UsbManager manager, UsbDevice device) {
        mConnection = manager.openDevice(device);
        mEndpointMap = new HashMap<Integer, UsbEndpoint>();
        mRequestMap = new HashMap<UsbEndpoint, UsbRequest>();

        for (int i = 0; i < device.getInterfaceCount(); ++i) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() != UsbConstants.USB_CLASS_AUDIO ||
                iface.getInterfaceSubclass() != MIDI_SUBCLASS) {
                continue;
            }
            mConnection.claimInterface(iface, true);
            for (int j = 0; j < iface.getEndpointCount(); ++j) {
                UsbEndpoint endpoint = iface.getEndpoint(j);
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    mEndpointMap.put(endpoint.getEndpointNumber(), endpoint);
                }
            }
        }
    }

    /**
     * Sends a USB-MIDI data to the device.
     * @param endpointNumber The endpoint number of the destination endpoint.
     * @param bs The data to be sent.
     */
    @CalledByNative
    void send(int endpointNumber, byte[] bs) {
        if (mConnection == null) {
            return;
        }
        if (!mEndpointMap.containsKey(endpointNumber)) {
            return;
        }
        UsbEndpoint endpoint = mEndpointMap.get(endpointNumber);
        UsbRequest request;
        if (mRequestMap.containsKey(endpoint)) {
            request = mRequestMap.get(endpoint);
        } else {
            request = new UsbRequest();
            request.initialize(mConnection, endpoint);
            mRequestMap.put(endpoint, request);
        }
        request.queue(ByteBuffer.wrap(bs), bs.length);
    }

    /**
     * Returns the descriptors bytes of this device.
     * @return The descriptors bytes of this device.
     */
    @CalledByNative
    byte[] getDescriptors() {
        if (mConnection == null) {
            return new byte[0];
        }
        return mConnection.getRawDescriptors();
    }

    /**
     * Closes the device connection.
     */
    @CalledByNative
    void close() {
        mEndpointMap.clear();
        for (UsbRequest request : mRequestMap.values()) {
            request.close();
        }
        mRequestMap.clear();
        if (mConnection != null) {
            mConnection.close();
            mConnection = null;
        }
    }
}

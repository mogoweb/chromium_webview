// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.profiles;

import org.chromium.base.CalledByNative;

/**
 * Wrapper that allows passing a Profile reference around in the Java layer.
 */
public class Profile {

    private int mNativeProfileAndroid;

    private Profile(int nativeProfileAndroid) {
        mNativeProfileAndroid = nativeProfileAndroid;
    }

    public static Profile getLastUsedProfile() {
        return (Profile) nativeGetLastUsedProfile();
    }

    @CalledByNative
    private static Profile create(int nativeProfileAndroid) {
        return new Profile(nativeProfileAndroid);
    }

    @CalledByNative
    private void destroy() {
        mNativeProfileAndroid = 0;
    }

    @CalledByNative
    private int getNativePointer() {
        return mNativeProfileAndroid;
    }

    private static native Object nativeGetLastUsedProfile();
}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * A wrapper of the android MediaDrmCredentialManager
 */
@JNINamespace("content")
public class MediaDrmCredentialManager {

    /**
     * Callback interface for getting notified from credential reset.
     */
    public interface MediaDrmCredentialManagerCallback {
        /**
         * This method will be called when credential reset attempt is done.
         * @param succeeded Whether or not it succeeded.
         */
        @CalledByNative("MediaDrmCredentialManagerCallback")
        public void onCredentialResetFinished(boolean succeeded);
    }

    /**
     * Attempts to reset the DRM credentials.
     * @param callback It notifies whether or not it succeeded.
     */
    public static void resetCredentials(MediaDrmCredentialManagerCallback callback) {
        nativeResetCredentials(callback);
    }

    private static native void nativeResetCredentials(MediaDrmCredentialManagerCallback callback);
}

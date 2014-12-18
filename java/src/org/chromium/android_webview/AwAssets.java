// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.io.IOException;

/**
 * A utility class to retrieve references to uncompressed assets insides the apk. A reference is
 * defined as tuple (file descriptor, offset, size) enabling direct mapping without deflation.
 */
@JNINamespace("android_webview")
public class AwAssets {
    private static final String LOGTAG = "AwAssets";

    @CalledByNative
    public static long[] openAsset(Context context, String fileName) {
        try {
            AssetManager manager = context.getAssets();
            AssetFileDescriptor afd = manager.openFd(fileName);
            return new long[] { afd.getParcelFileDescriptor().detachFd(),
                                afd.getStartOffset(),
                                afd.getLength() };
        } catch (IOException e) {
            Log.e(LOGTAG, "Error while loading asset " + fileName + ": " + e.getMessage());
            return new long[] {-1, -1, -1};
        }
    }
}

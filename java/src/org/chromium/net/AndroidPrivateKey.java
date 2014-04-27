// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Abstract private key that bundles the PrivateKey and AndroidKeyStore that it belongs to.
 */
@JNINamespace("net::android")
public interface AndroidPrivateKey {
    /** @return AndroidKeyStore that handles this key. */
    @CalledByNative
    AndroidKeyStore getKeyStore();
}

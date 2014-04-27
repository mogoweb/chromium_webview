// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.CalledByNative;
import org.chromium.ui.base.ViewAndroid;

class PowerSaveBlocker {
    @CalledByNative
    private static void applyBlock(ViewAndroid view) {
        view.incrementKeepScreenOnCount();
    }

    @CalledByNative
    private static void removeBlock(ViewAndroid view) {
        view.decrementKeepScreenOnCount();
    }
}

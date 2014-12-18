// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import org.chromium.content.browser.ScreenOrientationListener.ScreenOrientationObserver;

/**
 * Mock of an OrientationObserver for tests that need to observe ScreenOrientation changes.
 */
public class MockOrientationObserver implements ScreenOrientationObserver {

    public int mOrientation = -1;
    public boolean mHasChanged = false;

    @Override
    public void onScreenOrientationChanged(int orientation) {
        mOrientation = orientation;
        mHasChanged = true;
    }
}

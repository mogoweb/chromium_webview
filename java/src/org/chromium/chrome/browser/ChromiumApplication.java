// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.CalledByNative;
import org.chromium.content.app.ContentApplication;

/**
 * Basic application functionality that should be shared among all browser applications that use
 * chrome layer.
 */
public abstract class ChromiumApplication extends ContentApplication {
    /**
     * Opens a protected content settings page, if available.
     */
    @CalledByNative
    protected abstract void openProtectedContentSettings();

    @CalledByNative
    protected abstract void showSyncSettings();

    @CalledByNative
    protected abstract void showTermsOfServiceDialog();
}

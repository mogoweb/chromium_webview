// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.CalledByNative;

/**
 * Watches for when Chrome is told to restart itself.
 */
public class ApplicationLifetime {
    public interface Observer {
        void onTerminate(boolean restart);
    }
    private static Observer sObserver = null;

    /**
     * Sets the observer that monitors for ApplicationLifecycle events.
     * We only allow one observer to be set to avoid race conditions for shutdown events.
     */
    public static void setObserver(Observer observer) {
        assert sObserver == null;
        sObserver = observer;
    }

    /**
     * Removes whatever observer is currently watching this class.
     */
    public static void removeObserver() {
        sObserver = null;
    }

    @CalledByNative
    public static void terminate(boolean restart) {
        if (sObserver != null)
            sObserver.onTerminate(restart);
    }
}

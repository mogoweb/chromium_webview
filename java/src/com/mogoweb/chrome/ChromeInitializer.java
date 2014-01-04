// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome;

import android.content.Context;

import com.mogoweb.chrome.impl.ChromeInitializerImpl;

/**
 * Chromium setup chores.
 */
public class ChromeInitializer {
    /**
     * The entry point to the initialization process.
     *
     * @param context Android context for the application using ChromeView
     */
    public static void initialize(Context context) {
        ChromeInitializerImpl.initialize(context);
    }
}

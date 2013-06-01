// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.shell;

import android.app.Application;

import com.mogoweb.chrome.ChromeInitializer;

public class ShellApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ChromeInitializer.initialize(this);
    }
}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.app.Application;

/**
 * Basic application functionality that should be shared among all browser applications.
 */
public class BaseChromiumApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ActivityStatus.initialize(this);
    }

}

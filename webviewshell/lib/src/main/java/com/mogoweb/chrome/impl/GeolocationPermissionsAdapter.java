// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogoweb.chrome.impl;

import java.util.Set;

import org.chromium.android_webview.AwGeolocationPermissions;

import android.webkit.ValueCallback;

import com.mogoweb.chrome.GeolocationPermissions;

/**
 * Chromium implementation of GeolocationPermissions -- forwards calls to the
 * chromium internal implementation.
 */
final class GeolocationPermissionsAdapter extends GeolocationPermissions {

    private AwGeolocationPermissions mChromeGeolocationPermissions;

    public GeolocationPermissionsAdapter(AwGeolocationPermissions chromeGeolocationPermissions) {
        mChromeGeolocationPermissions = chromeGeolocationPermissions;
    }

    @Override
    public void allow(String origin) {
        mChromeGeolocationPermissions.allow(origin);
    }

    @Override
    public void clear(String origin) {
        mChromeGeolocationPermissions.clear(origin);
    }

    @Override
    public void clearAll() {
        mChromeGeolocationPermissions.clearAll();
    }

    @Override
    public void getAllowed(String origin, ValueCallback<Boolean> callback) {
        mChromeGeolocationPermissions.getAllowed(origin, callback);
    }

    @Override
    public void getOrigins(ValueCallback<Set<String>> callback) {
        mChromeGeolocationPermissions.getOrigins(callback);
    }
}

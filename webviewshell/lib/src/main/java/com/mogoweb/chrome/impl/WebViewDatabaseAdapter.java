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

import org.chromium.android_webview.AwFormDatabase;
import org.chromium.android_webview.HttpAuthDatabase;

import com.mogoweb.chrome.WebViewDatabase;

/**
 * Chromium implementation of WebViewDatabase -- forwards calls to the
 * chromium internal implementation.
 */
final class WebViewDatabaseAdapter extends WebViewDatabase {

    private AwFormDatabase mFormDatabase;
    private HttpAuthDatabase mHttpAuthDatabase;

    public WebViewDatabaseAdapter(AwFormDatabase formDatabase, HttpAuthDatabase httpAuthDatabase) {
        mFormDatabase = formDatabase;
        mHttpAuthDatabase = httpAuthDatabase;
    }

    @Override
    public boolean hasUsernamePassword() {
        // This is a deprecated API: intentional no-op.
        return false;
    }

    @Override
    public void clearUsernamePassword() {
        // This is a deprecated API: intentional no-op.
    }

    @Override
    public boolean hasHttpAuthUsernamePassword() {
        return mHttpAuthDatabase.hasHttpAuthUsernamePassword();
    }

    @Override
    public void clearHttpAuthUsernamePassword() {
        mHttpAuthDatabase.clearHttpAuthUsernamePassword();
    }

    @Override
    public boolean hasFormData() {
        return mFormDatabase.hasFormData();
    }

    @Override
    public void clearFormData() {
        mFormDatabase.clearFormData();
    }
}

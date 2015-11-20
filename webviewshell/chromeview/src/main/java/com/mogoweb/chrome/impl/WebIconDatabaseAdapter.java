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

import org.chromium.android_webview.AwContents;

import android.content.ContentResolver;

import com.mogoweb.chrome.WebIconDatabase;
import com.mogoweb.chrome.WebIconDatabase.IconListener;

/**
 * Chromium implementation of WebIconDatabase -- big old no-op (base class is deprecated).
 */
final class WebIconDatabaseAdapter extends WebIconDatabase {
    @Override
    public void open(String path) {
        AwContents.setShouldDownloadFavicons();
    }

    @Override
    public void close() {
        // Intentional no-op.
    }

    @Override
    public void removeAllIcons() {
        // Intentional no-op: we have no database so nothing to remove.
    }

    @Override
    public void requestIconForPageUrl(String url, IconListener listener) {
        // Intentional no-op.
    }

    @Override
    public void bulkRequestIconForPageUrl(ContentResolver cr, String where,
            IconListener listener) {
        // Intentional no-op: hidden in base class.
    }

    @Override
    public void retainIconForPageUrl(String url) {
        // Intentional no-op.
    }

    @Override
    public void releaseIconForPageUrl(String url) {
        // Intentional no-op.
    }
}

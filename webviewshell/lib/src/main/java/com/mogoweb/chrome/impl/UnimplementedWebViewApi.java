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

import android.util.Log;

// TODO: remove this when all WebView APIs have been implemented.
public class UnimplementedWebViewApi {
    private static String TAG = "UnimplementedWebViewApi";

    private static class UnimplementedWebViewApiException extends UnsupportedOperationException {
        public UnimplementedWebViewApiException() {
            super();
        }
    }

    private static boolean THROW = false;
    // By default we keep the traces down to one frame to reduce noise, but for debugging it might
    // be useful to set this to true.
    private static boolean FULL_TRACE = false;

    public static void invoke() throws UnimplementedWebViewApiException {
        if (THROW) {
            throw new UnimplementedWebViewApiException();
        } else {
            if (FULL_TRACE) {
                Log.w(TAG, "Unimplemented WebView method called in: " +
                      Log.getStackTraceString(new Throwable()));
            } else {
                StackTraceElement[] trace = new Throwable().getStackTrace();
                // The stack trace [0] index is this method (invoke()).
                StackTraceElement unimplementedMethod = trace[1];
                StackTraceElement caller = trace[2];
                Log.w(TAG, "Unimplemented WebView method " + unimplementedMethod.getMethodName() +
                        " called from: " + caller.toString());
            }
        }
    }

}

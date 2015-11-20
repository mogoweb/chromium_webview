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

import org.chromium.android_webview.AwCookieManager;

import android.net.ParseException;
import android.util.Log;

import com.mogoweb.chrome.CookieManager;

public class CookieManagerAdapter extends CookieManager {

    private static final String LOGTAG = "CookieManager";

    AwCookieManager mChromeCookieManager;

    public CookieManagerAdapter(AwCookieManager chromeCookieManager) {
        mChromeCookieManager = chromeCookieManager;
    }

    @Override
    public synchronized void setAcceptCookie(boolean accept) {
        mChromeCookieManager.setAcceptCookie(accept);
    }

    @Override
    public synchronized boolean acceptCookie() {
        return mChromeCookieManager.acceptCookie();
    }

    @Override
    public void setCookie(String url, String value) {
        try {
            mChromeCookieManager.setCookie(fixupUrl(url), value);
        } catch (ParseException e) {
            Log.e(LOGTAG, "Not setting cookie due to error parsing URL: " + url, e);
        }
    }

    @Override
    public String getCookie(String url) {
        try {
            return mChromeCookieManager.getCookie(fixupUrl(url));
        } catch (ParseException e) {
            Log.e(LOGTAG, "Unable to get cookies due to error parsing URL: " + url, e);
            return null;
        }
    }

    @Override
    public String getCookie(String url, boolean privateBrowsing) {
        return getCookie(url);
    }

//    @Override
//    public synchronized String getCookie(WebAddress uri) {
//        return mChromeCookieManager.getCookie(uri.toString());
//    }

    @Override
    public void removeSessionCookie() {
        mChromeCookieManager.removeSessionCookie();
    }

    @Override
    public void removeAllCookie() {
        mChromeCookieManager.removeAllCookie();
    }

    @Override
    public synchronized boolean hasCookies() {
        return mChromeCookieManager.hasCookies();
    }

    @Override
    public synchronized boolean hasCookies(boolean privateBrowsing) {
        return mChromeCookieManager.hasCookies();
    }

    @Override
    public void removeExpiredCookie() {
        mChromeCookieManager.removeExpiredCookie();
    }

    @Override
    protected void flushCookieStore() {
        mChromeCookieManager.flushCookieStore();
    }

    @Override
    protected boolean allowFileSchemeCookiesImpl() {
        return mChromeCookieManager.allowFileSchemeCookies();
    }

    @Override
    protected void setAcceptFileSchemeCookiesImpl(boolean accept) {
        mChromeCookieManager.setAcceptFileSchemeCookies(accept);
    }

    private static String fixupUrl(String url) throws ParseException {
        // WebAddress is a private API in the android framework and a "quirk"
        // of the Classic WebView implementation that allowed embedders to
        // be relaxed about what URLs they passed into the CookieManager, so we
        // do the same normalisation before entering the chromium stack.
// TODO(alex): fixupUrl
//        return new WebAddress(url).toString();
        return url;
    }

}

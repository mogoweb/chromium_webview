/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.mogoweb.chrome;

import org.chromium.android_webview.AwCookieManager;

import android.webkit.CookieSyncManager;

/**
 * Manages the cookies used by an application's {@link WebView} instances.
 * Cookies are manipulated according to RFC2109.
 */
public class CookieManager {
    /** The class that's doing all the work. */
    private AwCookieManager mAwCookieManager;

    private static CookieManager sInstance = null;

    private CookieManager() {
        mAwCookieManager = new AwCookieManager();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("doesn't implement Cloneable");
    }

    /**
     * Gets the singleton CookieManager instance. If this method is used
     * before the application instantiates a {@link WebView} instance,
     * {@link CookieSyncManager#createInstance CookieSyncManager.createInstance(Context)}
     * must be called first.
     *
     * @return the singleton CookieManager instance
     */
    public static synchronized CookieManager getInstance() {
        if (sInstance == null) {
            sInstance = new CookieManager();
        }
        return sInstance;
    }

    /**
     * Sets whether the application's {@link WebView} instances should send and
     * accept cookies.
     *
     * @param accept whether {@link WebView} instances should send and accept
     *               cookies
     */
    public synchronized void setAcceptCookie(boolean accept) {
        mAwCookieManager.setAcceptCookie(accept);
    }

    /**
     * Gets whether the application's {@link WebView} instances send and accept
     * cookies.
     *
     * @return true if {@link WebView} instances send and accept cookies
     */
    public synchronized boolean acceptCookie() {
        return mAwCookieManager.acceptCookie();
    }

    /**
    * Sets a cookie for the given URL. Any existing cookie with the same host,
    * path and name will be replaced with the new cookie. The cookie being set
    * must not have expired and must not be a session cookie, otherwise it
    * will be ignored.
    *
    * @param url the URL for which the cookie is set
    * @param value the cookie as a string, using the format of the 'Set-Cookie'
    *              HTTP response header
    */
   public void setCookie(String url, String value) {
       mAwCookieManager.setCookie(url, value);
   }

   /**
    * Gets the cookies for the given URL.
    *
    * @param url the URL for which the cookies are requested
    * @return value the cookies as a string, using the format of the 'Cookie'
    *               HTTP request header
    */
   public String getCookie(String url) {
       return mAwCookieManager.getCookie(url);
   }

   /**
    * Removes all session cookies, which are cookies without an expiration
    * date.
    */
   public void removeSessionCookie() {
       mAwCookieManager.removeSessionCookie();
   }

   /**
    * Removes all cookies.
    */
   public void removeAllCookie() {
       mAwCookieManager.removeAllCookie();
   }

   /**
    * Gets whether there are stored cookies.
    *
    * @return true if there are stored cookies
    */
   public synchronized boolean hasCookies() {
       return mAwCookieManager.hasCookies();
   }

   /**
    * Removes all expired cookies.
    */
   public void removeExpiredCookie() {
       mAwCookieManager.removeExpiredCookie();
   }

   /**
    * Gets whether the application's {@link WebView} instances send and accept
    * cookies for file scheme URLs.
    *
    * @return true if {@link WebView} instances send and accept cookies for
    *         file scheme URLs
    */
   // Static for backward compatibility.
   public static boolean allowFileSchemeCookies() {
       return getInstance().allowFileSchemeCookiesImpl();
   }

   /**
    * Sets whether the application's {@link WebView} instances should send and
    * accept cookies for file scheme URLs.
    * Use of cookies with file scheme URLs is potentially insecure. Do not use
    * this feature unless you can be sure that no unintentional sharing of
    * cookie data can take place.
    * <p>
    * Note that calls to this method will have no effect if made after a
    * {@link WebView} or CookieManager instance has been created.
    */
   // Static for backward compatibility.
   public static void setAcceptFileSchemeCookies(boolean accept) {
       getInstance().setAcceptFileSchemeCookiesImpl(accept);
   }

   private boolean allowFileSchemeCookiesImpl() {
       return mAwCookieManager.allowFileSchemeCookies();
   }

   private void setAcceptFileSchemeCookiesImpl(boolean accept) {
       mAwCookieManager.setAcceptFileSchemeCookies(accept);
   }
}

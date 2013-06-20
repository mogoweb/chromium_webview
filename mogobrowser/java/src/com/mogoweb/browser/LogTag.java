/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.mogoweb.browser;

import android.util.EventLog;

public class LogTag {

    /**
     * Log when the user is adding a new bookmark.
     *
     * @param url the url of the new bookmark.
     * @param where the location from where the bookmark was added
     */
    public static void logBookmarkAdded(String url, String where) {
//        EventLog.writeEvent(EventLogTags.BROWSER_BOOKMARK_ADDED, url + "|"
//            + where);
    }

    /**
     * Log when a page has finished loading with how much
     * time the browser used to load the page.
     *
     * Note that a redirect will restart the timer, so this time is not
     * always how long it takes for the user to load a page.
     *
     * @param url the url of that page that finished loading.
     * @param duration the time the browser spent loading the page.
     */
    public static void logPageFinishedLoading(String url, long duration) {
//        EventLog.writeEvent(EventLogTags.BROWSER_PAGE_LOADED, url + "|"
//            + duration);
    }

    /**
     * log the time the user has spent on a webpage
     *
     * @param url the url of the page that is being logged (old page).
     * @param duration the time spent on the webpage.
     */
    public static void logTimeOnPage(String url, long duration) {
//        EventLog.writeEvent(EventLogTags.BROWSER_TIMEONPAGE, url + "|"
//            + duration);
    }
}

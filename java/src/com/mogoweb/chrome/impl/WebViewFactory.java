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

import android.os.StrictMode;
import android.util.AndroidRuntimeException;
import android.util.Log;

/**
 * Top level factory, used creating all the main WebView implementation classes.
 *
 * @hide
 */
public final class WebViewFactory {

    private static final String CHROMIUM_WEBVIEW_FACTORY =
            "com.mogoweb.chrome.impl.WebViewChromiumFactoryProvider";

    private static final String LOGTAG = "WebViewFactory";

    private static final boolean DEBUG = true;

    private static class Preloader {
        static WebViewFactoryProvider sPreloadedProvider;
        static {
            try {
                sPreloadedProvider = getFactoryClass().newInstance();
            } catch (Exception e) {
                Log.w(LOGTAG, "error preloading provider", e);
            }

            org.chromium.content.R.attr.select_dialog_multichoice = com.mogoweb.chrome.R.attr.select_dialog_multichoice;
            org.chromium.content.R.attr.select_dialog_singlechoice = com.mogoweb.chrome.R.attr.select_dialog_singlechoice;
            org.chromium.content.R.dimen.link_preview_overlay_radius = com.mogoweb.chrome.R.dimen.link_preview_overlay_radius;
            org.chromium.content.R.drawable.ondemand_overlay = com.mogoweb.chrome.R.drawable.ondemand_overlay;
            org.chromium.content.R.id.ampm = com.mogoweb.chrome.R.id.ampm;
            org.chromium.content.R.id.arrow_image = com.mogoweb.chrome.R.id.arrow_image;
            org.chromium.content.R.id.date_picker = com.mogoweb.chrome.R.id.date_picker;
            org.chromium.content.R.id.date_time_suggestion_value = com.mogoweb.chrome.R.id.date_time_suggestion_value;
            org.chromium.content.R.id.hour = com.mogoweb.chrome.R.id.hour;
            org.chromium.content.R.id.main_text = com.mogoweb.chrome.R.id.main_text;
            org.chromium.content.R.id.milli = com.mogoweb.chrome.R.id.milli;
            org.chromium.content.R.id.minute = com.mogoweb.chrome.R.id.minute;
            org.chromium.content.R.id.pickers = com.mogoweb.chrome.R.id.pickers;
            org.chromium.content.R.id.position_in_year = com.mogoweb.chrome.R.id.position_in_year;
            org.chromium.content.R.id.second = com.mogoweb.chrome.R.id.second;
            org.chromium.content.R.id.second_colon = com.mogoweb.chrome.R.id.second_colon;
            org.chromium.content.R.id.second_dot = com.mogoweb.chrome.R.id.second_dot;
            org.chromium.content.R.id.select_action_menu_select_all = com.mogoweb.chrome.R.id.select_action_menu_select_all;
            org.chromium.content.R.id.select_action_menu_cut = com.mogoweb.chrome.R.id.select_action_menu_cut;
            org.chromium.content.R.id.select_action_menu_copy = com.mogoweb.chrome.R.id.select_action_menu_copy;
            org.chromium.content.R.id.select_action_menu_paste = com.mogoweb.chrome.R.id.select_action_menu_paste;
            org.chromium.content.R.id.select_action_menu_share = com.mogoweb.chrome.R.id.select_action_menu_share;
            org.chromium.content.R.id.select_action_menu_web_search = com.mogoweb.chrome.R.id.select_action_menu_web_search;
            org.chromium.content.R.id.sub_text = com.mogoweb.chrome.R.id.sub_text;
            org.chromium.content.R.id.time_picker = com.mogoweb.chrome.R.id.time_picker;
            org.chromium.content.R.id.year = com.mogoweb.chrome.R.id.year;
            org.chromium.content.R.layout.date_time_picker_dialog = com.mogoweb.chrome.R.layout.date_time_picker_dialog;
            org.chromium.content.R.layout.date_time_suggestion = com.mogoweb.chrome.R.layout.date_time_suggestion;
            org.chromium.content.R.layout.two_field_date_picker = com.mogoweb.chrome.R.layout.two_field_date_picker;
            org.chromium.content.R.layout.multi_field_time_picker_dialog = com.mogoweb.chrome.R.layout.multi_field_time_picker_dialog;
            org.chromium.content.R.layout.validation_message_bubble = com.mogoweb.chrome.R.layout.validation_message_bubble;
            org.chromium.content.R.menu.select_action_menu = com.mogoweb.chrome.R.menu.select_action_menu;
            org.chromium.content.R.string.accessibility_content_view = com.mogoweb.chrome.R.string.accessibility_content_view;
            org.chromium.content.R.string.accessibility_date_picker_month = com.mogoweb.chrome.R.string.accessibility_date_picker_month;
            org.chromium.content.R.string.accessibility_date_picker_week = com.mogoweb.chrome.R.string.accessibility_date_picker_week;
            org.chromium.content.R.string.accessibility_date_picker_year = com.mogoweb.chrome.R.string.accessibility_date_picker_year;
            org.chromium.content.R.string.accessibility_datetime_picker_date = com.mogoweb.chrome.R.string.accessibility_datetime_picker_date;
            org.chromium.content.R.string.accessibility_datetime_picker_time = com.mogoweb.chrome.R.string.accessibility_datetime_picker_time;
            org.chromium.content.R.string.actionbar_share = com.mogoweb.chrome.R.string.actionbar_share;
            org.chromium.content.R.string.actionbar_web_search = com.mogoweb.chrome.R.string.actionbar_web_search;
            org.chromium.content.R.string.date_picker_dialog_clear = com.mogoweb.chrome.R.string.date_picker_dialog_clear;
            org.chromium.content.R.string.date_picker_dialog_set = com.mogoweb.chrome.R.string.date_picker_dialog_set;
            org.chromium.content.R.string.date_picker_dialog_title = com.mogoweb.chrome.R.string.date_picker_dialog_title;
            org.chromium.content.R.string.date_picker_dialog_other_button_label = com.mogoweb.chrome.R.string.date_picker_dialog_other_button_label;
            org.chromium.content.R.string.date_time_picker_dialog_title = com.mogoweb.chrome.R.string.date_time_picker_dialog_title;
            org.chromium.content.R.string.media_player_error_button = com.mogoweb.chrome.R.string.media_player_error_button;
            org.chromium.content.R.string.media_player_error_text_invalid_progressive_playback = com.mogoweb.chrome.R.string.media_player_error_text_invalid_progressive_playback;
            org.chromium.content.R.string.media_player_error_text_unknown = com.mogoweb.chrome.R.string.media_player_error_text_unknown;
            org.chromium.content.R.string.media_player_error_title = com.mogoweb.chrome.R.string.media_player_error_title;
            org.chromium.content.R.string.media_player_loading_video = com.mogoweb.chrome.R.string.media_player_loading_video;
            org.chromium.content.R.string.month_picker_dialog_title = com.mogoweb.chrome.R.string.month_picker_dialog_title;
            org.chromium.content.R.string.profiler_error_toast = com.mogoweb.chrome.R.string.profiler_error_toast;
            org.chromium.content.R.string.profiler_no_storage_toast = com.mogoweb.chrome.R.string.profiler_no_storage_toast;
            org.chromium.content.R.string.profiler_started_toast = com.mogoweb.chrome.R.string.profiler_started_toast;
            org.chromium.content.R.string.profiler_stopped_toast = com.mogoweb.chrome.R.string.profiler_stopped_toast;
            org.chromium.content.R.string.time_picker_dialog_am = com.mogoweb.chrome.R.string.time_picker_dialog_am;
            org.chromium.content.R.string.time_picker_dialog_hour_minute_separator = com.mogoweb.chrome.R.string.time_picker_dialog_hour_minute_separator;
            org.chromium.content.R.string.time_picker_dialog_minute_second_separator = com.mogoweb.chrome.R.string.time_picker_dialog_minute_second_separator;
            org.chromium.content.R.string.time_picker_dialog_second_subsecond_separator = com.mogoweb.chrome.R.string.time_picker_dialog_second_subsecond_separator;
            org.chromium.content.R.string.time_picker_dialog_pm = com.mogoweb.chrome.R.string.time_picker_dialog_pm;
            org.chromium.content.R.string.time_picker_dialog_title = com.mogoweb.chrome.R.string.time_picker_dialog_title;
            org.chromium.content.R.string.week_picker_dialog_title = com.mogoweb.chrome.R.string.week_picker_dialog_title;
            org.chromium.content.R.style.SelectPopupDialog = com.mogoweb.chrome.R.style.SelectPopupDialog;
        }
    }

    // Cache the factory both for efficiency, and ensure any one process gets all webviews from the
    // same provider.
    private static WebViewFactoryProvider sProviderInstance;
    private static final Object sProviderLock = new Object();

    public static WebViewFactoryProvider getProvider() {
        synchronized (sProviderLock) {
            // For now the main purpose of this function (and the factory abstraction) is to keep
            // us honest and minimize usage of WebView internals when binding the proxy.
            if (sProviderInstance != null) return sProviderInstance;

            Class<WebViewFactoryProvider> providerClass;
            try {
                providerClass = getFactoryClass();
            } catch (ClassNotFoundException e) {
                Log.e(LOGTAG, "error loading provider", e);
                throw new AndroidRuntimeException(e);
            }

            // This implicitly loads Preloader even if it wasn't preloaded at boot.
            if (Preloader.sPreloadedProvider != null &&
                Preloader.sPreloadedProvider.getClass() == providerClass) {
                sProviderInstance = Preloader.sPreloadedProvider;
                if (DEBUG) Log.v(LOGTAG, "Using preloaded provider: " + sProviderInstance);
                return sProviderInstance;
            }

            // The preloaded provider isn't the one we wanted; construct our own.
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
            try {
                sProviderInstance = providerClass.newInstance();
                if (DEBUG) Log.v(LOGTAG, "Loaded provider: " + sProviderInstance);
                return sProviderInstance;
            } catch (Exception e) {
                Log.e(LOGTAG, "error instantiating provider", e);
                throw new AndroidRuntimeException(e);
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }
    }

    private static Class<WebViewFactoryProvider> getFactoryClass() throws ClassNotFoundException {
        return (Class<WebViewFactoryProvider>) Class.forName(CHROMIUM_WEBVIEW_FACTORY);
    }
}

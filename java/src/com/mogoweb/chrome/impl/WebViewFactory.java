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

import java.io.File;
import java.util.Arrays;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.Trace;
import android.text.TextUtils;
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

    private static final String NULL_WEBVIEW_FACTORY =
            "com.android.webview.nullwebview.NullWebViewFactoryProvider";

    private static final String CHROMIUM_WEBVIEW_NATIVE_RELRO_32 =
            "/data/misc/shared_relro/libwebviewchromium32.relro";
    private static final String CHROMIUM_WEBVIEW_NATIVE_RELRO_64 =
            "/data/misc/shared_relro/libwebviewchromium64.relro";

    public static final String CHROMIUM_WEBVIEW_VMSIZE_SIZE_PROPERTY =
            "persist.sys.webview.vmsize";
    private static final long CHROMIUM_WEBVIEW_DEFAULT_VMSIZE_BYTES = 100 * 1024 * 1024;

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

            org.chromium.ui.R.string.copy_to_clipboard_failure_message = com.mogoweb.chrome.R.string.copy_to_clipboard_failure_message;
            org.chromium.ui.R.string.low_memory_error = com.mogoweb.chrome.R.string.low_memory_error;
            org.chromium.ui.R.string.opening_file_error = com.mogoweb.chrome.R.string.opening_file_error;
            org.chromium.ui.R.string.color_picker_button_more = com.mogoweb.chrome.R.string.color_picker_button_more;
            org.chromium.ui.R.string.color_picker_hue = com.mogoweb.chrome.R.string.color_picker_hue;
            org.chromium.ui.R.string.color_picker_saturation = com.mogoweb.chrome.R.string.color_picker_saturation;
            org.chromium.ui.R.string.color_picker_value = com.mogoweb.chrome.R.string.color_picker_value;
            org.chromium.ui.R.string.color_picker_button_set = com.mogoweb.chrome.R.string.color_picker_button_set;
            org.chromium.ui.R.string.color_picker_button_cancel = com.mogoweb.chrome.R.string.color_picker_button_cancel;
            org.chromium.ui.R.string.color_picker_dialog_title = com.mogoweb.chrome.R.string.color_picker_dialog_title;
            org.chromium.ui.R.string.color_picker_button_red = com.mogoweb.chrome.R.string.color_picker_button_red;
            org.chromium.ui.R.string.color_picker_button_cyan = com.mogoweb.chrome.R.string.color_picker_button_cyan;
            org.chromium.ui.R.string.color_picker_button_blue = com.mogoweb.chrome.R.string.color_picker_button_blue;
            org.chromium.ui.R.string.color_picker_button_green = com.mogoweb.chrome.R.string.color_picker_button_green;
            org.chromium.ui.R.string.color_picker_button_magenta = com.mogoweb.chrome.R.string.color_picker_button_magenta;
            org.chromium.ui.R.string.color_picker_button_yellow = com.mogoweb.chrome.R.string.color_picker_button_yellow;
            org.chromium.ui.R.string.color_picker_button_black = com.mogoweb.chrome.R.string.color_picker_button_black;
            org.chromium.ui.R.string.color_picker_button_white = com.mogoweb.chrome.R.string.color_picker_button_white;
//            org.chromium.ui.R.id.autofill_label = com.mogoweb.chrome.R.id.autofill_label;
//            org.chromium.ui.R.id.autofill_popup_window = com.mogoweb.chrome.R.id.autofill_popup_window;
//            org.chromium.ui.R.id.autofill_sublabel = com.mogoweb.chrome.R.id.autofill_sublabel;
            org.chromium.ui.R.id.selected_color_view = com.mogoweb.chrome.R.id.selected_color_view;
            org.chromium.ui.R.id.title = com.mogoweb.chrome.R.id.title;
            org.chromium.ui.R.id.more_colors_button = com.mogoweb.chrome.R.id.more_colors_button;
            org.chromium.ui.R.id.color_picker_advanced = com.mogoweb.chrome.R.id.color_picker_advanced;
            org.chromium.ui.R.id.color_picker_simple = com.mogoweb.chrome.R.id.color_picker_simple;
            org.chromium.ui.R.id.color_button_swatch = com.mogoweb.chrome.R.id.color_button_swatch;
            org.chromium.ui.R.id.more_colors_button_border = com.mogoweb.chrome.R.id.more_colors_button_border;
            org.chromium.ui.R.id.gradient= com.mogoweb.chrome.R.id.gradient;
            org.chromium.ui.R.id.text = com.mogoweb.chrome.R.id.text;
            org.chromium.ui.R.id.seek_bar = com.mogoweb.chrome.R.id.seek_bar;
//            org.chromium.ui.R.layout.autofill_text = com.mogoweb.chrome.R.layout.autofill_text;
            org.chromium.ui.R.layout.color_picker_dialog_title = com.mogoweb.chrome.R.layout.color_picker_dialog_title;
            org.chromium.ui.R.layout.color_picker_dialog_content = com.mogoweb.chrome.R.layout.color_picker_dialog_content;
            org.chromium.ui.R.layout.color_picker_advanced_component = com.mogoweb.chrome.R.layout.color_picker_advanced_component;
            org.chromium.ui.R.drawable.color_button_background = com.mogoweb.chrome.R.drawable.color_button_background;
            org.chromium.ui.R.drawable.color_picker_advanced_select_handle = com.mogoweb.chrome.R.drawable.color_picker_advanced_select_handle;
//            org.chromium.ui.R.style.AutofillPopupWindow = com.mogoweb.chrome.R.style.AutofillPopupWindow;
//            org.chromium.ui.R.color.autofill_dark_divider_color = com.mogoweb.chrome.R.color.autofill_dark_divider_color;
//            org.chromium.ui.R.color.autofill_divider_color = com.mogoweb.chrome.R.color.autofill_divider_color;
            org.chromium.ui.R.color.color_picker_border_color = com.mogoweb.chrome.R.color.color_picker_border_color;
//            org.chromium.ui.R.dimen.autofill_text_height = com.mogoweb.chrome.R.dimen.autofill_text_height;
//            org.chromium.ui.R.dimen.autofill_text_divider_height = com.mogoweb.chrome.R.dimen.autofill_text_divider_height;
            org.chromium.ui.R.dimen.color_button_height = com.mogoweb.chrome.R.dimen.color_button_height;
        }
    }

    // Cache the factory both for efficiency, and ensure any one process gets all webviews from the
    // same provider.
    private static WebViewFactoryProvider sProviderInstance;
    private static final Object sProviderLock = new Object();
    private static boolean sAddressSpaceReserved = false;
    private static PackageInfo sPackageInfo;

    public static String getWebViewPackageName() {
//        return AppGlobals.getInitialApplication().getString(
//                com.android.internal.R.string.config_webViewPackageName);
        return "com.android.webview";
    }

    public static PackageInfo getLoadedPackageInfo() {
        return sPackageInfo;
    }

    public static WebViewFactoryProvider getProvider() {
        synchronized (sProviderLock) {
            // For now the main purpose of this function (and the factory abstraction) is to keep
            // us honest and minimize usage of WebView internals when binding the proxy.
            if (sProviderInstance != null) return sProviderInstance;

//            Trace.traceBegin(Trace.TRACE_TAG_WEBVIEW, "WebViewFactory.getProvider()");
            try {
//                Trace.traceBegin(Trace.TRACE_TAG_WEBVIEW, "WebViewFactory.loadNativeLibrary()");
                loadNativeLibrary();
//                Trace.traceEnd(Trace.TRACE_TAG_WEBVIEW);

                Class<WebViewFactoryProvider> providerClass;
//                Trace.traceBegin(Trace.TRACE_TAG_WEBVIEW, "WebViewFactory.getFactoryClass()");
                try {
                    providerClass = getFactoryClass();
                } catch (ClassNotFoundException e) {
                    Log.e(LOGTAG, "error loading provider", e);
                    throw new AndroidRuntimeException(e);
                } finally {
//                    Trace.traceEnd(Trace.TRACE_TAG_WEBVIEW);
                }

                StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
//                Trace.traceBegin(Trace.TRACE_TAG_WEBVIEW, "providerClass.newInstance()");
                try {
                    sProviderInstance = providerClass.newInstance();
                    if (DEBUG) Log.v(LOGTAG, "Loaded provider: " + sProviderInstance);
                    return sProviderInstance;
                } catch (Exception e) {
                    Log.e(LOGTAG, "error instantiating provider", e);
                    throw new AndroidRuntimeException(e);
                } finally {
//                    Trace.traceEnd(Trace.TRACE_TAG_WEBVIEW);
                    StrictMode.setThreadPolicy(oldPolicy);
                }
            } finally {
//                Trace.traceEnd(Trace.TRACE_TAG_WEBVIEW);
            }
        }
    }

    private static Class<WebViewFactoryProvider> getFactoryClass() throws ClassNotFoundException {
//        Application initialApplication = AppGlobals.getInitialApplication();
//        try {
//            // First fetch the package info so we can log the webview package version.
//            String packageName = getWebViewPackageName();
//            sPackageInfo = initialApplication.getPackageManager().getPackageInfo(packageName, 0);
//            Log.i(LOGTAG, "Loading " + packageName + " version " + sPackageInfo.versionName +
//                          " (code " + sPackageInfo.versionCode + ")");
//
//            // Construct a package context to load the Java code into the current app.
//            Context webViewContext = initialApplication.createPackageContext(packageName,
//                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
//            initialApplication.getAssets().addAssetPath(
//                    webViewContext.getApplicationInfo().sourceDir);
//            ClassLoader clazzLoader = webViewContext.getClassLoader();
//            Trace.traceBegin(Trace.TRACE_TAG_WEBVIEW, "Class.forName()");
//            try {
//                return (Class<WebViewFactoryProvider>) Class.forName(CHROMIUM_WEBVIEW_FACTORY, true,
//                                                                     clazzLoader);
//            } finally {
//                Trace.traceEnd(Trace.TRACE_TAG_WEBVIEW);
//            }
//        } catch (PackageManager.NameNotFoundException e) {
//            // If the package doesn't exist, then try loading the null WebView instead.
//            // If that succeeds, then this is a device without WebView support; if it fails then
//            // swallow the failure, complain that the real WebView is missing and rethrow the
//            // original exception.
//            try {
//                return (Class<WebViewFactoryProvider>) Class.forName(NULL_WEBVIEW_FACTORY);
//            } catch (ClassNotFoundException e2) {
//                // Ignore.
//            }
//            Log.e(LOGTAG, "Chromium WebView package does not exist", e);
//            throw new AndroidRuntimeException(e);
//        }
        return null;
    }

    /**
     * Perform any WebView loading preparations that must happen in the zygote.
     * Currently, this means allocating address space to load the real JNI library later.
     */
    public static void prepareWebViewInZygote() {
//        try {
//            System.loadLibrary("webviewchromium_loader");
//            long addressSpaceToReserve =
//                    SystemProperties.getLong(CHROMIUM_WEBVIEW_VMSIZE_SIZE_PROPERTY,
//                    CHROMIUM_WEBVIEW_DEFAULT_VMSIZE_BYTES);
//            sAddressSpaceReserved = nativeReserveAddressSpace(addressSpaceToReserve);
//
//            if (sAddressSpaceReserved) {
//                if (DEBUG) {
//                    Log.v(LOGTAG, "address space reserved: " + addressSpaceToReserve + " bytes");
//                }
//            } else {
//                Log.e(LOGTAG, "reserving " + addressSpaceToReserve +
//                        " bytes of address space failed");
//            }
//        } catch (Throwable t) {
//            // Log and discard errors at this stage as we must not crash the zygote.
//            Log.e(LOGTAG, "error preparing native loader", t);
//        }
    }

//    /**
//     * Perform any WebView loading preparations that must happen at boot from the system server,
//     * after the package manager has started or after an update to the webview is installed.
//     * This must be called in the system server.
//     * Currently, this means spawning the child processes which will create the relro files.
//     */
//    public static void prepareWebViewInSystemServer() {
//        String[] nativePaths = null;
//        try {
//            nativePaths = getWebViewNativeLibraryPaths();
//        } catch (Throwable t) {
//            // Log and discard errors at this stage as we must not crash the system server.
//            Log.e(LOGTAG, "error preparing webview native library", t);
//        }
//        prepareWebViewInSystemServer(nativePaths);
//    }
//
//    private static void prepareWebViewInSystemServer(String[] nativeLibraryPaths) {
//        if (DEBUG) Log.v(LOGTAG, "creating relro files");
//
//        // We must always trigger createRelRo regardless of the value of nativeLibraryPaths. Any
//        // unexpected values will be handled there to ensure that we trigger notifying any process
//        // waiting on relreo creation.
//        if (Build.SUPPORTED_32_BIT_ABIS.length > 0) {
//            if (DEBUG) Log.v(LOGTAG, "Create 32 bit relro");
//            createRelroFile(false /* is64Bit */, nativeLibraryPaths);
//        }
//
//        if (Build.SUPPORTED_64_BIT_ABIS.length > 0) {
//            if (DEBUG) Log.v(LOGTAG, "Create 64 bit relro");
//            createRelroFile(true /* is64Bit */, nativeLibraryPaths);
//        }
//    }
//
//    public static void onWebViewUpdateInstalled() {
//        String[] nativeLibs = null;
//        try {
//            nativeLibs = WebViewFactory.getWebViewNativeLibraryPaths();
//            if (nativeLibs != null) {
//                long newVmSize = 0L;
//
//                for (String path : nativeLibs) {
//                    if (DEBUG) Log.d(LOGTAG, "Checking file size of " + path);
//                    if (path == null) continue;
//                    File f = new File(path);
//                    if (f.exists()) {
//                        long length = f.length();
//                        if (length > newVmSize) {
//                            newVmSize = length;
//                        }
//                    }
//                }
//
//                if (DEBUG) {
//                    Log.v(LOGTAG, "Based on library size, need " + newVmSize +
//                            " bytes of address space.");
//                }
//                // The required memory can be larger than the file on disk (due to .bss), and an
//                // upgraded version of the library will likely be larger, so always attempt to
//                // reserve twice as much as we think to allow for the library to grow during this
//                // boot cycle.
//                newVmSize = Math.max(2 * newVmSize, CHROMIUM_WEBVIEW_DEFAULT_VMSIZE_BYTES);
//                Log.d(LOGTAG, "Setting new address space to " + newVmSize);
//                SystemProperties.set(CHROMIUM_WEBVIEW_VMSIZE_SIZE_PROPERTY,
//                        Long.toString(newVmSize));
//            }
//        } catch (Throwable t) {
//            // Log and discard errors at this stage as we must not crash the system server.
//            Log.e(LOGTAG, "error preparing webview native library", t);
//        }
//        prepareWebViewInSystemServer(nativeLibs);
//    }

    private static String[] getWebViewNativeLibraryPaths()
            throws PackageManager.NameNotFoundException {
        final String NATIVE_LIB_FILE_NAME = "libwebviewchromium.so";

//        PackageManager pm = AppGlobals.getInitialApplication().getPackageManager();
//        ApplicationInfo ai = pm.getApplicationInfo(getWebViewPackageName(), 0);
//
//        String path32;
//        String path64;
//        boolean primaryArchIs64bit = VMRuntime.is64BitAbi(ai.primaryCpuAbi);
//        if (!TextUtils.isEmpty(ai.secondaryCpuAbi)) {
//            // Multi-arch case.
//            if (primaryArchIs64bit) {
//                // Primary arch: 64-bit, secondary: 32-bit.
//                path64 = ai.nativeLibraryDir;
//                path32 = ai.secondaryNativeLibraryDir;
//            } else {
//                // Primary arch: 32-bit, secondary: 64-bit.
//                path64 = ai.secondaryNativeLibraryDir;
//                path32 = ai.nativeLibraryDir;
//            }
//        } else if (primaryArchIs64bit) {
//            // Single-arch 64-bit.
//            path64 = ai.nativeLibraryDir;
//            path32 = "";
//        } else {
//            // Single-arch 32-bit.
//            path32 = ai.nativeLibraryDir;
//            path64 = "";
//        }
//        if (!TextUtils.isEmpty(path32)) path32 += "/" + NATIVE_LIB_FILE_NAME;
//        if (!TextUtils.isEmpty(path64)) path64 += "/" + NATIVE_LIB_FILE_NAME;
//        return new String[] { path32, path64 };
        return new String[] {};
    }

//    private static void createRelroFile(final boolean is64Bit, String[] nativeLibraryPaths) {
//        final String abi =
//                is64Bit ? Build.SUPPORTED_64_BIT_ABIS[0] : Build.SUPPORTED_32_BIT_ABIS[0];
//
//        // crashHandler is invoked by the ActivityManagerService when the isolated process crashes.
//        Runnable crashHandler = new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Log.e(LOGTAG, "relro file creator for " + abi + " crashed. Proceeding without");
//                    getUpdateService().notifyRelroCreationCompleted(is64Bit, false);
//                } catch (RemoteException e) {
//                    Log.e(LOGTAG, "Cannot reach WebViewUpdateService. " + e.getMessage());
//                }
//            }
//        };
//
//        try {
//            if (nativeLibraryPaths == null
//                    || nativeLibraryPaths[0] == null || nativeLibraryPaths[1] == null) {
//                throw new IllegalArgumentException(
//                        "Native library paths to the WebView RelRo process must not be null!");
//            }
//            int pid = LocalServices.getService(ActivityManagerInternal.class).startIsolatedProcess(
//                    RelroFileCreator.class.getName(), nativeLibraryPaths, "WebViewLoader-" + abi, abi,
//                    Process.SHARED_RELRO_UID, crashHandler);
//            if (pid <= 0) throw new Exception("Failed to start the relro file creator process");
//        } catch (Throwable t) {
//            // Log and discard errors as we must not crash the system server.
//            Log.e(LOGTAG, "error starting relro file creator for abi " + abi, t);
//            crashHandler.run();
//        }
//    }
//
//    private static class RelroFileCreator {
//        // Called in an unprivileged child process to create the relro file.
//        public static void main(String[] args) {
//            boolean result = false;
//            boolean is64Bit = VMRuntime.getRuntime().is64Bit();
//            try{
//                if (args.length != 2 || args[0] == null || args[1] == null) {
//                    Log.e(LOGTAG, "Invalid RelroFileCreator args: " + Arrays.toString(args));
//                    return;
//                }
//                Log.v(LOGTAG, "RelroFileCreator (64bit = " + is64Bit + "), " +
//                        " 32-bit lib: " + args[0] + ", 64-bit lib: " + args[1]);
//                if (!sAddressSpaceReserved) {
//                    Log.e(LOGTAG, "can't create relro file; address space not reserved");
//                    return;
//                }
//                result = nativeCreateRelroFile(args[0] /* path32 */,
//                                               args[1] /* path64 */,
//                                               CHROMIUM_WEBVIEW_NATIVE_RELRO_32,
//                                               CHROMIUM_WEBVIEW_NATIVE_RELRO_64);
//                if (result && DEBUG) Log.v(LOGTAG, "created relro file");
//            } finally {
//                // We must do our best to always notify the update service, even if something fails.
//                try {
//                    getUpdateService().notifyRelroCreationCompleted(is64Bit, result);
//                } catch (RemoteException e) {
//                    Log.e(LOGTAG, "error notifying update service", e);
//                }
//
//                if (!result) Log.e(LOGTAG, "failed to create relro file");
//
//                // Must explicitly exit or else this process will just sit around after we return.
//                System.exit(0);
//            }
//        }
//    }

    private static void loadNativeLibrary() {
        if (!sAddressSpaceReserved) {
            Log.e(LOGTAG, "can't load with relro file; address space not reserved");
            return;
        }

//        try {
//            getUpdateService().waitForRelroCreationCompleted(VMRuntime.getRuntime().is64Bit());
//        } catch (RemoteException e) {
//            Log.e(LOGTAG, "error waiting for relro creation, proceeding without", e);
//            return;
//        }

        try {
            String[] args = getWebViewNativeLibraryPaths();
            boolean result = nativeLoadWithRelroFile(args[0] /* path32 */,
                                                     args[1] /* path64 */,
                                                     CHROMIUM_WEBVIEW_NATIVE_RELRO_32,
                                                     CHROMIUM_WEBVIEW_NATIVE_RELRO_64);
            if (!result) {
                Log.w(LOGTAG, "failed to load with relro file, proceeding without");
            } else if (DEBUG) {
                Log.v(LOGTAG, "loaded with relro file");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOGTAG, "Failed to list WebView package libraries for loadNativeLibrary", e);
        }
    }

//    private static IWebViewUpdateService getUpdateService() {
//        return IWebViewUpdateService.Stub.asInterface(ServiceManager.getService("webviewupdate"));
//    }

    private static native boolean nativeReserveAddressSpace(long addressSpaceToReserve);
    private static native boolean nativeCreateRelroFile(String lib32, String lib64,
                                                        String relro32, String relro64);
    private static native boolean nativeLoadWithRelroFile(String lib32, String lib64,
                                                          String relro32, String relro64);
}

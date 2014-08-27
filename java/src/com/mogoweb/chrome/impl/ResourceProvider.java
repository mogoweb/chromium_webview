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

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import org.chromium.android_webview.AwResource;

import com.mogoweb.chrome.R;

import java.lang.reflect.Field;

public class ResourceProvider {
    private static boolean sInitialized;

    static void registerResources(Context context) {
        if (sInitialized) {
            return;
        }

        AwResource.setResources(context.getResources());

//        // attr
//        org.chromium.content.R.attr.select_dialog_multichoice =
//                com.android.internal.R.attr.webviewchromium_select_dialog_multichoice;
//        org.chromium.content.R.attr.select_dialog_singlechoice =
//                com.android.internal.R.attr.webviewchromium_select_dialog_singlechoice;
//
//        // color
//        org.chromium.ui.R.color.color_picker_border_color =
//                com.android.internal.R.color.webviewchromium_color_picker_border_color;
//
//        // dimen
//
//        org.chromium.content.R.dimen.link_preview_overlay_radius =
//                com.android.internal.R.dimen.webviewchromium_link_preview_overlay_radius;
//
//        // drawable
//        org.chromium.content.R.drawable.ondemand_overlay =
//                com.android.internal.R.drawable.webviewchromium_ondemand_overlay;
//
//        org.chromium.ui.R.drawable.color_picker_advanced_select_handle =
//                com.android.internal.R.drawable.webviewchromium_color_picker_advanced_select_handle;
//
//        // id
//
//        org.chromium.content.R.id.position_in_year =
//                com.android.internal.R.id.webviewchromium_position_in_year;
//        org.chromium.content.R.id.year = com.android.internal.R.id.webviewchromium_year;
//        org.chromium.content.R.id.pickers = com.android.internal.R.id.webviewchromium_pickers;
//        org.chromium.content.R.id.date_picker =
//                com.android.internal.R.id.webviewchromium_date_picker;
//        org.chromium.content.R.id.select_action_menu_select_all =
//                com.android.internal.R.id.webviewchromium_select_action_menu_select_all;
//        org.chromium.content.R.id.select_action_menu_cut =
//                com.android.internal.R.id.webviewchromium_select_action_menu_cut;
//        org.chromium.content.R.id.select_action_menu_copy =
//                com.android.internal.R.id.webviewchromium_select_action_menu_copy;
//        org.chromium.content.R.id.select_action_menu_paste =
//                com.android.internal.R.id.webviewchromium_select_action_menu_paste;
//        org.chromium.content.R.id.select_action_menu_share =
//                com.android.internal.R.id.webviewchromium_select_action_menu_share;
//        org.chromium.content.R.id.select_action_menu_web_search =
//                com.android.internal.R.id.webviewchromium_select_action_menu_web_search;
//        org.chromium.content.R.id.time_picker =
//                com.android.internal.R.id.webviewchromium_time_picker;
//
//        org.chromium.ui.R.id.selected_color_view =
//                com.android.internal.R.id.webviewchromium_color_picker_selected_color_view;
//        org.chromium.ui.R.id.title =
//                com.android.internal.R.id.webviewchromium_color_picker_title;
//        org.chromium.ui.R.id.more_colors_button =
//                com.android.internal.R.id.webviewchromium_color_picker_more_colors_button;
//        org.chromium.ui.R.id.color_picker_advanced =
//                com.android.internal.R.id.webviewchromium_color_picker_advanced;
//        org.chromium.ui.R.id.color_picker_simple =
//                com.android.internal.R.id.webviewchromium_color_picker_simple;
//        org.chromium.ui.R.id.more_colors_button_border =
//                com.android.internal.R.id.webviewchromium_color_picker_more_colors_button_border;
//        org.chromium.ui.R.id.color_picker_simple_border =
//                com.android.internal.R.id.webviewchromium_color_picker_simple_border;
//        org.chromium.ui.R.id.gradient =
//                com.android.internal.R.id.webviewchromium_color_picker_gradient;
//        org.chromium.ui.R.id.text =
//                com.android.internal.R.id.webviewchromium_color_picker_text;
//        org.chromium.ui.R.id.seek_bar =
//                com.android.internal.R.id.webviewchromium_color_picker_seek_bar;
//        org.chromium.ui.R.id.autofill_label =
//                com.android.internal.R.id.webviewchromium_autofill_label;
//        org.chromium.ui.R.id.autofill_popup_window =
//                com.android.internal.R.id.webviewchromium_autofill_popup_window;
//        org.chromium.ui.R.id.autofill_sublabel =
//                com.android.internal.R.id.webviewchromium_autofill_sublabel;
//
//        // layout
//
//        org.chromium.content.R.layout.date_time_picker_dialog =
//                com.android.internal.R.layout.webviewchromium_date_time_picker_dialog;
//        org.chromium.content.R.layout.two_field_date_picker =
//                com.android.internal.R.layout.webviewchromium_two_field_date_picker;
//
//        org.chromium.ui.R.layout.color_picker_dialog_title =
//                com.android.internal.R.layout.webviewchromium_color_picker_dialog_title;
//        org.chromium.ui.R.layout.color_picker_dialog_content =
//                com.android.internal.R.layout.webviewchromium_color_picker_dialog_content;
//        org.chromium.ui.R.layout.color_picker_advanced_component =
//                com.android.internal.R.layout.webviewchromium_color_picker_advanced_component;
//        org.chromium.ui.R.layout.autofill_text =
//                com.android.internal.R.layout.webviewchromium_autofill_text;
//
//        // menu
//        org.chromium.content.R.menu.select_action_menu =
//                com.android.internal.R.menu.webviewchromium_select_action_menu;
//
//        // string
//
//        org.chromium.content.R.string.accessibility_content_view =
//                com.android.internal.R.string.webviewchromium_accessibility_content_view;
//        org.chromium.content.R.string.accessibility_date_picker_month =
//                com.android.internal.R.string.webviewchromium_accessibility_date_picker_month;
//        org.chromium.content.R.string.accessibility_date_picker_week =
//                com.android.internal.R.string.webviewchromium_accessibility_date_picker_week;
//        org.chromium.content.R.string.accessibility_date_picker_year =
//                com.android.internal.R.string.webviewchromium_accessibility_date_picker_year;
//        org.chromium.content.R.string.accessibility_datetime_picker_date =
//                com.android.internal.R.string.webviewchromium_accessibility_datetime_picker_date;
//        org.chromium.content.R.string.accessibility_datetime_picker_time =
//                com.android.internal.R.string.webviewchromium_accessibility_datetime_picker_time;
//        org.chromium.content.R.string.actionbar_share =
//                com.android.internal.R.string.share;
//        org.chromium.content.R.string.actionbar_web_search =
//                com.android.internal.R.string.websearch;
//        org.chromium.content.R.string.date_picker_dialog_clear =
//                com.android.internal.R.string.webviewchromium_date_picker_dialog_clear;
//        org.chromium.content.R.string.date_picker_dialog_set =
//                com.android.internal.R.string.webviewchromium_date_picker_dialog_set;
//        org.chromium.content.R.string.date_picker_dialog_title =
//                com.android.internal.R.string.webviewchromium_date_picker_dialog_title;
//        org.chromium.content.R.string.date_time_picker_dialog_title =
//                com.android.internal.R.string.webviewchromium_date_time_picker_dialog_title;
//        org.chromium.content.R.string.media_player_error_button =
//                com.android.internal.R.string.webviewchromium_media_player_error_button;
//        org.chromium.content.R.string.media_player_error_text_invalid_progressive_playback =
//                com.android.internal.R.string.webviewchromium_media_player_error_text_invalid_progressive_playback;
//        org.chromium.content.R.string.media_player_error_text_unknown =
//                com.android.internal.R.string.webviewchromium_media_player_error_text_unknown;
//        org.chromium.content.R.string.media_player_error_title =
//                com.android.internal.R.string.webviewchromium_media_player_error_title;
//        org.chromium.content.R.string.media_player_loading_video =
//                com.android.internal.R.string.webviewchromium_media_player_loading_video;
//        org.chromium.content.R.string.month_picker_dialog_title =
//                com.android.internal.R.string.webviewchromium_month_picker_dialog_title;
//        org.chromium.content.R.string.week_picker_dialog_title =
//                com.android.internal.R.string.webviewchromium_week_picker_dialog_title;
//
//        org.chromium.ui.R.string.low_memory_error =
//                com.android.internal.R.string.webviewchromium_low_memory_error;
//        org.chromium.ui.R.string.opening_file_error =
//                com.android.internal.R.string.webviewchromium_opening_file_error;
//        org.chromium.ui.R.string.color_picker_button_more =
//                com.android.internal.R.string.webviewchromium_color_picker_button_more;
//        org.chromium.ui.R.string.color_picker_hue =
//                com.android.internal.R.string.webviewchromium_color_picker_hue;
//        org.chromium.ui.R.string.color_picker_saturation =
//                com.android.internal.R.string.webviewchromium_color_picker_saturation;
//        org.chromium.ui.R.string.color_picker_value =
//                com.android.internal.R.string.webviewchromium_color_picker_value;
//        org.chromium.ui.R.string.color_picker_button_set =
//                com.android.internal.R.string.webviewchromium_color_picker_button_set;
//        org.chromium.ui.R.string.color_picker_button_cancel =
//                com.android.internal.R.string.webviewchromium_color_picker_button_cancel;
//        org.chromium.ui.R.string.color_picker_dialog_title =
//                com.android.internal.R.string.webviewchromium_color_picker_dialog_title;
//
//        // style
//        org.chromium.content.R.style.SelectPopupDialog =
//                com.android.internal.R.style.webviewchromium_SelectPopupDialog;
//        org.chromium.ui.R.style.AutofillPopupWindow =
//                com.android.internal.R.style.webviewchromium_AutofillPopupWindow;
//
//        if (Build.IS_DEBUGGABLE) {
//            // Ensure that we aren't missing any resource mappings.
//            verifyFields(org.chromium.content.R.class);
//            verifyFields(org.chromium.ui.R.class);
//        }

        // Resources needed by android_webview/
        AwResource.setErrorPageResources(R.raw.loaderror, R.raw.nodomain);
        AwResource.setDefaultTextEncoding(R.string.default_encoding);

        sInitialized = true;
    }

    // Verify that all the fields of the inner classes of |R| have a valid mapping.
    // This ensures that if a resource is added upstream, we won't miss providing
    // a mapping downstream.
    private static void verifyFields(Class<?> R) {
        for (Class<?> c : R.getDeclaredClasses()) {
            verifyFields(c);  // recursively check inner classes.
        }

        for (Field f : R.getDeclaredFields()) {
            try {
                if (f.getInt(null) == 0) {
                    throw new RuntimeException("Missing resource mapping for " +
                            R.getName() + "." + f.getName());
                }
            } catch (IllegalAccessException e) { }
        }
    }
}

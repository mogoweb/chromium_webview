// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content;

/**
 * Provide Android internal resources to Chrome's content layer.  This allows
 * classes that access resources via org.chromium.content.R to function properly
 * in webview.  In a normal Chrome build, content resources live in a res folder
 * in the content layer and the org.chromium.content.R class is generated at
 * build time based on these resources.  In webview, resources live in the
 * Android framework and can't be accessed directly from the content layer.
 * Instead, we copy resources needed by content into the Android framework and
 * use this R class to map resources IDs from org.chromium.content.R to
 * com.android.internal.R.
 */
public final class R {
    /** Attributes */
    public static final class attr {
        public static int select_dialog_multichoice;
        public static int select_dialog_singlechoice;
    }
    /** Dimensions */
    public static final class dimen {
        public static int link_preview_overlay_radius;
    }
    /** Drawables */
    public static final class drawable {
        public static int ondemand_overlay;
    }
    /** id */
    public static final class id {
        public static int ampm;
        public static int arrow_image;
        public static int date_picker;
        public static int date_time_suggestion_value;
        public static int date_time_suggestion_label;
        public static int hour;
        public static int main_text;
        public static int milli;
        public static int minute;
        public static int pickers;
        public static int position_in_year;
        public static int second;
        public static int second_colon;
        public static int second_dot;
        public static int select_action_menu_select_all;
        public static int select_action_menu_cut;
        public static int select_action_menu_copy;
        public static int select_action_menu_paste;
        public static int select_action_menu_share;
        public static int select_action_menu_web_search;
        public static int sub_text;
        public static int time_picker;
        public static int year;
    }
    /** layouts */
    public static final class layout {
        public static int date_time_picker_dialog;
        public static int date_time_suggestion;
        public static int two_field_date_picker;
        public static int multi_field_time_picker_dialog;
        public static int validation_message_bubble;
    }
    /** menus */
    public static final class menu {
        public static int select_action_menu;
    }
    /** strings */
    public static final class string {
        public static int accessibility_content_view;
        public static int accessibility_date_picker_month;
        public static int accessibility_date_picker_week;
        public static int accessibility_date_picker_year;
        public static int accessibility_datetime_picker_date;
        public static int accessibility_datetime_picker_time;
        public static int actionbar_share;
        public static int actionbar_web_search;
        public static int date_picker_dialog_clear;
        public static int date_picker_dialog_set;
        public static int date_picker_dialog_title;
        public static int date_picker_dialog_other_button_label;
        public static int date_time_picker_dialog_title;
        public static int media_player_error_button;
        public static int media_player_error_text_invalid_progressive_playback;
        public static int media_player_error_text_unknown;
        public static int media_player_error_title;
        public static int media_player_loading_video;
        public static int month_picker_dialog_title;
        public static int profiler_error_toast;
        public static int profiler_no_storage_toast;
        public static int profiler_started_toast;
        public static int profiler_stopped_toast;
        public static int time_picker_dialog_am;
        public static int time_picker_dialog_hour_minute_separator;
        public static int time_picker_dialog_minute_second_separator;
        public static int time_picker_dialog_second_subsecond_separator;
        public static int time_picker_dialog_pm;
        public static int time_picker_dialog_title;
        public static int week_picker_dialog_title;
    }
    /** styles */
    public static final class style {
        public static int SelectPopupDialog;
    }
}

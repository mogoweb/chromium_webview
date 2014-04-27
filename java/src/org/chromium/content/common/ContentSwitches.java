// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.common;

/**
 * Contains all of the command line switches that are specific to the content/
 * portion of Chromium on Android.
 */
public abstract class ContentSwitches {
    // Tell Java to use the official command line, loaded from the
    // official-command-line.xml files.  WARNING this is not done
    // immediately on startup, so early running Java code will not see
    // these flags.
    public static final String ADD_OFFICIAL_COMMAND_LINE = "add-official-command-line";

    // Enables test intent handling.
    public static final String ENABLE_TEST_INTENTS = "enable-test-intents";

    // Dump frames-per-second to the log
    public static final String LOG_FPS = "log-fps";

    // Whether Chromium should use a mobile user agent.
    public static final String USE_MOBILE_UA = "use-mobile-user-agent";

    // tablet specific UI components.
    // Native switch - chrome_switches::kTabletUI
    public static final String TABLET_UI = "tablet-ui";

    // Change the url of the JavaScript that gets injected when accessibility mode is enabled.
    public static final String ACCESSIBILITY_JAVASCRIPT_URL = "accessibility-js-url";

    // Whether to ignore signature mismatches when connecting to BrailleBack's
    // SelfBrailleService.
    public static final String ACCESSIBILITY_DEBUG_BRAILLE_SERVICE = "debug-braille-service";

    // Whether to always expose web content using Android's accessibility
    // framework instead of injecting javascript for accessibility.
    public static final String DISABLE_ACCESSIBILITY_SCRIPT_INJECTION =
            "disable-accessibility-script-injection";

    // Sets the ISO country code that will be used for phone number detection.
    public static final String NETWORK_COUNTRY_ISO = "network-country-iso";

    // Whether to enable the auto-hiding top controls.
    public static final String ENABLE_TOP_CONTROLS_POSITION_CALCULATION =
            "enable-top-controls-position-calculation";

    // The height of the movable top controls.
    public static final String TOP_CONTROLS_HEIGHT = "top-controls-height";

    // How much of the top controls need to be shown before they will auto show.
    public static final String TOP_CONTROLS_SHOW_THRESHOLD = "top-controls-show-threshold";

    // How much of the top controls need to be hidden before they will auto hide.
    public static final String TOP_CONTROLS_HIDE_THRESHOLD = "top-controls-hide-threshold";

    // Native switch - chrome_switches::kEnableInstantExtendedAPI
    public static final String ENABLE_INSTANT_EXTENDED_API = "enable-instant-extended-api";

    // Native switch - content_switches::kEnableSpeechRecognition
    public static final String ENABLE_SPEECH_RECOGNITION = "enable-speech-recognition";

    // Native switch - shell_switches::kDumpRenderTree
    public static final String DUMP_RENDER_TREE = "dump-render-tree";

    // Native switch - chrome_switches::kDisablePopupBlocking
    public static final String DISABLE_POPUP_BLOCKING = "disable-popup-blocking";

    // Whether to disable the click delay by sending click events during double tap
    public static final String DISABLE_CLICK_DELAY = "disable-click-delay";

    // Native switch - content_switches::kEnableOverlayFullscreenVideoSubtitle
    public static final String ENABLE_OVERLAY_FULLSCREEN_VIDEO_SUBTITLE =
            "enable-overlay-fullscreen-video-subtitle";

    // Prevent instantiation.
    private ContentSwitches() {}
}

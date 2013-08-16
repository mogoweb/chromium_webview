// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome;

import org.chromium.android_webview.AwBrowserProcess;
import org.chromium.android_webview.AwResource;
import org.chromium.content.browser.ResourceExtractor;
import org.chromium.content.common.CommandLine;

import android.content.Context;

import com.mogoweb.chrome.R;

/**
 * Chromium setup chores.
 */
public class ChromeInitializer {
  private static final String[] MANDATORY_PAKS = { "webviewchromium.pak" };

  /** Ensures that initialize() is only called once. */
  private static boolean sInitializeCalled = false;

  /**
   * The entry point to the initialization process.
   *
   * This is called by {@link ChromeView#initialize(Context)}.
   *
   * @param context Android context for the application using ChromeView
   */
  public static void initialize(Context context) {
    if (sInitializeCalled) {
      return;
    }
    sInitializeCalled = true;

    // Initialization lifted from
    //     chromium/src/android_webview/test/shell/src/org/chromium/android_webview/shell/AwShellResourceProvider

    AwResource.setResources(context.getResources());

    AwResource.RAW_LOAD_ERROR = R.raw.blank_html;
    AwResource.RAW_NO_DOMAIN = R.raw.blank_html;

    AwResource.STRING_DEFAULT_TEXT_ENCODING = R.string.default_encoding;

    // Initialization lifted from
    //     chromium/src/android_webview/test/shell/src/org/chromium/android_webview/shell/AwShellApplication

    CommandLine.initFromFile("/data/local/chrome-command-line");

    ResourceExtractor.setMandatoryPaksToExtract(MANDATORY_PAKS);
    ResourceExtractor.setExtractImplicitLocaleForTesting(false);
    AwBrowserProcess.loadLibrary();
    AwBrowserProcess.start(context);
  }
}

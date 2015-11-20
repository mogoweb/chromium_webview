// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import android.app.Instrumentation;
import android.view.KeyEvent;

/**
 * Collection of keyboard utilities.
 */
public class KeyUtils {
    /**
     * Press "Enter".
     */
    public static void pressEnter(Instrumentation instrumentation) {
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER));
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_ENTER));
        instrumentation.waitForIdleSync();
    }

    /**
     * Press "Tab".
     */
    public static void pressTab(Instrumentation instrumentation) {
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_TAB));
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_TAB));
        instrumentation.waitForIdleSync();
    }

    /**
     * Press "Backspace".
     */
    public static void pressBackspace(Instrumentation instrumentation) {
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DEL));
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_DEL));
        instrumentation.waitForIdleSync();
    }

    /**
     * Press "Back".
     */
    public static void pressBack(Instrumentation instrumentation) {
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_BACK));
        instrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_BACK));
        instrumentation.waitForIdleSync();
    }

    /**
     * Input a String.
     */
    public static void inputString(Instrumentation instrumentation, String text) {
        instrumentation.sendStringSync(text);
        instrumentation.waitForIdleSync();
    }
}

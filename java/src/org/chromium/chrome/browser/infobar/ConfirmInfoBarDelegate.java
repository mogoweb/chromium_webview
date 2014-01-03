// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import org.chromium.base.CalledByNative;
import org.chromium.chrome.browser.ResourceId;

/**
 * Provides JNI methods for ConfirmInfoBars
 */
public class ConfirmInfoBarDelegate {

    private ConfirmInfoBarDelegate() {
    }

    @CalledByNative
    public static ConfirmInfoBarDelegate create() {
        return new ConfirmInfoBarDelegate();
    }

    /**
     * Creates and begins the process for showing a ConfirmInfoBar.
     * @param nativeInfoBar Pointer to the C++ InfoBar corresponding to the Java InfoBar.
     * @param enumeratedIconId ID corresponding to the icon that will be shown for the InfoBar.
     *                         The ID must have been mapped using the ResourceMapper class before
     *                         passing it to this function.
     * @param message Message to display to the user indicating what the InfoBar is for.
     * @param linkText Link text to display in addition to the message.
     * @param buttonOk String to display on the OK button.
     * @param buttonCancel String to display on the Cancel button.
     */
    @CalledByNative
    InfoBar showConfirmInfoBar(int nativeInfoBar, int enumeratedIconId, String message,
            String linkText, String buttonOk, String buttonCancel) {
        int drawableId = ResourceId.mapToDrawableId(enumeratedIconId);

        // Apparently, yellow was the popular choice at the time these InfoBars were implemented
        // because they stuck out more (hence the BACKGROUND_TYPE_WARNING) default.
        ConfirmInfoBar infoBar = new ConfirmInfoBar(nativeInfoBar, null,
                InfoBar.BACKGROUND_TYPE_WARNING, drawableId, message, linkText, buttonOk,
                buttonCancel);
        return infoBar;
    }
}
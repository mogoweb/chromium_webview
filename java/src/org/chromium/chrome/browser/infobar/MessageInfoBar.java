// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.widget.TextView;

import org.chromium.chrome.R;

/**
 * A simple infobar that contains a message and a close icon on the right side.
 * This is used only in the context of Java code and is not associated with any native
 * InfoBarDelegate.
 */
public class MessageInfoBar extends InfoBar {
    private CharSequence mTitle;

    /**
     * Creates and returns an infobar with a white background and a close button on the right.
     * @param title the text displayed in the infobar
     * @return the infobar.
     */
    public static MessageInfoBar createInfoBar(CharSequence title) {
        return new MessageInfoBar(null, 0, title, BACKGROUND_TYPE_INFO);
    }

    /**
     * Creates and returns an infobar with a white background and a close button on the right.
     * @param iconResourceId the icon shown on the right
     * @param title the text displayed in the infobar
     * @return the infobar.
     */
    public static MessageInfoBar createInfoBar(int iconResourceId, CharSequence title) {
        return new MessageInfoBar(null, iconResourceId, title, BACKGROUND_TYPE_INFO);
    }

    /**
     * Creates a warning infobar, with a yellow background and a warning icon on the right.
     * @param title the text displayed in the infobar
     * @return the infobar.
     */
    public static MessageInfoBar createWarningInfoBar(CharSequence title) {
        return createWarningInfoBar(null, title);
    }

    /**
     * Creates a warning infobar, with a yellow background and a warning icon on the right.
     * @param listener an infobar dismissed listener
     * @param title the text displayed in the infobar
     * @return the infobar.
     */
    public static MessageInfoBar createWarningInfoBar(InfoBarListeners.Dismiss listener,
            CharSequence title) {
        return new MessageInfoBar(listener, R.drawable.warning, title, BACKGROUND_TYPE_WARNING);
    }

    protected MessageInfoBar(InfoBarListeners.Dismiss listener, int iconResourceId,
            CharSequence title, int backgroundType) {
        super(listener, backgroundType, iconResourceId);
        mTitle = title;
    }

    @Override
    public CharSequence getMessageText(Context context) {
        return mTitle;
    }

    @Override
    public void onCloseButtonClicked() {
        super.dismissJavaOnlyInfoBar();
    }
}

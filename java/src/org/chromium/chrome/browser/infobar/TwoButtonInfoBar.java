// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.chrome.R;

/**
 * An infobar that presents the user with up to 2 buttons.
 */
public abstract class TwoButtonInfoBar extends InfoBar {
    public TwoButtonInfoBar(InfoBarListeners.Dismiss dismissListener, int backgroundType,
            int iconDrawableId) {
        super(dismissListener, backgroundType, iconDrawableId);
    }

    /**
     * Creates controls for the current InfoBar.
     * @param layout InfoBarLayout to find controls in.
     */
    @Override
    public void createContent(InfoBarLayout layout) {
        Context context = layout.getContext();
        layout.addButtons(getPrimaryButtonText(context), getSecondaryButtonText(context));
    }

    @Override
    public void setControlsEnabled(boolean state) {
        super.setControlsEnabled(state);

        // Handle the buttons.
        ContentWrapperView wrapper = getContentWrapper(false);
        if (wrapper != null) {
            Button primary = (Button) wrapper.findViewById(R.id.button_primary);
            Button secondary = (Button) wrapper.findViewById(R.id.button_secondary);
            if (primary != null) primary.setEnabled(state);
            if (secondary != null) secondary.setEnabled(state);
        }
    }
}

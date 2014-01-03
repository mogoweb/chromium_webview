// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.chromium.chrome.browser.infobar.InfoBar;
import org.chromium.chrome.browser.infobar.InfoBarLayout;
import org.chromium.chrome.R;

/**
 * Options panel shown in the after translate infobar.
 */
public class TranslateAlwaysPanel implements TranslateSubPanel {

    private final TranslateOptions mOptions;
    private final SubPanelListener mListener;

    TranslateAlwaysPanel(SubPanelListener listener, TranslateOptions options) {
        mOptions = options;
        mListener = listener;
    }

    @Override
    public void createContent(Context context, InfoBarLayout layout) {
        TextView panelMessage = (TextView) layout.findViewById(R.id.infobar_message);
        panelMessage.setText(context.getString(
                R.string.translate_infobar_translation_done, mOptions.targetLanguage()));

        TranslateCheckBox checkBox = new TranslateCheckBox(mOptions, mListener);
        checkBox.createContent(context, layout);

        layout.addButtons(context.getString(R.string.translate_button_done),
                context.getString(R.string.translate_show_original));
    }

    @Override
    public void onButtonClicked(boolean primary) {
        if (primary) {
            mListener.onPanelClosed(InfoBar.ACTION_TYPE_NONE);
        } else {
            mListener.onPanelClosed(InfoBar.ACTION_TYPE_TRANSLATE_SHOW_ORIGINAL);
        }
    }
}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.chrome.browser.infobar.InfoBar;
import org.chromium.chrome.browser.infobar.InfoBarLayout;
import org.chromium.chrome.R;

/**
 * Never panel shown in the translate infobar
 */
public class TranslateNeverPanel implements TranslateSubPanel {

    private final TranslateOptions mOptions;
    private final SubPanelListener mListener;

    public TranslateNeverPanel(SubPanelListener listener, TranslateOptions options) {
        mOptions = options;
        mListener = listener;
    }

    @Override
    public void createContent(Context context, InfoBarLayout layout) {
        String changeLanguage = context.getString(
                R.string.translate_never_translate_message_text, mOptions.sourceLanguage());

        TextView panelMessage = (TextView) layout.findViewById(R.id.infobar_message);
        panelMessage.setText(changeLanguage);

        layout.addButtons(
                context.getString(R.string.translate_never_translate_site),
                context.getString(R.string.translate_never_translate_language,
                        mOptions.sourceLanguage()));
    }

    @Override
    public void onButtonClicked(boolean primary) {
        if (primary) {
            mOptions.toggleNeverTranslateDomainState(true);
        } else {
            mOptions.toggleNeverTranslateLanguageState(true);
        }
        mListener.onPanelClosed(InfoBar.ACTION_TYPE_NONE);
    }
}

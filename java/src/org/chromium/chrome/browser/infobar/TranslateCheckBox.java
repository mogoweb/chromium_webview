// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.CompoundButton;


import org.chromium.chrome.browser.infobar.InfoBar;
import org.chromium.chrome.browser.infobar.InfoBarLayout;
import org.chromium.chrome.R;

/**
 * A check box used to determine if a page should always be translated.
 */
public class TranslateCheckBox {
    private final SubPanelListener mListener;
    private final TranslateOptions mOptions;

    public TranslateCheckBox(TranslateOptions options, SubPanelListener listener) {
        mOptions = options;
        mListener = listener;
    }

    public void createContent(Context context, InfoBarLayout layout) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setId(R.id.infobar_extra_check);
        checkBox.setText(context.getString(R.string.translate_always_text,
                mOptions.sourceLanguage()));
        checkBox.setChecked(mOptions.alwaysTranslateLanguageState());
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton view, boolean isChecked) {
                mOptions.toggleAlwaysTranslateLanguageState(isChecked);
                if (isChecked){
                    mListener.onPanelClosed(InfoBar.ACTION_TYPE_NONE);
                } else {
                    mListener.onOptionsChanged();
                }
            }
        });
        layout.addGroup(checkBox);
    }
}

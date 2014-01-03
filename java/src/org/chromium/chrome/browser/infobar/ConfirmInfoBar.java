// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.View;

/**
 * An infobar that presents the user with 2 buttons (typically OK, Cancel).
 */
public class ConfirmInfoBar extends TwoButtonInfoBar {
    // Message to prompt the user.
    private final String mMessage;

    // Link text shown to the user, in addition to the message.
    private final String mLinkText;

    // Typically set to "OK", or some other positive action.
    private final String mPrimaryButtonText;

    // Typically set to "Cancel", or some other negative action.
    private final String mSecondaryButtonText;

    // Listens for when either of the buttons is clicked.
    private final InfoBarListeners.Confirm mConfirmListener;

    public ConfirmInfoBar(InfoBarListeners.Confirm confirmListener, int backgroundType,
            int iconDrawableId, String message, String primaryButtonText,
            String secondaryButtonText) {
        this(confirmListener, backgroundType, iconDrawableId, message, null, primaryButtonText,
                secondaryButtonText);
    }

    public ConfirmInfoBar(InfoBarListeners.Confirm confirmListener, int backgroundType,
            int iconDrawableId, String message, String linkText, String primaryButtonText,
            String secondaryButtonText) {
        this(0, confirmListener, backgroundType, iconDrawableId, message, linkText,
                primaryButtonText, secondaryButtonText);
    }

    public ConfirmInfoBar(int nativeInfoBar, InfoBarListeners.Confirm confirmListener,
            int backgroundType, int iconDrawableId, String message, String linkText,
            String primaryButtonText, String secondaryButtonText) {
        super(confirmListener, backgroundType, iconDrawableId);
        mMessage = message;
        mLinkText = linkText;
        mPrimaryButtonText = primaryButtonText;
        mSecondaryButtonText = secondaryButtonText;
        mConfirmListener = confirmListener;
        setNativeInfoBar(nativeInfoBar);
    }

    @Override
    public CharSequence getMessageText(Context context) {
        // Construct text to be displayed on the infobar.
        SpannableStringBuilder infobarMessage = new SpannableStringBuilder(mMessage);

        // If we have a link text to display, append it.
        if (!TextUtils.isEmpty(mLinkText)) {
            SpannableStringBuilder spannableLinkText = new SpannableStringBuilder(mLinkText);
            ClickableSpan onLinkClicked = new ClickableSpan() {
                @Override
                public void onClick(View view) {
                    onLinkClicked();
                }
            };
            spannableLinkText.setSpan(onLinkClicked, 0, spannableLinkText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            infobarMessage.append(" ");
            infobarMessage.append(spannableLinkText);
        }
        return infobarMessage;
    }

    @Override
    public String getPrimaryButtonText(Context context) {
        return mPrimaryButtonText;
    }

    @Override
    public String getSecondaryButtonText(Context context) {
        return mSecondaryButtonText;
    }

    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
        if (mConfirmListener != null) {
            mConfirmListener.onConfirmInfoBarButtonClicked(this, isPrimaryButton);
        }

        if (mNativeInfoBarPtr != 0) {
            int action = isPrimaryButton ? InfoBar.ACTION_TYPE_OK : InfoBar.ACTION_TYPE_CANCEL;
            nativeOnButtonClicked(mNativeInfoBarPtr, action, "");
        }
    }

    @Override
    public void onCloseButtonClicked() {
        if (mNativeInfoBarPtr != 0) {
            nativeOnCloseButtonClicked(mNativeInfoBarPtr);
        } else {
            super.dismissJavaOnlyInfoBar();
        }
    }
}

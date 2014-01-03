// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;

/**
 * Functions needed to display an InfoBar UI.
 */
public interface InfoBarView {
    /**
     * Prepare the InfoBar for display and adding InfoBar-specific controls to the layout.
     * @param layout Layout containing all of the controls.
     */
    public void createContent(InfoBarLayout layout);

    /**
     * Returns the message indicating what the InfoBar is informing or asking the user about.
     * @param context Context to pull the string from.
     * @return The string to display.
     */
    public CharSequence getMessageText(Context context);

    /**
     * Returns text to display on the primary button indicating that some action will be taken.
     * Setting this to null prevents the button from being created.
     * @param context Context to pull the string from.
     * @return The string to display.
     */
    public String getPrimaryButtonText(Context context);

    /**
     * Returns text to display on the secondary button, typically indicating that some action will
     * not be taken.
     *
     * Example text includes "Cancel" or "Nope".  Setting this to null prevents the button from
     * being created.  It is illegal to have a secondary button without a primary button.
     *
     * @param context Context to pull the string from.
     * @return The string to display.
     */
    public String getSecondaryButtonText(Context context);

    /**
     * Take some action related to the link being clicked.
     */
    public void onLinkClicked();

    /**
     * Take some action related to the close button being clicked.
     */
    public void onCloseButtonClicked();

    /**
     * Performs some action related to either the primary or secondary button being pressed.
     * @param isPrimaryButton True if the primary button was clicked, false otherwise.
     */
    public void onButtonClicked(boolean isPrimaryButton);

    /**
     * Sets whether or not controls for this View should be clickable.
     * @param state If set to false, controls cannot be clicked and will be grayed out.
     */
    public void setControlsEnabled(boolean state);
}

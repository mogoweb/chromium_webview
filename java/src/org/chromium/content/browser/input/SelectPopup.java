// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

/**
 * Handles the popup UI for the <select> HTML tag support.
 */
public interface SelectPopup {
    /**
     * Shows the popup.
     */
    public void show();
    /**
     * Hides the popup.
     */
    public void hide();
}

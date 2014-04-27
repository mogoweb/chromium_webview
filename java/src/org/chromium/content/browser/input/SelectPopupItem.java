// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

/**
 * Select popup item containing the label, the type and the enabled state
 * of an item belonging to a select popup dialog.
 */
public class SelectPopupItem {
    private final String mLabel;
    private final int mType;

    public SelectPopupItem(String label, int type) {
        mLabel = label;
        mType = type;
    }

    public String getLabel() {
        return mLabel;
    }

    public int getType() {
        return mType;
    }
}

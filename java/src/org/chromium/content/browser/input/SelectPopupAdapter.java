// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Select popup item adapter for SelectPopupDialog, used so we can disable
 * OPTION_GROUP items.
 */
public class SelectPopupAdapter extends ArrayAdapter<SelectPopupItem> {
    // Holds the items of the select popup alert dialog list.
    private List<SelectPopupItem> mItems;

    // True if all items have type PopupItemType.ENABLED.
    private boolean mAreAllItemsEnabled;

    /**
     * Creates a new SelectPopupItem adapter for the select popup alert dialog list.
     * @param context        Application context.
     * @param layoutResource Layout resource used for the alert dialog list.
     * @param items          SelectPopupItem array list.
     */
    public SelectPopupAdapter(Context context, int layoutResource,
            List<SelectPopupItem> items) {
        super(context, layoutResource, items);
        mItems = new ArrayList<SelectPopupItem>(items);

        mAreAllItemsEnabled = true;
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).getType() != PopupItemType.ENABLED) {
                mAreAllItemsEnabled = false;
                break;
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position < 0 || position >= getCount()) return null;

        // Always pass in null so that we will get a new CheckedTextView. Otherwise, an item
        // which was previously used as an <optgroup> element (i.e. has no check), could get
        // used as an <option> element, which needs a checkbox/radio, but it would not have
        // one.
        convertView = super.getView(position, null, parent);
        ((TextView) convertView).setText(mItems.get(position).getLabel());

        if (mItems.get(position).getType() != PopupItemType.ENABLED) {
            if (mItems.get(position).getType() == PopupItemType.GROUP) {
                // Currently select_dialog_multichoice uses CheckedTextViews.
                // If that changes, the class cast will no longer be valid.
                // The WebView build cannot rely on this being the case, so
                // we must check.
                if (convertView instanceof CheckedTextView) {
                    ((CheckedTextView) convertView).setCheckMarkDrawable(null);
                }
            } else {
                // Draw the disabled element in a disabled state.
                convertView.setEnabled(false);
            }
        }
        return convertView;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return mAreAllItemsEnabled;
    }

    @Override
    public boolean isEnabled(int position) {
        if (position < 0 || position >= getCount()) return false;
        return mItems.get(position).getType() == PopupItemType.ENABLED;
    }
}

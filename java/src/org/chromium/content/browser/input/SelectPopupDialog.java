// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import org.chromium.content.R;
import org.chromium.content.browser.ContentViewCore;

import java.util.List;

/**
 * Handles the popup dialog for the <select> HTML tag support.
 */
public class SelectPopupDialog implements SelectPopup {
    private static final int[] SELECT_DIALOG_ATTRS = {
        R.attr.select_dialog_multichoice,
        R.attr.select_dialog_singlechoice
    };

    // The dialog hosting the popup list view.
    private AlertDialog mListBoxPopup = null;

    private final ContentViewCore mContentViewCore;
    private final Context mContext;

    public SelectPopupDialog(ContentViewCore contentViewCore, List<SelectPopupItem> items,
            boolean multiple, int[] selected) {
        mContentViewCore = contentViewCore;
        mContext = mContentViewCore.getContext();

        final ListView listView = new ListView(mContext);
        listView.setCacheColorHint(0);
        AlertDialog.Builder b = new AlertDialog.Builder(mContext)
                .setView(listView)
                .setCancelable(true)
                .setInverseBackgroundForced(true);

        if (multiple) {
            b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mContentViewCore.selectPopupMenuItems(getSelectedIndices(listView));
                }
            });
            b.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mContentViewCore.selectPopupMenuItems(null);
                        }
                    });
        }
        mListBoxPopup = b.create();
        final SelectPopupAdapter adapter = new SelectPopupAdapter(
                mContext, getSelectDialogLayout(multiple), items);
        listView.setAdapter(adapter);
        listView.setFocusableInTouchMode(true);

        if (multiple) {
            listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            for (int i = 0; i < selected.length; ++i) {
                listView.setItemChecked(selected[i], true);
            }
        } else {
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View v,
                        int position, long id) {
                    mContentViewCore.selectPopupMenuItems(getSelectedIndices(listView));
                    mListBoxPopup.dismiss();
                }
            });
            if (selected.length > 0) {
                listView.setSelection(selected[0]);
                listView.setItemChecked(selected[0], true);
            }
        }
        mListBoxPopup.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mContentViewCore.selectPopupMenuItems(null);
            }
        });
    }

    private int getSelectDialogLayout(boolean isMultiChoice) {
        int resourceId;
        TypedArray styledAttributes = mContext.obtainStyledAttributes(
                R.style.SelectPopupDialog, SELECT_DIALOG_ATTRS);
        resourceId = styledAttributes.getResourceId(isMultiChoice ? 0 : 1, 0);
        styledAttributes.recycle();
        return resourceId;
    }

    private int[] getSelectedIndices(ListView listView) {
        SparseBooleanArray sparseArray = listView.getCheckedItemPositions();
        int selectedCount = 0;
        for (int i = 0; i < sparseArray.size(); ++i) {
            if (sparseArray.valueAt(i)) {
                selectedCount++;
            }
        }
        int[] indices = new int[selectedCount];
        for (int i = 0, j = 0; i < sparseArray.size(); ++i) {
            if (sparseArray.valueAt(i)) {
                indices[j++] = sparseArray.keyAt(i);
            }
        }
        return indices;
    }

    @Override
    public void show() {
        mListBoxPopup.show();
    }

    @Override
    public void hide() {
        mListBoxPopup.cancel();
    }
}

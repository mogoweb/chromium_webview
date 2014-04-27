// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.ClipboardManager;
import android.content.Context;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import org.chromium.content.R;

/**
 * An ActionMode.Callback for in-page selection. This class handles both the editable and
 * non-editable cases.
 */
public class SelectActionModeCallback implements ActionMode.Callback {
    /**
     * An interface to retrieve information about the current selection, and also to perform
     * actions based on the selection or when the action bar is dismissed.
     */
    public interface ActionHandler {
        /**
         * Perform a select all action.
         */
        void selectAll();

        /**
         * Perform a copy (to clipboard) action.
         */
        void copy();

        /**
         * Perform a cut (to clipboard) action.
         */
        void cut();

        /**
         * Perform a paste action.
         */
        void paste();

        /**
         * Perform a share action.
         */
        void share();

        /**
         * Perform a search action.
         */
        void search();

        /**
         * @return true iff the current selection is editable (e.g. text within an input field).
         */
        boolean isSelectionEditable();

        /**
         * Called when the onDestroyActionMode of the SelectActionmodeCallback is called.
         */
        void onDestroyActionMode();

        /**
         * @return Whether or not share is available.
         */
        boolean isShareAvailable();

        /**
         * @return Whether or not web search is available.
         */
        boolean isWebSearchAvailable();
    }

    private final Context mContext;
    private final ActionHandler mActionHandler;
    private final boolean mIncognito;
    private boolean mEditable;

    protected SelectActionModeCallback(
            Context context, ActionHandler actionHandler, boolean incognito) {
        mContext = context;
        mActionHandler = actionHandler;
        mIncognito = incognito;
    }

    protected Context getContext() {
        return mContext;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.setTitle(null);
        mode.setSubtitle(null);
        mEditable = mActionHandler.isSelectionEditable();
        createActionMenu(mode, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        boolean isEditableNow = mActionHandler.isSelectionEditable();
        if (mEditable != isEditableNow) {
            mEditable = isEditableNow;
            menu.clear();
            createActionMenu(mode, menu);
            return true;
        }
        return false;
    }

    private void createActionMenu(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.select_action_menu, menu);
        if (!mEditable || !canPaste()) {
            menu.removeItem(R.id.select_action_menu_paste);
        }

        if (!mEditable) {
            menu.removeItem(R.id.select_action_menu_cut);
        }

        if (mEditable || !mActionHandler.isShareAvailable()) {
            menu.removeItem(R.id.select_action_menu_share);
        }

        if (mEditable || mIncognito || !mActionHandler.isWebSearchAvailable()) {
            menu.removeItem(R.id.select_action_menu_web_search);
        }
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.select_action_menu_select_all) {
            mActionHandler.selectAll();
        } else if (id == R.id.select_action_menu_cut) {
            mActionHandler.cut();
        } else if (id == R.id.select_action_menu_copy) {
            mActionHandler.copy();
            mode.finish();
        } else if (id == R.id.select_action_menu_paste) {
            mActionHandler.paste();
        } else if (id == R.id.select_action_menu_share) {
            mActionHandler.share();
            mode.finish();
        } else if (id == R.id.select_action_menu_web_search) {
            mActionHandler.search();
            mode.finish();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionHandler.onDestroyActionMode();
    }

    private boolean canPaste() {
        ClipboardManager clipMgr = (ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        return clipMgr.hasPrimaryClip();
    }
}

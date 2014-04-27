// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.input.InputMethodManagerWrapper;

public class TestInputMethodManagerWrapper extends InputMethodManagerWrapper {
    private final ContentViewCore mContentViewCore;
    private InputConnection mInputConnection;
    private int mShowSoftInputCounter = 0;
    private int mUpdateSelectionCounter = 0;
    private EditorInfo mEditorInfo;

    public TestInputMethodManagerWrapper(ContentViewCore contentViewCore) {
        super(null);
        mContentViewCore = contentViewCore;
    }

    @Override
    public void restartInput(View view) {
        mEditorInfo = new EditorInfo();
        mInputConnection = mContentViewCore.onCreateInputConnection(mEditorInfo);
    }

    @Override
    public void showSoftInput(View view, int flags, ResultReceiver resultReceiver) {
        mShowSoftInputCounter++;
        if (mInputConnection != null) return;
        mEditorInfo = new EditorInfo();
        mInputConnection = mContentViewCore.onCreateInputConnection(mEditorInfo);
    }

    @Override
    public boolean isActive(View view) {
        if (mInputConnection == null) return false;
        return true;
    }

    @Override
    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags,
            ResultReceiver resultReceiver) {
        boolean retVal = mInputConnection == null;
        mInputConnection = null;
        return retVal;
    }

    @Override
    public void updateSelection(View view, int selStart, int selEnd,
            int candidatesStart, int candidatesEnd) {
        mUpdateSelectionCounter++;
    }

    public int getShowSoftInputCounter() {
        return mShowSoftInputCounter;
    }

    public int getUpdateSelectionCounter() {
        return mUpdateSelectionCounter;
    }

    public EditorInfo getEditorInfo() {
        return mEditorInfo;
    }
}


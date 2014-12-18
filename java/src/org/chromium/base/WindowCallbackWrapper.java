// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.annotation.SuppressLint;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;

/**
 * A wrapper for a Window.Callback instance, allowing subclasses to listen to or override specific
 * window messages.
 */
class WindowCallbackWrapper implements Window.Callback {
    private final Window.Callback mCallback;

    public WindowCallbackWrapper(Window.Callback callback) {
        mCallback = callback;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return mCallback.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mCallback.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return mCallback.dispatchKeyShortcutEvent(event);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return mCallback.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mCallback.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return mCallback.dispatchTrackballEvent(event);
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        mCallback.onActionModeFinished(mode);
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        mCallback.onActionModeStarted(mode);
    }

    @Override
    public void onAttachedToWindow() {
        mCallback.onAttachedToWindow();
    }

    @Override
    public void onContentChanged() {
        mCallback.onContentChanged();
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return mCallback.onCreatePanelMenu(featureId, menu);
    }

    @Override
    public View onCreatePanelView(int featureId) {
        return mCallback.onCreatePanelView(featureId);
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onDetachedFromWindow() {
        mCallback.onDetachedFromWindow();
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return mCallback.onMenuItemSelected(featureId, item);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        return mCallback.onMenuOpened(featureId, menu);
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        mCallback.onPanelClosed(featureId, menu);
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return mCallback.onPreparePanel(featureId, view, menu);
    }

    @Override
    public boolean onSearchRequested() {
        return mCallback.onSearchRequested();
    }

    @Override
    public void onWindowAttributesChanged(LayoutParams attrs) {
        mCallback.onWindowAttributesChanged(attrs);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        mCallback.onWindowFocusChanged(hasFocus);
    }

    @Override
    public ActionMode onWindowStartingActionMode(Callback callback) {
        return mCallback.onWindowStartingActionMode(callback);
    }

    public void onWindowDismissed() {
        // TODO(benm): implement me.
    }

}

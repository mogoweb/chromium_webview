// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

/**
 * Utility functions for common Android UI tasks.
 * This class is not supposed to be instantiated.
 */
public class UiUtils {
    /**
     * Guards this class from being instantiated.
     */
    private UiUtils() {
    }

    /** The minimum size of the bottom margin below the app to detect a keyboard. */
    private static float KEYBOARD_DETECT_BOTTOM_THRESHOLD_DP = 100;

    /**
     * Shows the software keyboard if necessary.
     * @param view The currently focused {@link View}, which would receive soft keyboard input.
     */
    public static void showKeyboard(View view) {
        InputMethodManager imm =
                (InputMethodManager) view.getContext().getSystemService(
                        Context.INPUT_METHOD_SERVICE);
        // Only shows soft keyboard if there isn't an open physical keyboard.
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    /**
     * Hides the keyboard.
     * @param view The {@link View} that is currently accepting input.
     * @return Whether the keyboard was visible before.
     */
    public static boolean hideKeyboard(View view) {
        InputMethodManager imm =
                (InputMethodManager) view.getContext().getSystemService(
                        Context.INPUT_METHOD_SERVICE);
        return imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static boolean isKeyboardShowing(Context context, View view) {
        View rootView = view.getRootView();
        if (rootView == null) return false;
        Rect appRect = new Rect();
        rootView.getWindowVisibleDisplayFrame(appRect);
        final float screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        final float bottomMargin = Math.abs(appRect.bottom - screenHeight);
        final float density = context.getResources().getDisplayMetrics().density;
        return bottomMargin > KEYBOARD_DETECT_BOTTOM_THRESHOLD_DP * density;
    }

    /**
     * Inserts a {@link View} into a {@link ViewGroup} after directly before a given {@View}.
     * @param container The {@link View} to add newView to.
     * @param newView The new {@link View} to add.
     * @param existingView The {@link View} to insert the newView before.
     * @return The index where newView was inserted, or -1 if it was not inserted.
     */
    public static int insertBefore(ViewGroup container, View newView, View existingView) {
        return insertView(container, newView, existingView, false);
    }

    /**
     * Inserts a {@link View} into a {@link ViewGroup} after directly after a given {@View}.
     * @param container The {@link View} to add newView to.
     * @param newView The new {@link View} to add.
     * @param existingView The {@link View} to insert the newView after.
     * @return The index where newView was inserted, or -1 if it was not inserted.
     */
    public static int insertAfter(ViewGroup container, View newView, View existingView) {
        return insertView(container, newView, existingView, true);
    }

    private static int insertView(
            ViewGroup container, View newView, View existingView, boolean after) {
        // See if the view has already been added.
        int index = container.indexOfChild(newView);
        if (index >= 0) return index;

        // Find the location of the existing view.
        index = container.indexOfChild(existingView);
        if (index < 0) return -1;

        // Add the view.
        if (after) index++;
        container.addView(newView, index);
        return index;
    }
}

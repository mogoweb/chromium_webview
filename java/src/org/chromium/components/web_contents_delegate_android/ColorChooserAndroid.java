// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.web_contents_delegate_android;

import android.content.Context;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.ColorPickerDialog;
import org.chromium.ui.ColorSuggestion;
import org.chromium.ui.OnColorChangedListener;

/**
 * ColorChooserAndroid communicates with the java ColorPickerDialog and the
 * native color_chooser_android.cc
 */
@JNINamespace("web_contents_delegate_android")
public class ColorChooserAndroid {
    private final ColorPickerDialog mDialog;
    private final long mNativeColorChooserAndroid;

    private ColorChooserAndroid(long nativeColorChooserAndroid,
            Context context, int initialColor, ColorSuggestion[] suggestions) {
        OnColorChangedListener listener = new OnColorChangedListener() {
          @Override
          public void onColorChanged(int color) {
              mDialog.dismiss();
              nativeOnColorChosen(mNativeColorChooserAndroid, color);
          }
        };

        mNativeColorChooserAndroid = nativeColorChooserAndroid;
        mDialog = new ColorPickerDialog(context, listener, initialColor, suggestions);
    }

    private void openColorChooser() {
        mDialog.show();
    }

    @CalledByNative
    public void closeColorChooser() {
        mDialog.dismiss();
    }

    @CalledByNative
    public static ColorChooserAndroid createColorChooserAndroid(
            long nativeColorChooserAndroid,
            ContentViewCore contentViewCore,
            int initialColor,
            ColorSuggestion[] suggestions) {
        ColorChooserAndroid chooser = new ColorChooserAndroid(nativeColorChooserAndroid,
            contentViewCore.getContext(), initialColor, suggestions);
        chooser.openColorChooser();
        return chooser;
    }

    @CalledByNative
    private static ColorSuggestion[] createColorSuggestionArray(int size) {
        return new ColorSuggestion[size];
    }

    /**
     * @param array ColorSuggestion array that should get a new suggestion added.
     * @param index Index in the array where to place a new suggestion.
     * @param color Color of the suggestion.
     * @param label Label of the suggestion.
     */
    @CalledByNative
    private static void addToColorSuggestionArray(ColorSuggestion[] array, int index,
            int color, String label) {
        array[index] = new ColorSuggestion(color, label);
    }

    // Implemented in color_chooser_android.cc
    private native void nativeOnColorChosen(long nativeColorChooserAndroid, int color);
}

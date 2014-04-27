// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.content.Context;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;

/**
 * Plumbing for the different date/time dialog adapters.
 */
@JNINamespace("content")
class DateTimeChooserAndroid {

    private final long mNativeDateTimeChooserAndroid;
    private final InputDialogContainer mInputDialogContainer;

    private DateTimeChooserAndroid(Context context,
            long nativeDateTimeChooserAndroid) {
        mNativeDateTimeChooserAndroid = nativeDateTimeChooserAndroid;
        mInputDialogContainer = new InputDialogContainer(context,
                new InputDialogContainer.InputActionDelegate() {

                    @Override
                    public void replaceDateTime(double value) {
                        nativeReplaceDateTime(mNativeDateTimeChooserAndroid, value);
                    }

                    @Override
                    public void cancelDateTimeDialog() {
                        nativeCancelDialog(mNativeDateTimeChooserAndroid);
                    }
                });
    }

    private void showDialog(int dialogType, double dialogValue,
                            double min, double max, double step,
                            DateTimeSuggestion[] suggestions) {
        mInputDialogContainer.showDialog(dialogType, dialogValue, min, max, step, suggestions);
    }

    @CalledByNative
    private static DateTimeChooserAndroid createDateTimeChooser(
            ContentViewCore contentViewCore,
            long nativeDateTimeChooserAndroid,
            int dialogType, double dialogValue,
            double min, double max, double step,
            DateTimeSuggestion[] suggestions) {
        DateTimeChooserAndroid chooser =
                new DateTimeChooserAndroid(
                        contentViewCore.getContext(),
                        nativeDateTimeChooserAndroid);
        chooser.showDialog(dialogType, dialogValue, min, max, step, suggestions);
        return chooser;
    }

    @CalledByNative
    private static DateTimeSuggestion[] createSuggestionsArray(int size) {
        return new DateTimeSuggestion[size];
    }

    /**
     * @param array DateTimeSuggestion array that should get a new suggestion set.
     * @param index Index in the array where to place a new suggestion.
     * @param value Value of the suggestion.
     * @param localizedValue Localized value of the suggestion.
     * @param label Label of the suggestion.
     */
    @CalledByNative
    private static void setDateTimeSuggestionAt(DateTimeSuggestion[] array, int index,
            double value, String localizedValue, String label) {
        array[index] = new DateTimeSuggestion(value, localizedValue, label);
    }

    @CalledByNative
    private static void initializeDateInputTypes(
            int textInputTypeDate, int textInputTypeDateTime,
            int textInputTypeDateTimeLocal, int textInputTypeMonth,
            int textInputTypeTime, int textInputTypeWeek) {
        InputDialogContainer.initializeInputTypes(
                textInputTypeDate,
                textInputTypeDateTime, textInputTypeDateTimeLocal,
                textInputTypeMonth, textInputTypeTime, textInputTypeWeek);
    }

    private native void nativeReplaceDateTime(long nativeDateTimeChooserAndroid,
                                              double dialogValue);

    private native void nativeCancelDialog(long nativeDateTimeChooserAndroid);
}

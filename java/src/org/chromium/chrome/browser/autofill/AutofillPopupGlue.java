// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.ui.ViewAndroid;
import org.chromium.ui.ViewAndroidDelegate;
import org.chromium.ui.WindowAndroid;
import org.chromium.ui.autofill.AutofillPopup;
import org.chromium.ui.autofill.AutofillPopup.AutofillPopupDelegate;
import org.chromium.ui.autofill.AutofillSuggestion;

/**
* JNI call glue for AutofillExternalDelagate C++ and Java objects.
*/
@JNINamespace("autofill")
public class AutofillPopupGlue implements AutofillPopupDelegate{
    private final int mNativeAutofillPopup;
    private final AutofillPopup mAutofillPopup;

    public AutofillPopupGlue(int nativeAutofillPopupViewAndroid, WindowAndroid windowAndroid,
            ViewAndroidDelegate containerViewDelegate) {
        mNativeAutofillPopup = nativeAutofillPopupViewAndroid;
        mAutofillPopup = new AutofillPopup(windowAndroid.getContext(), containerViewDelegate, this);
    }

    @CalledByNative
    private static AutofillPopupGlue create(int nativeAutofillPopupViewAndroid,
            WindowAndroid windowAndroid, ViewAndroid viewAndroid) {
        return new AutofillPopupGlue(nativeAutofillPopupViewAndroid, windowAndroid,
                viewAndroid.getViewAndroidDelegate());
    }

    @Override
    public void requestHide() {
        nativeRequestHide(mNativeAutofillPopup);
    }

    @Override
    public void suggestionSelected(int listIndex) {
        nativeSuggestionSelected(mNativeAutofillPopup, listIndex);
    }

    /**
     * Hides the Autofill Popup and removes its anchor from the ContainerView.
     */
    @CalledByNative
    private void hide() {
        mAutofillPopup.hide();
    }

    /**
     * Shows an Autofill popup with specified suggestions.
     * @param suggestions Autofill suggestions to be displayed.
     */
    @CalledByNative
    private void show(AutofillSuggestion[] suggestions) {
        mAutofillPopup.show(suggestions);
    }

    /**
     * Sets the location and size of the Autofill popup anchor (input field).
     * @param x X coordinate.
     * @param y Y coordinate.
     * @param width The width of the anchor.
     * @param height The height of the anchor.
     */
    @CalledByNative
    private void setAnchorRect(float x, float y, float width, float height) {
        mAutofillPopup.setAnchorRect(x, y, width, height);
    }

    // Helper methods for AutofillSuggestion

    @CalledByNative
    private static AutofillSuggestion[] createAutofillSuggestionArray(int size) {
        return new AutofillSuggestion[size];
    }

    /**
     * @param array AutofillSuggestion array that should get a new suggestion added.
     * @param index Index in the array where to place a new suggestion.
     * @param label First line of the suggestion.
     * @param sublabel Second line of the suggestion.
     * @param uniqueId Unique suggestion id.
     */
    @CalledByNative
    private static void addToAutofillSuggestionArray(AutofillSuggestion[] array, int index,
            String label, String sublabel, int uniqueId) {
        array[index] = new AutofillSuggestion(label, sublabel, uniqueId);
    }

    private native void nativeRequestHide(int nativeAutofillPopupViewAndroid);
    private native void nativeSuggestionSelected(int nativeAutofillPopupViewAndroid,
            int listIndex);
}

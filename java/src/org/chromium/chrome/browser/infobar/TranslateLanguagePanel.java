// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;


import org.chromium.chrome.browser.infobar.InfoBar;
import org.chromium.chrome.browser.infobar.InfoBarLayout;
import org.chromium.chrome.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Language panel shown in the translate infobar.
 */
public class TranslateLanguagePanel
        implements TranslateSubPanel, AdapterView.OnItemSelectedListener {

    private static final int LANGUAGE_TYPE_SOURCE = 0;
    private static final int LANGUAGE_TYPE_TARGET = 1;

    // UI elements.
    private Spinner mSourceSpinner;
    private Spinner mTargetSpinner;

    // Items that are not interacted with.
    // Provided by the caller, the new languages will be set here if the user
    // clicks "done".
    private final TranslateOptions mOptions;

    // This object will be used to keep the state for the time the
    // panel is opened it can be totally discarded in the end if the user
    // clicks "cancel".
    private TranslateOptions mSessionOptions;

    private LanguageArrayAdapter mSourceAdapter;
    private LanguageArrayAdapter mTargetAdapter;

    private final SubPanelListener mListener;

    /**
     * Display language drop downs so they can be picked as source or
     * target for a translation.
     *
     * @param listener triggered when the panel is closed
     * @param options will be modified with the new languages selected.
     */
    public TranslateLanguagePanel(SubPanelListener listener, TranslateOptions options) {
        mListener = listener;
        mOptions = options;
        mSessionOptions = new TranslateOptions(mOptions);
    }

    @Override
    public void createContent(Context context, InfoBarLayout layout) {
        mSourceSpinner = null;
        mTargetSpinner = null;

        String changeLanguage = context.getString(R.string.translate_infobar_change_languages);
        TextView panelMessage = (TextView) layout.findViewById(R.id.infobar_message);
        panelMessage.setText(changeLanguage);

        // Set up the spinners.
        createSpinners(context);
        layout.addGroup(mSourceSpinner, mTargetSpinner);

        // Set up the buttons.
        layout.addButtons(context.getString(R.string.translate_button_done),
                context.getString(R.string.cancel));
    }

    @Override
    public void onButtonClicked(boolean primary) {
        if (primary) {
            mOptions.setSourceLanguage(mSessionOptions.sourceLanguageIndex());
            mOptions.setTargetLanguage(mSessionOptions.targetLanguageIndex());
        }
        mListener.onPanelClosed(InfoBar.ACTION_TYPE_NONE);
    }

    private void createSpinners(Context context) {
        mSourceAdapter = new LanguageArrayAdapter(context, R.layout.translate_spinner,
                LANGUAGE_TYPE_SOURCE);
        mTargetAdapter = new LanguageArrayAdapter(context, R.layout.translate_spinner,
                LANGUAGE_TYPE_TARGET);

        // Determine how wide each spinner needs to be to avoid truncating its children.
        mSourceAdapter.addAll(createSpinnerLanguages(-1));
        mTargetAdapter.addAll(createSpinnerLanguages(-1));
        mSourceAdapter.measureWidthRequiredForView();
        mTargetAdapter.measureWidthRequiredForView();

        // Create the spinners.
        mSourceSpinner = new Spinner(context);
        mTargetSpinner = new Spinner(context);
        mSourceSpinner.setOnItemSelectedListener(this);
        mTargetSpinner.setOnItemSelectedListener(this);
        mSourceSpinner.setAdapter(mSourceAdapter);
        mTargetSpinner.setAdapter(mTargetAdapter);
        reloadSpinners();
    }

    private void reloadSpinners() {
        mSourceAdapter.clear();
        mTargetAdapter.clear();

        int sourceAvoidLanguage = mSessionOptions.targetLanguageIndex();
        int targetAvoidLanguage = mSessionOptions.sourceLanguageIndex();
        mSourceAdapter.addAll(createSpinnerLanguages(sourceAvoidLanguage));
        mTargetAdapter.addAll(createSpinnerLanguages(targetAvoidLanguage));

        int originalSourceSelection = mSourceSpinner.getSelectedItemPosition();
        int newSourceSelection = getSelectionPosition(LANGUAGE_TYPE_SOURCE);
        if (originalSourceSelection != newSourceSelection)
            mSourceSpinner.setSelection(newSourceSelection);

        int originalTargetSelection = mTargetSpinner.getSelectedItemPosition();
        int newTargetSelection = getSelectionPosition(LANGUAGE_TYPE_TARGET);
        if (originalTargetSelection != newTargetSelection)
            mTargetSpinner.setSelection(newTargetSelection);
    }

    private int getSelectionPosition(int languageType) {
        int position = languageType == LANGUAGE_TYPE_SOURCE ? mSessionOptions.sourceLanguageIndex()
                : mSessionOptions.targetLanguageIndex();

        // Since the source and target languages cannot appear in both spinners, the index for the
        // source language can be off by one if comes after the target language alphabetically (and
        // vice versa).
        int opposite = languageType == LANGUAGE_TYPE_SOURCE ? mSessionOptions.targetLanguageIndex()
                : mSessionOptions.sourceLanguageIndex();
        if (opposite < position) position -= 1;

        return position;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int position, long id) {
        Spinner spinner = (Spinner) adapter;
        int newId = ((SpinnerLanguageElement) spinner.getSelectedItem()).getLanguageId();
        if (spinner == mSourceSpinner) {
            mSessionOptions.setSourceLanguage(newId);
        } else {
            mSessionOptions.setTargetLanguage(newId);
        }
        reloadSpinners();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapter) {
    }

    /**
     * Determines what languages will be shown in the Spinner.
     * @param avoidLanguage Index of the language to avoid.  Use -1 to display all languages.
     */
    private ArrayList<SpinnerLanguageElement> createSpinnerLanguages(int avoidLanguage) {
        ArrayList<SpinnerLanguageElement> result = new ArrayList<SpinnerLanguageElement>();
        List<String> languages = mSessionOptions.allLanguages();
        for (int i = 0; i <  languages.size(); ++i) {
            if (i != avoidLanguage) {
                result.add(new SpinnerLanguageElement(languages.get(i), i));
            }
        }
        return result;
    }

    /**
     * The drop down view displayed to show the currently selected value.
     */
    private static class LanguageArrayAdapter extends ArrayAdapter<SpinnerLanguageElement> {
        private final SpannableString mTextTemplate;
        private int mMinimumWidth;

        public LanguageArrayAdapter(Context context, int textViewResourceId,
                int languageType) {
            super(context, textViewResourceId);

            // Get the string that we will display inside the Spinner, indicating whether the
            // spinner is used for the source or target language.
            String textTemplate = languageType == LANGUAGE_TYPE_SOURCE
                    ? context.getString(R.string.translate_options_source_hint)
                    : context.getString(R.string.translate_options_target_hint);
            mTextTemplate = new SpannableString(textTemplate);
            mTextTemplate.setSpan(
                    new ForegroundColorSpan(Color.GRAY), 0, textTemplate.length(), 0);
        }

        /** Measures how large the view needs to be to avoid truncating its children. */
        public void measureWidthRequiredForView() {
            mMinimumWidth = 0;

            final int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

            FrameLayout layout = new FrameLayout(getContext());
            TextView estimator = (TextView) LayoutInflater.from(getContext()).inflate(
                    R.layout.infobar_text, null);
            layout.addView(estimator);
            for (int i = 0; i < getCount(); ++i) {
                estimator.setText(getStringForLanguage(i));
                estimator.measure(spec, spec);
                mMinimumWidth = Math.max(mMinimumWidth, estimator.getMeasuredWidth());
            }
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView result;
            if (!(convertView instanceof TextView)) {
                result = (TextView) LayoutInflater.from(getContext()).inflate(
                        R.layout.infobar_spinner_item, null);
            } else {
                result = (TextView) convertView;
            }

            String language = ((SpinnerLanguageElement) getItem(position)).toString();
            result.setText(language);
            return result;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView result;
            if (!(convertView instanceof TextView)) {
                result = (TextView) LayoutInflater.from(getContext()).inflate(
                        R.layout.infobar_text, null);
            } else {
                result = (TextView) convertView;
            }
            result.setEllipsize(TextUtils.TruncateAt.END);
            result.setMaxLines(1);
            result.setText(getStringForLanguage(position));
            result.setMinWidth(mMinimumWidth);
            return result;
        }

        private CharSequence getStringForLanguage(int position) {
            // The spinners prepend a string to show if they're for the source or target language.
            String language = getItem(position).toString();
            SpannableString lang = new SpannableString(language);
            lang.setSpan(new ForegroundColorSpan(Color.BLACK), 0, lang.length(), 0);
            return TextUtils.expandTemplate(mTextTemplate, lang);
        }
    }

    /**
     * The element that goes inside the spinner.
     */
    private static class SpinnerLanguageElement {
        private final String mLanguageName;
        private final int mLanguageId;

        public SpinnerLanguageElement(String languageName, int languageId) {
            mLanguageName = languageName;
            mLanguageId = languageId;
        }

        public int getLanguageId() {
            return mLanguageId;
        }

        /**
         * This is the text displayed in the spinner element so make sure no debug information
         * is added.
         */
        @Override
        public String toString() {
            return mLanguageName;
        }
    }
}

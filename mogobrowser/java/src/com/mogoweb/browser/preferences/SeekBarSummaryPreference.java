///*
// * Copyright (C) 2011 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.mogoweb.browser.preferences;
//
//import android.content.Context;
//import android.preference.SeekBarPreference;
//import android.text.TextUtils;
//import android.util.AttributeSet;
//import android.view.View;
//import android.widget.SeekBar;
//import android.widget.TextView;
//
//import com.mogoweb.browser.R;
//
//public class SeekBarSummaryPreference extends SeekBarPreference {
//
//    CharSequence mSummary;
//    TextView mSummaryView;
//
//    public SeekBarSummaryPreference(
//            Context context, AttributeSet attrs, int defStyle) {
//        super(context, attrs, defStyle);
//        init();
//    }
//
//    public SeekBarSummaryPreference(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init();
//    }
//
//    public SeekBarSummaryPreference(Context context) {
//        super(context);
//        init();
//    }
//
//    void init() {
//        setWidgetLayoutResource(R.layout.font_size_widget);
//    }
//
//    @Override
//    public void setSummary(CharSequence summary) {
//        mSummary = summary;
//        if (mSummaryView != null) {
//            mSummaryView.setText(mSummary);
//        }
//    }
//
//    @Override
//    public CharSequence getSummary() {
//        return null;
//    }
//
//    @Override
//    protected void onBindView(View view) {
//        super.onBindView(view);
//        mSummaryView = (TextView) view.findViewById(R.id.text);
//        if (TextUtils.isEmpty(mSummary)) {
//            mSummaryView.setVisibility(View.GONE);
//        } else {
//            mSummaryView.setVisibility(View.VISIBLE);
//            mSummaryView.setText(mSummary);
//        }
//    }
//
//    @Override
//    public void onStartTrackingTouch(SeekBar seekBar) {
//        // Intentionally blank - prevent super.onStartTrackingTouch from running
//    }
//
//    @Override
//    public void onStopTrackingTouch(SeekBar seekBar) {
//        // Intentionally blank - prevent onStopTrackingTouch from running
//    }
//
//}

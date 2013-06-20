/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mogoweb.browser.preferences;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class NonformattingListPreference extends ListPreference {

    private CharSequence mSummary;

    public NonformattingListPreference(Context context) {
        super(context);
    }

    public NonformattingListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setSummary(CharSequence summary) {
        mSummary = summary;
        super.setSummary(summary);
    }

    @Override
    public CharSequence getSummary() {
        if (mSummary != null) {
            return mSummary;
        }
        return super.getSummary();
    }

}

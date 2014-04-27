// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.autofill;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.ui.R;

import java.util.ArrayList;
import java.util.Set;

/**
 * Autofill suggestion adapter for AutofillWindow.
 */
public class AutofillListAdapter extends ArrayAdapter<AutofillSuggestion> {
    private Context mContext;
    private Set<Integer> mSeparators;

    AutofillListAdapter(Context context,
                        ArrayList<AutofillSuggestion> objects,
                        Set<Integer> separators) {
        super(context, R.layout.autofill_text, objects);
        mSeparators = separators;
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View layout = convertView;
        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            layout = inflater.inflate(R.layout.autofill_text, null);
            ApiCompatibilityUtils.setBackgroundForView(layout, new AutofillDividerDrawable());
        }
        TextView labelView = (TextView) layout.findViewById(R.id.autofill_label);
        labelView.setText(getItem(position).mLabel);

        AutofillDividerDrawable divider = (AutofillDividerDrawable) layout.getBackground();
        int height = mContext.getResources().getDimensionPixelSize(R.dimen.autofill_text_height);
        if (position == 0) {
            divider.setColor(Color.TRANSPARENT);
        } else {
            int dividerHeight = mContext.getResources().getDimensionPixelSize(
                    R.dimen.autofill_text_divider_height);
            height += dividerHeight;
            divider.setHeight(dividerHeight);
            if (mSeparators.contains(position)) {
                divider.setColor(mContext.getResources().getColor(
                                 R.color.autofill_dark_divider_color));
            } else {
                divider.setColor(mContext.getResources().getColor(
                                 R.color.autofill_divider_color));
            }
        }
        layout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, height));

        TextView sublabelView = (TextView) layout.findViewById(R.id.autofill_sublabel);
        CharSequence sublabel = getItem(position).mSublabel;
        if (TextUtils.isEmpty(sublabel)) {
            sublabelView.setVisibility(View.GONE);
        } else {
            sublabelView.setText(sublabel);
            sublabelView.setVisibility(View.VISIBLE);
        }

        return layout;
    }
}

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
//package com.mogoweb.browser.addbookmark;
//
//import android.content.Context;
//import android.view.View;
//import android.util.AttributeSet;
//import android.widget.AdapterView;
//import android.widget.Spinner;
//
///**
// * Special Spinner class with its own callback for when the selection is set, which
// * can be ignored by calling setSelectionIgnoringSelectionChange
// */
//public class FolderSpinner extends Spinner
//        implements AdapterView.OnItemSelectedListener {
//    private OnSetSelectionListener mOnSetSelectionListener;
//    private boolean mFireSetSelection;
//
//    /**
//     * Callback for knowing when the selection has been manually set.  Does not
//     * get called until the selected view has changed.
//     */
//    public interface OnSetSelectionListener {
//        public void onSetSelection(long id);
//    }
//
//    public FolderSpinner(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        super.setOnItemSelectedListener(this);
//    }
//
//    @Override
//    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener l) {
//        // Disallow setting an OnItemSelectedListener, since it is used by us
//        // to fire onSetSelection.
//        throw new RuntimeException("Cannot set an OnItemSelectedListener on a FolderSpinner");
//    }
//
//    public void setOnSetSelectionListener(OnSetSelectionListener l) {
//        mOnSetSelectionListener = l;
//    }
//
//    /**
//     * Call setSelection, without firing the callback
//     * @param position New position to select.
//     */
//    public void setSelectionIgnoringSelectionChange(int position) {
//        super.setSelection(position);
//    }
//
//    @Override
//    public void setSelection(int position) {
//        mFireSetSelection = true;
//        int oldPosition = getSelectedItemPosition();
//        super.setSelection(position);
//        if (mOnSetSelectionListener != null) {
//            if (oldPosition == position) {
//                long id = getAdapter().getItemId(position);
//                // Normally this is not called because the item did not actually
//                // change, but in this case, we still want it to be called.
//                onItemSelected(this, null, position, id);
//            }
//        }
//    }
//
//    @Override
//    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//        if (mFireSetSelection) {
//            mOnSetSelectionListener.onSetSelection(id);
//            mFireSetSelection = false;
//        }
//    }
//
//    @Override
//    public void onNothingSelected(AdapterView<?> parent) {}
//}
//

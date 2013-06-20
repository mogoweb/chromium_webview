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
//import com.mogoweb.browser.R;
//
//import android.content.Context;
//import android.graphics.drawable.Drawable;
//import android.view.Gravity;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.BaseAdapter;
//import android.widget.TextView;
//
///**
// * SpinnerAdapter used in the AddBookmarkPage to select where to save a
// * bookmark/folder.
// */
//public class FolderSpinnerAdapter extends BaseAdapter {
//
//    public static final int HOME_SCREEN = 0;
//    public static final int ROOT_FOLDER = 1;
//    public static final int OTHER_FOLDER = 2;
//    public static final int RECENT_FOLDER = 3;
//
//    private boolean mIncludeHomeScreen;
//    private boolean mIncludesRecentFolder;
//    private long mRecentFolderId;
//    private String mRecentFolderName;
//    private LayoutInflater mInflater;
//    private Context mContext;
//    private String mOtherFolderDisplayText;
//
//    public FolderSpinnerAdapter(Context context, boolean includeHomeScreen) {
//        mIncludeHomeScreen = includeHomeScreen;
//        mContext = context;
//        mInflater = LayoutInflater.from(mContext);
//    }
//
//    public void addRecentFolder(long folderId, String folderName) {
//        mIncludesRecentFolder = true;
//        mRecentFolderId = folderId;
//        mRecentFolderName = folderName;
//    }
//
//    public long recentFolderId() { return mRecentFolderId; }
//
//    private void bindView(int position, View view, boolean isDropDown) {
//        int labelResource;
//        int drawableResource;
//        if (!mIncludeHomeScreen) {
//            position++;
//        }
//        switch (position) {
//            case HOME_SCREEN:
//                labelResource = R.string.add_to_homescreen_menu_option;
//                drawableResource = R.drawable.ic_home_holo_dark;
//                break;
//            case ROOT_FOLDER:
//                labelResource = R.string.add_to_bookmarks_menu_option;
//                drawableResource = R.drawable.ic_bookmarks_holo_dark;
//                break;
//            case RECENT_FOLDER:
//                // Fall through and use the same icon resource
//            case OTHER_FOLDER:
//                labelResource = R.string.add_to_other_folder_menu_option;
//                drawableResource = R.drawable.ic_folder_holo_dark;
//                break;
//            default:
//                labelResource = 0;
//                drawableResource = 0;
//                // assert
//                break;
//        }
//        TextView textView = (TextView) view;
//        if (position == RECENT_FOLDER) {
//            textView.setText(mRecentFolderName);
//        } else if (position == OTHER_FOLDER && !isDropDown
//                && mOtherFolderDisplayText != null) {
//            textView.setText(mOtherFolderDisplayText);
//        } else {
//            textView.setText(labelResource);
//        }
//        textView.setGravity(Gravity.CENTER_VERTICAL);
//        Drawable drawable = mContext.getResources().getDrawable(drawableResource);
//        textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null,
//                null, null);
//    }
//
//    @Override
//    public View getDropDownView(int position, View convertView, ViewGroup parent) {
//        if (convertView == null) {
//            convertView = mInflater.inflate(
//                    android.R.layout.simple_spinner_dropdown_item, parent, false);
//        }
//        bindView(position, convertView, true);
//        return convertView;
//    }
//
//    @Override
//    public View getView(int position, View convertView, ViewGroup parent) {
//        if (convertView == null) {
//            convertView = mInflater.inflate(android.R.layout.simple_spinner_item,
//                    parent, false);
//        }
//        bindView(position, convertView, false);
//        return convertView;
//    }
//
//    @Override
//    public int getCount() {
//        int count = 2;
//        if (mIncludeHomeScreen) count++;
//        if (mIncludesRecentFolder) count++;
//        return count;
//    }
//
//    @Override
//    public Object getItem(int position) {
//        return null;
//    }
//
//    @Override
//    public long getItemId(int position) {
//        long id = position;
//        if (!mIncludeHomeScreen) {
//            id++;
//        }
//        return id;
//    }
//
//    @Override
//    public boolean hasStableIds() {
//        return true;
//    }
//
//    public void setOtherFolderDisplayText(String parentTitle) {
//        mOtherFolderDisplayText = parentTitle;
//        notifyDataSetChanged();
//    }
//
//    public void clearRecentFolder() {
//        if (mIncludesRecentFolder) {
//            mIncludesRecentFolder = false;
//            notifyDataSetChanged();
//        }
//    }
//}

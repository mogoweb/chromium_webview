///*
// * Copyright (C) 2011 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License"); you may not
// * use this file except in compliance with the License. You may obtain a copy of
// * the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// * License for the specific language governing permissions and limitations under
// * the License.
// */
//
//package com.mogoweb.browser;
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.util.AttributeSet;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.webkit.WebView;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
//public class NavTabView extends LinearLayout {
//
//    private ViewGroup mContent;
//    private Tab mTab;
//    private ImageView mClose;
//    private TextView mTitle;
//    private View mTitleBar;
//    ImageView mImage;
//    private OnClickListener mClickListener;
//    private boolean mHighlighted;
//
//    public NavTabView(Context context, AttributeSet attrs, int defStyle) {
//        super(context, attrs, defStyle);
//        init();
//    }
//
//    public NavTabView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init();
//    }
//
//    public NavTabView(Context context) {
//        super(context);
//        init();
//    }
//
//    private void init() {
//        LayoutInflater.from(mContext).inflate(R.layout.nav_tab_view, this);
//        mContent = (ViewGroup) findViewById(R.id.main);
//        mClose = (ImageView) findViewById(R.id.closetab);
//        mTitle = (TextView) findViewById(R.id.title);
//        mTitleBar = findViewById(R.id.titlebar);
//        mImage = (ImageView) findViewById(R.id.tab_view);
//    }
//
//    protected boolean isClose(View v) {
//        return v == mClose;
//    }
//
//    protected boolean isTitle(View v) {
//        return v == mTitleBar;
//    }
//
//    protected boolean isWebView(View v) {
//        return v == mImage;
//    }
//
//    private void setTitle() {
//        if (mTab == null) return;
//        if (mHighlighted) {
//            mTitle.setText(mTab.getUrl());
//        } else {
//            String txt = mTab.getTitle();
//            if (txt == null) {
//                txt = mTab.getUrl();
//            }
//            mTitle.setText(txt);
//        }
//        if (mTab.isSnapshot()) {
//            setTitleIcon(R.drawable.ic_history_holo_dark);
//        } else if (mTab.isPrivateBrowsingEnabled()) {
//            setTitleIcon(R.drawable.ic_incognito_holo_dark);
//        } else {
//            setTitleIcon(0);
//        }
//    }
//
//    private void setTitleIcon(int id) {
//        if (id == 0) {
//            mTitle.setPadding(mTitle.getCompoundDrawablePadding(), 0, 0, 0);
//        } else {
//            mTitle.setPadding(0, 0, 0, 0);
//        }
//        mTitle.setCompoundDrawablesWithIntrinsicBounds(id, 0, 0, 0);
//    }
//
//    protected boolean isHighlighted() {
//        return mHighlighted;
//    }
//
//    protected void setWebView(Tab tab) {
//        mTab = tab;
//        setTitle();
//        Bitmap image = tab.getScreenshot();
//        if (image != null) {
//            mImage.setImageBitmap(image);
//            if (tab != null) {
//                mImage.setContentDescription(tab.getTitle());
//            }
//        }
//    }
//
//    @Override
//    public void setOnClickListener(OnClickListener listener) {
//        mClickListener = listener;
//        mTitleBar.setOnClickListener(mClickListener);
//        mClose.setOnClickListener(mClickListener);
//        if (mImage != null) {
//            mImage.setOnClickListener(mClickListener);
//        }
//    }
//
//}

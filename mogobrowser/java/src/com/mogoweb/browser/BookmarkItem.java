///*
// * Copyright (C) 2008 The Android Open Source Project
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
//package com.mogoweb.browser;
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.drawable.Drawable;
//import android.view.LayoutInflater;
//import android.view.MotionEvent;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.HorizontalScrollView;
//import android.widget.ImageView;
//import android.widget.TextView;
//
///**
// *  Custom layout for an item representing a bookmark in the browser.
// */
//class BookmarkItem extends HorizontalScrollView {
//
//    final static int MAX_TEXTVIEW_LEN = 80;
//
//    protected TextView    mTextView;
//    protected TextView    mUrlText;
//    protected ImageView   mImageView;
//    protected String      mUrl;
//    protected String      mTitle;
//    protected boolean mEnableScrolling = false;
//
//    /**
//     *  Instantiate a bookmark item, including a default favicon.
//     *
//     *  @param context  The application context for the item.
//     */
//    BookmarkItem(Context context) {
//        super(context);
//
//        setClickable(false);
//        setEnableScrolling(false);
//        LayoutInflater factory = LayoutInflater.from(context);
//        factory.inflate(R.layout.history_item, this);
//        mTextView = (TextView) findViewById(R.id.title);
//        mUrlText = (TextView) findViewById(R.id.url);
//        mImageView = (ImageView) findViewById(R.id.favicon);
//        View star = findViewById(R.id.star);
//        star.setVisibility(View.GONE);
//    }
//
//    /**
//     *  Copy this BookmarkItem to item.
//     *  @param item BookmarkItem to receive the info from this BookmarkItem.
//     */
//    /* package */ void copyTo(BookmarkItem item) {
//        item.mTextView.setText(mTextView.getText());
//        item.mUrlText.setText(mUrlText.getText());
//        item.mImageView.setImageDrawable(mImageView.getDrawable());
//    }
//
//    /**
//     * Return the name assigned to this bookmark item.
//     */
//    /* package */ String getName() {
//        return mTitle;
//    }
//
//    /* package */ String getUrl() {
//        return mUrl;
//    }
//
//    /**
//     *  Set the favicon for this item.
//     *
//     *  @param b    The new bitmap for this item.
//     *              If it is null, will use the default.
//     */
//    /* package */ void setFavicon(Bitmap b) {
//        if (b != null) {
//            mImageView.setImageBitmap(b);
//        } else {
//            mImageView.setImageResource(R.drawable.app_web_browser_sm);
//        }
//    }
//
//    void setFaviconBackground(Drawable d) {
//        mImageView.setBackgroundDrawable(d);
//    }
//
//    /**
//     *  Set the new name for the bookmark item.
//     *
//     *  @param name The new name for the bookmark item.
//     */
//    /* package */ void setName(String name) {
//        if (name == null) {
//            return;
//        }
//
//        mTitle = name;
//
//        if (name.length() > MAX_TEXTVIEW_LEN) {
//            name = name.substring(0, MAX_TEXTVIEW_LEN);
//        }
//
//        mTextView.setText(name);
//    }
//
//    /**
//     *  Set the new url for the bookmark item.
//     *  @param url  The new url for the bookmark item.
//     */
//    /* package */ void setUrl(String url) {
//        if (url == null) {
//            return;
//        }
//
//        mUrl = url;
//
//        url = UrlUtils.stripUrl(url);
//        if (url.length() > MAX_TEXTVIEW_LEN) {
//            url = url.substring(0, MAX_TEXTVIEW_LEN);
//        }
//
//        mUrlText.setText(url);
//    }
//
//    void setEnableScrolling(boolean enable) {
//        mEnableScrolling = enable;
//        setFocusable(mEnableScrolling);
//        setFocusableInTouchMode(mEnableScrolling);
//        requestDisallowInterceptTouchEvent(!mEnableScrolling);
//        requestLayout();
//    }
//
//    @Override
//    public boolean onTouchEvent(MotionEvent ev) {
//        if (mEnableScrolling) {
//            return super.onTouchEvent(ev);
//        }
//        return false;
//    }
//
//    @Override
//    protected void measureChild(View child, int parentWidthMeasureSpec,
//            int parentHeightMeasureSpec) {
//        if (mEnableScrolling) {
//            super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
//            return;
//        }
//
//        final ViewGroup.LayoutParams lp = child.getLayoutParams();
//
//        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
//                mPaddingLeft + mPaddingRight, lp.width);
//        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
//                mPaddingTop + mPaddingBottom, lp.height);
//
//        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
//    }
//
//    @Override
//    protected void measureChildWithMargins(View child,
//            int parentWidthMeasureSpec, int widthUsed,
//            int parentHeightMeasureSpec, int heightUsed) {
//        if (mEnableScrolling) {
//            super.measureChildWithMargins(child, parentWidthMeasureSpec,
//                    widthUsed, parentHeightMeasureSpec, heightUsed);
//            return;
//        }
//
//        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
//
//        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
//                mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin
//                        + widthUsed, lp.width);
//        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
//                mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin
//                        + heightUsed, lp.height);
//
//        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
//    }
//}

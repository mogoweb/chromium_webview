///*
// * Copyright (C) 2006 The Android Open Source Project
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
//import android.database.Cursor;
//import android.graphics.Bitmap;
//import android.graphics.drawable.BitmapDrawable;
//import android.provider.BrowserContract.Bookmarks;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.ImageView;
//import android.widget.ImageView.ScaleType;
//import android.widget.TextView;
//
//import com.mogoweb.browser.util.ThreadedCursorAdapter;
//import com.mogoweb.browser.view.BookmarkContainer;
//
//public class BrowserBookmarksAdapter extends
//        ThreadedCursorAdapter<BrowserBookmarksAdapterItem> {
//
//    LayoutInflater mInflater;
//    Context mContext;
//
//    /**
//     *  Create a new BrowserBookmarksAdapter.
//     */
//    public BrowserBookmarksAdapter(Context context) {
//        // Make sure to tell the CursorAdapter to avoid the observer and auto-requery
//        // since the Loader will do that for us.
//        super(context, null);
//        mInflater = LayoutInflater.from(context);
//        mContext = context;
//    }
//
//    @Override
//    protected long getItemId(Cursor c) {
//        return c.getLong(BookmarksLoader.COLUMN_INDEX_ID);
//    }
//
//    @Override
//    public View newView(Context context, ViewGroup parent) {
//        return mInflater.inflate(R.layout.bookmark_thumbnail, parent, false);
//    }
//
//    @Override
//    public void bindView(View view, BrowserBookmarksAdapterItem object) {
//        BookmarkContainer container = (BookmarkContainer) view;
//        container.setIgnoreRequestLayout(true);
//        bindGridView(view, mContext, object);
//        container.setIgnoreRequestLayout(false);
//    }
//
//    CharSequence getTitle(Cursor cursor) {
//        int type = cursor.getInt(BookmarksLoader.COLUMN_INDEX_TYPE);
//        switch (type) {
//        case Bookmarks.BOOKMARK_TYPE_OTHER_FOLDER:
//            return mContext.getText(R.string.other_bookmarks);
//        }
//        return cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
//    }
//
//    void bindGridView(View view, Context context, BrowserBookmarksAdapterItem item) {
//        // We need to set this to handle rotation and other configuration change
//        // events. If the padding didn't change, this is a no op.
//        int padding = context.getResources()
//                .getDimensionPixelSize(R.dimen.combo_horizontalSpacing);
//        view.setPadding(padding, view.getPaddingTop(),
//                padding, view.getPaddingBottom());
//        ImageView thumb = (ImageView) view.findViewById(R.id.thumb);
//        TextView tv = (TextView) view.findViewById(R.id.label);
//
//        tv.setText(item.title);
//        if (item.is_folder) {
//            // folder
//            thumb.setImageResource(R.drawable.thumb_bookmark_widget_folder_holo);
//            thumb.setScaleType(ScaleType.FIT_END);
//            thumb.setBackground(null);
//        } else {
//            thumb.setScaleType(ScaleType.CENTER_CROP);
//            if (item.thumbnail == null || !item.has_thumbnail) {
//                thumb.setImageResource(R.drawable.browser_thumbnail);
//            } else {
//                thumb.setImageDrawable(item.thumbnail);
//            }
//            thumb.setBackgroundResource(R.drawable.border_thumb_bookmarks_widget_holo);
//        }
//    }
//
//    @Override
//    public BrowserBookmarksAdapterItem getRowObject(Cursor c,
//            BrowserBookmarksAdapterItem item) {
//        if (item == null) {
//            item = new BrowserBookmarksAdapterItem();
//        }
//        Bitmap thumbnail = item.thumbnail != null ? item.thumbnail.getBitmap() : null;
//        thumbnail = BrowserBookmarksPage.getBitmap(c,
//                BookmarksLoader.COLUMN_INDEX_THUMBNAIL, thumbnail);
//        item.has_thumbnail = thumbnail != null;
//        if (thumbnail != null
//                && (item.thumbnail == null || item.thumbnail.getBitmap() != thumbnail)) {
//            item.thumbnail = new BitmapDrawable(mContext.getResources(), thumbnail);
//        }
//        item.is_folder = c.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) != 0;
//        item.title = getTitle(c);
//        item.url = c.getString(BookmarksLoader.COLUMN_INDEX_URL);
//        return item;
//    }
//
//    @Override
//    public BrowserBookmarksAdapterItem getLoadingObject() {
//        BrowserBookmarksAdapterItem item = new BrowserBookmarksAdapterItem();
//        return item;
//    }
//}

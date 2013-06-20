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
//package com.mogoweb.browser.view;
//
//import android.content.Context;
//import android.database.DataSetObserver;
//import android.provider.BrowserContract;
//import android.util.AttributeSet;
//import android.view.ContextMenu;
//import android.view.ContextMenu.ContextMenuInfo;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.BaseExpandableListAdapter;
//import android.widget.ExpandableListAdapter;
//import android.widget.ExpandableListView;
//import android.widget.FrameLayout;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
//import com.android.internal.view.menu.MenuBuilder;
//import com.mogoweb.browser.BreadCrumbView;
//import com.mogoweb.browser.BrowserBookmarksAdapter;
//import com.mogoweb.browser.R;
//
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//
//public class BookmarkExpandableView extends ExpandableListView
//        implements BreadCrumbView.Controller {
//
//    public static final String LOCAL_ACCOUNT_NAME = "local";
//
//    private BookmarkAccountAdapter mAdapter;
//    private int mColumnWidth;
//    private Context mContext;
//    private OnChildClickListener mOnChildClickListener;
//    private ContextMenuInfo mContextMenuInfo = null;
//    private OnCreateContextMenuListener mOnCreateContextMenuListener;
//    private boolean mLongClickable;
//    private BreadCrumbView.Controller mBreadcrumbController;
//    private int mMaxColumnCount;
//
//    public BookmarkExpandableView(Context context) {
//        super(context);
//        init(context);
//    }
//
//    public BookmarkExpandableView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init(context);
//    }
//
//    public BookmarkExpandableView(
//            Context context, AttributeSet attrs, int defStyle) {
//        super(context, attrs, defStyle);
//        init(context);
//    }
//
//    void init(Context context) {
//        mContext = context;
//        setItemsCanFocus(true);
//        setLongClickable(false);
//        mMaxColumnCount = mContext.getResources()
//                .getInteger(R.integer.max_bookmark_columns);
//        setScrollBarStyle(SCROLLBARS_OUTSIDE_OVERLAY);
//        mAdapter = new BookmarkAccountAdapter(mContext);
//        super.setAdapter(mAdapter);
//    }
//
//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        int width = MeasureSpec.getSize(widthMeasureSpec);
//        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
//        if (width > 0) {
//            mAdapter.measureChildren(width);
//            setPadding(mAdapter.mRowPadding, 0, mAdapter.mRowPadding, 0);
//            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, widthMode);
//        }
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        if (width != getMeasuredWidth()) {
//            mAdapter.measureChildren(getMeasuredWidth());
//        }
//    }
//
//    @Override
//    public void setAdapter(ExpandableListAdapter adapter) {
//        throw new RuntimeException("Not supported");
//    }
//
//    public void setColumnWidthFromLayout(int layout) {
//        LayoutInflater infalter = LayoutInflater.from(mContext);
//        View v = infalter.inflate(layout, this, false);
//        v.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
//        mColumnWidth = v.getMeasuredWidth();
//    }
//
//    public void clearAccounts() {
//        mAdapter.clear();
//    }
//
//    public void addAccount(String accountName, BrowserBookmarksAdapter adapter,
//            boolean expandGroup) {
//        // First, check if it already exists
//        int indexOf = mAdapter.mGroups.indexOf(accountName);
//        if (indexOf >= 0) {
//            BrowserBookmarksAdapter existing = mAdapter.mChildren.get(indexOf);
//            if (existing != adapter) {
//                existing.unregisterDataSetObserver(mAdapter.mObserver);
//                // Replace the existing one
//                mAdapter.mChildren.remove(indexOf);
//                mAdapter.mChildren.add(indexOf, adapter);
//                adapter.registerDataSetObserver(mAdapter.mObserver);
//            }
//        } else {
//            mAdapter.mGroups.add(accountName);
//            mAdapter.mChildren.add(adapter);
//            adapter.registerDataSetObserver(mAdapter.mObserver);
//        }
//        mAdapter.notifyDataSetChanged();
//        if (expandGroup) {
//            expandGroup(mAdapter.getGroupCount() - 1);
//        }
//    }
//
//    @Override
//    public void setOnChildClickListener(OnChildClickListener onChildClickListener) {
//        mOnChildClickListener = onChildClickListener;
//    }
//
//    @Override
//    public void setOnCreateContextMenuListener(OnCreateContextMenuListener l) {
//        mOnCreateContextMenuListener = l;
//        if (!mLongClickable) {
//            mLongClickable = true;
//            if (mAdapter != null) {
//                mAdapter.notifyDataSetChanged();
//            }
//        }
//    }
//
//    @Override
//    public void createContextMenu(ContextMenu menu) {
//        // The below is copied from View - we want to bypass the override
//        // in AbsListView
//
//        ContextMenuInfo menuInfo = getContextMenuInfo();
//
//        // Sets the current menu info so all items added to menu will have
//        // my extra info set.
//        ((MenuBuilder)menu).setCurrentMenuInfo(menuInfo);
//
//        onCreateContextMenu(menu);
//        if (mOnCreateContextMenuListener != null) {
//            mOnCreateContextMenuListener.onCreateContextMenu(menu, this, menuInfo);
//        }
//
//        // Clear the extra information so subsequent items that aren't mine don't
//        // have my extra info.
//        ((MenuBuilder)menu).setCurrentMenuInfo(null);
//
//        if (mParent != null) {
//            mParent.createContextMenu(menu);
//        }
//    }
//
//    @Override
//    public boolean showContextMenuForChild(View originalView) {
//        int groupPosition = (Integer) originalView.getTag(R.id.group_position);
//        int childPosition = (Integer) originalView.getTag(R.id.child_position);
//
//        mContextMenuInfo = new BookmarkContextMenuInfo(childPosition,
//                groupPosition);
//        if (getParent() != null) {
//            getParent().showContextMenuForChild(this);
//        }
//
//        return true;
//    }
//
//    @Override
//    public void onTop(BreadCrumbView view, int level, Object data) {
//        if (mBreadcrumbController != null) {
//            mBreadcrumbController.onTop(view, level, data);
//        }
//    }
//
//    public void setBreadcrumbController(BreadCrumbView.Controller controller) {
//        mBreadcrumbController = controller;
//    }
//
//    @Override
//    protected ContextMenuInfo getContextMenuInfo() {
//        return mContextMenuInfo;
//    }
//
//    public BrowserBookmarksAdapter getChildAdapter(int groupPosition) {
//        return mAdapter.mChildren.get(groupPosition);
//    }
//
//    private OnClickListener mChildClickListener = new OnClickListener() {
//
//        @Override
//        public void onClick(View v) {
//            if (v.getVisibility() != View.VISIBLE) {
//                return;
//            }
//            int groupPosition = (Integer) v.getTag(R.id.group_position);
//            int childPosition = (Integer) v.getTag(R.id.child_position);
//            if (mAdapter.getGroupCount() <= groupPosition
//                    || mAdapter.mChildren.get(groupPosition).getCount() <= childPosition) {
//                return;
//            }
//            long id = mAdapter.mChildren.get(groupPosition).getItemId(childPosition);
//            if (mOnChildClickListener != null) {
//                mOnChildClickListener.onChildClick(BookmarkExpandableView.this,
//                        v, groupPosition, childPosition, id);
//            }
//        }
//    };
//
//    private OnClickListener mGroupOnClickListener = new OnClickListener() {
//
//        @Override
//        public void onClick(View v) {
//            int groupPosition = (Integer) v.getTag(R.id.group_position);
//            if (isGroupExpanded(groupPosition)) {
//                collapseGroup(groupPosition);
//            } else {
//                expandGroup(groupPosition, true);
//            }
//        }
//    };
//
//    public BreadCrumbView getBreadCrumbs(int groupPosition) {
//        return mAdapter.getBreadCrumbView(groupPosition);
//    }
//
//    public JSONObject saveGroupState() throws JSONException {
//        JSONObject obj = new JSONObject();
//        int count = mAdapter.getGroupCount();
//        for (int i = 0; i < count; i++) {
//            String acctName = mAdapter.mGroups.get(i);
//            if (!isGroupExpanded(i)) {
//                obj.put(acctName != null ? acctName : LOCAL_ACCOUNT_NAME, false);
//            }
//        }
//        return obj;
//    }
//
//    class BookmarkAccountAdapter extends BaseExpandableListAdapter {
//        ArrayList<BrowserBookmarksAdapter> mChildren;
//        ArrayList<String> mGroups;
//        HashMap<Integer, BreadCrumbView> mBreadcrumbs =
//                new HashMap<Integer, BreadCrumbView>();
//        LayoutInflater mInflater;
//        int mRowCount = 1; // assume at least 1 child fits in a row
//        int mLastViewWidth = -1;
//        int mRowPadding = -1;
//        DataSetObserver mObserver = new DataSetObserver() {
//            @Override
//            public void onChanged() {
//                notifyDataSetChanged();
//            }
//
//            @Override
//            public void onInvalidated() {
//                notifyDataSetInvalidated();
//            }
//        };
//
//        public BookmarkAccountAdapter(Context context) {
//            mContext = context;
//            mInflater = LayoutInflater.from(mContext);
//            mChildren = new ArrayList<BrowserBookmarksAdapter>();
//            mGroups = new ArrayList<String>();
//        }
//
//        public void clear() {
//            mGroups.clear();
//            mChildren.clear();
//            notifyDataSetChanged();
//        }
//
//        @Override
//        public Object getChild(int groupPosition, int childPosition) {
//            return mChildren.get(groupPosition).getItem(childPosition);
//        }
//
//        @Override
//        public long getChildId(int groupPosition, int childPosition) {
//            return childPosition;
//        }
//
//        @Override
//        public View getChildView(int groupPosition, int childPosition,
//                boolean isLastChild, View convertView, ViewGroup parent) {
//            if (convertView == null) {
//                convertView = mInflater.inflate(R.layout.bookmark_grid_row, parent, false);
//            }
//            BrowserBookmarksAdapter childAdapter = mChildren.get(groupPosition);
//            int rowCount = mRowCount;
//            LinearLayout row = (LinearLayout) convertView;
//            if (row.getChildCount() > rowCount) {
//                row.removeViews(rowCount, row.getChildCount() - rowCount);
//            }
//            for (int i = 0; i < rowCount; i++) {
//                View cv = null;
//                if (row.getChildCount() > i) {
//                    cv = row.getChildAt(i);
//                }
//                int realChildPosition = (childPosition * rowCount) + i;
//                if (realChildPosition < childAdapter.getCount()) {
//                    View v = childAdapter.getView(realChildPosition, cv, row);
//                    v.setTag(R.id.group_position, groupPosition);
//                    v.setTag(R.id.child_position, realChildPosition);
//                    v.setOnClickListener(mChildClickListener);
//                    v.setLongClickable(mLongClickable);
//                    if (cv == null) {
//                        row.addView(v);
//                    } else if (cv != v) {
//                        row.removeViewAt(i);
//                        row.addView(v, i);
//                    } else {
//                        cv.setVisibility(View.VISIBLE);
//                    }
//                } else if (cv != null) {
//                    cv.setVisibility(View.GONE);
//                }
//            }
//            return row;
//        }
//
//        @Override
//        public int getChildrenCount(int groupPosition) {
//            BrowserBookmarksAdapter adapter = mChildren.get(groupPosition);
//            return (int) Math.ceil(adapter.getCount() / (float)mRowCount);
//        }
//
//        @Override
//        public Object getGroup(int groupPosition) {
//            return mChildren.get(groupPosition);
//        }
//
//        @Override
//        public int getGroupCount() {
//            return mGroups.size();
//        }
//
//        public void measureChildren(int viewWidth) {
//            if (mLastViewWidth == viewWidth) return;
//
//            int rowCount = viewWidth / mColumnWidth;
//            if (mMaxColumnCount > 0) {
//                rowCount = Math.min(rowCount, mMaxColumnCount);
//            }
//            int rowPadding = (viewWidth - (rowCount * mColumnWidth)) / 2;
//            boolean notify = rowCount != mRowCount || rowPadding != mRowPadding;
//            mRowCount = rowCount;
//            mRowPadding = rowPadding;
//            mLastViewWidth = viewWidth;
//            if (notify) {
//                notifyDataSetChanged();
//            }
//        }
//
//        @Override
//        public long getGroupId(int groupPosition) {
//            return groupPosition;
//        }
//
//        @Override
//        public View getGroupView(int groupPosition, boolean isExpanded,
//                View view, ViewGroup parent) {
//            if (view == null) {
//                view = mInflater.inflate(R.layout.bookmark_group_view, parent, false);
//                view.setOnClickListener(mGroupOnClickListener);
//            }
//            view.setTag(R.id.group_position, groupPosition);
//            FrameLayout crumbHolder = (FrameLayout) view.findViewById(R.id.crumb_holder);
//            crumbHolder.removeAllViews();
//            BreadCrumbView crumbs = getBreadCrumbView(groupPosition);
//            if (crumbs.getParent() != null) {
//                ((ViewGroup)crumbs.getParent()).removeView(crumbs);
//            }
//            crumbHolder.addView(crumbs);
//            TextView name = (TextView) view.findViewById(R.id.group_name);
//            String groupName = mGroups.get(groupPosition);
//            if (groupName == null) {
//                groupName = mContext.getString(R.string.local_bookmarks);
//            }
//            name.setText(groupName);
//            return view;
//        }
//
//        public BreadCrumbView getBreadCrumbView(int groupPosition) {
//            BreadCrumbView crumbs = mBreadcrumbs.get(groupPosition);
//            if (crumbs == null) {
//                crumbs = (BreadCrumbView)
//                        mInflater.inflate(R.layout.bookmarks_header, null);
//                crumbs.setController(BookmarkExpandableView.this);
//                crumbs.setUseBackButton(true);
//                crumbs.setMaxVisible(2);
//                String bookmarks = mContext.getString(R.string.bookmarks);
//                crumbs.pushView(bookmarks, false,
//                        BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER);
//                crumbs.setTag(R.id.group_position, groupPosition);
//                crumbs.setVisibility(View.GONE);
//                mBreadcrumbs.put(groupPosition, crumbs);
//            }
//            return crumbs;
//        }
//
//        @Override
//        public boolean hasStableIds() {
//            return false;
//        }
//
//        @Override
//        public boolean isChildSelectable(int groupPosition, int childPosition) {
//            return true;
//        }
//    }
//
//    public static class BookmarkContextMenuInfo implements ContextMenuInfo {
//
//        private BookmarkContextMenuInfo(int childPosition, int groupPosition) {
//            this.childPosition = childPosition;
//            this.groupPosition = groupPosition;
//        }
//
//        public int childPosition;
//        public int groupPosition;
//    }
//
//}

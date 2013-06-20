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
//import android.app.Activity;
//import android.app.AlertDialog;
//import android.app.Dialog;
//import android.app.Fragment;
//import android.app.FragmentBreadCrumbs;
//import android.app.LoaderManager.LoaderCallbacks;
//import android.content.ClipboardManager;
//import android.content.ContentResolver;
//import android.content.Context;
//import android.content.CursorLoader;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.content.Loader;
//import android.content.pm.PackageManager;
//import android.content.pm.ResolveInfo;
//import android.database.Cursor;
//import android.database.DataSetObserver;
//import android.graphics.BitmapFactory;
//import android.graphics.drawable.Drawable;
//import android.net.Uri;
//import android.os.Bundle;
//import android.provider.Browser;
//import android.provider.BrowserContract;
//import android.provider.BrowserContract.Combined;
//import android.view.ContextMenu;
//import android.view.ContextMenu.ContextMenuInfo;
//import android.view.LayoutInflater;
//import android.view.Menu;
//import android.view.MenuInflater;
//import android.view.MenuItem;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.ViewStub;
//import android.widget.AbsListView;
//import android.widget.AdapterView;
//import android.widget.AdapterView.AdapterContextMenuInfo;
//import android.widget.AdapterView.OnItemClickListener;
//import android.widget.BaseAdapter;
//import android.widget.ExpandableListView;
//import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
//import android.widget.ExpandableListView.OnChildClickListener;
//import android.widget.ListView;
//import android.widget.TextView;
//import android.widget.Toast;
//
///**
// * Activity for displaying the browser's history, divided into
// * days of viewing.
// */
//public class BrowserHistoryPage extends Fragment
//        implements LoaderCallbacks<Cursor>, OnChildClickListener {
//
//    static final int LOADER_HISTORY = 1;
//    static final int LOADER_MOST_VISITED = 2;
//
//    CombinedBookmarksCallbacks mCallback;
//    HistoryAdapter mAdapter;
//    HistoryChildWrapper mChildWrapper;
//    boolean mDisableNewWindow;
//    HistoryItem mContextHeader;
//    String mMostVisitsLimit;
//    ListView mGroupList, mChildList;
//    private ViewGroup mPrefsContainer;
//    private FragmentBreadCrumbs mFragmentBreadCrumbs;
//    private ExpandableListView mHistoryList;
//
//    private View mRoot;
//
//    static interface HistoryQuery {
//        static final String[] PROJECTION = new String[] {
//                Combined._ID, // 0
//                Combined.DATE_LAST_VISITED, // 1
//                Combined.TITLE, // 2
//                Combined.URL, // 3
//                Combined.FAVICON, // 4
//                Combined.VISITS, // 5
//                Combined.IS_BOOKMARK, // 6
//        };
//
//        static final int INDEX_ID = 0;
//        static final int INDEX_DATE_LAST_VISITED = 1;
//        static final int INDEX_TITE = 2;
//        static final int INDEX_URL = 3;
//        static final int INDEX_FAVICON = 4;
//        static final int INDEX_VISITS = 5;
//        static final int INDEX_IS_BOOKMARK = 6;
//    }
//
//    private void copy(CharSequence text) {
//        ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(
//                Context.CLIPBOARD_SERVICE);
//        cm.setText(text);
//    }
//
//    @Override
//    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
//        Uri.Builder combinedBuilder = Combined.CONTENT_URI.buildUpon();
//
//        switch (id) {
//            case LOADER_HISTORY: {
//                String sort = Combined.DATE_LAST_VISITED + " DESC";
//                String where = Combined.VISITS + " > 0";
//                CursorLoader loader = new CursorLoader(getActivity(), combinedBuilder.build(),
//                        HistoryQuery.PROJECTION, where, null, sort);
//                return loader;
//            }
//
//            case LOADER_MOST_VISITED: {
//                Uri uri = combinedBuilder
//                        .appendQueryParameter(BrowserContract.PARAM_LIMIT, mMostVisitsLimit)
//                        .build();
//                String where = Combined.VISITS + " > 0";
//                CursorLoader loader = new CursorLoader(getActivity(), uri,
//                        HistoryQuery.PROJECTION, where, null, Combined.VISITS + " DESC");
//                return loader;
//            }
//
//            default: {
//                throw new IllegalArgumentException();
//            }
//        }
//    }
//
//    void selectGroup(int position) {
//        mGroupItemClickListener.onItemClick(null,
//                mAdapter.getGroupView(position, false, null, null),
//                position, position);
//    }
//
//    void checkIfEmpty() {
//        if (mAdapter.mMostVisited != null && mAdapter.mHistoryCursor != null) {
//            // Both cursors have loaded - check to see if we have data
//            if (mAdapter.isEmpty()) {
//                mRoot.findViewById(R.id.history).setVisibility(View.GONE);
//                mRoot.findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
//            } else {
//                mRoot.findViewById(R.id.history).setVisibility(View.VISIBLE);
//                mRoot.findViewById(android.R.id.empty).setVisibility(View.GONE);
//            }
//        }
//    }
//
//    @Override
//    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
//        switch (loader.getId()) {
//            case LOADER_HISTORY: {
//                mAdapter.changeCursor(data);
//                if (!mAdapter.isEmpty() && mGroupList != null
//                        && mGroupList.getCheckedItemPosition() == ListView.INVALID_POSITION) {
//                    selectGroup(0);
//                }
//
//                checkIfEmpty();
//                break;
//            }
//
//            case LOADER_MOST_VISITED: {
//                mAdapter.changeMostVisitedCursor(data);
//
//                checkIfEmpty();
//                break;
//            }
//
//            default: {
//                throw new IllegalArgumentException();
//            }
//        }
//    }
//
//    @Override
//    public void onLoaderReset(Loader<Cursor> loader) {
//    }
//
//    @Override
//    public void onCreate(Bundle icicle) {
//        super.onCreate(icicle);
//
//        setHasOptionsMenu(true);
//
//        Bundle args = getArguments();
//        mDisableNewWindow = args.getBoolean(BrowserBookmarksPage.EXTRA_DISABLE_WINDOW, false);
//        int mvlimit = getResources().getInteger(R.integer.most_visits_limit);
//        mMostVisitsLimit = Integer.toString(mvlimit);
//        mCallback = (CombinedBookmarksCallbacks) getActivity();
//    }
//
//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container,
//            Bundle savedInstanceState) {
//        mRoot = inflater.inflate(R.layout.history, container, false);
//        mAdapter = new HistoryAdapter(getActivity());
//        ViewStub stub = (ViewStub) mRoot.findViewById(R.id.pref_stub);
//        if (stub != null) {
//            inflateTwoPane(stub);
//        } else {
//            inflateSinglePane();
//        }
//
//        // Start the loaders
//        getLoaderManager().restartLoader(LOADER_HISTORY, null, this);
//        getLoaderManager().restartLoader(LOADER_MOST_VISITED, null, this);
//
//        return mRoot;
//    }
//
//    private void inflateSinglePane() {
//        mHistoryList = (ExpandableListView) mRoot.findViewById(R.id.history);
//        mHistoryList.setAdapter(mAdapter);
//        mHistoryList.setOnChildClickListener(this);
//        registerForContextMenu(mHistoryList);
//    }
//
//    private void inflateTwoPane(ViewStub stub) {
//        stub.setLayoutResource(R.layout.preference_list_content);
//        stub.inflate();
//        mGroupList = (ListView) mRoot.findViewById(android.R.id.list);
//        mPrefsContainer = (ViewGroup) mRoot.findViewById(R.id.prefs_frame);
//        mFragmentBreadCrumbs = (FragmentBreadCrumbs) mRoot.findViewById(android.R.id.title);
//        mFragmentBreadCrumbs.setMaxVisible(1);
//        mFragmentBreadCrumbs.setActivity(getActivity());
//        mPrefsContainer.setVisibility(View.VISIBLE);
//        mGroupList.setAdapter(new HistoryGroupWrapper(mAdapter));
//        mGroupList.setOnItemClickListener(mGroupItemClickListener);
//        mGroupList.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
//        mChildWrapper = new HistoryChildWrapper(mAdapter);
//        mChildList = new ListView(getActivity());
//        mChildList.setAdapter(mChildWrapper);
//        mChildList.setOnItemClickListener(mChildItemClickListener);
//        registerForContextMenu(mChildList);
//        ViewGroup prefs = (ViewGroup) mRoot.findViewById(R.id.prefs);
//        prefs.addView(mChildList);
//    }
//
//    private OnItemClickListener mGroupItemClickListener = new OnItemClickListener() {
//        @Override
//        public void onItemClick(
//                AdapterView<?> parent, View view, int position, long id) {
//            CharSequence title = ((TextView) view).getText();
//            mFragmentBreadCrumbs.setTitle(title, title);
//            mChildWrapper.setSelectedGroup(position);
//            mGroupList.setItemChecked(position, true);
//        }
//    };
//
//    private OnItemClickListener mChildItemClickListener = new OnItemClickListener() {
//        @Override
//        public void onItemClick(
//                AdapterView<?> parent, View view, int position, long id) {
//            mCallback.openUrl(((HistoryItem) view).getUrl());
//        }
//    };
//
//    @Override
//    public boolean onChildClick(ExpandableListView parent, View view,
//            int groupPosition, int childPosition, long id) {
//        mCallback.openUrl(((HistoryItem) view).getUrl());
//        return true;
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        getLoaderManager().destroyLoader(LOADER_HISTORY);
//        getLoaderManager().destroyLoader(LOADER_MOST_VISITED);
//    }
//
//    @Override
//    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        super.onCreateOptionsMenu(menu, inflater);
//        inflater.inflate(R.menu.history, menu);
//    }
//
//    void promptToClearHistory() {
//        final ContentResolver resolver = getActivity().getContentResolver();
//        final ClearHistoryTask clear = new ClearHistoryTask(resolver);
//        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
//                .setMessage(R.string.pref_privacy_clear_history_dlg)
//                .setIconAttribute(android.R.attr.alertDialogIcon)
//                .setNegativeButton(R.string.cancel, null)
//                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
//                     @Override
//                     public void onClick(DialogInterface dialog, int which) {
//                         if (which == DialogInterface.BUTTON_POSITIVE) {
//                             clear.start();
//                         }
//                     }
//                });
//        final Dialog dialog = builder.create();
//        dialog.show();
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        if (item.getItemId() == R.id.clear_history_menu_id) {
//            promptToClearHistory();
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }
//
//    static class ClearHistoryTask extends Thread {
//        ContentResolver mResolver;
//
//        public ClearHistoryTask(ContentResolver resolver) {
//            mResolver = resolver;
//        }
//
//        @Override
//        public void run() {
//            Browser.clearHistory(mResolver);
//        }
//    }
//
//    View getTargetView(ContextMenuInfo menuInfo) {
//        if (menuInfo instanceof AdapterContextMenuInfo) {
//            return ((AdapterContextMenuInfo) menuInfo).targetView;
//        }
//        if (menuInfo instanceof ExpandableListContextMenuInfo) {
//            return ((ExpandableListContextMenuInfo) menuInfo).targetView;
//        }
//        return null;
//    }
//
//    @Override
//    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
//
//        View targetView = getTargetView(menuInfo);
//        if (!(targetView instanceof HistoryItem)) {
//            return;
//        }
//        HistoryItem historyItem = (HistoryItem) targetView;
//
//        // Inflate the menu
//        Activity parent = getActivity();
//        MenuInflater inflater = parent.getMenuInflater();
//        inflater.inflate(R.menu.historycontext, menu);
//
//        // Setup the header
//        if (mContextHeader == null) {
//            mContextHeader = new HistoryItem(parent, false);
//            mContextHeader.setEnableScrolling(true);
//        } else if (mContextHeader.getParent() != null) {
//            ((ViewGroup) mContextHeader.getParent()).removeView(mContextHeader);
//        }
//        historyItem.copyTo(mContextHeader);
//        menu.setHeaderView(mContextHeader);
//
//        // Only show open in new tab if it was not explicitly disabled
//        if (mDisableNewWindow) {
//            menu.findItem(R.id.new_window_context_menu_id).setVisible(false);
//        }
//        // For a bookmark, provide the option to remove it from bookmarks
//        if (historyItem.isBookmark()) {
//            MenuItem item = menu.findItem(R.id.save_to_bookmarks_menu_id);
//            item.setTitle(R.string.remove_from_bookmarks);
//        }
//        // decide whether to show the share link option
//        PackageManager pm = parent.getPackageManager();
//        Intent send = new Intent(Intent.ACTION_SEND);
//        send.setType("text/plain");
//        ResolveInfo ri = pm.resolveActivity(send, PackageManager.MATCH_DEFAULT_ONLY);
//        menu.findItem(R.id.share_link_context_menu_id).setVisible(ri != null);
//
//        super.onCreateContextMenu(menu, v, menuInfo);
//    }
//
//    @Override
//    public boolean onContextItemSelected(MenuItem item) {
//        ContextMenuInfo menuInfo = item.getMenuInfo();
//        if (menuInfo == null) {
//            return false;
//        }
//        View targetView = getTargetView(menuInfo);
//        if (!(targetView instanceof HistoryItem)) {
//            return false;
//        }
//        HistoryItem historyItem = (HistoryItem) targetView;
//        String url = historyItem.getUrl();
//        String title = historyItem.getName();
//        Activity activity = getActivity();
//        switch (item.getItemId()) {
//            case R.id.open_context_menu_id:
//                mCallback.openUrl(url);
//                return true;
//            case R.id.new_window_context_menu_id:
//                mCallback.openInNewTab(url);
//                return true;
//            case R.id.save_to_bookmarks_menu_id:
//                if (historyItem.isBookmark()) {
//                    Bookmarks.removeFromBookmarks(activity, activity.getContentResolver(),
//                            url, title);
//                } else {
//                    Browser.saveBookmark(activity, title, url);
//                }
//                return true;
//            case R.id.share_link_context_menu_id:
//                Browser.sendString(activity, url,
//                        activity.getText(R.string.choosertitle_sharevia).toString());
//                return true;
//            case R.id.copy_url_context_menu_id:
//                copy(url);
//                return true;
//            case R.id.delete_context_menu_id:
//                Browser.deleteFromHistory(activity.getContentResolver(), url);
//                return true;
//            case R.id.homepage_context_menu_id:
//                BrowserSettings.getInstance().setHomePage(url);
//                Toast.makeText(activity, R.string.homepage_set, Toast.LENGTH_LONG).show();
//                return true;
//            default:
//                break;
//        }
//        return super.onContextItemSelected(item);
//    }
//
//    private static abstract class HistoryWrapper extends BaseAdapter {
//
//        protected HistoryAdapter mAdapter;
//        private DataSetObserver mObserver = new DataSetObserver() {
//            @Override
//            public void onChanged() {
//                super.onChanged();
//                notifyDataSetChanged();
//            }
//
//            @Override
//            public void onInvalidated() {
//                super.onInvalidated();
//                notifyDataSetInvalidated();
//            }
//        };
//
//        public HistoryWrapper(HistoryAdapter adapter) {
//            mAdapter = adapter;
//            mAdapter.registerDataSetObserver(mObserver);
//        }
//
//    }
//    private static class HistoryGroupWrapper extends HistoryWrapper {
//
//        public HistoryGroupWrapper(HistoryAdapter adapter) {
//            super(adapter);
//        }
//
//        @Override
//        public int getCount() {
//            return mAdapter.getGroupCount();
//        }
//
//        @Override
//        public Object getItem(int position) {
//            return null;
//        }
//
//        @Override
//        public long getItemId(int position) {
//            return position;
//        }
//
//        @Override
//        public View getView(int position, View convertView, ViewGroup parent) {
//            return mAdapter.getGroupView(position, false, convertView, parent);
//        }
//
//    }
//
//    private static class HistoryChildWrapper extends HistoryWrapper {
//
//        private int mSelectedGroup;
//
//        public HistoryChildWrapper(HistoryAdapter adapter) {
//            super(adapter);
//        }
//
//        void setSelectedGroup(int groupPosition) {
//            mSelectedGroup = groupPosition;
//            notifyDataSetChanged();
//        }
//
//        @Override
//        public int getCount() {
//            return mAdapter.getChildrenCount(mSelectedGroup);
//        }
//
//        @Override
//        public Object getItem(int position) {
//            return null;
//        }
//
//        @Override
//        public long getItemId(int position) {
//            return position;
//        }
//
//        @Override
//        public View getView(int position, View convertView, ViewGroup parent) {
//            return mAdapter.getChildView(mSelectedGroup, position,
//                    false, convertView, parent);
//        }
//
//    }
//
//    private class HistoryAdapter extends DateSortedExpandableListAdapter {
//
//        private Cursor mMostVisited, mHistoryCursor;
//        Drawable mFaviconBackground;
//
//        HistoryAdapter(Context context) {
//            super(context, HistoryQuery.INDEX_DATE_LAST_VISITED);
//            mFaviconBackground = BookmarkUtils.createListFaviconBackground(context);
//        }
//
//        @Override
//        public void changeCursor(Cursor cursor) {
//            mHistoryCursor = cursor;
//            super.changeCursor(cursor);
//        }
//
//        void changeMostVisitedCursor(Cursor cursor) {
//            if (mMostVisited == cursor) {
//                return;
//            }
//            if (mMostVisited != null) {
//                mMostVisited.unregisterDataSetObserver(mDataSetObserver);
//                mMostVisited.close();
//            }
//            mMostVisited = cursor;
//            if (mMostVisited != null) {
//                mMostVisited.registerDataSetObserver(mDataSetObserver);
//            }
//            notifyDataSetChanged();
//        }
//
//        @Override
//        public long getChildId(int groupPosition, int childPosition) {
//            if (moveCursorToChildPosition(groupPosition, childPosition)) {
//                Cursor cursor = getCursor(groupPosition);
//                return cursor.getLong(HistoryQuery.INDEX_ID);
//            }
//            return 0;
//        }
//
//        @Override
//        public int getGroupCount() {
//            return super.getGroupCount() + (!isMostVisitedEmpty() ? 1 : 0);
//        }
//
//        @Override
//        public int getChildrenCount(int groupPosition) {
//            if (groupPosition >= super.getGroupCount()) {
//                if (isMostVisitedEmpty()) {
//                    return 0;
//                }
//                return mMostVisited.getCount();
//            }
//            return super.getChildrenCount(groupPosition);
//        }
//
//        @Override
//        public boolean isEmpty() {
//            if (!super.isEmpty()) {
//                return false;
//            }
//            return isMostVisitedEmpty();
//        }
//
//        private boolean isMostVisitedEmpty() {
//            return mMostVisited == null
//                    || mMostVisited.isClosed()
//                    || mMostVisited.getCount() == 0;
//        }
//
//        Cursor getCursor(int groupPosition) {
//            if (groupPosition >= super.getGroupCount()) {
//                return mMostVisited;
//            }
//            return mHistoryCursor;
//        }
//
//        @Override
//        public View getGroupView(int groupPosition, boolean isExpanded,
//                View convertView, ViewGroup parent) {
//            if (groupPosition >= super.getGroupCount()) {
//                if (mMostVisited == null || mMostVisited.isClosed()) {
//                    throw new IllegalStateException("Data is not valid");
//                }
//                TextView item;
//                if (null == convertView || !(convertView instanceof TextView)) {
//                    LayoutInflater factory = LayoutInflater.from(getContext());
//                    item = (TextView) factory.inflate(R.layout.history_header, null);
//                } else {
//                    item = (TextView) convertView;
//                }
//                item.setText(R.string.tab_most_visited);
//                return item;
//            }
//            return super.getGroupView(groupPosition, isExpanded, convertView, parent);
//        }
//
//        @Override
//        boolean moveCursorToChildPosition(
//                int groupPosition, int childPosition) {
//            if (groupPosition >= super.getGroupCount()) {
//                if (mMostVisited != null && !mMostVisited.isClosed()) {
//                    mMostVisited.moveToPosition(childPosition);
//                    return true;
//                }
//                return false;
//            }
//            return super.moveCursorToChildPosition(groupPosition, childPosition);
//        }
//
//        @Override
//        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
//                View convertView, ViewGroup parent) {
//            HistoryItem item;
//            if (null == convertView || !(convertView instanceof HistoryItem)) {
//                item = new HistoryItem(getContext());
//                // Add padding on the left so it will be indented from the
//                // arrows on the group views.
//                item.setPadding(item.getPaddingLeft() + 10,
//                        item.getPaddingTop(),
//                        item.getPaddingRight(),
//                        item.getPaddingBottom());
//                item.setFaviconBackground(mFaviconBackground);
//            } else {
//                item = (HistoryItem) convertView;
//            }
//
//            // Bail early if the Cursor is closed.
//            if (!moveCursorToChildPosition(groupPosition, childPosition)) {
//                return item;
//            }
//
//            Cursor cursor = getCursor(groupPosition);
//            item.setName(cursor.getString(HistoryQuery.INDEX_TITE));
//            String url = cursor.getString(HistoryQuery.INDEX_URL);
//            item.setUrl(url);
//            byte[] data = cursor.getBlob(HistoryQuery.INDEX_FAVICON);
//            if (data != null) {
//                item.setFavicon(BitmapFactory.decodeByteArray(data, 0,
//                        data.length));
//            }
//            item.setIsBookmark(cursor.getInt(HistoryQuery.INDEX_IS_BOOKMARK) == 1);
//            return item;
//        }
//    }
//}

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
//import com.mogoweb.browser.addbookmark.FolderSpinner;
//import com.mogoweb.browser.addbookmark.FolderSpinnerAdapter;
//
//import android.app.Activity;
//import android.app.LoaderManager;
//import android.app.LoaderManager.LoaderCallbacks;
//import android.content.AsyncTaskLoader;
//import android.content.ContentResolver;
//import android.content.ContentUris;
//import android.content.ContentValues;
//import android.content.Context;
//import android.content.CursorLoader;
//import android.content.Loader;
//import android.content.res.Resources;
//import android.database.Cursor;
//import android.graphics.Bitmap;
//import android.graphics.drawable.Drawable;
//import android.net.ParseException;
//import android.net.Uri;
//import android.net.WebAddress;
//import android.os.AsyncTask;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Message;
//import android.provider.BrowserContract;
//import android.provider.BrowserContract.Accounts;
//import android.text.TextUtils;
//import android.util.AttributeSet;
//import android.view.KeyEvent;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.Window;
//import android.view.WindowManager;
//import android.view.inputmethod.EditorInfo;
//import android.view.inputmethod.InputMethodManager;
//import android.widget.AdapterView;
//import android.widget.AdapterView.OnItemSelectedListener;
//import android.widget.ArrayAdapter;
//import android.widget.CursorAdapter;
//import android.widget.EditText;
//import android.widget.ListView;
//import android.widget.Spinner;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import java.net.URI;
//import java.net.URISyntaxException;
//
//public class AddBookmarkPage extends Activity
//        implements View.OnClickListener, TextView.OnEditorActionListener,
//        AdapterView.OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor>,
//        BreadCrumbView.Controller, FolderSpinner.OnSetSelectionListener,
//        OnItemSelectedListener {
//
//    public static final long DEFAULT_FOLDER_ID = -1;
//    public static final String TOUCH_ICON_URL = "touch_icon_url";
//    // Place on an edited bookmark to remove the saved thumbnail
//    public static final String REMOVE_THUMBNAIL = "remove_thumbnail";
//    public static final String USER_AGENT = "user_agent";
//    public static final String CHECK_FOR_DUPE = "check_for_dupe";
//
//    /* package */ static final String EXTRA_EDIT_BOOKMARK = "bookmark";
//    /* package */ static final String EXTRA_IS_FOLDER = "is_folder";
//
//    private static final int MAX_CRUMBS_SHOWN = 2;
//
//    private final String LOGTAG = "Bookmarks";
//
//    // IDs for the CursorLoaders that are used.
//    private final int LOADER_ID_ACCOUNTS = 0;
//    private final int LOADER_ID_FOLDER_CONTENTS = 1;
//    private final int LOADER_ID_EDIT_INFO = 2;
//
//    private EditText    mTitle;
//    private EditText    mAddress;
//    private TextView    mButton;
//    private View        mCancelButton;
//    private boolean     mEditingExisting;
//    private boolean     mEditingFolder;
//    private Bundle      mMap;
//    private String      mTouchIconUrl;
//    private String      mOriginalUrl;
//    private FolderSpinner mFolder;
//    private View mDefaultView;
//    private View mFolderSelector;
//    private EditText mFolderNamer;
//    private View mFolderCancel;
//    private boolean mIsFolderNamerShowing;
//    private View mFolderNamerHolder;
//    private View mAddNewFolder;
//    private View mAddSeparator;
//    private long mCurrentFolder;
//    private FolderAdapter mAdapter;
//    private BreadCrumbView mCrumbs;
//    private TextView mFakeTitle;
//    private View mCrumbHolder;
//    private CustomListView mListView;
//    private boolean mSaveToHomeScreen;
//    private long mRootFolder;
//    private TextView mTopLevelLabel;
//    private Drawable mHeaderIcon;
//    private View mRemoveLink;
//    private View mFakeTitleHolder;
//    private FolderSpinnerAdapter mFolderAdapter;
//    private Spinner mAccountSpinner;
//    private ArrayAdapter<BookmarkAccount> mAccountAdapter;
//
//    private static class Folder {
//        String Name;
//        long Id;
//        Folder(String name, long id) {
//            Name = name;
//            Id = id;
//        }
//    }
//
//    // Message IDs
//    private static final int SAVE_BOOKMARK = 100;
//    private static final int TOUCH_ICON_DOWNLOADED = 101;
//    private static final int BOOKMARK_DELETED = 102;
//
//    private Handler mHandler;
//
//    private InputMethodManager getInputMethodManager() {
//        return (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
//    }
//
//    private Uri getUriForFolder(long folder) {
//        BookmarkAccount account =
//                (BookmarkAccount) mAccountSpinner.getSelectedItem();
//        if (folder == mRootFolder && account != null) {
//            return BookmarksLoader.addAccount(
//                    BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER,
//                    account.accountType, account.accountName);
//        }
//        return BrowserContract.Bookmarks.buildFolderUri(folder);
//    }
//
//    @Override
//    public void onTop(BreadCrumbView view, int level, Object data) {
//        if (null == data) return;
//        Folder folderData = (Folder) data;
//        long folder = folderData.Id;
//        LoaderManager manager = getLoaderManager();
//        CursorLoader loader = (CursorLoader) ((Loader<?>) manager.getLoader(
//                LOADER_ID_FOLDER_CONTENTS));
//        loader.setUri(getUriForFolder(folder));
//        loader.forceLoad();
//        if (mIsFolderNamerShowing) {
//            completeOrCancelFolderNaming(true);
//        }
//        setShowBookmarkIcon(level == 1);
//    }
//
//    /**
//     * Show or hide the icon for bookmarks next to "Bookmarks" in the crumb view.
//     * @param show True if the icon should visible, false otherwise.
//     */
//    private void setShowBookmarkIcon(boolean show) {
//        Drawable drawable = show ? mHeaderIcon: null;
//        mTopLevelLabel.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
//    }
//
//    @Override
//    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
//        if (v == mFolderNamer) {
//            if (v.getText().length() > 0) {
//                if (actionId == EditorInfo.IME_NULL) {
//                    // Only want to do this once.
//                    if (event.getAction() == KeyEvent.ACTION_UP) {
//                        completeOrCancelFolderNaming(false);
//                    }
//                }
//            }
//            // Steal the key press; otherwise a newline will be added
//            return true;
//        }
//        return false;
//    }
//
//    private void switchToDefaultView(boolean changedFolder) {
//        mFolderSelector.setVisibility(View.GONE);
//        mDefaultView.setVisibility(View.VISIBLE);
//        mCrumbHolder.setVisibility(View.GONE);
//        mFakeTitleHolder.setVisibility(View.VISIBLE);
//        if (changedFolder) {
//            Object data = mCrumbs.getTopData();
//            if (data != null) {
//                Folder folder = (Folder) data;
//                mCurrentFolder = folder.Id;
//                if (mCurrentFolder == mRootFolder) {
//                    // The Spinner changed to show "Other folder ..."  Change
//                    // it back to "Bookmarks", which is position 0 if we are
//                    // editing a folder, 1 otherwise.
//                    mFolder.setSelectionIgnoringSelectionChange(mEditingFolder ? 0 : 1);
//                } else {
//                    mFolderAdapter.setOtherFolderDisplayText(folder.Name);
//                }
//            }
//        } else {
//            // The user canceled selecting a folder.  Revert back to the earlier
//            // selection.
//            if (mSaveToHomeScreen) {
//                mFolder.setSelectionIgnoringSelectionChange(0);
//            } else {
//                if (mCurrentFolder == mRootFolder) {
//                    mFolder.setSelectionIgnoringSelectionChange(mEditingFolder ? 0 : 1);
//                } else {
//                    Object data = mCrumbs.getTopData();
//                    if (data != null && ((Folder) data).Id == mCurrentFolder) {
//                        // We are showing the correct folder hierarchy. The
//                        // folder selector will say "Other folder..."  Change it
//                        // to say the name of the folder once again.
//                        mFolderAdapter.setOtherFolderDisplayText(((Folder) data).Name);
//                    } else {
//                        // We are not showing the correct folder hierarchy.
//                        // Clear the Crumbs and find the proper folder
//                        setupTopCrumb();
//                        LoaderManager manager = getLoaderManager();
//                        manager.restartLoader(LOADER_ID_FOLDER_CONTENTS, null, this);
//
//                    }
//                }
//            }
//        }
//    }
//
//    @Override
//    public void onClick(View v) {
//        if (v == mButton) {
//            if (mFolderSelector.getVisibility() == View.VISIBLE) {
//                // We are showing the folder selector.
//                if (mIsFolderNamerShowing) {
//                    completeOrCancelFolderNaming(false);
//                } else {
//                    // User has selected a folder.  Go back to the opening page
//                    mSaveToHomeScreen = false;
//                    switchToDefaultView(true);
//                }
//            } else if (save()) {
//                finish();
//            }
//        } else if (v == mCancelButton) {
//            if (mIsFolderNamerShowing) {
//                completeOrCancelFolderNaming(true);
//            } else if (mFolderSelector.getVisibility() == View.VISIBLE) {
//                switchToDefaultView(false);
//            } else {
//                finish();
//            }
//        } else if (v == mFolderCancel) {
//            completeOrCancelFolderNaming(true);
//        } else if (v == mAddNewFolder) {
//            setShowFolderNamer(true);
//            mFolderNamer.setText(R.string.new_folder);
//            mFolderNamer.requestFocus();
//            mAddNewFolder.setVisibility(View.GONE);
//            mAddSeparator.setVisibility(View.GONE);
//            InputMethodManager imm = getInputMethodManager();
//            // Set the InputMethodManager to focus on the ListView so that it
//            // can transfer the focus to mFolderNamer.
//            imm.focusIn(mListView);
//            imm.showSoftInput(mFolderNamer, InputMethodManager.SHOW_IMPLICIT);
//        } else if (v == mRemoveLink) {
//            if (!mEditingExisting) {
//                throw new AssertionError("Remove button should not be shown for"
//                        + " new bookmarks");
//            }
//            long id = mMap.getLong(BrowserContract.Bookmarks._ID);
//            createHandler();
//            Message msg = Message.obtain(mHandler, BOOKMARK_DELETED);
//            BookmarkUtils.displayRemoveBookmarkDialog(id,
//                    mTitle.getText().toString(), this, msg);
//        }
//    }
//
//    // FolderSpinner.OnSetSelectionListener
//
//    @Override
//    public void onSetSelection(long id) {
//        int intId = (int) id;
//        switch (intId) {
//            case FolderSpinnerAdapter.ROOT_FOLDER:
//                mCurrentFolder = mRootFolder;
//                mSaveToHomeScreen = false;
//                break;
//            case FolderSpinnerAdapter.HOME_SCREEN:
//                // Create a short cut to the home screen
//                mSaveToHomeScreen = true;
//                break;
//            case FolderSpinnerAdapter.OTHER_FOLDER:
//                switchToFolderSelector();
//                break;
//            case FolderSpinnerAdapter.RECENT_FOLDER:
//                mCurrentFolder = mFolderAdapter.recentFolderId();
//                mSaveToHomeScreen = false;
//                // In case the user decides to select OTHER_FOLDER
//                // and choose a different one, so that we will start from
//                // the correct place.
//                LoaderManager manager = getLoaderManager();
//                manager.restartLoader(LOADER_ID_FOLDER_CONTENTS, null, this);
//                break;
//            default:
//                break;
//        }
//    }
//
//    /**
//     * Finish naming a folder, and close the IME
//     * @param cancel If true, the new folder is not created.  If false, the new
//     *      folder is created and the user is taken inside it.
//     */
//    private void completeOrCancelFolderNaming(boolean cancel) {
//        if (!cancel && !TextUtils.isEmpty(mFolderNamer.getText())) {
//            String name = mFolderNamer.getText().toString();
//            long id = addFolderToCurrent(mFolderNamer.getText().toString());
//            descendInto(name, id);
//        }
//        setShowFolderNamer(false);
//        mAddNewFolder.setVisibility(View.VISIBLE);
//        mAddSeparator.setVisibility(View.VISIBLE);
//        getInputMethodManager().hideSoftInputFromWindow(
//                mListView.getWindowToken(), 0);
//    }
//
//    private long addFolderToCurrent(String name) {
//        // Add the folder to the database
//        ContentValues values = new ContentValues();
//        values.put(BrowserContract.Bookmarks.TITLE,
//                name);
//        values.put(BrowserContract.Bookmarks.IS_FOLDER, 1);
//        long currentFolder;
//        Object data = mCrumbs.getTopData();
//        if (data != null) {
//            currentFolder = ((Folder) data).Id;
//        } else {
//            currentFolder = mRootFolder;
//        }
//        values.put(BrowserContract.Bookmarks.PARENT, currentFolder);
//        Uri uri = getContentResolver().insert(
//                BrowserContract.Bookmarks.CONTENT_URI, values);
//        if (uri != null) {
//            return ContentUris.parseId(uri);
//        } else {
//            return -1;
//        }
//    }
//
//    private void switchToFolderSelector() {
//        // Set the list to the top in case it is scrolled.
//        mListView.setSelection(0);
//        mDefaultView.setVisibility(View.GONE);
//        mFolderSelector.setVisibility(View.VISIBLE);
//        mCrumbHolder.setVisibility(View.VISIBLE);
//        mFakeTitleHolder.setVisibility(View.GONE);
//        mAddNewFolder.setVisibility(View.VISIBLE);
//        mAddSeparator.setVisibility(View.VISIBLE);
//        getInputMethodManager().hideSoftInputFromWindow(
//                mListView.getWindowToken(), 0);
//    }
//
//    private void descendInto(String foldername, long id) {
//        if (id != DEFAULT_FOLDER_ID) {
//            mCrumbs.pushView(foldername, new Folder(foldername, id));
//            mCrumbs.notifyController();
//        }
//    }
//
//    private LoaderCallbacks<EditBookmarkInfo> mEditInfoLoaderCallbacks =
//            new LoaderCallbacks<EditBookmarkInfo>() {
//
//        @Override
//        public void onLoaderReset(Loader<EditBookmarkInfo> loader) {
//            // Don't care
//        }
//
//        @Override
//        public void onLoadFinished(Loader<EditBookmarkInfo> loader,
//                EditBookmarkInfo info) {
//            boolean setAccount = false;
//            if (info.id != -1) {
//                mEditingExisting = true;
//                showRemoveButton();
//                mFakeTitle.setText(R.string.edit_bookmark);
//                mTitle.setText(info.title);
//                mFolderAdapter.setOtherFolderDisplayText(info.parentTitle);
//                mMap.putLong(BrowserContract.Bookmarks._ID, info.id);
//                setAccount = true;
//                setAccount(info.accountName, info.accountType);
//                mCurrentFolder = info.parentId;
//                onCurrentFolderFound();
//            }
//            // TODO: Detect if lastUsedId is a subfolder of info.id in the
//            // editing folder case. For now, just don't show the last used
//            // folder at all to prevent any chance of the user adding a parent
//            // folder to a child folder
//            if (info.lastUsedId != -1 && info.lastUsedId != info.id
//                    && !mEditingFolder) {
//                if (setAccount && info.lastUsedId != mRootFolder
//                        && TextUtils.equals(info.lastUsedAccountName, info.accountName)
//                        && TextUtils.equals(info.lastUsedAccountType, info.accountType)) {
//                    mFolderAdapter.addRecentFolder(info.lastUsedId, info.lastUsedTitle);
//                } else if (!setAccount) {
//                    setAccount = true;
//                    setAccount(info.lastUsedAccountName, info.lastUsedAccountType);
//                    if (info.lastUsedId != mRootFolder) {
//                        mFolderAdapter.addRecentFolder(info.lastUsedId,
//                                info.lastUsedTitle);
//                    }
//                }
//            }
//            if (!setAccount) {
//                mAccountSpinner.setSelection(0);
//            }
//        }
//
//        @Override
//        public Loader<EditBookmarkInfo> onCreateLoader(int id, Bundle args) {
//            return new EditBookmarkInfoLoader(AddBookmarkPage.this, mMap);
//        }
//    };
//
//    void setAccount(String accountName, String accountType) {
//        for (int i = 0; i < mAccountAdapter.getCount(); i++) {
//            BookmarkAccount account = mAccountAdapter.getItem(i);
//            if (TextUtils.equals(account.accountName, accountName)
//                    && TextUtils.equals(account.accountType, accountType)) {
//                mAccountSpinner.setSelection(i);
//                onRootFolderFound(account.rootFolderId);
//                return;
//            }
//        }
//    }
//
//    @Override
//    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
//        String[] projection;
//        switch (id) {
//            case LOADER_ID_ACCOUNTS:
//                return new AccountsLoader(this);
//            case LOADER_ID_FOLDER_CONTENTS:
//                projection = new String[] {
//                        BrowserContract.Bookmarks._ID,
//                        BrowserContract.Bookmarks.TITLE,
//                        BrowserContract.Bookmarks.IS_FOLDER
//                };
//                String where = BrowserContract.Bookmarks.IS_FOLDER + " != 0";
//                String whereArgs[] = null;
//                if (mEditingFolder) {
//                    where += " AND " + BrowserContract.Bookmarks._ID + " != ?";
//                    whereArgs = new String[] { Long.toString(mMap.getLong(
//                            BrowserContract.Bookmarks._ID)) };
//                }
//                long currentFolder;
//                Object data = mCrumbs.getTopData();
//                if (data != null) {
//                    currentFolder = ((Folder) data).Id;
//                } else {
//                    currentFolder = mRootFolder;
//                }
//                return new CursorLoader(this,
//                        getUriForFolder(currentFolder),
//                        projection,
//                        where,
//                        whereArgs,
//                        BrowserContract.Bookmarks._ID + " ASC");
//            default:
//                throw new AssertionError("Asking for nonexistant loader!");
//        }
//    }
//
//    @Override
//    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
//        switch (loader.getId()) {
//            case LOADER_ID_ACCOUNTS:
//                mAccountAdapter.clear();
//                while (cursor.moveToNext()) {
//                    mAccountAdapter.add(new BookmarkAccount(this, cursor));
//                }
//                getLoaderManager().destroyLoader(LOADER_ID_ACCOUNTS);
//                getLoaderManager().restartLoader(LOADER_ID_EDIT_INFO, null,
//                        mEditInfoLoaderCallbacks);
//                break;
//            case LOADER_ID_FOLDER_CONTENTS:
//                mAdapter.changeCursor(cursor);
//                break;
//        }
//    }
//
//    public void onLoaderReset(Loader<Cursor> loader) {
//        switch (loader.getId()) {
//            case LOADER_ID_FOLDER_CONTENTS:
//                mAdapter.changeCursor(null);
//                break;
//        }
//    }
//
//    /**
//     * Move cursor to the position that has folderToFind as its "_id".
//     * @param cursor Cursor containing folders in the bookmarks database
//     * @param folderToFind "_id" of the folder to move to.
//     * @param idIndex Index in cursor of "_id"
//     * @throws AssertionError if cursor is empty or there is no row with folderToFind
//     *      as its "_id".
//     */
//    void moveCursorToFolder(Cursor cursor, long folderToFind, int idIndex)
//            throws AssertionError {
//        if (!cursor.moveToFirst()) {
//            throw new AssertionError("No folders in the database!");
//        }
//        long folder;
//        do {
//            folder = cursor.getLong(idIndex);
//        } while (folder != folderToFind && cursor.moveToNext());
//        if (cursor.isAfterLast()) {
//            throw new AssertionError("Folder(id=" + folderToFind
//                    + ") holding this bookmark does not exist!");
//        }
//    }
//
//    @Override
//    public void onItemClick(AdapterView<?> parent, View view, int position,
//            long id) {
//        TextView tv = (TextView) view.findViewById(android.R.id.text1);
//        // Switch to the folder that was clicked on.
//        descendInto(tv.getText().toString(), id);
//    }
//
//    private void setShowFolderNamer(boolean show) {
//        if (show != mIsFolderNamerShowing) {
//            mIsFolderNamerShowing = show;
//            if (show) {
//                // Set the selection to the folder namer so it will be in
//                // view.
//                mListView.addFooterView(mFolderNamerHolder);
//            } else {
//                mListView.removeFooterView(mFolderNamerHolder);
//            }
//            // Refresh the list.
//            mListView.setAdapter(mAdapter);
//            if (show) {
//                mListView.setSelection(mListView.getCount() - 1);
//            }
//        }
//    }
//
//    /**
//     * Shows a list of names of folders.
//     */
//    private class FolderAdapter extends CursorAdapter {
//        public FolderAdapter(Context context) {
//            super(context, null);
//        }
//
//        @Override
//        public void bindView(View view, Context context, Cursor cursor) {
//            ((TextView) view.findViewById(android.R.id.text1)).setText(
//                    cursor.getString(cursor.getColumnIndexOrThrow(
//                    BrowserContract.Bookmarks.TITLE)));
//        }
//
//        @Override
//        public View newView(Context context, Cursor cursor, ViewGroup parent) {
//            View view = LayoutInflater.from(context).inflate(
//                    R.layout.folder_list_item, null);
//            view.setBackgroundDrawable(context.getResources().
//                    getDrawable(android.R.drawable.list_selector_background));
//            return view;
//        }
//
//        @Override
//        public boolean isEmpty() {
//            // Do not show the empty view if the user is creating a new folder.
//            return super.isEmpty() && !mIsFolderNamerShowing;
//        }
//    }
//
//    @Override
//    protected void onCreate(Bundle icicle) {
//        super.onCreate(icicle);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//
//        mMap = getIntent().getExtras();
//
//        setContentView(R.layout.browser_add_bookmark);
//
//        Window window = getWindow();
//
//        String title = null;
//        String url = null;
//
//        mFakeTitle = (TextView) findViewById(R.id.fake_title);
//
//        if (mMap != null) {
//            Bundle b = mMap.getBundle(EXTRA_EDIT_BOOKMARK);
//            if (b != null) {
//                mEditingFolder = mMap.getBoolean(EXTRA_IS_FOLDER, false);
//                mMap = b;
//                mEditingExisting = true;
//                mFakeTitle.setText(R.string.edit_bookmark);
//                if (mEditingFolder) {
//                    findViewById(R.id.row_address).setVisibility(View.GONE);
//                } else {
//                    showRemoveButton();
//                }
//            } else {
//                int gravity = mMap.getInt("gravity", -1);
//                if (gravity != -1) {
//                    WindowManager.LayoutParams l = window.getAttributes();
//                    l.gravity = gravity;
//                    window.setAttributes(l);
//                }
//            }
//            title = mMap.getString(BrowserContract.Bookmarks.TITLE);
//            url = mOriginalUrl = mMap.getString(BrowserContract.Bookmarks.URL);
//            mTouchIconUrl = mMap.getString(TOUCH_ICON_URL);
//            mCurrentFolder = mMap.getLong(BrowserContract.Bookmarks.PARENT, DEFAULT_FOLDER_ID);
//        }
//
//        mTitle = (EditText) findViewById(R.id.title);
//        mTitle.setText(title);
//
//        mAddress = (EditText) findViewById(R.id.address);
//        mAddress.setText(url);
//
//        mButton = (TextView) findViewById(R.id.OK);
//        mButton.setOnClickListener(this);
//
//        mCancelButton = findViewById(R.id.cancel);
//        mCancelButton.setOnClickListener(this);
//
//        mFolder = (FolderSpinner) findViewById(R.id.folder);
//        mFolderAdapter = new FolderSpinnerAdapter(this, !mEditingFolder);
//        mFolder.setAdapter(mFolderAdapter);
//        mFolder.setOnSetSelectionListener(this);
//
//        mDefaultView = findViewById(R.id.default_view);
//        mFolderSelector = findViewById(R.id.folder_selector);
//
//        mFolderNamerHolder = getLayoutInflater().inflate(R.layout.new_folder_layout, null);
//        mFolderNamer = (EditText) mFolderNamerHolder.findViewById(R.id.folder_namer);
//        mFolderNamer.setOnEditorActionListener(this);
//        mFolderCancel = mFolderNamerHolder.findViewById(R.id.close);
//        mFolderCancel.setOnClickListener(this);
//
//        mAddNewFolder = findViewById(R.id.add_new_folder);
//        mAddNewFolder.setOnClickListener(this);
//        mAddSeparator = findViewById(R.id.add_divider);
//
//        mCrumbs = (BreadCrumbView) findViewById(R.id.crumbs);
//        mCrumbs.setUseBackButton(true);
//        mCrumbs.setController(this);
//        mHeaderIcon = getResources().getDrawable(R.drawable.ic_folder_holo_dark);
//        mCrumbHolder = findViewById(R.id.crumb_holder);
//        mCrumbs.setMaxVisible(MAX_CRUMBS_SHOWN);
//
//        mAdapter = new FolderAdapter(this);
//        mListView = (CustomListView) findViewById(R.id.list);
//        View empty = findViewById(R.id.empty);
//        mListView.setEmptyView(empty);
//        mListView.setAdapter(mAdapter);
//        mListView.setOnItemClickListener(this);
//        mListView.addEditText(mFolderNamer);
//
//        mAccountAdapter = new ArrayAdapter<BookmarkAccount>(this,
//                android.R.layout.simple_spinner_item);
//        mAccountAdapter.setDropDownViewResource(
//                android.R.layout.simple_spinner_dropdown_item);
//        mAccountSpinner = (Spinner) findViewById(R.id.accounts);
//        mAccountSpinner.setAdapter(mAccountAdapter);
//        mAccountSpinner.setOnItemSelectedListener(this);
//
//
//        mFakeTitleHolder = findViewById(R.id.title_holder);
//
//        if (!window.getDecorView().isInTouchMode()) {
//            mButton.requestFocus();
//        }
//
//        getLoaderManager().restartLoader(LOADER_ID_ACCOUNTS, null, this);
//    }
//
//    private void showRemoveButton() {
//        findViewById(R.id.remove_divider).setVisibility(View.VISIBLE);
//        mRemoveLink = findViewById(R.id.remove);
//        mRemoveLink.setVisibility(View.VISIBLE);
//        mRemoveLink.setOnClickListener(this);
//    }
//
//    // Called once we have determined which folder is the root folder
//    private void onRootFolderFound(long root) {
//        mRootFolder = root;
//        mCurrentFolder = mRootFolder;
//        setupTopCrumb();
//        onCurrentFolderFound();
//    }
//
//    private void setupTopCrumb() {
//        mCrumbs.clear();
//        String name = getString(R.string.bookmarks);
//        mTopLevelLabel = (TextView) mCrumbs.pushView(name, false,
//                new Folder(name, mRootFolder));
//        // To better match the other folders.
//        mTopLevelLabel.setCompoundDrawablePadding(6);
//    }
//
//    private void onCurrentFolderFound() {
//        LoaderManager manager = getLoaderManager();
//        if (mCurrentFolder != mRootFolder) {
//            // Since we're not in the root folder, change the selection to other
//            // folder now.  The text will get changed once we select the correct
//            // folder.
//            mFolder.setSelectionIgnoringSelectionChange(mEditingFolder ? 1 : 2);
//        } else {
//            setShowBookmarkIcon(true);
//            if (!mEditingFolder) {
//                // Initially the "Bookmarks" folder should be showing, rather than
//                // the home screen.  In the editing folder case, home screen is not
//                // an option, so "Bookmarks" folder is already at the top.
//                mFolder.setSelectionIgnoringSelectionChange(FolderSpinnerAdapter.ROOT_FOLDER);
//            }
//        }
//        // Find the contents of the current folder
//        manager.restartLoader(LOADER_ID_FOLDER_CONTENTS, null, this);
//    }
//
//    /**
//     * Runnable to save a bookmark, so it can be performed in its own thread.
//     */
//    private class SaveBookmarkRunnable implements Runnable {
//        // FIXME: This should be an async task.
//        private Message mMessage;
//        private Context mContext;
//        public SaveBookmarkRunnable(Context ctx, Message msg) {
//            mContext = ctx.getApplicationContext();
//            mMessage = msg;
//        }
//        public void run() {
//            // Unbundle bookmark data.
//            Bundle bundle = mMessage.getData();
//            String title = bundle.getString(BrowserContract.Bookmarks.TITLE);
//            String url = bundle.getString(BrowserContract.Bookmarks.URL);
//            boolean invalidateThumbnail = bundle.getBoolean(REMOVE_THUMBNAIL);
//            Bitmap thumbnail = invalidateThumbnail ? null
//                    : (Bitmap) bundle.getParcelable(BrowserContract.Bookmarks.THUMBNAIL);
//            String touchIconUrl = bundle.getString(TOUCH_ICON_URL);
//
//            // Save to the bookmarks DB.
//            try {
//                final ContentResolver cr = getContentResolver();
//                Bookmarks.addBookmark(AddBookmarkPage.this, false, url,
//                        title, thumbnail, mCurrentFolder);
//                if (touchIconUrl != null) {
//                    new DownloadTouchIcon(mContext, cr, url).execute(mTouchIconUrl);
//                }
//                mMessage.arg1 = 1;
//            } catch (IllegalStateException e) {
//                mMessage.arg1 = 0;
//            }
//            mMessage.sendToTarget();
//        }
//    }
//
//    private static class UpdateBookmarkTask extends AsyncTask<ContentValues, Void, Void> {
//        Context mContext;
//        Long mId;
//
//        public UpdateBookmarkTask(Context context, long id) {
//            mContext = context.getApplicationContext();
//            mId = id;
//        }
//
//        @Override
//        protected Void doInBackground(ContentValues... params) {
//            if (params.length != 1) {
//                throw new IllegalArgumentException("No ContentValues provided!");
//            }
//            Uri uri = ContentUris.withAppendedId(BookmarkUtils.getBookmarksUri(mContext), mId);
//            mContext.getContentResolver().update(
//                    uri,
//                    params[0], null, null);
//            return null;
//        }
//    }
//
//    private void createHandler() {
//        if (mHandler == null) {
//            mHandler = new Handler() {
//                @Override
//                public void handleMessage(Message msg) {
//                    switch (msg.what) {
//                        case SAVE_BOOKMARK:
//                            if (1 == msg.arg1) {
//                                Toast.makeText(AddBookmarkPage.this, R.string.bookmark_saved,
//                                        Toast.LENGTH_LONG).show();
//                            } else {
//                                Toast.makeText(AddBookmarkPage.this, R.string.bookmark_not_saved,
//                                        Toast.LENGTH_LONG).show();
//                            }
//                            break;
//                        case TOUCH_ICON_DOWNLOADED:
//                            Bundle b = msg.getData();
//                            sendBroadcast(BookmarkUtils.createAddToHomeIntent(
//                                    AddBookmarkPage.this,
//                                    b.getString(BrowserContract.Bookmarks.URL),
//                                    b.getString(BrowserContract.Bookmarks.TITLE),
//                                    (Bitmap) b.getParcelable(BrowserContract.Bookmarks.TOUCH_ICON),
//                                    (Bitmap) b.getParcelable(BrowserContract.Bookmarks.FAVICON)));
//                            break;
//                        case BOOKMARK_DELETED:
//                            finish();
//                            break;
//                    }
//                }
//            };
//        }
//    }
//
//    /**
//     * Parse the data entered in the dialog and post a message to update the bookmarks database.
//     */
//    boolean save() {
//        createHandler();
//
//        String title = mTitle.getText().toString().trim();
//        String unfilteredUrl;
//        unfilteredUrl = UrlUtils.fixUrl(mAddress.getText().toString());
//
//        boolean emptyTitle = title.length() == 0;
//        boolean emptyUrl = unfilteredUrl.trim().length() == 0;
//        Resources r = getResources();
//        if (emptyTitle || (emptyUrl && !mEditingFolder)) {
//            if (emptyTitle) {
//                mTitle.setError(r.getText(R.string.bookmark_needs_title));
//            }
//            if (emptyUrl) {
//                mAddress.setError(r.getText(R.string.bookmark_needs_url));
//            }
//            return false;
//
//        }
//        String url = unfilteredUrl.trim();
//        if (!mEditingFolder) {
//            try {
//                // We allow bookmarks with a javascript: scheme, but these will in most cases
//                // fail URI parsing, so don't try it if that's the kind of bookmark we have.
//
//                if (!url.toLowerCase().startsWith("javascript:")) {
//                    URI uriObj = new URI(url);
//                    String scheme = uriObj.getScheme();
//                    if (!Bookmarks.urlHasAcceptableScheme(url)) {
//                        // If the scheme was non-null, let the user know that we
//                        // can't save their bookmark. If it was null, we'll assume
//                        // they meant http when we parse it in the WebAddress class.
//                        if (scheme != null) {
//                            mAddress.setError(r.getText(R.string.bookmark_cannot_save_url));
//                            return false;
//                        }
//                        WebAddress address;
//                        try {
//                            address = new WebAddress(unfilteredUrl);
//                        } catch (ParseException e) {
//                            throw new URISyntaxException("", "");
//                        }
//                        if (address.getHost().length() == 0) {
//                            throw new URISyntaxException("", "");
//                        }
//                        url = address.toString();
//                    }
//                }
//            } catch (URISyntaxException e) {
//                mAddress.setError(r.getText(R.string.bookmark_url_not_valid));
//                return false;
//            }
//        }
//
//        if (mSaveToHomeScreen) {
//            mEditingExisting = false;
//        }
//
//        boolean urlUnmodified = url.equals(mOriginalUrl);
//
//        if (mEditingExisting) {
//            Long id = mMap.getLong(BrowserContract.Bookmarks._ID);
//            ContentValues values = new ContentValues();
//            values.put(BrowserContract.Bookmarks.TITLE, title);
//            values.put(BrowserContract.Bookmarks.PARENT, mCurrentFolder);
//            if (!mEditingFolder) {
//                values.put(BrowserContract.Bookmarks.URL, url);
//                if (!urlUnmodified) {
//                    values.putNull(BrowserContract.Bookmarks.THUMBNAIL);
//                }
//            }
//            if (values.size() > 0) {
//                new UpdateBookmarkTask(getApplicationContext(), id).execute(values);
//            }
//            setResult(RESULT_OK);
//        } else {
//            Bitmap thumbnail;
//            Bitmap favicon;
//            if (urlUnmodified) {
//                thumbnail = (Bitmap) mMap.getParcelable(
//                        BrowserContract.Bookmarks.THUMBNAIL);
//                favicon = (Bitmap) mMap.getParcelable(
//                        BrowserContract.Bookmarks.FAVICON);
//            } else {
//                thumbnail = null;
//                favicon = null;
//            }
//
//            Bundle bundle = new Bundle();
//            bundle.putString(BrowserContract.Bookmarks.TITLE, title);
//            bundle.putString(BrowserContract.Bookmarks.URL, url);
//            bundle.putParcelable(BrowserContract.Bookmarks.FAVICON, favicon);
//
//            if (mSaveToHomeScreen) {
//                if (mTouchIconUrl != null && urlUnmodified) {
//                    Message msg = Message.obtain(mHandler,
//                            TOUCH_ICON_DOWNLOADED);
//                    msg.setData(bundle);
//                    DownloadTouchIcon icon = new DownloadTouchIcon(this, msg,
//                            mMap.getString(USER_AGENT));
//                    icon.execute(mTouchIconUrl);
//                } else {
//                    sendBroadcast(BookmarkUtils.createAddToHomeIntent(this, url,
//                            title, null /*touchIcon*/, favicon));
//                }
//            } else {
//                bundle.putParcelable(BrowserContract.Bookmarks.THUMBNAIL, thumbnail);
//                bundle.putBoolean(REMOVE_THUMBNAIL, !urlUnmodified);
//                bundle.putString(TOUCH_ICON_URL, mTouchIconUrl);
//                // Post a message to write to the DB.
//                Message msg = Message.obtain(mHandler, SAVE_BOOKMARK);
//                msg.setData(bundle);
//                // Start a new thread so as to not slow down the UI
//                Thread t = new Thread(new SaveBookmarkRunnable(getApplicationContext(), msg));
//                t.start();
//            }
//            setResult(RESULT_OK);
//            LogTag.logBookmarkAdded(url, "bookmarkview");
//        }
//        return true;
//    }
//
//    @Override
//    public void onItemSelected(AdapterView<?> parent, View view, int position,
//            long id) {
//        if (mAccountSpinner == parent) {
//            long root = mAccountAdapter.getItem(position).rootFolderId;
//            if (root != mRootFolder) {
//                onRootFolderFound(root);
//                mFolderAdapter.clearRecentFolder();
//            }
//        }
//    }
//
//    @Override
//    public void onNothingSelected(AdapterView<?> parent) {
//        // Don't care
//    }
//
//    /*
//     * Class used as a proxy for the InputMethodManager to get to mFolderNamer
//     */
//    public static class CustomListView extends ListView {
//        private EditText mEditText;
//
//        public void addEditText(EditText editText) {
//            mEditText = editText;
//        }
//
//        public CustomListView(Context context) {
//            super(context);
//        }
//
//        public CustomListView(Context context, AttributeSet attrs) {
//            super(context, attrs);
//        }
//
//        public CustomListView(Context context, AttributeSet attrs, int defStyle) {
//            super(context, attrs, defStyle);
//        }
//
//        @Override
//        public boolean checkInputConnectionProxy(View view) {
//            return view == mEditText;
//        }
//    }
//
//    static class AccountsLoader extends CursorLoader {
//
//        static final String[] PROJECTION = new String[] {
//            Accounts.ACCOUNT_NAME,
//            Accounts.ACCOUNT_TYPE,
//            Accounts.ROOT_ID,
//        };
//
//        static final int COLUMN_INDEX_ACCOUNT_NAME = 0;
//        static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
//        static final int COLUMN_INDEX_ROOT_ID = 2;
//
//        public AccountsLoader(Context context) {
//            super(context, Accounts.CONTENT_URI, PROJECTION, null, null, null);
//        }
//
//    }
//
//    public static class BookmarkAccount {
//
//        private String mLabel;
//        String accountName, accountType;
//        public long rootFolderId;
//
//        public BookmarkAccount(Context context, Cursor cursor) {
//            accountName = cursor.getString(
//                    AccountsLoader.COLUMN_INDEX_ACCOUNT_NAME);
//            accountType = cursor.getString(
//                    AccountsLoader.COLUMN_INDEX_ACCOUNT_TYPE);
//            rootFolderId = cursor.getLong(
//                    AccountsLoader.COLUMN_INDEX_ROOT_ID);
//            mLabel = accountName;
//            if (TextUtils.isEmpty(mLabel)) {
//                mLabel = context.getString(R.string.local_bookmarks);
//            }
//        }
//
//        @Override
//        public String toString() {
//            return mLabel;
//        }
//    }
//
//    static class EditBookmarkInfo {
//        long id = -1;
//        long parentId = -1;
//        String parentTitle;
//        String title;
//        String accountName;
//        String accountType;
//
//        long lastUsedId = -1;
//        String lastUsedTitle;
//        String lastUsedAccountName;
//        String lastUsedAccountType;
//    }
//
//    static class EditBookmarkInfoLoader extends AsyncTaskLoader<EditBookmarkInfo> {
//
//        private Context mContext;
//        private Bundle mMap;
//
//        public EditBookmarkInfoLoader(Context context, Bundle bundle) {
//            super(context);
//            mContext = context.getApplicationContext();
//            mMap = bundle;
//        }
//
//        @Override
//        public EditBookmarkInfo loadInBackground() {
//            final ContentResolver cr = mContext.getContentResolver();
//            EditBookmarkInfo info = new EditBookmarkInfo();
//            Cursor c = null;
//
//            try {
//                // First, let's lookup the bookmark (check for dupes, get needed info)
//                String url = mMap.getString(BrowserContract.Bookmarks.URL);
//                info.id = mMap.getLong(BrowserContract.Bookmarks._ID, -1);
//                boolean checkForDupe = mMap.getBoolean(CHECK_FOR_DUPE);
//                if (checkForDupe && info.id == -1 && !TextUtils.isEmpty(url)) {
//                    c = cr.query(BrowserContract.Bookmarks.CONTENT_URI,
//                            new String[] { BrowserContract.Bookmarks._ID},
//                            BrowserContract.Bookmarks.URL + "=?",
//                            new String[] { url }, null);
//                    if (c.getCount() == 1 && c.moveToFirst()) {
//                        info.id = c.getLong(0);
//                    }
//                    c.close();
//                }
//                if (info.id != -1) {
//                    c = cr.query(ContentUris.withAppendedId(
//                            BrowserContract.Bookmarks.CONTENT_URI, info.id),
//                            new String[] {
//                            BrowserContract.Bookmarks.PARENT,
//                            BrowserContract.Bookmarks.ACCOUNT_NAME,
//                            BrowserContract.Bookmarks.ACCOUNT_TYPE,
//                            BrowserContract.Bookmarks.TITLE},
//                            null, null, null);
//                    if (c.moveToFirst()) {
//                        info.parentId = c.getLong(0);
//                        info.accountName = c.getString(1);
//                        info.accountType = c.getString(2);
//                        info.title = c.getString(3);
//                    }
//                    c.close();
//                    c = cr.query(ContentUris.withAppendedId(
//                            BrowserContract.Bookmarks.CONTENT_URI, info.parentId),
//                            new String[] {
//                            BrowserContract.Bookmarks.TITLE,},
//                            null, null, null);
//                    if (c.moveToFirst()) {
//                        info.parentTitle = c.getString(0);
//                    }
//                    c.close();
//                }
//
//                // Figure out the last used folder/account
//                c = cr.query(BrowserContract.Bookmarks.CONTENT_URI,
//                        new String[] {
//                        BrowserContract.Bookmarks.PARENT,
//                        }, null, null,
//                        BrowserContract.Bookmarks.DATE_MODIFIED + " DESC LIMIT 1");
//                if (c.moveToFirst()) {
//                    long parent = c.getLong(0);
//                    c.close();
//                    c = cr.query(BrowserContract.Bookmarks.CONTENT_URI,
//                            new String[] {
//                            BrowserContract.Bookmarks.TITLE,
//                            BrowserContract.Bookmarks.ACCOUNT_NAME,
//                            BrowserContract.Bookmarks.ACCOUNT_TYPE},
//                            BrowserContract.Bookmarks._ID + "=?", new String[] {
//                            Long.toString(parent)}, null);
//                    if (c.moveToFirst()) {
//                        info.lastUsedId = parent;
//                        info.lastUsedTitle = c.getString(0);
//                        info.lastUsedAccountName = c.getString(1);
//                        info.lastUsedAccountType = c.getString(2);
//                    }
//                    c.close();
//                }
//            } finally {
//                if (c != null) {
//                    c.close();
//                }
//            }
//
//            return info;
//        }
//
//        @Override
//        protected void onStartLoading() {
//            forceLoad();
//        }
//
//    }
//
//}

/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.mogoweb.browser;

import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DateSorter;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

/**
 * ExpandableListAdapter which separates data into categories based on date.
 * Used for History and Downloads.
 */
public class DateSortedExpandableListAdapter extends BaseExpandableListAdapter {
    // Array for each of our bins.  Each entry represents how many items are
    // in that bin.
    private int mItemMap[];
    // This is our GroupCount.  We will have at most DateSorter.DAY_COUNT
    // bins, less if the user has no items in one or more bins.
    private int mNumberOfBins;
    private Cursor mCursor;
    private DateSorter mDateSorter;
    private int mDateIndex;
    private int mIdIndex;
    private Context mContext;

    boolean mDataValid;

    DataSetObserver mDataSetObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            mDataValid = true;
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            mDataValid = false;
            notifyDataSetInvalidated();
        }
    };
    
    public DateSortedExpandableListAdapter(Context context, int dateIndex) {
        mContext = context;
        mDateSorter = new DateSorter(context);
        mDateIndex = dateIndex;
        mDataValid = false;
        mIdIndex = -1;
    }

    /**
     * Set up the bins for determining which items belong to which groups.
     */
    private void buildMap() {
        // The cursor is sorted by date
        // The ItemMap will store the number of items in each bin.
        int array[] = new int[DateSorter.DAY_COUNT];
        // Zero out the array.
        for (int j = 0; j < DateSorter.DAY_COUNT; j++) {
            array[j] = 0;
        }
        mNumberOfBins = 0;
        int dateIndex = -1;
        if (mCursor.moveToFirst() && mCursor.getCount() > 0) {
            while (!mCursor.isAfterLast()) {
                long date = getLong(mDateIndex);
                int index = mDateSorter.getIndex(date);
                if (index > dateIndex) {
                    mNumberOfBins++;
                    if (index == DateSorter.DAY_COUNT - 1) {
                        // We are already in the last bin, so it will
                        // include all the remaining items
                        array[index] = mCursor.getCount()
                                - mCursor.getPosition();
                        break;
                    }
                    dateIndex = index;
                }
                array[dateIndex]++;
                mCursor.moveToNext();
            }
        }
        mItemMap = array;
    }

    /**
     * Get the byte array at cursorIndex from the Cursor.  Assumes the Cursor
     * has already been moved to the correct position.  Along with
     * {@link #getInt} and {@link #getString}, these are provided so the client
     * does not need to access the Cursor directly
     * @param cursorIndex Index to query the Cursor.
     * @return corresponding byte array from the Cursor.
     */
    /* package */ byte[] getBlob(int cursorIndex) {
        if (!mDataValid) return null; 
        return mCursor.getBlob(cursorIndex);
    }

    /* package */ Context getContext() {
        return mContext;
    }

    /**
     * Get the integer at cursorIndex from the Cursor.  Assumes the Cursor has
     * already been moved to the correct position.  Along with
     * {@link #getBlob} and {@link #getString}, these are provided so the client
     * does not need to access the Cursor directly
     * @param cursorIndex Index to query the Cursor.
     * @return corresponding integer from the Cursor.
     */
    /* package */ int getInt(int cursorIndex) {
        if (!mDataValid) return 0; 
        return mCursor.getInt(cursorIndex);
    }

    /**
     * Get the long at cursorIndex from the Cursor.  Assumes the Cursor has
     * already been moved to the correct position.
     */
    /* package */ long getLong(int cursorIndex) {
        if (!mDataValid) return 0; 
        return mCursor.getLong(cursorIndex);
    }

    /**
     * Get the String at cursorIndex from the Cursor.  Assumes the Cursor has
     * already been moved to the correct position.  Along with
     * {@link #getInt} and {@link #getInt}, these are provided so the client
     * does not need to access the Cursor directly
     * @param cursorIndex Index to query the Cursor.
     * @return corresponding String from the Cursor.
     */
    /* package */ String getString(int cursorIndex) {
        if (!mDataValid) return null; 
        return mCursor.getString(cursorIndex);
    }

    /**
     * Determine which group an item belongs to.
     * @param childId ID of the child view in question.
     * @return int Group position of the containing group.
    /* package */ int groupFromChildId(long childId) {
        if (!mDataValid) return -1; 
        int group = -1;
        for (mCursor.moveToFirst(); !mCursor.isAfterLast();
                mCursor.moveToNext()) {
            if (getLong(mIdIndex) == childId) {
                int bin = mDateSorter.getIndex(getLong(mDateIndex));
                // bin is the same as the group if the number of bins is the
                // same as DateSorter
                if (DateSorter.DAY_COUNT == mNumberOfBins) {
                    return bin;
                }
                // There are some empty bins.  Find the corresponding group.
                group = 0;
                for (int i = 0; i < bin; i++) {
                    if (mItemMap[i] != 0) {
                        group++;
                    }
                }
                break;
            }
        }
        return group;
    }

    /**
     * Translates from a group position in the ExpandableList to a bin.  This is
     * necessary because some groups have no history items, so we do not include
     * those in the ExpandableList.
     * @param groupPosition Position in the ExpandableList's set of groups
     * @return The corresponding bin that holds that group.
     */
    private int groupPositionToBin(int groupPosition) {
        if (!mDataValid) return -1; 
        if (groupPosition < 0 || groupPosition >= DateSorter.DAY_COUNT) {
            throw new AssertionError("group position out of range");
        }
        if (DateSorter.DAY_COUNT == mNumberOfBins || 0 == mNumberOfBins) {
            // In the first case, we have exactly the same number of bins
            // as our maximum possible, so there is no need to do a
            // conversion
            // The second statement is in case this method gets called when
            // the array is empty, in which case the provided groupPosition
            // will do fine.
            return groupPosition;
        }
        int arrayPosition = -1;
        while (groupPosition > -1) {
            arrayPosition++;
            if (mItemMap[arrayPosition] != 0) {
                groupPosition--;
            }
        }
        return arrayPosition;
    }

    /**
     * Move the cursor to the position indicated.
     * @param packedPosition Position in packed position representation.
     * @return True on success, false otherwise.
     */
    boolean moveCursorToPackedChildPosition(long packedPosition) {
        if (ExpandableListView.getPackedPositionType(packedPosition) !=
                ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            return false;
        }
        int groupPosition = ExpandableListView.getPackedPositionGroup(
                packedPosition);
        int childPosition = ExpandableListView.getPackedPositionChild(
                packedPosition);
        return moveCursorToChildPosition(groupPosition, childPosition);
    }

    /**
     * Move the cursor the the position indicated.
     * @param groupPosition Index of the group containing the desired item.
     * @param childPosition Index of the item within the specified group.
     * @return boolean False if the cursor is closed, so the Cursor was not
     *      moved.  True on success.
     */
    /* package */ boolean moveCursorToChildPosition(int groupPosition,
            int childPosition) {
        if (!mDataValid || mCursor.isClosed()) {
            return false;
        }
        groupPosition = groupPositionToBin(groupPosition);
        int index = childPosition;
        for (int i = 0; i < groupPosition; i++) {
            index += mItemMap[i];
        }
        return mCursor.moveToPosition(index);
    }

    public void changeCursor(Cursor cursor) {
        if (cursor == mCursor) {
            return;
        }
        if (mCursor != null) {
            mCursor.unregisterDataSetObserver(mDataSetObserver);
            mCursor.close();
        }
        mCursor = cursor;
        if (cursor != null) {
            cursor.registerDataSetObserver(mDataSetObserver);
            mIdIndex = cursor.getColumnIndexOrThrow("_id");
            mDataValid = true;
            buildMap();
            // notify the observers about the new cursor
            notifyDataSetChanged();
        } else {
            mIdIndex = -1;
            mDataValid = false;
            // notify the observers about the lack of a data set
            notifyDataSetInvalidated();
        }
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
            View convertView, ViewGroup parent) {
        if (!mDataValid) throw new IllegalStateException("Data is not valid"); 
        TextView item;
        if (null == convertView || !(convertView instanceof TextView)) {
            LayoutInflater factory = LayoutInflater.from(mContext);
            item = (TextView) factory.inflate(R.layout.history_header, null);
        } else {
            item = (TextView) convertView;
        }
        String label = mDateSorter.getLabel(groupPositionToBin(groupPosition));
        item.setText(label);
        return item;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
            boolean isLastChild, View convertView, ViewGroup parent) {
        if (!mDataValid) throw new IllegalStateException("Data is not valid"); 
        return null;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public int getGroupCount() {
        if (!mDataValid) return 0;
        return mNumberOfBins;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        if (!mDataValid) return 0;
        return mItemMap[groupPositionToBin(groupPosition)];
    }

    @Override
    public Object getGroup(int groupPosition) {
        return null;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    @Override
    public long getGroupId(int groupPosition) {
        if (!mDataValid) return 0; 
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        if (!mDataValid) return 0; 
        if (moveCursorToChildPosition(groupPosition, childPosition)) {
            return getLong(mIdIndex);
        }
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public void onGroupExpanded(int groupPosition) {
    }

    @Override
    public void onGroupCollapsed(int groupPosition) {
    }

    @Override
    public long getCombinedChildId(long groupId, long childId) {
        if (!mDataValid) return 0; 
        return childId;
    }

    @Override
    public long getCombinedGroupId(long groupId) {
        if (!mDataValid) return 0; 
        return groupId;
    }

    @Override
    public boolean isEmpty() {
        return !mDataValid || mCursor == null || mCursor.isClosed() || mCursor.getCount() == 0;
    }
}

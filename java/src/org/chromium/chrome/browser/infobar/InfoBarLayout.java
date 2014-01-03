// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;

import org.chromium.ui.LocalizationUtils;

import java.util.ArrayList;

/**
 * Layout that can be used to arrange an InfoBar's View.
 * All InfoBars consist of at least:
 * - An icon representing the InfoBar's purpose on the left side.
 * - A message describing the action that the user can take.
 * - A close button on the right side.
 *
 * Views should never be added with anything but a call to addGroup() to ensure that groups are not
 * broken apart.
 *
 * Widths and heights defined in the LayoutParams will be overwritten due to the nature of the
 * layout algorithm.  However, setting a minimum width in another way, like TextView.getMinWidth(),
 * should still be obeyed.
 *
 * Logic for what happens when things are clicked should be implemented by the InfoBarView.
 */
public class InfoBarLayout extends ViewGroup implements View.OnClickListener {
    private static final String TAG = "InfoBarLayout";

    /**
     * Parameters used for laying out children.
     */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        /** Alignment parameters that determine where in the main row an item will float. */
        public static final int ALIGN_START = 0;
        public static final int ALIGN_END = 1;

        /** Whether the View is meant for the main row. */
        public boolean isInMainRow;

        /** Views grouped together are laid out together immediately adjacent to each other. */
        public boolean isGroupedWithNextView;

        /** When on the main row, indicates whether the control floats on the left or the right. */
        public int align;

        /** If the control is a button, ID of the resource that was last used as its background. */
        public int background;

        public LayoutParams() {
            super(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            align = ALIGN_END;
            isInMainRow = true;
        }

        public LayoutParams(LayoutParams other) {
            super(other);
            isGroupedWithNextView = other.isGroupedWithNextView;
            align = other.align;
            isInMainRow = other.isInMainRow;
        }
    }

    private static class GroupInfo {
        public int numViews;
        public int width;
        public int greatestMemberWidth;
        public int endIndex;
        public boolean hasButton;
    };

    private final int mDimensionMinSize;
    private final int mDimensionMargin;
    private final int mDimensionIconSize;
    private final boolean mLayoutRTL;
    private final InfoBarView mInfoBarView;

    private ImageView mIconView;
    private TextView mMessageView;
    private ImageButton mCloseButton;

    /** Background resource IDs to use for the buttons. */
    private final int mBackgroundFloating;
    private final int mBackgroundFullLeft;
    private final int mBackgroundFullRight;

    /**
     * Indices of child Views that start new layout rows.
     * The last entry is the number of child Views, allowing calculation of the size of each row by
     * taking the difference between subsequent indices.
     */
    private ArrayList<Integer> mIndicesOfRows;

    /**
     * Constructs the layout for the specified InfoBar.
     * @param context The context used to render.
     * @param infoBarView InfoBarView that listens to events.
     * @param backgroundType Type of InfoBar background being shown.
     * @param iconResourceId ID of the icon to use for the InfoBar.
     * @param message Message to display.
     */
    public InfoBarLayout(Context context, InfoBarView infoBarView, int backgroundType,
            int iconResourceId) {
        super(context);
        mIndicesOfRows = new ArrayList<Integer>();
        mLayoutRTL = LocalizationUtils.isSystemLayoutDirectionRtl();
        mInfoBarView = infoBarView;

        // Determine what backgrounds we'll be needing for the buttons.
        if (backgroundType == InfoBar.BACKGROUND_TYPE_INFO) {
            mBackgroundFloating = R.drawable.infobar_button_normal_floating;
            mBackgroundFullLeft = R.drawable.infobar_button_normal_full_left;
            mBackgroundFullRight = R.drawable.infobar_button_normal_full_right;
        } else {
            mBackgroundFloating = R.drawable.infobar_button_warning_floating;
            mBackgroundFullLeft = R.drawable.infobar_button_warning_full_left;
            mBackgroundFullRight = R.drawable.infobar_button_warning_full_right;
        }

        // Grab the dimensions.
        mDimensionMinSize =
                context.getResources().getDimensionPixelSize(R.dimen.infobar_min_size);
        mDimensionMargin =
                context.getResources().getDimensionPixelSize(R.dimen.infobar_margin);
        mDimensionIconSize =
                context.getResources().getDimensionPixelSize(R.dimen.infobar_icon_size);

        // Create the main controls.
        mCloseButton = new ImageButton(context);
        mIconView = new ImageView(context);
        mMessageView = (TextView) LayoutInflater.from(context).inflate(R.layout.infobar_text, null);
        addGroup(mCloseButton, mIconView, mMessageView);

        // Set up the close button.
        mCloseButton.setId(R.id.infobar_close_button);
        mCloseButton.setImageResource(R.drawable.infobar_dismiss);
        mCloseButton.setBackgroundResource(R.drawable.infobar_close_bg);
        mCloseButton.setOnClickListener(this);

        mCloseButton.setContentDescription(getResources().getString(R.string.infobar_close));

        // Set up the icon.
        mIconView.setFocusable(false);
        if (iconResourceId != 0) {
            mIconView.setImageResource(iconResourceId);
        } else {
            mIconView.setVisibility(View.INVISIBLE);
        }

        // Set up the TextView.
        mMessageView.setMovementMethod(LinkMovementMethod.getInstance());
        mMessageView.setText(infoBarView.getMessageText(context), TextView.BufferType.SPANNABLE);

        // Only the close button floats to the right; the icon and the message both float left.
        ((LayoutParams) mIconView.getLayoutParams()).align = LayoutParams.ALIGN_START;
        ((LayoutParams) mMessageView.getLayoutParams()).align = LayoutParams.ALIGN_START;

        // Vertically center the icon and close buttons of an unstretched InfoBar.  If the InfoBar
        // is stretched, they both stay in place.
        mIconView.getLayoutParams().width = mDimensionIconSize;
        mIconView.getLayoutParams().height = mDimensionIconSize;

        // We apply padding to the close button so that it has a big touch target.
        int closeButtonHeight = mCloseButton.getDrawable().getIntrinsicHeight();
        int closePadding = (mDimensionMinSize - closeButtonHeight) / 2;
        if (closePadding >= 0) {
            mCloseButton.setPadding(closePadding, closePadding, closePadding, closePadding);
        } else {
            assert closePadding >= 0 : "Assets are too large for this layout.";
        }

        // Add all of the other InfoBar specific controls.
        infoBarView.createContent(this);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    /**
     * Add a view to the Layout.
     * This function must never be called with an index that isn't -1 to ensure that groups aren't
     * broken apart.
     */
    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (index == -1) {
            super.addView(child, index, params);
        } else {
            assert false : "Adding children at random places can break group structure.";
            super.addView(child, -1, params);
        }
    }

    /**
     * Add a group of Views that are measured and laid out together.
     */
    public void addGroup(View... group) {
        for (int i = 0; i < group.length; i++) {
            final View member = group[i];
            addView(member);

            LayoutParams params = (LayoutParams) member.getLayoutParams();
            params.isGroupedWithNextView = (i != group.length - 1);
        }
    }

    /**
     * Add up to two buttons to the layout.
     *
     * Buttons with null text are hidden from view.  The secondary button may only exist if the
     * primary button does.
     *
     * @param primaryText Text for the primary button.
     * @param secondaryText Text for the secondary button.
     */
    public void addButtons(String primaryText, String secondaryText) {
        Button primaryButton = null;
        Button secondaryButton = null;

        if (!TextUtils.isEmpty(secondaryText)) {
            secondaryButton = (Button) LayoutInflater.from(getContext()).inflate(
                    R.layout.infobar_button, null);
            secondaryButton.setId(R.id.button_secondary);
            secondaryButton.setOnClickListener(this);
            secondaryButton.setText(secondaryText);
        }

        if (!TextUtils.isEmpty(primaryText)) {
            primaryButton = (Button) LayoutInflater.from(getContext()).inflate(
                    R.layout.infobar_button, null);
            primaryButton.setId(R.id.button_primary);
            primaryButton.setOnClickListener(this);
            primaryButton.setText(primaryText);
        }

        // Group the buttons together so that they are laid out next to each other.
        if (primaryButton == null && secondaryButton != null) {
            assert false : "When using only one button, make it the primary button.";
        } else if (primaryButton != null && secondaryButton != null) {
            addGroup(secondaryButton, primaryButton);
        } else if (primaryButton != null) {
            addGroup(primaryButton);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int rowWidth = right - left;
        int rowTop = layoutMainRow(rowWidth);
        for (int row = 1; row < mIndicesOfRows.size() - 1; row++) {
            rowTop = layoutRow(row, rowTop, rowWidth);
        }
    }

    /**
     * Lays out the controls in the main row.
     *
     * This method is complicated mainly because of the arbitrariness for when a control can
     * float either left or right, and whether we're doing an RTL layout.
     *
     * Layout proceeds in three phases:
     * - Laying out of the icon and close button are done separately from the rest of the controls
     *   because they are locked into their respective corners.  These two controls bound the rest
     *   of the controls in the main row.
     *
     * - Items floating to the left are then laid out, traversing the children array in a forwards
     *   manner.  This includes the InfoBar message.
     *
     * - A final pass lays out items aligned to the end of the bar, traversing the children array
     *   backwards so that the correct ordering of the children is preserved.  Going forwards would
     *   cause buttons to flip (e.g.).
     *
     * @param width Maximum width of the row.
     * @return How tall the main row is.
     */
    private int layoutMainRow(int width) {
        final int rowStart = mIndicesOfRows.get(0);
        final int rowEnd = mIndicesOfRows.get(1);
        final int rowHeight = computeMainRowHeight(rowStart, rowEnd);

        // Lay out the icon and the close button.
        int closeLeft;
        int iconPadding = (mDimensionMinSize - mDimensionIconSize) / 2;
        int iconLeft = iconPadding;
        if (mLayoutRTL) {
            iconLeft += width - mDimensionMinSize;
            closeLeft = 0;
        } else {
            closeLeft = width - mCloseButton.getMeasuredWidth();
        }
        mIconView.layout(iconLeft, iconPadding, iconLeft + mDimensionIconSize,
                iconPadding + mDimensionIconSize);
        mCloseButton.layout(closeLeft, 0, closeLeft + mDimensionMinSize, mDimensionMinSize);

        // Go from left to right to catch all items aligned with the start of the InfoBar.
        int rowLeft = mDimensionMinSize;
        int rowRight = width - mDimensionMinSize;
        for (int i = rowStart; i < rowEnd; i++) {
            final View child = getChildAt(i);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            if (params.align != LayoutParams.ALIGN_START || child.getVisibility() == View.GONE
                    || child == mCloseButton || child == mIconView) {
                continue;
            }

            // Everything is vertically centered.
            int childTop = (rowHeight - child.getMeasuredHeight()) / 2;
            int childLeft;

            if (mLayoutRTL) {
                if (!isMainControl(child)) rowRight -= mDimensionMargin;
                childLeft = rowRight - child.getMeasuredWidth();
                rowRight -= child.getMeasuredWidth();
            } else {
                if (!isMainControl(child)) rowLeft += mDimensionMargin;
                childLeft = rowLeft;
                rowLeft += child.getMeasuredWidth();
            }

            child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(),
                    childTop + child.getMeasuredHeight());
        }

        // Go from right to left to catch all items aligned with the end of the InfoBar.
        for (int i = rowEnd - 1; i >= rowStart; i--) {
            final View child = getChildAt(i);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            if (params.align != LayoutParams.ALIGN_END || child.getVisibility() == View.GONE
                    || child == mCloseButton || child == mIconView) {
                continue;
            }

            // Everything is vertically centered.
            int childTop = (rowHeight - child.getMeasuredHeight()) / 2;
            int childLeft;

            if (!mLayoutRTL) {
                childLeft = rowRight - child.getMeasuredWidth();
                rowRight -= child.getMeasuredWidth();
                if (!isMainControl(child)) rowRight -= mDimensionMargin;
            } else {
                childLeft = rowLeft;
                rowLeft += child.getMeasuredWidth();
                if (!isMainControl(child)) rowLeft += mDimensionMargin;
            }

            child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(),
                    childTop + child.getMeasuredHeight());
        }

        return rowHeight;
    }

    /**
     * Lays out the controls in the row other than the main one.
     *
     * This case is much simpler than the main row since the items are all equally sized and simply
     * entails moving through the children and laying them down from the start of the InfoBar to the
     * end.
     *
     * @param row Index of the row
     * @param rowTop Y-coordinate of the layout the controls should be aligned to.
     * @param width Maximum width of the row.
     * @return How tall the row is.
     */
    private int layoutRow(int row, int rowTop, int width) {
        final int rowStart = mIndicesOfRows.get(row);
        final int rowEnd = mIndicesOfRows.get(row + 1);
        final boolean hasButton = isButton(getChildAt(rowStart));

        int rowLeft = hasButton ? 0 : mDimensionMargin;
        int rowRight = width - (hasButton ? 0 : mDimensionMargin);

        for (int i = rowStart; i < rowEnd; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;

            int childLeft;
            if (mLayoutRTL) {
                childLeft = rowRight - child.getMeasuredWidth();
                rowRight -= child.getMeasuredWidth() + (hasButton ? 0 : mDimensionMargin);
            } else {
                childLeft = rowLeft;
                rowLeft += child.getMeasuredWidth() + (hasButton ? 0 : mDimensionMargin);
            }

            child.layout(childLeft, rowTop, childLeft + child.getMeasuredWidth(),
                    rowTop + child.getMeasuredHeight());
        }

        return rowTop + computeRowHeight(rowStart, rowEnd);
    }

    /**
     * Checks if the child is one of the main InfoBar controls.
     * @param child View to check.
     * @return True if the child is one of the main controls.
     */
    private boolean isMainControl(View child) {
        return child == mIconView || child == mMessageView || child == mCloseButton;
    }

    /**
     * Marks that the given index is the start of its own row.
     * @param rowStartIndex Index of the child view at the start of the next row.
     */
    private void addRowStartIndex(int rowStartIndex) {
        if (mIndicesOfRows.size() == 0
                || rowStartIndex != mIndicesOfRows.get(mIndicesOfRows.size() - 1)) {
            mIndicesOfRows.add(rowStartIndex);
        }
    }

    /**
     * Computes properties of the next group of Views to assign to rows.
     * @param startIndex Index of the first child in the group.
     * @return GroupInfo containing information about the current group.
     */
    private GroupInfo getNextGroup(int startIndex) {
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.endIndex = startIndex;

        final int childCount = getChildCount();
        int currentChildIndex = startIndex;
        while (groupInfo.endIndex < childCount) {
            final View groupChild = getChildAt(groupInfo.endIndex);
            if (groupChild.getVisibility() != View.GONE) {
                groupInfo.hasButton |= isButton(groupChild);
                groupInfo.width += groupChild.getMeasuredWidth();
                groupInfo.greatestMemberWidth =
                        Math.max(groupInfo.greatestMemberWidth, groupChild.getMeasuredWidth());
                groupInfo.numViews++;
            }
            groupInfo.endIndex++;

            LayoutParams params = (LayoutParams) groupChild.getLayoutParams();
            if (!params.isGroupedWithNextView) break;
        }

        return groupInfo;
    }

    @Override
    protected void measureChild(View child, int widthSpec, int heightSpec) {
        // If a control is on the main row, then it should be only as large as it wants to be.
        // Otherwise, it must occupy the same amount of space as everything else on its row.
        LayoutParams params = (LayoutParams) child.getLayoutParams();
        params.width = params.isInMainRow ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;
        super.measureChild(child, widthSpec, heightSpec);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        assert getLayoutParams().height == LayoutParams.WRAP_CONTENT
                : "InfoBar heights cannot be constrained.";

        final int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
        mIndicesOfRows.clear();

        // Measure all children with the assumption that they may take up the full size of the
        // parent.  This determines how big each child wants to be.
        final int childCount = getChildCount();
        for (int numChild = 0; numChild < childCount; numChild++) {
            final View child = getChildAt(numChild);
            if (child.getVisibility() == View.GONE) continue;
            ((LayoutParams) child.getLayoutParams()).isInMainRow = true;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
        }

        // Allocate as many Views as possible to the main row, then place everything else on the
        // following rows.
        int currentChildIndex = measureMainRow(maxWidth);
        measureRemainingRows(maxWidth, currentChildIndex);

        // Buttons must have their backgrounds manually changed to give the illusion of having a
        // single pixel boundary between them.
        updateBackgroundsForButtons();

        // Determine how tall the container should be by measuring all the children in their rows.
        int layoutHeight = computeHeight();
        setMeasuredDimension(resolveSize(maxWidth, widthMeasureSpec),
                resolveSize(layoutHeight, heightMeasureSpec));
    }

    /**
     * Assign as many Views as can fit onto the main row.
     *
     * The main row consists of at least the icon, the close button, and the message.  Groups of
     * controls are added to the main row as long as they can fit within the width of the InfoBar.
     *
     * @param maxWidth The maximum width of the main row.
     * @return The index of the last child that couldn't fit on the main row.
     */
    private int measureMainRow(int maxWidth) {
        final int childCount = getChildCount();

        // The main row has the icon and the close button taking the upper left and upper right
        // corners of the InfoBar, each of which occupies a square of
        // mDimensionMinSize x mDimensionMinSize pixels.
        GroupInfo mainControlInfo = getNextGroup(0);
        int remainingWidth = maxWidth - (mDimensionMinSize * 2) - mMessageView.getMeasuredWidth();
        addRowStartIndex(0);

        // Go through the rest of the Views and keep adding them until they can't fit.
        int currentChildIndex = mainControlInfo.endIndex;
        while (currentChildIndex < childCount && remainingWidth > 0) {
            GroupInfo groupInfo = getNextGroup(currentChildIndex);
            int widthWithMargins = groupInfo.width + mDimensionMargin * groupInfo.numViews;

            if (widthWithMargins <= remainingWidth) {
                // If the group fits on the main row, add it.
                currentChildIndex = groupInfo.endIndex;
                remainingWidth -= widthWithMargins;
            } else {
                // We can't fit the current group on the main row.
                break;
            }
        }
        addRowStartIndex(currentChildIndex);

        // The icon and the close button are set to be squares occupying the upper left and
        // upper right corners of the InfoBar.
        int specWidth = MeasureSpec.makeMeasureSpec(mDimensionMinSize, MeasureSpec.EXACTLY);
        int specHeight = MeasureSpec.makeMeasureSpec(mDimensionMinSize, MeasureSpec.EXACTLY);
        measureChild(mIconView, specWidth, specHeight);
        measureChild(mCloseButton, specWidth, specHeight);

        // Measure out everything else except the message.
        remainingWidth = maxWidth - (mDimensionMinSize * 2);
        for (int i = 0; i < currentChildIndex; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.GONE || isMainControl(child)) continue;

            specWidth = MeasureSpec.makeMeasureSpec(remainingWidth, MeasureSpec.AT_MOST);
            specHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            measureChild(child, specWidth, specHeight);
            remainingWidth -= child.getMeasuredWidth() + mDimensionMargin;
        }

        // The message sucks up the remaining width on the line after all other controls
        // have gotten all the space they requested.
        specWidth = MeasureSpec.makeMeasureSpec(remainingWidth, MeasureSpec.AT_MOST);
        specHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        measureChild(mMessageView, specWidth, specHeight);

        return currentChildIndex;
    }

    /**
     * Assign children to rows in the layout.
     *
     * We first try to assign children in the same group to the same row, but only if they fit when
     * they are of equal width.  Otherwise, we split the group onto multiple rows.
     *
     * @param maxWidth Maximum width that the row can take.
     * @param currentChildIndex Start index of the current group.
     */
    private void measureRemainingRows(int maxWidth, int currentChildIndex) {
        final int childCount = getChildCount();
        final int specHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        while (currentChildIndex < childCount) {
            GroupInfo groupInfo = getNextGroup(currentChildIndex);

            int availableWidth;
            int boundaryMargins;
            if (groupInfo.hasButton) {
                // Buttons take up the full width of the InfoBar.
                availableWidth = maxWidth;
                boundaryMargins = 0;
            } else {
                // Other controls obey the side boundaries, and have boundaries between them.
                availableWidth = maxWidth - mDimensionMargin * 2;
                boundaryMargins = (groupInfo.numViews - 1) * mDimensionMargin;
            }

            // Determine how wide each item would be on the same row, including boundaries.
            int evenWidth = (availableWidth - boundaryMargins) / groupInfo.numViews;

            if (groupInfo.greatestMemberWidth <= evenWidth) {
                // Fit everything on the same row.
                int specWidth = MeasureSpec.makeMeasureSpec(evenWidth, MeasureSpec.EXACTLY);
                for (int i = currentChildIndex; i < groupInfo.endIndex; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == View.GONE) continue;
                    ((LayoutParams) child.getLayoutParams()).isInMainRow = false;
                    measureChild(child, specWidth, specHeight);
                }
                addRowStartIndex(currentChildIndex);
            } else {
                // Add each member of the group to its own row.
                int specWidth = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY);
                for (int i = currentChildIndex; i < groupInfo.endIndex; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == View.GONE) continue;
                    ((LayoutParams) child.getLayoutParams()).isInMainRow = false;
                    measureChild(child, specWidth, specHeight);
                    addRowStartIndex(i);
                }
            }

            currentChildIndex = groupInfo.endIndex;
        }

        addRowStartIndex(childCount);
    }

    /**
     * Calculate how tall the layout is, accounting for margins and children.
     * @return How big the layout should be.
     */
    private int computeHeight() {
        int cumulativeHeight = 0;

        // Calculate how big each row is.
        final int numRows = mIndicesOfRows.size() - 1;
        for (int row = 0; row < numRows; row++) {
            final int rowStart = mIndicesOfRows.get(row);
            final int rowEnd = mIndicesOfRows.get(row + 1);

            if (row == 0) {
                cumulativeHeight += computeMainRowHeight(rowStart, rowEnd);
            } else {
                cumulativeHeight += computeRowHeight(rowStart, rowEnd);
            }
        }

        return cumulativeHeight;
    }

    /**
     * Computes how tall the main row is.
     * @param rowStart Index of the first child.
     * @param rowEnd One past the index of the last child.
     */
    private int computeMainRowHeight(int rowStart, int rowEnd) {
        // The icon and close button already have their margins baked into their padding values,
        // but the other Views have a margin above and below.
        final int verticalMargins = mDimensionMargin * 2;
        int rowHeight = mDimensionMinSize;
        for (int i = rowStart; i < rowEnd; i++) {
            View child = getChildAt(i);
            if (child == mCloseButton || child == mIconView || child.getVisibility() == View.GONE) {
                continue;
            }
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight() + verticalMargins);
        }
        return rowHeight;
    }

    /**
     * Computes how tall a row below the main row is.
     *
     * Margins are only applied downward since the rows above are handling the margin on their side.
     * Buttons ignore margins since they have to be right against the boundary.
     *
     * @param rowStart Index of the first child.
     * @param rowEnd One past the index of the last child.
     */
    private int computeRowHeight(int rowStart, int rowEnd) {
        boolean isButtonRow = isButton(getChildAt(rowStart));
        final int verticalMargins = isButtonRow ? 0 : mDimensionMargin;
        int rowHeight = 0;
        for (int i = rowStart; i < rowEnd; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight() + verticalMargins);
        }
        return rowHeight;
    }

    /**
     * Determines if the given View is either the primary or secondary button.
     * @param child View to check.
     * @return Whether the child is the primary or secondary button.
     */
    private boolean isButton(View child) {
        return child.getId() == R.id.button_secondary || child.getId() == R.id.button_primary;
    }

    /**
     * Update the backgrounds for the buttons to account for their current positioning.
     * The primary and secondary buttons are special-cased in that their backgrounds change to
     * create the illusion of a single-stroke boundary between them.
     */
    private void updateBackgroundsForButtons() {
        boolean bothButtonsExist = findViewById(R.id.button_primary) != null
                && findViewById(R.id.button_secondary) != null;

        for (int row = 0; row < mIndicesOfRows.size() - 1; row++) {
            final int rowStart = mIndicesOfRows.get(row);
            final int rowEnd = mIndicesOfRows.get(row + 1);
            final int rowSize = rowEnd - rowStart;

            for (int i = rowStart; i < rowEnd; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == View.GONE || !isButton(child)) continue;

                // Determine which background we need to show.
                int background;
                if (row == 0) {
                    // Button will be floating.
                    background = mBackgroundFloating;
                } else if (rowSize == 1 || !bothButtonsExist) {
                    // Button takes up the full width of the screen.
                    background = mBackgroundFullRight;
                } else if (mLayoutRTL) {
                    // Primary button will be to the left of the secondary.
                    background = child.getId() == R.id.button_primary
                            ? mBackgroundFullLeft : mBackgroundFullRight;
                } else {
                    // Primary button will be to the right of the secondary.
                    background = child.getId() == R.id.button_primary
                            ? mBackgroundFullRight : mBackgroundFullLeft;
                }

                // Update the background.
                LayoutParams params = (LayoutParams) child.getLayoutParams();
                if (params.background != background) {
                    params.background = background;

                    // Save the padding; Android decides to overwrite it on some builds.
                    int paddingLeft = child.getPaddingLeft();
                    int paddingTop = child.getPaddingTop();
                    int paddingRight = child.getPaddingRight();
                    int paddingBottom = child.getPaddingBottom();
                    int buttonWidth = child.getMeasuredWidth();
                    int buttonHeight = child.getMeasuredHeight();

                    // Set the background, then restore the padding.
                    child.setBackgroundResource(background);
                    child.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);

                    // Re-measuring is necessary to correct the text gravity.
                    int specWidth = MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY);
                    int specHeight = MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY);
                    measureChild(child, specWidth, specHeight);
                }
            }
        }
    }

    /**
     * Listens for View clicks.
     * Classes that override this function MUST call this one.
     * @param view View that was clicked on.
     */
    @Override
    public void onClick(View view) {
        mInfoBarView.setControlsEnabled(false);
        if (view.getId() == R.id.infobar_close_button) {
            mInfoBarView.onCloseButtonClicked();
        } else if (view.getId() == R.id.button_primary) {
            mInfoBarView.onButtonClicked(true);
        } else if (view.getId() == R.id.button_secondary) {
            mInfoBarView.onButtonClicked(false);
        }
    }

}

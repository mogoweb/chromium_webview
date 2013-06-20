///*
// * Copyright (C) 2010 The Android Open Source Project
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
//import android.content.res.TypedArray;
//import android.graphics.drawable.Drawable;
//import android.text.TextUtils;
//import android.util.AttributeSet;
//import android.util.TypedValue;
//import android.view.Gravity;
//import android.view.View;
//import android.view.View.OnClickListener;
//import android.widget.ImageButton;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * Simple bread crumb view
// * Use setController to receive callbacks from user interactions
// * Use pushView, popView, clear, and getTopData to change/access the view stack
// */
//public class BreadCrumbView extends LinearLayout implements OnClickListener {
//    private static final int DIVIDER_PADDING = 12; // dips
//    private static final int CRUMB_PADDING = 8; // dips
//
//    public interface Controller {
//        public void onTop(BreadCrumbView view, int level, Object data);
//    }
//
//    private ImageButton mBackButton;
//    private Controller mController;
//    private List<Crumb> mCrumbs;
//    private boolean mUseBackButton;
//    private Drawable mSeparatorDrawable;
//    private float mDividerPadding;
//    private int mMaxVisible = -1;
//    private Context mContext;
//    private int mCrumbPadding;
//
//    /**
//     * @param context
//     * @param attrs
//     * @param defStyle
//     */
//    public BreadCrumbView(Context context, AttributeSet attrs, int defStyle) {
//        super(context, attrs, defStyle);
//        init(context);
//    }
//
//    /**
//     * @param context
//     * @param attrs
//     */
//    public BreadCrumbView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init(context);
//    }
//
//    /**
//     * @param context
//     */
//    public BreadCrumbView(Context context) {
//        super(context);
//        init(context);
//    }
//
//    private void init(Context ctx) {
//        mContext = ctx;
//        setFocusable(true);
//        mUseBackButton = false;
//        mCrumbs = new ArrayList<Crumb>();
//        TypedArray a = mContext.obtainStyledAttributes(com.android.internal.R.styleable.Theme);
//        mSeparatorDrawable = a.getDrawable(com.android.internal.R.styleable.Theme_dividerVertical);
//        a.recycle();
//        float density = mContext.getResources().getDisplayMetrics().density;
//        mDividerPadding = DIVIDER_PADDING * density;
//        mCrumbPadding = (int) (CRUMB_PADDING * density);
//        addBackButton();
//    }
//
//    public void setUseBackButton(boolean useflag) {
//        mUseBackButton = useflag;
//        updateVisible();
//    }
//
//    public void setController(Controller ctl) {
//        mController = ctl;
//    }
//
//    public int getMaxVisible() {
//        return mMaxVisible;
//    }
//
//    public void setMaxVisible(int max) {
//        mMaxVisible = max;
//        updateVisible();
//    }
//
//    public int getTopLevel() {
//        return mCrumbs.size();
//    }
//
//    public Object getTopData() {
//        Crumb c = getTopCrumb();
//        if (c != null) {
//            return c.data;
//        }
//        return null;
//    }
//
//    public int size() {
//        return mCrumbs.size();
//    }
//
//    public void clear() {
//        while (mCrumbs.size() > 1) {
//            pop(false);
//        }
//        pop(true);
//    }
//
//    public void notifyController() {
//        if (mController != null) {
//            if (mCrumbs.size() > 0) {
//                mController.onTop(this, mCrumbs.size(), getTopCrumb().data);
//            } else {
//                mController.onTop(this, 0, null);
//            }
//        }
//    }
//
//    public View pushView(String name, Object data) {
//        return pushView(name, true, data);
//    }
//
//    public View pushView(String name, boolean canGoBack, Object data) {
//        Crumb crumb = new Crumb(name, canGoBack, data);
//        pushCrumb(crumb);
//        return crumb.crumbView;
//    }
//
//    public void pushView(View view, Object data) {
//        Crumb crumb = new Crumb(view, true, data);
//        pushCrumb(crumb);
//    }
//
//    public void popView() {
//        pop(true);
//    }
//
//    private void addBackButton() {
//        mBackButton = new ImageButton(mContext);
//        mBackButton.setImageResource(R.drawable.ic_back_hierarchy_holo_dark);
//        TypedValue outValue = new TypedValue();
//        getContext().getTheme().resolveAttribute(
//                android.R.attr.selectableItemBackground, outValue, true);
//        int resid = outValue.resourceId;
//        mBackButton.setBackgroundResource(resid);
//        mBackButton.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
//                LayoutParams.MATCH_PARENT));
//        mBackButton.setOnClickListener(this);
//        mBackButton.setVisibility(View.GONE);
//        mBackButton.setContentDescription(mContext.getText(
//                R.string.accessibility_button_bookmarks_folder_up));
//        addView(mBackButton, 0);
//    }
//
//    private void pushCrumb(Crumb crumb) {
//        if (mCrumbs.size() > 0) {
//            addSeparator();
//        }
//        mCrumbs.add(crumb);
//        addView(crumb.crumbView);
//        updateVisible();
//        crumb.crumbView.setOnClickListener(this);
//    }
//
//    private void addSeparator() {
//        View sep = makeDividerView();
//        sep.setLayoutParams(makeDividerLayoutParams());
//        addView(sep);
//    }
//
//    private ImageView makeDividerView() {
//        ImageView result = new ImageView(mContext);
//        result.setImageDrawable(mSeparatorDrawable);
//        result.setScaleType(ImageView.ScaleType.FIT_XY);
//        return result;
//    }
//
//    private LayoutParams makeDividerLayoutParams() {
//        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
//                LayoutParams.MATCH_PARENT);
//        params.topMargin = (int) mDividerPadding;
//        params.bottomMargin = (int) mDividerPadding;
//        return params;
//    }
//
//    private void pop(boolean notify) {
//        int n = mCrumbs.size();
//        if (n > 0) {
//            removeLastView();
//            if (!mUseBackButton || (n > 1)) {
//                // remove separator
//                removeLastView();
//            }
//            mCrumbs.remove(n - 1);
//            if (mUseBackButton) {
//                Crumb top = getTopCrumb();
//                if (top != null && top.canGoBack) {
//                    mBackButton.setVisibility(View.VISIBLE);
//                } else {
//                    mBackButton.setVisibility(View.GONE);
//                }
//            }
//            updateVisible();
//            if (notify) {
//                notifyController();
//            }
//        }
//    }
//
//    private void updateVisible() {
//        // start at index 1 (0 == back button)
//        int childIndex = 1;
//        if (mMaxVisible >= 0) {
//            int invisibleCrumbs = size() - mMaxVisible;
//            if (invisibleCrumbs > 0) {
//                int crumbIndex = 0;
//                while (crumbIndex < invisibleCrumbs) {
//                    // Set the crumb to GONE.
//                    getChildAt(childIndex).setVisibility(View.GONE);
//                    childIndex++;
//                    // Each crumb is followed by a separator (except the last
//                    // one).  Also make it GONE
//                    if (getChildAt(childIndex) != null) {
//                        getChildAt(childIndex).setVisibility(View.GONE);
//                    }
//                    childIndex++;
//                    // Move to the next crumb.
//                    crumbIndex++;
//                }
//            }
//            // Make sure the last two are visible.
//            int childCount = getChildCount();
//            while (childIndex < childCount) {
//                getChildAt(childIndex).setVisibility(View.VISIBLE);
//                childIndex++;
//            }
//        } else {
//            int count = getChildCount();
//            for (int i = childIndex; i < count ; i++) {
//                getChildAt(i).setVisibility(View.VISIBLE);
//            }
//        }
//        if (mUseBackButton) {
//            boolean canGoBack = getTopCrumb() != null ? getTopCrumb().canGoBack : false;
//            mBackButton.setVisibility(canGoBack ? View.VISIBLE : View.GONE);
//        } else {
//            mBackButton.setVisibility(View.GONE);
//        }
//    }
//
//    private void removeLastView() {
//        int ix = getChildCount();
//        if (ix > 0) {
//            removeViewAt(ix-1);
//        }
//    }
//
//    Crumb getTopCrumb() {
//        Crumb crumb = null;
//        if (mCrumbs.size() > 0) {
//            crumb = mCrumbs.get(mCrumbs.size() - 1);
//        }
//        return crumb;
//    }
//
//    @Override
//    public void onClick(View v) {
//        if (mBackButton == v) {
//            popView();
//            notifyController();
//        } else {
//            // pop until view matches crumb view
//            while (v != getTopCrumb().crumbView) {
//                pop(false);
//            }
//            notifyController();
//        }
//    }
//    @Override
//    public int getBaseline() {
//        int ix = getChildCount();
//        if (ix > 0) {
//            // If there is at least one crumb, the baseline will be its
//            // baseline.
//            return getChildAt(ix-1).getBaseline();
//        }
//        return super.getBaseline();
//    }
//
//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        int height = mSeparatorDrawable.getIntrinsicHeight();
//        if (getMeasuredHeight() < height) {
//            // This should only be an issue if there are currently no separators
//            // showing; i.e. if there is one crumb and no back button.
//            int mode = View.MeasureSpec.getMode(heightMeasureSpec);
//            switch(mode) {
//                case View.MeasureSpec.AT_MOST:
//                    if (View.MeasureSpec.getSize(heightMeasureSpec) < height) {
//                        return;
//                    }
//                    break;
//                case View.MeasureSpec.EXACTLY:
//                    return;
//                default:
//                    break;
//            }
//            setMeasuredDimension(getMeasuredWidth(), height);
//        }
//    }
//
//    class Crumb {
//
//        public View crumbView;
//        public boolean canGoBack;
//        public Object data;
//
//        public Crumb(String title, boolean backEnabled, Object tag) {
//            init(makeCrumbView(title), backEnabled, tag);
//        }
//
//        public Crumb(View view, boolean backEnabled, Object tag) {
//            init(view, backEnabled, tag);
//        }
//
//        private void init(View view, boolean back, Object tag) {
//            canGoBack = back;
//            crumbView = view;
//            data = tag;
//        }
//
//        private TextView makeCrumbView(String name) {
//            TextView tv = new TextView(mContext);
//            tv.setTextAppearance(mContext, android.R.style.TextAppearance_Medium);
//            tv.setPadding(mCrumbPadding, 0, mCrumbPadding, 0);
//            tv.setGravity(Gravity.CENTER_VERTICAL);
//            tv.setText(name);
//            tv.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
//                    LayoutParams.MATCH_PARENT));
//            tv.setSingleLine();
//            tv.setEllipsize(TextUtils.TruncateAt.END);
//            return tv;
//        }
//
//    }
//
//}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.view.View;
import android.widget.ImageButton;

import org.chromium.chrome.R;
import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;

/**
 * The base class for all InfoBar classes.
 * Note that infobars expire by default when a new navigation occurs.
 * Make sure to use setExpireOnNavigation(false) if you want an infobar to be sticky.
 */
public abstract class InfoBar implements InfoBarView {
    private static final String TAG = "InfoBar";

    /**
     * Possible labels of all the infobar buttons.
     *
     * Make sure this set of values is aligned with the C++ correspondent in
     * infobar_android.h
     */
    public static final int ACTION_TYPE_NONE = 0;

    // Confirm infobar
    public static final int ACTION_TYPE_OK = 1;
    public static final int ACTION_TYPE_CANCEL = 2;

    // Translate infobar
    public static final int ACTION_TYPE_TRANSLATE = 3;
    public static final int ACTION_TYPE_TRANSLATE_SHOW_ORIGINAL = 4;

    // Background types
    public static final int BACKGROUND_TYPE_INFO = 0;
    public static final int BACKGROUND_TYPE_WARNING = 1;

    private final int mBackgroundType;
    private final int mIconDrawableId;

    private InfoBarListeners.Dismiss mListener;
    private ContentWrapperView mContentView;
    private InfoBarContainer mContainer;
    private Context mContext;

    private boolean mExpireOnNavigation;
    private boolean mIsDismissed;
    private boolean mControlsEnabled;

    // This cannot be private until the swap in place infrastructure is
    // improved since subclasses need to access a possibly replaced native
    // pointer.
    protected int mNativeInfoBarPtr;

    // Used by tests to reference infobars.
    private final int mId;
    private static int sIdCounter = 0;
    private static int generateId() {
        return sIdCounter++;
    }

    /**
     * @param listener Listens to when buttons have been clicked on the InfoBar.
     * @param backgroundType Background type to use (INFO or WARNING).
     * @param iconDrawableId ID of the resource to use for the Icon.  If 0, no icon will be shown.
     */
    public InfoBar(InfoBarListeners.Dismiss listener, int backgroundType, int iconDrawableId) {
        mListener = listener;
        mId = generateId();
        mBackgroundType = backgroundType;
        mIconDrawableId = iconDrawableId;
        mExpireOnNavigation = true;
    }

    /**
     * Stores a pointer to the native-side counterpart of this InfoBar.
     * @param nativeInfoBarPtr Pointer to the NativeInfoBar.
     */
    protected void setNativeInfoBar(int nativeInfoBarPtr) {
        if (nativeInfoBarPtr != 0) {
            // The native code takes care of expiring infobars on navigations.
            mExpireOnNavigation = false;
            mNativeInfoBarPtr = nativeInfoBarPtr;
        }
    }

    /**
     * Change the pointer to the native-side counterpart of this InfoBar.  Native-side code is
     * responsible for managing the cleanup of the pointer.
     * @param nativeInfoBarPtr Pointer to the NativeInfoBar.
     */
    protected void replaceNativePointer(int newInfoBarPtr) {
        mNativeInfoBarPtr = newInfoBarPtr;
    }

    // Determine if the infobar should be dismissed when |url| is loaded.  Calling
    // setExpireOnNavigation(true/false) causes this method always to return true/false.
    // This only applies to java-only infobars. C++ infobars will use the same logic
    // as other platforms so they are not attempted to be dismissed twice.
    // It should really be removed once all infobars have a C++ counterpart.
    public final boolean shouldExpire(String url) {
        return mExpireOnNavigation && mNativeInfoBarPtr == 0;
    }

    // Sets whether the bar should be dismissed when a navigation occurs.
    public void setExpireOnNavigation(boolean expireOnNavigation) {
        mExpireOnNavigation = expireOnNavigation;
    }

    /**
     * @return true if this java infobar owns this {@code nativePointer}
     */
    boolean ownsNativeInfoBar(int nativePointer) {
        return mNativeInfoBarPtr == nativePointer;
    }

    /**
     * @return whether or not the InfoBar has been dismissed.
     */
    protected boolean isDismissed() {
        return mIsDismissed;
    }

    /**
     * Sets the Context used when creating the InfoBar.
     */
    protected void setContext(Context context) {
        mContext = context;
    }

    /**
     * @return The Context used to create the InfoBar.  This will be null until the InfoBar is added
     *         to the InfoBarContainer, and should never be null afterward.
     */
    protected Context getContext() {
        return mContext;
    }

    /**
     * Creates the View that represents the InfoBar.
     * @return The View representing the InfoBar.
     */
    protected final View createView() {
        assert mContext != null;
        return new InfoBarLayout(mContext, this, mBackgroundType, mIconDrawableId);
    }

    /**
     * Used to close a java only infobar.
     */
    public void dismissJavaOnlyInfoBar() {
        assert mNativeInfoBarPtr == 0;
        if (closeInfoBar() && mListener != null) {
            mListener.onInfoBarDismissed(this);
        }
    }

    /**
     * @return whether the infobar actually needed closing.
     */
    @CalledByNative
    public boolean closeInfoBar() {
        if (!mIsDismissed) {
            mIsDismissed = true;
            if (!mContainer.hasBeenDestroyed()) {
                // If the container was destroyed, it's already been emptied of all its infobars.
                mContainer.removeInfoBar(this);
            }
            return true;
        }
        return false;
    }

    protected ContentWrapperView getContentWrapper(boolean createIfNotFound) {
        if (mContentView == null && createIfNotFound) {
            mContentView = new ContentWrapperView(getContext(), this, mBackgroundType,
                    createView(), getInfoBarContainer().areInfoBarsOnTop());
            mContentView.setFocusable(false);
        }
        return mContentView;
    }

    protected InfoBarContainer getInfoBarContainer() {
        return mContainer;
    }

    /**
     * @return The content view for the info bar.
     */
    public ContentWrapperView getContentWrapper() {
        return getContentWrapper(true);
    }

    void setInfoBarContainer(InfoBarContainer container) {
        mContainer = container;
    }

    public boolean areControlsEnabled() {
        return mControlsEnabled;
    }

    @Override
    public void setControlsEnabled(boolean state) {
        mControlsEnabled = state;

        // Handle the close button.
        if (mContentView != null) {
            View closeButton = mContentView.findViewById(R.id.infobar_close_button);
            if (closeButton != null) closeButton.setEnabled(state);
        }
    }

    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
    }

    @Override
    public void onLinkClicked() {
        nativeOnLinkClicked(mNativeInfoBarPtr);
    }

    @Override
    public void createContent(InfoBarLayout layout) {
    }

    @Override
    public String getPrimaryButtonText(Context context) {
        return null;
    }

    @Override
    public String getSecondaryButtonText(Context context) {
        return null;
    }

    /**
     * Returns the id of the tab this infobar is showing into.
     */
    public int getTabId() {
        return mContainer.getTabId();
    }

    @VisibleForTesting
    public int getId() {
        return mId;
    }

    @VisibleForTesting
    public void setDismissedListener(InfoBarListeners.Dismiss listener) {
        mListener = listener;
    }

    protected native void nativeOnLinkClicked(int nativeInfoBarAndroid);
    protected native void nativeOnButtonClicked(
            int nativeInfoBarAndroid, int action, String actionValue);
    protected native void nativeOnCloseButtonClicked(int nativeInfoBarAndroid);
}

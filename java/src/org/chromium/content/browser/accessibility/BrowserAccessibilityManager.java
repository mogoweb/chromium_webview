// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.accessibility;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.RenderCoordinates;

import java.util.ArrayList;
import java.util.List;

/**
 * Native accessibility for a {@link ContentViewCore}.
 *
 * This class is safe to load on ICS and can be used to run tests, but
 * only the subclass, JellyBeanBrowserAccessibilityManager, actually
 * has a AccessibilityNodeProvider implementation needed for native
 * accessibility.
 */
@JNINamespace("content")
public class BrowserAccessibilityManager {
    private static final String TAG = "BrowserAccessibilityManager";

    private ContentViewCore mContentViewCore;
    private final AccessibilityManager mAccessibilityManager;
    private final RenderCoordinates mRenderCoordinates;
    private long mNativeObj;
    private int mAccessibilityFocusId;
    private int mCurrentHoverId;
    private int mCurrentRootId;
    private final int[] mTempLocation = new int[2];
    private final View mView;
    private boolean mUserHasTouchExplored;
    private boolean mPendingScrollToMakeNodeVisible;
    private boolean mFrameInfoInitialized;

    /**
     * Create a BrowserAccessibilityManager object, which is owned by the C++
     * BrowserAccessibilityManagerAndroid instance, and connects to the content view.
     * @param nativeBrowserAccessibilityManagerAndroid A pointer to the counterpart native
     *     C++ object that owns this object.
     * @param contentViewCore The content view that this object provides accessibility for.
     */
    @CalledByNative
    private static BrowserAccessibilityManager create(long nativeBrowserAccessibilityManagerAndroid,
            ContentViewCore contentViewCore) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return new KitKatBrowserAccessibilityManager(
                    nativeBrowserAccessibilityManagerAndroid, contentViewCore);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return new JellyBeanBrowserAccessibilityManager(
                    nativeBrowserAccessibilityManagerAndroid, contentViewCore);
        } else {
            return new BrowserAccessibilityManager(
                    nativeBrowserAccessibilityManagerAndroid, contentViewCore);
        }
    }

    protected BrowserAccessibilityManager(long nativeBrowserAccessibilityManagerAndroid,
            ContentViewCore contentViewCore) {
        mNativeObj = nativeBrowserAccessibilityManagerAndroid;
        mContentViewCore = contentViewCore;
        mContentViewCore.setBrowserAccessibilityManager(this);
        mAccessibilityFocusId = View.NO_ID;
        mCurrentHoverId = View.NO_ID;
        mCurrentRootId = View.NO_ID;
        mView = mContentViewCore.getContainerView();
        mRenderCoordinates = mContentViewCore.getRenderCoordinates();
        mAccessibilityManager =
            (AccessibilityManager) mContentViewCore.getContext()
            .getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @CalledByNative
    private void onNativeObjectDestroyed() {
        if (mContentViewCore.getBrowserAccessibilityManager() == this) {
            mContentViewCore.setBrowserAccessibilityManager(null);
        }
        mNativeObj = 0;
        mContentViewCore = null;
    }

    /**
     * @return An AccessibilityNodeProvider on JellyBean, and null on previous versions.
     */
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return null;
    }

    /**
     * @see AccessibilityNodeProvider#createAccessibilityNodeInfo(int)
     */
    protected AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        if (!mAccessibilityManager.isEnabled() || mNativeObj == 0) {
            return null;
        }

        int rootId = nativeGetRootId(mNativeObj);

        if (virtualViewId == View.NO_ID) {
            return createNodeForHost(rootId);
        }

        if (!mFrameInfoInitialized) {
            return null;
        }

        final AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(mView);
        info.setPackageName(mContentViewCore.getContext().getPackageName());
        info.setSource(mView, virtualViewId);

        if (virtualViewId == rootId) {
            info.setParent(mView);
        }

        if (nativePopulateAccessibilityNodeInfo(mNativeObj, info, virtualViewId)) {
            return info;
        } else {
            info.recycle();
            return null;
        }
    }

    /**
     * @see AccessibilityNodeProvider#findAccessibilityNodeInfosByText(String, int)
     */
    protected List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text,
            int virtualViewId) {
        return new ArrayList<AccessibilityNodeInfo>();
    }

    /**
     * @see AccessibilityNodeProvider#performAction(int, int, Bundle)
     */
    protected boolean performAction(int virtualViewId, int action, Bundle arguments) {
        // We don't support any actions on the host view or nodes
        // that are not (any longer) in the tree.
        if (!mAccessibilityManager.isEnabled() || mNativeObj == 0
                || !nativeIsNodeValid(mNativeObj, virtualViewId)) {
            return false;
        }

        switch (action) {
            case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS:
                if (mAccessibilityFocusId == virtualViewId) {
                    return true;
                }

                mAccessibilityFocusId = virtualViewId;
                sendAccessibilityEvent(mAccessibilityFocusId,
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                if (mCurrentHoverId == View.NO_ID) {
                    nativeScrollToMakeNodeVisible(
                            mNativeObj, mAccessibilityFocusId);
                } else {
                    mPendingScrollToMakeNodeVisible = true;
                }
                return true;
            case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
                if (mAccessibilityFocusId == virtualViewId) {
                    sendAccessibilityEvent(mAccessibilityFocusId,
                            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                    mAccessibilityFocusId = View.NO_ID;
                }
                return true;
            case AccessibilityNodeInfo.ACTION_CLICK:
                nativeClick(mNativeObj, virtualViewId);
                sendAccessibilityEvent(virtualViewId,
                        AccessibilityEvent.TYPE_VIEW_CLICKED);
                return true;
            case AccessibilityNodeInfo.ACTION_FOCUS:
                nativeFocus(mNativeObj, virtualViewId);
                return true;
            case AccessibilityNodeInfo.ACTION_CLEAR_FOCUS:
                nativeBlur(mNativeObj);
                return true;
            default:
                break;
        }
        return false;
    }

    /**
     * @see View#onHoverEvent(MotionEvent)
     */
    public boolean onHoverEvent(MotionEvent event) {
        if (!mAccessibilityManager.isEnabled() || mNativeObj == 0) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
            if (mCurrentHoverId != View.NO_ID) {
                sendAccessibilityEvent(mCurrentHoverId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                mCurrentHoverId = View.NO_ID;
            }
            if (mPendingScrollToMakeNodeVisible) {
                nativeScrollToMakeNodeVisible(
                        mNativeObj, mAccessibilityFocusId);
            }
            mPendingScrollToMakeNodeVisible = false;
            return true;
        }

        mUserHasTouchExplored = true;
        float x = event.getX();
        float y = event.getY();

        // Convert to CSS coordinates.
        int cssX = (int) (mRenderCoordinates.fromPixToLocalCss(x) +
                          mRenderCoordinates.getScrollX());
        int cssY = (int) (mRenderCoordinates.fromPixToLocalCss(y) +
                          mRenderCoordinates.getScrollY());
        int id = nativeHitTest(mNativeObj, cssX, cssY);
        if (mCurrentHoverId != id) {
            sendAccessibilityEvent(mCurrentHoverId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
            sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
            mCurrentHoverId = id;
        }

        return true;
    }

    /**
     * Called by ContentViewCore to notify us when the frame info is initialized,
     * the first time, since until that point, we can't use mRenderCoordinates to transform
     * web coordinates to screen coordinates.
     */
    public void notifyFrameInfoInitialized() {
        if (mFrameInfoInitialized) return;

        mFrameInfoInitialized = true;
        // Invalidate the host, since the chrome accessibility tree is now
        // ready and listed as the child of the host.
        mView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);

        // (Re-) focus focused element, since we weren't able to create an
        // AccessibilityNodeInfo for this element before.
        if (mAccessibilityFocusId != View.NO_ID) {
            sendAccessibilityEvent(mAccessibilityFocusId,
                                   AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        }
    }

    private void sendAccessibilityEvent(int virtualViewId, int eventType) {
        // If mFrameInfoInitialized is false, then the virtual hierarchy
        // doesn't exist in the view of the Android framework, so should
        // never send any events.
        if (!mAccessibilityManager.isEnabled() || mNativeObj == 0
                || !mFrameInfoInitialized) {
            return;
        }

        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(mContentViewCore.getContext().getPackageName());
        event.setSource(mView, virtualViewId);
        if (!nativePopulateAccessibilityEvent(mNativeObj, event, virtualViewId, eventType)) {
            event.recycle();
            return;
        }

        // This is currently needed if we want Android to draw the yellow box around
        // the item that has accessibility focus. In practice, this doesn't seem to slow
        // things down, because it's only called when the accessibility focus moves.
        // TODO(dmazzoni): remove this if/when Android framework fixes bug.
        mContentViewCore.getContainerView().postInvalidate();

        mContentViewCore.getContainerView().requestSendAccessibilityEvent(mView, event);
    }

    private Bundle getOrCreateBundleForAccessibilityEvent(AccessibilityEvent event) {
        Bundle bundle = (Bundle) event.getParcelableData();
        if (bundle == null) {
            bundle = new Bundle();
            event.setParcelableData(bundle);
        }
        return bundle;
    }

    private AccessibilityNodeInfo createNodeForHost(int rootId) {
        // Since we don't want the parent to be focusable, but we can't remove
        // actions from a node, copy over the necessary fields.
        final AccessibilityNodeInfo result = AccessibilityNodeInfo.obtain(mView);
        final AccessibilityNodeInfo source = AccessibilityNodeInfo.obtain(mView);
        mView.onInitializeAccessibilityNodeInfo(source);

        // Copy over parent and screen bounds.
        Rect rect = new Rect();
        source.getBoundsInParent(rect);
        result.setBoundsInParent(rect);
        source.getBoundsInScreen(rect);
        result.setBoundsInScreen(rect);

        // Set up the parent view, if applicable.
        final ViewParent parent = mView.getParentForAccessibility();
        if (parent instanceof View) {
            result.setParent((View) parent);
        }

        // Populate the minimum required fields.
        result.setVisibleToUser(source.isVisibleToUser());
        result.setEnabled(source.isEnabled());
        result.setPackageName(source.getPackageName());
        result.setClassName(source.getClassName());

        // Add the Chrome root node.
        if (mFrameInfoInitialized) {
            result.addChild(mView, rootId);
        }

        return result;
    }

    @CalledByNative
    private void handlePageLoaded(int id) {
        if (mUserHasTouchExplored) return;

        mAccessibilityFocusId = id;
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
    }

    @CalledByNative
    private void handleFocusChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_FOCUSED);

        // Update accessibility focus if not already set to this node.
        if (mAccessibilityFocusId != id) {
            sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            mAccessibilityFocusId = id;
        }
    }

    @CalledByNative
    private void handleCheckStateChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    @CalledByNative
    private void handleTextSelectionChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }

    @CalledByNative
    private void handleEditableTextChanged(int id) {
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
    }

    @CalledByNative
    private void handleContentChanged(int id) {
        int rootId = nativeGetRootId(mNativeObj);
        if (rootId != mCurrentRootId) {
            mCurrentRootId = rootId;
            mView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        } else {
            sendAccessibilityEvent(id, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }
    }

    @CalledByNative
    private void handleNavigate() {
        mAccessibilityFocusId = View.NO_ID;
        mUserHasTouchExplored = false;
        mFrameInfoInitialized = false;
        // Invalidate the host, since its child is now gone.
        mView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @CalledByNative
    private void handleScrolledToAnchor(int id) {
        if (mAccessibilityFocusId == id) {
            return;
        }

        mAccessibilityFocusId = id;
        sendAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
    }

    @CalledByNative
    private void announceLiveRegionText(String text) {
        mView.announceForAccessibility(text);
    }

    @CalledByNative
    private void setAccessibilityNodeInfoParent(AccessibilityNodeInfo node, int parentId) {
        node.setParent(mView, parentId);
    }

    @CalledByNative
    private void addAccessibilityNodeInfoChild(AccessibilityNodeInfo node, int childId) {
        node.addChild(mView, childId);
    }

    @CalledByNative
    private void setAccessibilityNodeInfoBooleanAttributes(AccessibilityNodeInfo node,
            int virtualViewId, boolean checkable, boolean checked, boolean clickable,
            boolean enabled, boolean focusable, boolean focused, boolean password,
            boolean scrollable, boolean selected, boolean visibleToUser) {
        node.setCheckable(checkable);
        node.setChecked(checked);
        node.setClickable(clickable);
        node.setEnabled(enabled);
        node.setFocusable(focusable);
        node.setFocused(focused);
        node.setPassword(password);
        node.setScrollable(scrollable);
        node.setSelected(selected);
        node.setVisibleToUser(visibleToUser);

        if (focusable) {
            if (focused) {
                node.addAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
            } else {
                node.addAction(AccessibilityNodeInfo.ACTION_FOCUS);
            }
        }

        if (mAccessibilityFocusId == virtualViewId) {
            node.setAccessibilityFocused(true);
            node.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            node.setAccessibilityFocused(false);
            node.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        }

        if (clickable) {
            node.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    @CalledByNative
    private void setAccessibilityNodeInfoStringAttributes(AccessibilityNodeInfo node,
            String className, String contentDescription) {
        node.setClassName(className);
        node.setContentDescription(contentDescription);
    }

    @CalledByNative
    private void setAccessibilityNodeInfoLocation(AccessibilityNodeInfo node,
            int absoluteLeft, int absoluteTop, int parentRelativeLeft, int parentRelativeTop,
            int width, int height, boolean isRootNode) {
        // First set the bounds in parent.
        Rect boundsInParent = new Rect(parentRelativeLeft, parentRelativeTop,
                parentRelativeLeft + width, parentRelativeTop + height);
        if (isRootNode) {
            // Offset of the web content relative to the View.
            boundsInParent.offset(0, (int) mRenderCoordinates.getContentOffsetYPix());
        }
        node.setBoundsInParent(boundsInParent);

        // Now set the absolute rect, which requires several transformations.
        Rect rect = new Rect(absoluteLeft, absoluteTop, absoluteLeft + width, absoluteTop + height);

        // Offset by the scroll position.
        rect.offset(-(int) mRenderCoordinates.getScrollX(),
                    -(int) mRenderCoordinates.getScrollY());

        // Convert CSS (web) pixels to Android View pixels
        rect.left = (int) mRenderCoordinates.fromLocalCssToPix(rect.left);
        rect.top = (int) mRenderCoordinates.fromLocalCssToPix(rect.top);
        rect.bottom = (int) mRenderCoordinates.fromLocalCssToPix(rect.bottom);
        rect.right = (int) mRenderCoordinates.fromLocalCssToPix(rect.right);

        // Offset by the location of the web content within the view.
        rect.offset(0,
                    (int) mRenderCoordinates.getContentOffsetYPix());

        // Finally offset by the location of the view within the screen.
        final int[] viewLocation = new int[2];
        mView.getLocationOnScreen(viewLocation);
        rect.offset(viewLocation[0], viewLocation[1]);

        node.setBoundsInScreen(rect);
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoKitKatAttributes(AccessibilityNodeInfo node,
            boolean canOpenPopup,
            boolean contentInvalid,
            boolean dismissable,
            boolean multiLine,
            int inputType,
            int liveRegion) {
        // Requires KitKat or higher.
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoCollectionInfo(AccessibilityNodeInfo node,
            int rowCount, int columnCount, boolean hierarchical) {
        // Requires KitKat or higher.
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoCollectionItemInfo(AccessibilityNodeInfo node,
            int rowIndex, int rowSpan, int columnIndex, int columnSpan, boolean heading) {
        // Requires KitKat or higher.
    }

    @CalledByNative
    protected void setAccessibilityNodeInfoRangeInfo(AccessibilityNodeInfo node,
            int rangeType, float min, float max, float current) {
        // Requires KitKat or higher.
    }

    @CalledByNative
    private void setAccessibilityEventBooleanAttributes(AccessibilityEvent event,
            boolean checked, boolean enabled, boolean password, boolean scrollable) {
        event.setChecked(checked);
        event.setEnabled(enabled);
        event.setPassword(password);
        event.setScrollable(scrollable);
    }

    @CalledByNative
    private void setAccessibilityEventClassName(AccessibilityEvent event, String className) {
        event.setClassName(className);
    }

    @CalledByNative
    private void setAccessibilityEventListAttributes(AccessibilityEvent event,
            int currentItemIndex, int itemCount) {
        event.setCurrentItemIndex(currentItemIndex);
        event.setItemCount(itemCount);
    }

    @CalledByNative
    private void setAccessibilityEventScrollAttributes(AccessibilityEvent event,
            int scrollX, int scrollY, int maxScrollX, int maxScrollY) {
        event.setScrollX(scrollX);
        event.setScrollY(scrollY);
        event.setMaxScrollX(maxScrollX);
        event.setMaxScrollY(maxScrollY);
    }

    @CalledByNative
    private void setAccessibilityEventTextChangedAttrs(AccessibilityEvent event,
            int fromIndex, int addedCount, int removedCount, String beforeText, String text) {
        event.setFromIndex(fromIndex);
        event.setAddedCount(addedCount);
        event.setRemovedCount(removedCount);
        event.setBeforeText(beforeText);
        event.getText().add(text);
    }

    @CalledByNative
    private void setAccessibilityEventSelectionAttrs(AccessibilityEvent event,
            int fromIndex, int addedCount, int itemCount, String text) {
        event.setFromIndex(fromIndex);
        event.setAddedCount(addedCount);
        event.setItemCount(itemCount);
        event.getText().add(text);
    }

    @CalledByNative
    protected void setAccessibilityEventKitKatAttributes(AccessibilityEvent event,
            boolean canOpenPopup,
            boolean contentInvalid,
            boolean dismissable,
            boolean multiLine,
            int inputType,
            int liveRegion) {
        // Backwards compatibility for KitKat AccessibilityNodeInfo fields.
        Bundle bundle = getOrCreateBundleForAccessibilityEvent(event);
        bundle.putBoolean("AccessibilityNodeInfo.canOpenPopup", canOpenPopup);
        bundle.putBoolean("AccessibilityNodeInfo.contentInvalid", contentInvalid);
        bundle.putBoolean("AccessibilityNodeInfo.dismissable", dismissable);
        bundle.putBoolean("AccessibilityNodeInfo.multiLine", multiLine);
        bundle.putInt("AccessibilityNodeInfo.inputType", inputType);
        bundle.putInt("AccessibilityNodeInfo.liveRegion", liveRegion);
    }

    @CalledByNative
    protected void setAccessibilityEventCollectionInfo(AccessibilityEvent event,
            int rowCount, int columnCount, boolean hierarchical) {
        // Backwards compatibility for KitKat AccessibilityNodeInfo fields.
        Bundle bundle = getOrCreateBundleForAccessibilityEvent(event);
        bundle.putInt("AccessibilityNodeInfo.CollectionInfo.rowCount", rowCount);
        bundle.putInt("AccessibilityNodeInfo.CollectionInfo.columnCount", columnCount);
        bundle.putBoolean("AccessibilityNodeInfo.CollectionInfo.hierarchical", hierarchical);
    }

    @CalledByNative
    protected void setAccessibilityEventCollectionItemInfo(AccessibilityEvent event,
            int rowIndex, int rowSpan, int columnIndex, int columnSpan, boolean heading) {
        // Backwards compatibility for KitKat AccessibilityNodeInfo fields.
        Bundle bundle = getOrCreateBundleForAccessibilityEvent(event);
        bundle.putInt("AccessibilityNodeInfo.CollectionItemInfo.rowIndex", rowIndex);
        bundle.putInt("AccessibilityNodeInfo.CollectionItemInfo.rowSpan", rowSpan);
        bundle.putInt("AccessibilityNodeInfo.CollectionItemInfo.columnIndex", columnIndex);
        bundle.putInt("AccessibilityNodeInfo.CollectionItemInfo.columnSpan", columnSpan);
        bundle.putBoolean("AccessibilityNodeInfo.CollectionItemInfo.heading", heading);
    }

    @CalledByNative
    protected void setAccessibilityEventRangeInfo(AccessibilityEvent event,
            int rangeType, float min, float max, float current) {
        // Backwards compatibility for KitKat AccessibilityNodeInfo fields.
        Bundle bundle = getOrCreateBundleForAccessibilityEvent(event);
        bundle.putInt("AccessibilityNodeInfo.RangeInfo.type", rangeType);
        bundle.putFloat("AccessibilityNodeInfo.RangeInfo.min", min);
        bundle.putFloat("AccessibilityNodeInfo.RangeInfo.max", max);
        bundle.putFloat("AccessibilityNodeInfo.RangeInfo.current", current);
    }

    private native int nativeGetRootId(long nativeBrowserAccessibilityManagerAndroid);
    private native boolean nativeIsNodeValid(long nativeBrowserAccessibilityManagerAndroid, int id);
    private native int nativeHitTest(long nativeBrowserAccessibilityManagerAndroid, int x, int y);
    private native boolean nativePopulateAccessibilityNodeInfo(
        long nativeBrowserAccessibilityManagerAndroid, AccessibilityNodeInfo info, int id);
    private native boolean nativePopulateAccessibilityEvent(
        long nativeBrowserAccessibilityManagerAndroid, AccessibilityEvent event, int id,
        int eventType);
    private native void nativeClick(long nativeBrowserAccessibilityManagerAndroid, int id);
    private native void nativeFocus(long nativeBrowserAccessibilityManagerAndroid, int id);
    private native void nativeBlur(long nativeBrowserAccessibilityManagerAndroid);
    private native void nativeScrollToMakeNodeVisible(
            long nativeBrowserAccessibilityManagerAndroid, int id);
}

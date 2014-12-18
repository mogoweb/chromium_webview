// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.accessibility;

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;

/**
 * Subclass of BrowserAccessibilityManager for KitKat that creates an
 * AccessibilityNodeProvider and delegates its implementation to this object.
 *
 * THIS CLASS IS NOT USED! A bug in the KitKat framework prevents us
 * from using these new APIs. We can re-enable this class after the next
 * Android system update. http://crbug.com/348088/
 */
@JNINamespace("content")
public class KitKatBrowserAccessibilityManager extends JellyBeanBrowserAccessibilityManager {
    KitKatBrowserAccessibilityManager(long nativeBrowserAccessibilityManagerAndroid,
            ContentViewCore contentViewCore) {
        super(nativeBrowserAccessibilityManagerAndroid, contentViewCore);
    }

    @Override
    protected void setAccessibilityNodeInfoKitKatAttributes(AccessibilityNodeInfo node,
            boolean canOpenPopup,
            boolean contentInvalid,
            boolean dismissable,
            boolean multiLine,
            int inputType,
            int liveRegion) {
        node.setCanOpenPopup(canOpenPopup);
        node.setContentInvalid(contentInvalid);
        node.setDismissable(contentInvalid);
        node.setMultiLine(multiLine);
        node.setInputType(inputType);
        node.setLiveRegion(liveRegion);
    }

    @Override
    protected void setAccessibilityNodeInfoCollectionInfo(AccessibilityNodeInfo node,
            int rowCount, int columnCount, boolean hierarchical) {
        node.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(
                rowCount, columnCount, hierarchical));
    }

    @Override
    protected void setAccessibilityNodeInfoCollectionItemInfo(AccessibilityNodeInfo node,
            int rowIndex, int rowSpan, int columnIndex, int columnSpan, boolean heading) {
        node.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(
                rowIndex, rowSpan, columnIndex, columnSpan, heading));
    }

    @Override
    protected void setAccessibilityNodeInfoRangeInfo(AccessibilityNodeInfo node,
            int rangeType, float min, float max, float current) {
        node.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                rangeType, min, max, current));
    }

    @Override
    protected void setAccessibilityEventKitKatAttributes(AccessibilityEvent event,
            boolean canOpenPopup,
            boolean contentInvalid,
            boolean dismissable,
            boolean multiLine,
            int inputType,
            int liveRegion) {
        // This is just a fallback for pre-KitKat systems.
        // Do nothing on KitKat and higher.
    }

    @Override
    protected void setAccessibilityEventCollectionInfo(AccessibilityEvent event,
            int rowCount, int columnCount, boolean hierarchical) {
        // This is just a fallback for pre-KitKat systems.
        // Do nothing on KitKat and higher.
    }

    @Override
    protected void setAccessibilityEventCollectionItemInfo(AccessibilityEvent event,
            int rowIndex, int rowSpan, int columnIndex, int columnSpan, boolean heading) {
        // This is just a fallback for pre-KitKat systems.
        // Do nothing on KitKat and higher.
    }

    @Override
    protected void setAccessibilityEventRangeInfo(AccessibilityEvent event,
            int rangeType, float min, float max, float current) {
        // This is just a fallback for pre-KitKat systems.
        // Do nothing on KitKat and higher.
    }
}

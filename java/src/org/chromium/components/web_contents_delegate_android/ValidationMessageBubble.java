// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.web_contents_delegate_android;

import android.graphics.Point;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CalledByNative;
import org.chromium.content.R;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.RenderCoordinates;

/**
 * This class is an implementation of validation message bubble UI.
 */
class ValidationMessageBubble {
    private PopupWindow mPopup;

    /**
     * Creates a popup window to show the specified messages, and show it on
     * the specified anchor rectangle.
     *
     * @param contentViewCore The ContentViewCore object to provide various
     *                        information.
     * @param anchorX Anchor position in the CSS unit.
     * @param anchorY Anchor position in the CSS unit.
     * @param anchorWidth Anchor size in the CSS unit.
     * @param anchorHeight Anchor size in the CSS unit.
     * @param mainText The main message. It will shown at the top of the popup
     *                 window, and its font size is larger.
     * @param subText The sub message. It will shown below the main message, and
     *                its font size is smaller.
     */
    @CalledByNative
    private static ValidationMessageBubble createAndShow(
            ContentViewCore contentViewCore, int anchorX, int anchorY,
            int anchorWidth, int anchorHeight, String mainText, String subText) {
        final RectF anchorPixInScreen = makePixRectInScreen(
                contentViewCore, anchorX, anchorY, anchorWidth, anchorHeight);
        return new ValidationMessageBubble(contentViewCore, anchorPixInScreen, mainText, subText);
    }

    private ValidationMessageBubble(
            ContentViewCore contentViewCore, RectF anchor, String mainText, String subText) {
        final ViewGroup root = (ViewGroup) View.inflate(contentViewCore.getContext(),
                R.layout.validation_message_bubble, null);
        mPopup = new PopupWindow(root);
        updateTextViews(root, mainText, subText);
        measure(contentViewCore.getRenderCoordinates());
        Point origin = adjustWindowPosition(
                contentViewCore, (int) (anchor.centerX() - getAnchorOffset()), (int) anchor.bottom);
        mPopup.showAtLocation(
                contentViewCore.getContainerView(), Gravity.NO_GRAVITY, origin.x, origin.y);
    }

    @CalledByNative
    private void close() {
        if (mPopup == null) return;
        mPopup.dismiss();
        mPopup = null;
    }

    /**
     * Moves the popup window on the specified anchor rectangle.
     *
     * @param contentViewCore The ContentViewCore object to provide various
     *                        information.
     * @param anchorX Anchor position in the CSS unit.
     * @param anchorY Anchor position in the CSS unit.
     * @param anchorWidth Anchor size in the CSS unit.
     * @param anchorHeight Anchor size in the CSS unit.
     */
    @CalledByNative
    private void setPositionRelativeToAnchor(ContentViewCore contentViewCore,
            int anchorX, int anchorY, int anchorWidth, int anchorHeight) {
        RectF anchor = makePixRectInScreen(
                contentViewCore, anchorX, anchorY, anchorWidth, anchorHeight);
        Point origin = adjustWindowPosition(
                contentViewCore, (int) (anchor.centerX() - getAnchorOffset()), (int) anchor.bottom);
        mPopup.update(origin.x, origin.y, mPopup.getWidth(), mPopup.getHeight());
    }

    private static RectF makePixRectInScreen(ContentViewCore contentViewCore,
            int anchorX, int anchorY, int anchorWidth, int anchorHeight) {
        final RenderCoordinates coordinates = contentViewCore.getRenderCoordinates();
        final float yOffset = getWebViewOffsetYPixInScreen(contentViewCore);
        return new RectF(
                coordinates.fromLocalCssToPix(anchorX),
                coordinates.fromLocalCssToPix(anchorY) + yOffset,
                coordinates.fromLocalCssToPix(anchorX + anchorWidth),
                coordinates.fromLocalCssToPix(anchorY + anchorHeight) + yOffset);
    }

    private static float getWebViewOffsetYPixInScreen(ContentViewCore contentViewCore) {
        int[] location = new int[2];
        contentViewCore.getContainerView().getLocationOnScreen(location);
        return location[1] + contentViewCore.getRenderCoordinates().getContentOffsetYPix();
    }

    private static void updateTextViews(ViewGroup root, String mainText, String subText) {
        ((TextView) root.findViewById(R.id.main_text)).setText(mainText);
        final TextView subTextView = (TextView) root.findViewById(R.id.sub_text);
        if (!TextUtils.isEmpty(subText)) {
            subTextView.setText(subText);
        } else {
            ((ViewGroup) subTextView.getParent()).removeView(subTextView);
        }
    }

    private void measure(RenderCoordinates coordinates) {
        mPopup.setWindowLayoutMode(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mPopup.getContentView().setLayoutParams(
                new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT));
        mPopup.getContentView().measure(
                View.MeasureSpec.makeMeasureSpec(coordinates.getLastFrameViewportWidthPixInt(),
                        View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(coordinates.getLastFrameViewportHeightPixInt(),
                        View.MeasureSpec.AT_MOST));
    }

    private float getAnchorOffset() {
        final View root = mPopup.getContentView();
        final int width = root.getMeasuredWidth();
        final int arrowWidth = root.findViewById(R.id.arrow_image).getMeasuredWidth();
        return ApiCompatibilityUtils.isLayoutRtl(root) ?
                (width * 3 / 4 - arrowWidth / 2) : (width / 4 + arrowWidth / 2);
    }

    /**
     * This adjusts the position if the popup protrudes the web view.
     */
    private Point adjustWindowPosition(ContentViewCore contentViewCore, int x, int y) {
        final RenderCoordinates coordinates = contentViewCore.getRenderCoordinates();
        final int viewWidth = coordinates.getLastFrameViewportWidthPixInt();
        final int viewBottom = (int) getWebViewOffsetYPixInScreen(contentViewCore) +
                coordinates.getLastFrameViewportHeightPixInt();
        final int width = mPopup.getContentView().getMeasuredWidth();
        final int height = mPopup.getContentView().getMeasuredHeight();
        if (x < 0) {
            x = 0;
        } else if (x + width > viewWidth) {
            x = viewWidth - width;
        }
        if (y + height > viewBottom) {
            y = viewBottom - height;
        }
        return new Point(x, y);
    }
}

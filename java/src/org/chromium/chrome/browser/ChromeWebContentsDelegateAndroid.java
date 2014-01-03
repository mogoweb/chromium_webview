// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.graphics.Rect;
import android.graphics.RectF;

import org.chromium.base.CalledByNative;
import org.chromium.components.web_contents_delegate_android.WebContentsDelegateAndroid;

/**
 * Chromium Android specific WebContentsDelegate.
 * This file is the Java version of the native class of the same name.
 * It should contain empty WebContentsDelegate methods to be implemented by the embedder.
 * These methods belong to the Chromium Android port but not to WebView.
 */
public class ChromeWebContentsDelegateAndroid extends WebContentsDelegateAndroid {

    @CalledByNative
    public void onFindResultAvailable(FindNotificationDetails result) {
    }

    @CalledByNative
    public void onFindMatchRectsAvailable(FindMatchRectsDetails result) {
    }

    @CalledByNative
    public boolean addNewContents(int nativeSourceWebContents, int nativeWebContents,
            int disposition, Rect initialPosition, boolean userGesture) {
        return false;
    }

    // Helper functions used to create types that are part of the public interface
    @CalledByNative
    private static Rect createRect(int x, int y, int right, int bottom) {
        return new Rect(x, y, right, bottom);
    }

    @CalledByNative
    private static RectF createRectF(float x, float y, float right, float bottom) {
        return new RectF(x, y, right, bottom);
    }

    @CalledByNative
    private static FindNotificationDetails createFindNotificationDetails(
            int numberOfMatches, Rect rendererSelectionRect,
            int activeMatchOrdinal, boolean finalUpdate) {
        return new FindNotificationDetails(numberOfMatches, rendererSelectionRect,
                activeMatchOrdinal, finalUpdate);
    }

    @CalledByNative
    private static FindMatchRectsDetails createFindMatchRectsDetails(
            int version, int numRects, RectF activeRect) {
        return new FindMatchRectsDetails(version, new RectF[numRects], activeRect);
    }

    @CalledByNative
    private static void setMatchRectByIndex(
            FindMatchRectsDetails findMatchRectsDetails, int index, RectF rect) {
        findMatchRectsDetails.rects[index] = rect;
    }
}

// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;
import org.chromium.content_public.browser.WebContents;

/**
 * This class receives callbacks that act as hooks for various a native web contents events related
 * to loading a url. A single web contents can have multiple WebContentObserverAndroids.
 */
@JNINamespace("content")
public abstract class WebContentsObserverAndroid {
    private long mNativeWebContentsObserverAndroid;

    // TODO(yfriedman): Switch everyone to use the WebContents constructor.
    public WebContentsObserverAndroid(ContentViewCore contentViewCore) {
        this(contentViewCore.getWebContents());
    }

    public WebContentsObserverAndroid(WebContents webContents) {
        ThreadUtils.assertOnUiThread();
        mNativeWebContentsObserverAndroid = nativeInit(webContents);
    }

    @CalledByNative
    public void renderProcessGone(boolean wasOomProtected) {
    }

    /**
     * Called when the a page starts loading.
     * @param url The validated url for the loading page.
     */
    @CalledByNative
    public void didStartLoading(String url) {
    }

    /**
     * Called when the a page finishes loading.
     * @param url The validated url for the page.
     */
    @CalledByNative
    public void didStopLoading(String url) {
    }

    /**
     * Called when an error occurs while loading a page and/or the page fails to load.
     * @param errorCode Error code for the occurring error.
     * @param description The description for the error.
     * @param failingUrl The url that was loading when the error occurred.
     */
    @CalledByNative
    public void didFailLoad(boolean isProvisionalLoad,
            boolean isMainFrame, int errorCode, String description, String failingUrl) {
    }

    /**
     * Called when the main frame of the page has committed.
     * @param url The validated url for the page.
     * @param baseUrl The validated base url for the page.
     * @param isNavigationToDifferentPage Whether the main frame navigated to a different page.
     * @param isFragmentNavigation Whether the main frame navigation did not cause changes to the
     *                             document (for example scrolling to a named anchor or PopState).
     */
    @CalledByNative
    public void didNavigateMainFrame(String url, String baseUrl,
            boolean isNavigationToDifferentPage, boolean isFragmentNavigation) {
    }

    /**
     * Called when the page had painted something non-empty.
     */
    @CalledByNative
    public void didFirstVisuallyNonEmptyPaint() {
    }

    /**
     * Similar to didNavigateMainFrame but also called on subframe navigations.
     * @param url The validated url for the page.
     * @param baseUrl The validated base url for the page.
     * @param isReload True if this navigation is a reload.
     */
    @CalledByNative
    public void didNavigateAnyFrame(String url, String baseUrl, boolean isReload) {
    }

    /**
     * Notifies that a load is started for a given frame.
     * @param frameId A positive, non-zero integer identifying the navigating frame.
     * @param parentFrameId The frame identifier of the frame containing the navigating frame,
     *                      or -1 if the frame is not contained in another frame.
     * @param isMainFrame Whether the load is happening for the main frame.
     * @param validatedUrl The validated URL that is being navigated to.
     * @param isErrorPage Whether this is navigating to an error page.
     * @param isIframeSrcdoc Whether this is navigating to about:srcdoc.
     */
    @CalledByNative
    public void didStartProvisionalLoadForFrame(
            long frameId,
            long parentFrameId,
            boolean isMainFrame,
            String validatedUrl,
            boolean isErrorPage,
            boolean isIframeSrcdoc) {
    }

    /**
     * Notifies that the provisional load was successfully committed. The RenderViewHost is now
     * the current RenderViewHost of the WebContents.
     * @param frameId A positive, non-zero integer identifying the navigating frame.
     * @param isMainFrame Whether the load is happening for the main frame.
     * @param url The committed URL being navigated to.
     * @param transitionType The transition type as defined in
     *                      {@link org.chromium.content.browser.PageTransitionTypes} for the load.
     */
    @CalledByNative
    public void didCommitProvisionalLoadForFrame(
            long frameId, boolean isMainFrame, String url, int transitionType) {

    }

    /**
     * Notifies that a load has finished for a given frame.
     * @param frameId A positive, non-zero integer identifying the navigating frame.
     * @param validatedUrl The validated URL that is being navigated to.
     * @param isMainFrame Whether the load is happening for the main frame.
     */
    @CalledByNative
    public void didFinishLoad(long frameId, String validatedUrl, boolean isMainFrame) {
    }

    /**
     * Notifies that the document has finished loading for the given frame.
     * @param frameId A positive, non-zero integer identifying the navigating frame.
     */
    @CalledByNative
    public void documentLoadedInFrame(long frameId) {
    }

    /**
     * Notifies that a navigation entry has been committed.
     */
    @CalledByNative
    public void navigationEntryCommitted() {
    }

    /**
     * Called when an interstitial page gets attached to the tab content.
     */
    @CalledByNative
    public void didAttachInterstitialPage() {
    }

    /**
     * Called when an interstitial page gets detached from the tab content.
     */
    @CalledByNative
    public void didDetachInterstitialPage() {
    }

    /**
     * Called when the theme color was changed.
     * @param color the new color in ARGB format
     */
    @CalledByNative
    public void didChangeThemeColor(int color) {
    }

    /**
     * Destroy the corresponding native object.
     */
    @CalledByNative
    public void detachFromWebContents() {
        if (mNativeWebContentsObserverAndroid != 0) {
            nativeDestroy(mNativeWebContentsObserverAndroid);
            mNativeWebContentsObserverAndroid = 0;
        }
    }

    private native long nativeInit(WebContents webContents);
    private native void nativeDestroy(long nativeWebContentsObserverAndroid);
}

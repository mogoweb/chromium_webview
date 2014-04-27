// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.app.Activity;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.Rect;
import android.net.http.SslCertificate;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.widget.OverScroller;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;
import org.chromium.components.navigation_interception.InterceptNavigationDelegate;
import org.chromium.components.navigation_interception.NavigationParams;
import org.chromium.content.browser.ContentSettings;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.content.browser.LoadUrlParams;
import org.chromium.content.browser.NavigationHistory;
import org.chromium.content.browser.PageTransitionTypes;
import org.chromium.content.common.CleanupReference;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.ui.base.ActivityWindowAndroid;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.gfx.DeviceDisplayInfo;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Exposes the native AwContents class, and together these classes wrap the ContentViewCore
 * and Browser components that are required to implement Android WebView API. This is the
 * primary entry point for the WebViewProvider implementation; it holds a 1:1 object
 * relationship with application WebView instances.
 * (We define this class independent of the hidden WebViewProvider interfaces, to allow
 * continuous build & test in the open source SDK-based tree).
 */
@JNINamespace("android_webview")
public class AwContents {
    private static final String TAG = "AwContents";

    private static final String WEB_ARCHIVE_EXTENSION = ".mht";

    // Used to avoid enabling zooming in / out if resulting zooming will
    // produce little visible difference.
    private static final float ZOOM_CONTROLS_EPSILON = 0.007f;

    /**
     * WebKit hit test related data strcutre. These are used to implement
     * getHitTestResult, requestFocusNodeHref, requestImageRef methods in WebView.
     * All values should be updated together. The native counterpart is
     * AwHitTestData.
     */
    public static class HitTestData {
        // Used in getHitTestResult.
        public int hitTestResultType;
        public String hitTestResultExtraData;

        // Used in requestFocusNodeHref (all three) and requestImageRef (only imgSrc).
        public String href;
        public String anchorText;
        public String imgSrc;
    }

    /**
     * Interface that consumers of {@link AwContents} must implement to allow the proper
     * dispatching of view methods through the containing view.
     */
    public interface InternalAccessDelegate extends ContentViewCore.InternalAccessDelegate {

        /**
         * @see View#overScrollBy(int, int, int, int, int, int, int, int, boolean);
         */
        void overScrollBy(int deltaX, int deltaY,
                int scrollX, int scrollY,
                int scrollRangeX, int scrollRangeY,
                int maxOverScrollX, int maxOverScrollY,
                boolean isTouchEvent);

        /**
         * @see View#scrollTo(int, int)
         */
        void super_scrollTo(int scrollX, int scrollY);

        /**
         * @see View#setMeasuredDimension(int, int)
         */
        void setMeasuredDimension(int measuredWidth, int measuredHeight);

        /**
         * @see View#getScrollBarStyle()
         */
        int super_getScrollBarStyle();

        /**
         * Requests a callback on the native DrawGL method (see getAwDrawGLFunction)
         * if called from within onDraw, |canvas| will be non-null and hardware accelerated.
         * otherwise, |canvas| will be null, and the container view itself will be hardware
         * accelerated.
         *
         * @return false indicates the GL draw request was not accepted, and the caller
         *         should fallback to the SW path.
         */
        boolean requestDrawGL(Canvas canvas);
    }

    private long mNativeAwContents;
    private final AwBrowserContext mBrowserContext;
    private final ViewGroup mContainerView;
    private ContentViewCore mContentViewCore;
    private final AwContentsClient mContentsClient;
    private final AwContentViewClient mContentViewClient;
    private final AwContentsClientBridge mContentsClientBridge;
    private final AwWebContentsDelegate mWebContentsDelegate;
    private final AwContentsIoThreadClient mIoThreadClient;
    private final InterceptNavigationDelegateImpl mInterceptNavigationDelegate;
    private final InternalAccessDelegate mInternalAccessAdapter;
    private final AwLayoutSizer mLayoutSizer;
    private final AwZoomControls mZoomControls;
    private final AwScrollOffsetManager mScrollOffsetManager;
    private OverScrollGlow mOverScrollGlow;
    // This can be accessed on any thread after construction. See AwContentsIoThreadClient.
    private final AwSettings mSettings;
    private final ScrollAccessibilityHelper mScrollAccessibilityHelper;

    private boolean mIsPaused;
    private boolean mIsViewVisible;
    private boolean mIsWindowVisible;
    private boolean mIsAttachedToWindow;
    private Bitmap mFavicon;
    private boolean mHasRequestedVisitedHistoryFromClient;
    // TODO(boliu): This should be in a global context, not per webview.
    private final double mDIPScale;

    // The base background color, i.e. not accounting for any CSS body from the current page.
    private int mBaseBackgroundColor = Color.WHITE;

    // Must call nativeUpdateLastHitTestData first to update this before use.
    private final HitTestData mPossiblyStaleHitTestData = new HitTestData();

    private final DefaultVideoPosterRequestHandler mDefaultVideoPosterRequestHandler;

    // Bound method for suppling Picture instances to the AwContentsClient. Will be null if the
    // picture listener API has not yet been enabled, or if it is using invalidation-only mode.
    private Callable<Picture> mPictureListenerContentProvider;

    private boolean mContainerViewFocused;
    private boolean mWindowFocused;

    // These come from the compositor and are updated synchronously (in contrast to the values in
    // ContentViewCore, which are updated at end of every frame).
    private float mPageScaleFactor = 1.0f;
    private float mMinPageScaleFactor = 1.0f;
    private float mMaxPageScaleFactor = 1.0f;
    private float mContentWidthDip;
    private float mContentHeightDip;

    private AwAutofillManagerDelegate mAwAutofillManagerDelegate;

    private ComponentCallbacks2 mComponentCallbacks;

    private AwPdfExporter mAwPdfExporter;

    // This flag indicates that ShouldOverrideUrlNavigation should be posted
    // through the resourcethrottle. This is only used for popup windows.
    private boolean mDeferredShouldOverrideUrlLoadingIsPendingForPopup;

    private static final class DestroyRunnable implements Runnable {
        private final long mNativeAwContents;
        private DestroyRunnable(long nativeAwContents) {
            mNativeAwContents = nativeAwContents;
        }
        @Override
        public void run() {
            // This is a no-op if not currently attached.
            nativeOnDetachedFromWindow(mNativeAwContents);
            nativeDestroy(mNativeAwContents);
        }
    }

    // Reference to the active mNativeAwContents pointer while it is active use
    // (ie before it is destroyed).
    private CleanupReference mCleanupReference;

    // A list of references to native pointers where the Java counterpart has been
    // destroyed, but are held here because they are waiting for onDetachFromWindow
    // to release GL resources. This is cleared inside onDetachFromWindow.
    private List<CleanupReference> mPendingDetachCleanupReferences;

    //--------------------------------------------------------------------------------------------
    private class IoThreadClientImpl implements AwContentsIoThreadClient {
        // All methods are called on the IO thread.

        @Override
        public int getCacheMode() {
            return mSettings.getCacheMode();
        }

        @Override
        public InterceptedRequestData shouldInterceptRequest(final String url,
                boolean isMainFrame) {
            InterceptedRequestData interceptedRequestData;
            // Return the response directly if the url is default video poster url.
            interceptedRequestData = mDefaultVideoPosterRequestHandler.shouldInterceptRequest(url);
            if (interceptedRequestData != null) return interceptedRequestData;

            interceptedRequestData = mContentsClient.shouldInterceptRequest(url);

            if (interceptedRequestData == null) {
                mContentsClient.getCallbackHelper().postOnLoadResource(url);
            }

            if (isMainFrame && interceptedRequestData != null &&
                    interceptedRequestData.getData() == null) {
                // In this case the intercepted URLRequest job will simulate an empty response
                // which doesn't trigger the onReceivedError callback. For WebViewClassic
                // compatibility we synthesize that callback. http://crbug.com/180950
                mContentsClient.getCallbackHelper().postOnReceivedError(
                        ErrorCodeConversionHelper.ERROR_UNKNOWN,
                        null /* filled in by the glue layer */, url);
            }
            return interceptedRequestData;
        }

        @Override
        public boolean shouldBlockContentUrls() {
            return !mSettings.getAllowContentAccess();
        }

        @Override
        public boolean shouldBlockFileUrls() {
            return !mSettings.getAllowFileAccess();
        }

        @Override
        public boolean shouldBlockNetworkLoads() {
            return mSettings.getBlockNetworkLoads();
        }

        @Override
        public void onDownloadStart(String url,
                                    String userAgent,
                                    String contentDisposition,
                                    String mimeType,
                                    long contentLength) {
            mContentsClient.getCallbackHelper().postOnDownloadStart(url, userAgent,
                    contentDisposition, mimeType, contentLength);
        }

        @Override
        public void newLoginRequest(String realm, String account, String args) {
            mContentsClient.getCallbackHelper().postOnReceivedLoginRequest(realm, account, args);
        }
    }

    //--------------------------------------------------------------------------------------------
    // When the navigation is for a newly created WebView (i.e. a popup), intercept the navigation
    // here for implementing shouldOverrideUrlLoading. This is to send the shouldOverrideUrlLoading
    // callback to the correct WebViewClient that is associated with the WebView.
    // Otherwise, use this delegate only to post onPageStarted messages.
    //
    // We are not using WebContentsObserver.didStartLoading because of stale URLs, out of order
    // onPageStarted's and double onPageStarted's.
    //
    private class InterceptNavigationDelegateImpl implements InterceptNavigationDelegate {
        @Override
        public boolean shouldIgnoreNavigation(NavigationParams navigationParams) {
            final String url = navigationParams.url;
            boolean ignoreNavigation = false;
            if (mDeferredShouldOverrideUrlLoadingIsPendingForPopup) {
                mDeferredShouldOverrideUrlLoadingIsPendingForPopup = false;
                // If this is used for all navigations in future, cases for application initiated
                // load, redirect and backforward should also be filtered out.
                if (!navigationParams.isPost) {
                    ignoreNavigation = mContentsClient.shouldOverrideUrlLoading(url);
                }
            }
            // The shouldOverrideUrlLoading call might have resulted in posting messages to the
            // UI thread. Using sendMessage here (instead of calling onPageStarted directly)
            // will allow those to run in order.
            if (!ignoreNavigation) {
                mContentsClient.getCallbackHelper().postOnPageStarted(url);
            }
            return ignoreNavigation;
        }
    }

    //--------------------------------------------------------------------------------------------
    private class AwLayoutSizerDelegate implements AwLayoutSizer.Delegate {
        @Override
        public void requestLayout() {
            mContainerView.requestLayout();
        }

        @Override
        public void setMeasuredDimension(int measuredWidth, int measuredHeight) {
            mInternalAccessAdapter.setMeasuredDimension(measuredWidth, measuredHeight);
        }

        @Override
        public void setFixedLayoutSize(int widthDip, int heightDip) {
            if (mNativeAwContents == 0) return;
            nativeSetFixedLayoutSize(mNativeAwContents, widthDip, heightDip);
        }

        @Override
        public boolean isLayoutParamsHeightWrapContent() {
            return mContainerView.getLayoutParams() != null &&
                mContainerView.getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT;
        }
    }

    //--------------------------------------------------------------------------------------------
    private class AwScrollOffsetManagerDelegate implements AwScrollOffsetManager.Delegate {
        @Override
        public void overScrollContainerViewBy(int deltaX, int deltaY, int scrollX, int scrollY,
                int scrollRangeX, int scrollRangeY, boolean isTouchEvent) {
            mInternalAccessAdapter.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                    scrollRangeX, scrollRangeY, 0, 0, isTouchEvent);
        }

        @Override
        public void scrollContainerViewTo(int x, int y) {
            mInternalAccessAdapter.super_scrollTo(x, y);
        }

        @Override
        public void scrollNativeTo(int x, int y) {
            if (mNativeAwContents == 0) return;
            nativeScrollTo(mNativeAwContents, x, y);
        }

        @Override
        public int getContainerViewScrollX() {
            return mContainerView.getScrollX();
        }

        @Override
        public int getContainerViewScrollY() {
            return mContainerView.getScrollY();
        }

        @Override
        public void invalidate() {
            mContainerView.invalidate();
        }
    }

    //--------------------------------------------------------------------------------------------
    private class AwGestureStateListener extends GestureStateListener {
        @Override
        public void onPinchStarted() {
            // While it's possible to re-layout the view during a pinch gesture, the effect is very
            // janky (especially that the page scale update notification comes from the renderer
            // main thread, not from the impl thread, so it's usually out of sync with what's on
            // screen). It's also quite expensive to do a re-layout, so we simply postpone
            // re-layout for the duration of the gesture. This is compatible with what
            // WebViewClassic does.
            mLayoutSizer.freezeLayoutRequests();
        }

        @Override
        public void onPinchEnded() {
            mLayoutSizer.unfreezeLayoutRequests();
        }

        @Override
        public void onFlingCancelGesture() {
            mScrollOffsetManager.onFlingCancelGesture();
        }

        @Override
        public void onUnhandledFlingStartEvent(int velocityX, int velocityY) {
            mScrollOffsetManager.onUnhandledFlingStartEvent(velocityX, velocityY);
        }

        @Override
        public void onScrollUpdateGestureConsumed() {
            mScrollAccessibilityHelper.postViewScrolledAccessibilityEventCallback();
        }
    }

    //--------------------------------------------------------------------------------------------
    private class AwComponentCallbacks implements ComponentCallbacks2 {
        @Override
        public void onTrimMemory(int level) {
            if (mNativeAwContents == 0) return;
            nativeTrimMemory(mNativeAwContents, level);
        }

        @Override
        public void onLowMemory() {}

        @Override
        public void onConfigurationChanged(Configuration configuration) {}
    };

    //--------------------------------------------------------------------------------------------
    private class AwLayoutChangeListener implements View.OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                int oldLeft, int oldTop, int oldRight, int oldBottom) {
            assert v == mContainerView;
            mLayoutSizer.onLayoutChange();
        }
    }

    /**
     * @param browserContext the browsing context to associate this view contents with.
     * @param containerView the view-hierarchy item this object will be bound to.
     * @param internalAccessAdapter to access private methods on containerView.
     * @param contentsClient will receive API callbacks from this WebView Contents.
     * @param awSettings AwSettings instance used to configure the AwContents.
     *
     * This constructor uses the default view sizing policy.
     */
    public AwContents(AwBrowserContext browserContext, ViewGroup containerView,
            InternalAccessDelegate internalAccessAdapter, AwContentsClient contentsClient,
            AwSettings awSettings) {
        this(browserContext, containerView, internalAccessAdapter, contentsClient, awSettings,
                new AwLayoutSizer());
    }

    /**
     * @param layoutSizer the AwLayoutSizer instance implementing the sizing policy for the view.
     *
     * This version of the constructor is used in test code to inject test versions of the above
     * documented classes.
     */
    public AwContents(AwBrowserContext browserContext, ViewGroup containerView,
            InternalAccessDelegate internalAccessAdapter, AwContentsClient contentsClient,
            AwSettings settings, AwLayoutSizer layoutSizer) {
        mBrowserContext = browserContext;
        mContainerView = containerView;
        mInternalAccessAdapter = internalAccessAdapter;
        mContentsClient = contentsClient;
        mContentViewClient = new AwContentViewClient(contentsClient, settings);
        mLayoutSizer = layoutSizer;
        mSettings = settings;
        mDIPScale = DeviceDisplayInfo.create(mContainerView.getContext()).getDIPScale();
        mLayoutSizer.setDelegate(new AwLayoutSizerDelegate());
        mLayoutSizer.setDIPScale(mDIPScale);
        mWebContentsDelegate = new AwWebContentsDelegateAdapter(contentsClient, mContainerView);
        mContentsClientBridge = new AwContentsClientBridge(contentsClient);
        mZoomControls = new AwZoomControls(this);
        mIoThreadClient = new IoThreadClientImpl();
        mInterceptNavigationDelegate = new InterceptNavigationDelegateImpl();

        AwSettings.ZoomSupportChangeListener zoomListener =
                new AwSettings.ZoomSupportChangeListener() {
                    @Override
                    public void onGestureZoomSupportChanged(
                            boolean supportsDoubleTapZoom, boolean supportsMultiTouchZoom) {
                        mContentViewCore.updateDoubleTapSupport(supportsDoubleTapZoom);
                        mContentViewCore.updateMultiTouchZoomSupport(supportsMultiTouchZoom);
                    }

                };
        mSettings.setZoomListener(zoomListener);
        mDefaultVideoPosterRequestHandler = new DefaultVideoPosterRequestHandler(mContentsClient);
        mSettings.setDefaultVideoPosterURL(
                mDefaultVideoPosterRequestHandler.getDefaultVideoPosterURL());
        mSettings.setDIPScale(mDIPScale);
        mScrollOffsetManager = new AwScrollOffsetManager(new AwScrollOffsetManagerDelegate(),
                new OverScroller(mContainerView.getContext()));
        mScrollAccessibilityHelper = new ScrollAccessibilityHelper(mContainerView);

        setOverScrollMode(mContainerView.getOverScrollMode());
        setScrollBarStyle(mInternalAccessAdapter.super_getScrollBarStyle());
        mContainerView.addOnLayoutChangeListener(new AwLayoutChangeListener());

        setNewAwContents(nativeInit(mBrowserContext));

        onVisibilityChanged(mContainerView, mContainerView.getVisibility());
        onWindowVisibilityChanged(mContainerView.getWindowVisibility());
    }

    private static ContentViewCore createAndInitializeContentViewCore(ViewGroup containerView,
            InternalAccessDelegate internalDispatcher, int nativeWebContents,
            GestureStateListener gestureStateListener,
            ContentViewClient contentViewClient,
            ContentViewCore.ZoomControlsDelegate zoomControlsDelegate) {
        Context context = containerView.getContext();
        ContentViewCore contentViewCore = new ContentViewCore(context);
        contentViewCore.initialize(containerView, internalDispatcher, nativeWebContents,
                context instanceof Activity ?
                        new ActivityWindowAndroid((Activity) context) :
                        new WindowAndroid(context.getApplicationContext()));
        contentViewCore.addGestureStateListener(gestureStateListener);
        contentViewCore.setContentViewClient(contentViewClient);
        contentViewCore.setZoomControlsDelegate(zoomControlsDelegate);
        return contentViewCore;
    }

    /**
     * Common initialization routine for adopting a native AwContents instance into this
     * java instance.
     *
     * TAKE CARE! This method can get called multiple times per java instance. Code accordingly.
     * ^^^^^^^^^  See the native class declaration for more details on relative object lifetimes.
     */
    private void setNewAwContents(long newAwContentsPtr) {
        if (mNativeAwContents != 0) {
            destroy();
            mContentViewCore = null;
        }

        assert mNativeAwContents == 0 && mCleanupReference == null && mContentViewCore == null;

        mNativeAwContents = newAwContentsPtr;
        // TODO(joth): when the native and java counterparts of AwBrowserContext are hooked up to
        // each other, we should update |mBrowserContext| according to the newly received native
        // WebContent's browser context.

        // The native side object has been bound to this java instance, so now is the time to
        // bind all the native->java relationships.
        mCleanupReference = new CleanupReference(this, new DestroyRunnable(mNativeAwContents));

        int nativeWebContents = nativeGetWebContents(mNativeAwContents);
        mContentViewCore = createAndInitializeContentViewCore(
                mContainerView, mInternalAccessAdapter, nativeWebContents,
                new AwGestureStateListener(), mContentViewClient, mZoomControls);
        nativeSetJavaPeers(mNativeAwContents, this, mWebContentsDelegate, mContentsClientBridge,
                mIoThreadClient, mInterceptNavigationDelegate);
        mContentsClient.installWebContentsObserver(mContentViewCore);
        mSettings.setWebContents(nativeWebContents);
        nativeSetDipScale(mNativeAwContents, (float) mDIPScale);
        updateGlobalVisibleRect();

        // The only call to onShow. onHide should never be called.
        mContentViewCore.onShow();
    }

    /**
     * Called on the "source" AwContents that is opening the popup window to
     * provide the AwContents to host the pop up content.
     */
    public void supplyContentsForPopup(AwContents newContents) {
        int popupNativeAwContents = nativeReleasePopupAwContents(mNativeAwContents);
        if (popupNativeAwContents == 0) {
            Log.w(TAG, "Popup WebView bind failed: no pending content.");
            if (newContents != null) newContents.destroy();
            return;
        }
        if (newContents == null) {
            nativeDestroy(popupNativeAwContents);
            return;
        }

        newContents.receivePopupContents(popupNativeAwContents);
    }

    // Recap: supplyContentsForPopup() is called on the parent window's content, this method is
    // called on the popup window's content.
    private void receivePopupContents(int popupNativeAwContents) {
        mDeferredShouldOverrideUrlLoadingIsPendingForPopup = true;
        // Save existing view state.
        final boolean wasAttached = mIsAttachedToWindow;
        final boolean wasViewVisible = mIsViewVisible;
        final boolean wasWindowVisible = mIsWindowVisible;
        final boolean wasPaused = mIsPaused;
        final boolean wasFocused = mContainerViewFocused;
        final boolean wasWindowFocused = mWindowFocused;

        // Properly clean up existing mContentViewCore and mNativeAwContents.
        if (wasFocused) onFocusChanged(false, 0, null);
        if (wasWindowFocused) onWindowFocusChanged(false);
        if (wasViewVisible) setViewVisibilityInternal(false);
        if (wasWindowVisible) setWindowVisibilityInternal(false);
        if (!wasPaused) onPause();
        // Not calling onDetachedFromWindow here because native code requires GL context to release
        // GL resources. This case is properly handled when destroy is called while still attached
        // to window.

        setNewAwContents(popupNativeAwContents);

        // Finally refresh all view state for mContentViewCore and mNativeAwContents.
        if (!wasPaused) onResume();
        if (wasAttached) onAttachedToWindow();
        onSizeChanged(mContainerView.getWidth(), mContainerView.getHeight(), 0, 0);
        if (wasWindowVisible) setWindowVisibilityInternal(true);
        if (wasViewVisible) setViewVisibilityInternal(true);
        if (wasWindowFocused) onWindowFocusChanged(wasWindowFocused);
        if (wasFocused) onFocusChanged(true, 0, null);
    }

    /**
     * Deletes the native counterpart of this object. Normally happens immediately,
     * but maybe deferred until the appropriate time for GL resource cleanup. Either way
     * this is transparent to the caller: after this function returns the object is
     * effectively dead and methods are no-ops.
     */
    public void destroy() {
        if (mCleanupReference != null) {
            // We explicitly do not null out the mContentViewCore reference here
            // because ContentViewCore already has code to deal with the case
            // methods are called on it after it's been destroyed, and other
            // code relies on AwContents.mContentViewCore to be non-null.
            mContentViewCore.destroy();
            mNativeAwContents = 0;

            // We cannot destroy immediately if we are still attached to the window.
            // Instead if we make sure to null out the native pointer so there is no more native
            // calls, and delay the actual destroy until onDetachedFromWindow.
            if (mIsAttachedToWindow) {
                if (mPendingDetachCleanupReferences == null) {
                    mPendingDetachCleanupReferences = new ArrayList<CleanupReference>();
                }
                mPendingDetachCleanupReferences.add(mCleanupReference);
            } else {
                mCleanupReference.cleanupNow();
            }
            mCleanupReference = null;
        }

        assert !mContentViewCore.isAlive();
        assert mNativeAwContents == 0;
    }

    @VisibleForTesting
    public ContentViewCore getContentViewCore() {
        return mContentViewCore;
    }

    // Can be called from any thread.
    public AwSettings getSettings() {
        return mSettings;
    }

    public AwPdfExporter getPdfExporter() {
        // mNativeAwContents can be null, due to destroy().
        if (mNativeAwContents == 0) {
            return null;
        }
        if (mAwPdfExporter == null) {
            mAwPdfExporter = new AwPdfExporter(mContainerView);
            nativeCreatePdfExporter(mNativeAwContents, mAwPdfExporter);
        }
        return mAwPdfExporter;
    }

    public static void setAwDrawSWFunctionTable(int functionTablePointer) {
        nativeSetAwDrawSWFunctionTable(functionTablePointer);
    }

    public static void setAwDrawGLFunctionTable(int functionTablePointer) {
        nativeSetAwDrawGLFunctionTable(functionTablePointer);
    }

    public static int getAwDrawGLFunction() {
        return nativeGetAwDrawGLFunction();
    }

    public static void setShouldDownloadFavicons() {
        nativeSetShouldDownloadFavicons();
    }

    /**
     * Intended for test code.
     * @return the number of native instances of this class.
     */
    @VisibleForTesting
    public static int getNativeInstanceCount() {
        return nativeGetNativeInstanceCount();
    }

    public int getAwDrawGLViewContext() {
        // Only called during early construction, so client should not have had a chance to
        // call destroy yet.
        assert mNativeAwContents != 0;

        // Using the native pointer as the returned viewContext. This is matched by the
        // reinterpret_cast back to BrowserViewRenderer pointer in the native DrawGLFunction.
        return nativeGetAwDrawGLViewContext(mNativeAwContents);
    }

    // This is only to avoid heap allocations inside updateGLobalVisibleRect. It should treated
    // as a local variable in the function and not used anywhere else.
    private static final Rect sLocalGlobalVisibleRect = new Rect();

    @CalledByNative
    private void updateGlobalVisibleRect() {
        if (mNativeAwContents == 0) return;
        if (!mContainerView.getGlobalVisibleRect(sLocalGlobalVisibleRect)) {
            sLocalGlobalVisibleRect.setEmpty();
        }

        nativeSetGlobalVisibleRect(mNativeAwContents, sLocalGlobalVisibleRect.left,
                sLocalGlobalVisibleRect.top, sLocalGlobalVisibleRect.right,
                sLocalGlobalVisibleRect.bottom);
    }

    //--------------------------------------------------------------------------------------------
    //  WebView[Provider] method implementations (where not provided by ContentViewCore)
    //--------------------------------------------------------------------------------------------

    // Only valid within onDraw().
    private final Rect mClipBoundsTemporary = new Rect();

    public void onDraw(Canvas canvas) {
        if (mNativeAwContents == 0) {
            canvas.drawColor(getEffectiveBackgroundColor());
            return;
        }

        mScrollOffsetManager.syncScrollOffsetFromOnDraw();
        canvas.getClipBounds(mClipBoundsTemporary);

        if (!nativeOnDraw(mNativeAwContents, canvas, canvas.isHardwareAccelerated(),
                mContainerView.getScrollX(), mContainerView.getScrollY(),
                mClipBoundsTemporary.left, mClipBoundsTemporary.top,
                mClipBoundsTemporary.right, mClipBoundsTemporary.bottom)) {
            // Can happen during initialization when compositor is not set up. Or when clearView
            // is in effect. Just draw background color instead.
            canvas.drawColor(getEffectiveBackgroundColor());
        }

        if (mOverScrollGlow != null && mOverScrollGlow.drawEdgeGlows(canvas,
                    mScrollOffsetManager.computeMaximumHorizontalScrollOffset(),
                    mScrollOffsetManager.computeMaximumVerticalScrollOffset())) {
            mContainerView.invalidate();
        }
    }

    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mLayoutSizer.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public int getContentHeightCss() {
        return (int) Math.ceil(mContentHeightDip);
    }

    public int getContentWidthCss() {
        return (int) Math.ceil(mContentWidthDip);
    }

    public Picture capturePicture() {
        if (mNativeAwContents == 0) return null;
        return new AwPicture(nativeCapturePicture(mNativeAwContents,
                    mScrollOffsetManager.computeHorizontalScrollRange(),
                    mScrollOffsetManager.computeVerticalScrollRange()));
    }

    public void clearView() {
        if (mNativeAwContents == 0) return;
        nativeClearView(mNativeAwContents);
    }

    /**
     * Enable the onNewPicture callback.
     * @param enabled Flag to enable the callback.
     * @param invalidationOnly Flag to call back only on invalidation without providing a picture.
     */
    public void enableOnNewPicture(boolean enabled, boolean invalidationOnly) {
        if (mNativeAwContents == 0) return;
        if (invalidationOnly) {
            mPictureListenerContentProvider = null;
        } else if (enabled && mPictureListenerContentProvider == null) {
            mPictureListenerContentProvider = new Callable<Picture>() {
                @Override
                public Picture call() {
                    return capturePicture();
                }
            };
        }
        nativeEnableOnNewPicture(mNativeAwContents, enabled);
    }

    public void findAllAsync(String searchString) {
        if (mNativeAwContents == 0) return;
        nativeFindAllAsync(mNativeAwContents, searchString);
    }

    public void findNext(boolean forward) {
        if (mNativeAwContents == 0) return;
        nativeFindNext(mNativeAwContents, forward);
    }

    public void clearMatches() {
        if (mNativeAwContents == 0) return;
        nativeClearMatches(mNativeAwContents);
    }

    /**
     * @return load progress of the WebContents.
     */
    public int getMostRecentProgress() {
        // WebContentsDelegateAndroid conveniently caches the most recent notified value for us.
        return mWebContentsDelegate.getMostRecentProgress();
    }

    public Bitmap getFavicon() {
        return mFavicon;
    }

    private void requestVisitedHistoryFromClient() {
        ValueCallback<String[]> callback = new ValueCallback<String[]>() {
            @Override
            public void onReceiveValue(final String[] value) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mNativeAwContents == 0) return;
                        nativeAddVisitedLinks(mNativeAwContents, value);
                    }
                });
            }
        };
        mContentsClient.getVisitedHistory(callback);
    }

    /**
     * Load url without fixing up the url string. Consumers of ContentView are responsible for
     * ensuring the URL passed in is properly formatted (i.e. the scheme has been added if left
     * off during user input).
     *
     * @param params Parameters for this load.
     */
    public void loadUrl(LoadUrlParams params) {
        if (params.getLoadUrlType() == LoadUrlParams.LOAD_TYPE_DATA &&
                !params.isBaseUrlDataScheme()) {
            // This allows data URLs with a non-data base URL access to file:///android_asset/ and
            // file:///android_res/ URLs. If AwSettings.getAllowFileAccess permits, it will also
            // allow access to file:// URLs (subject to OS level permission checks).
            params.setCanLoadLocalResources(true);
        }

        // If we are reloading the same url, then set transition type as reload.
        if (params.getUrl() != null &&
                params.getUrl().equals(mContentViewCore.getUrl()) &&
                params.getTransitionType() == PageTransitionTypes.PAGE_TRANSITION_LINK) {
                params.setTransitionType(PageTransitionTypes.PAGE_TRANSITION_RELOAD);
        }
        params.setTransitionType(
                params.getTransitionType() | PageTransitionTypes.PAGE_TRANSITION_FROM_API);

        // For WebView, always use the user agent override, which is set
        // every time the user agent in AwSettings is modified.
        params.setOverrideUserAgent(LoadUrlParams.UA_OVERRIDE_TRUE);

        // We don't pass extra headers to the content layer, as WebViewClassic
        // was adding them in a very narrow set of conditions. See http://crbug.com/306873
        if (mNativeAwContents != 0) {
            nativeSetExtraHeadersForUrl(
                    mNativeAwContents, params.getUrl(), params.getExtraHttpRequestHeadersString());
        }
        params.setExtraHeaders(new HashMap<String, String>());

        mContentViewCore.loadUrl(params);

        // The behavior of WebViewClassic uses the populateVisitedLinks callback in WebKit.
        // Chromium does not use this use code path and the best emulation of this behavior to call
        // request visited links once on the first URL load of the WebView.
        if (!mHasRequestedVisitedHistoryFromClient) {
            mHasRequestedVisitedHistoryFromClient = true;
            requestVisitedHistoryFromClient();
        }

        if (params.getLoadUrlType() == LoadUrlParams.LOAD_TYPE_DATA &&
                params.getBaseUrl() != null) {
            // Data loads with a base url will be resolved in Blink, and not cause an onPageStarted
            // event to be sent. Sending the callback directly from here.
            mContentsClient.getCallbackHelper().postOnPageStarted(params.getBaseUrl());
        }
    }

    /**
     * Get the URL of the current page.
     *
     * @return The URL of the current page or null if it's empty.
     */
    public String getUrl() {
        String url =  mContentViewCore.getUrl();
        if (url == null || url.trim().isEmpty()) return null;
        return url;
    }

    public void requestFocus() {
        if (mNativeAwContents == 0) return;
        if (!mContainerView.isInTouchMode() && mSettings.shouldFocusFirstNode()) {
            nativeFocusFirstNode(mNativeAwContents);
        }
    }

    public void setBackgroundColor(int color) {
        mBaseBackgroundColor = color;
        if (mNativeAwContents != 0) nativeSetBackgroundColor(mNativeAwContents, color);
    }

    private int getEffectiveBackgroundColor() {
        // Do not ask the ContentViewCore for the background color, as it will always
        // report white prior to initial navigation or post destruction,  whereas we want
        // to use the client supplied base value in those cases.
        if (mNativeAwContents == 0 || !mContentsClient.isCachedRendererBackgroundColorValid()) {
            return mBaseBackgroundColor;
        }
        return mContentsClient.getCachedRendererBackgroundColor();
    }

    public boolean isMultiTouchZoomSupported() {
        return mSettings.supportsMultiTouchZoom();
    }

    public View getZoomControlsForTest() {
        return mZoomControls.getZoomControlsViewForTest();
    }

    /**
     * @see ContentViewCore#getContentSettings()
     */
    public ContentSettings getContentSettings() {
        return mContentViewCore.getContentSettings();
    }

    /**
     * @see View#setOverScrollMode(int)
     */
    public void setOverScrollMode(int mode) {
        if (mode != View.OVER_SCROLL_NEVER) {
            mOverScrollGlow = new OverScrollGlow(mContainerView);
        } else {
            mOverScrollGlow = null;
        }
    }

    // TODO(mkosiba): In WebViewClassic these appear in some of the scroll extent calculation
    // methods but toggling them has no visiual effect on the content (in other words the scrolling
    // code behaves as if the scrollbar-related padding is in place but the onDraw code doesn't
    // take that into consideration).
    // http://crbug.com/269032
    private boolean mOverlayHorizontalScrollbar = true;
    private boolean mOverlayVerticalScrollbar = false;

    /**
     * @see View#setScrollBarStyle(int)
     */
    public void setScrollBarStyle(int style) {
        if (style == View.SCROLLBARS_INSIDE_OVERLAY
                || style == View.SCROLLBARS_OUTSIDE_OVERLAY) {
            mOverlayHorizontalScrollbar = mOverlayVerticalScrollbar = true;
        } else {
            mOverlayHorizontalScrollbar = mOverlayVerticalScrollbar = false;
        }
    }

    /**
     * @see View#setHorizontalScrollbarOverlay(boolean)
     */
    public void setHorizontalScrollbarOverlay(boolean overlay) {
        mOverlayHorizontalScrollbar = overlay;
    }

    /**
     * @see View#setVerticalScrollbarOverlay(boolean)
     */
    public void setVerticalScrollbarOverlay(boolean overlay) {
        mOverlayVerticalScrollbar = overlay;
    }

    /**
     * @see View#overlayHorizontalScrollbar()
     */
    public boolean overlayHorizontalScrollbar() {
        return mOverlayHorizontalScrollbar;
    }

    /**
     * @see View#overlayVerticalScrollbar()
     */
    public boolean overlayVerticalScrollbar() {
        return mOverlayVerticalScrollbar;
    }

    /**
     * Called by the embedder when the scroll offset of the containing view has changed.
     * @see View#onScrollChanged(int,int)
     */
    public void onContainerViewScrollChanged(int l, int t, int oldl, int oldt) {
        // A side-effect of View.onScrollChanged is that the scroll accessibility event being sent
        // by the base class implementation. This is completely hidden from the base classes and
        // cannot be prevented, which is why we need the code below.
        mScrollAccessibilityHelper.removePostedViewScrolledAccessibilityEventCallback();
        mScrollOffsetManager.onContainerViewScrollChanged(l, t);
    }

    /**
     * Called by the embedder when the containing view is to be scrolled or overscrolled.
     * @see View#onOverScrolled(int,int,int,int)
     */
    public void onContainerViewOverScrolled(int scrollX, int scrollY, boolean clampedX,
            boolean clampedY) {
        int oldX = mContainerView.getScrollX();
        int oldY = mContainerView.getScrollY();

        mScrollOffsetManager.onContainerViewOverScrolled(scrollX, scrollY, clampedX, clampedY);

        if (mOverScrollGlow != null) {
            mOverScrollGlow.pullGlow(mContainerView.getScrollX(), mContainerView.getScrollY(),
                    oldX, oldY,
                    mScrollOffsetManager.computeMaximumHorizontalScrollOffset(),
                    mScrollOffsetManager.computeMaximumVerticalScrollOffset());
        }
    }

    /**
     * @see android.webkit.WebView#requestChildRectangleOnScreen(View, Rect, boolean)
     */
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        return mScrollOffsetManager.requestChildRectangleOnScreen(
                child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY(),
                rect, immediate);
    }

    /**
     * @see View.computeScroll()
     */
    public void computeScroll() {
        mScrollOffsetManager.computeScrollAndAbsorbGlow(mOverScrollGlow);
    }

    /**
     * @see View#computeHorizontalScrollRange()
     */
    public int computeHorizontalScrollRange() {
        return mScrollOffsetManager.computeHorizontalScrollRange();
    }

    /**
     * @see View#computeHorizontalScrollOffset()
     */
    public int computeHorizontalScrollOffset() {
        return mScrollOffsetManager.computeHorizontalScrollOffset();
    }

    /**
     * @see View#computeVerticalScrollRange()
     */
    public int computeVerticalScrollRange() {
        return mScrollOffsetManager.computeVerticalScrollRange();
    }

    /**
     * @see View#computeVerticalScrollOffset()
     */
    public int computeVerticalScrollOffset() {
        return mScrollOffsetManager.computeVerticalScrollOffset();
    }

    /**
     * @see View#computeVerticalScrollExtent()
     */
    public int computeVerticalScrollExtent() {
        return mScrollOffsetManager.computeVerticalScrollExtent();
    }

    /**
     * @see android.webkit.WebView#stopLoading()
     */
    public void stopLoading() {
        mContentViewCore.stopLoading();
    }

    /**
     * @see android.webkit.WebView#reload()
     */
    public void reload() {
        mContentViewCore.reload(true);
    }

    /**
     * @see android.webkit.WebView#canGoBack()
     */
    public boolean canGoBack() {
        return mContentViewCore.canGoBack();
    }

    /**
     * @see android.webkit.WebView#goBack()
     */
    public void goBack() {
        mContentViewCore.goBack();
    }

    /**
     * @see android.webkit.WebView#canGoForward()
     */
    public boolean canGoForward() {
        return mContentViewCore.canGoForward();
    }

    /**
     * @see android.webkit.WebView#goForward()
     */
    public void goForward() {
        mContentViewCore.goForward();
    }

    /**
     * @see android.webkit.WebView#canGoBackOrForward(int)
     */
    public boolean canGoBackOrForward(int steps) {
        return mContentViewCore.canGoToOffset(steps);
    }

    /**
     * @see android.webkit.WebView#goBackOrForward(int)
     */
    public void goBackOrForward(int steps) {
        mContentViewCore.goToOffset(steps);
    }

    /**
     * @see android.webkit.WebView#pauseTimers()
     */
    public void pauseTimers() {
        ContentViewStatics.setWebKitSharedTimersSuspended(true);
    }

    /**
     * @see android.webkit.WebView#resumeTimers()
     */
    public void resumeTimers() {
        ContentViewStatics.setWebKitSharedTimersSuspended(false);
    }

    /**
     * @see android.webkit.WebView#onPause()
     */
    public void onPause() {
        if (mIsPaused || mNativeAwContents == 0) return;
        mIsPaused = true;
        nativeSetIsPaused(mNativeAwContents, mIsPaused);
    }

    /**
     * @see android.webkit.WebView#onResume()
     */
    public void onResume() {
        if (!mIsPaused || mNativeAwContents == 0) return;
        mIsPaused = false;
        nativeSetIsPaused(mNativeAwContents, mIsPaused);
    }

    /**
     * @see android.webkit.WebView#isPaused()
     */
    public boolean isPaused() {
        return mIsPaused;
    }

    /**
     * @see android.webkit.WebView#onCreateInputConnection(EditorInfo)
     */
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return mContentViewCore.onCreateInputConnection(outAttrs);
    }

    /**
     * @see android.webkit.WebView#onKeyUp(int, KeyEvent)
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mContentViewCore.onKeyUp(keyCode, event);
    }

    private boolean isDpadEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
            }
        }
        return false;
    }

    /**
     * @see android.webkit.WebView#dispatchKeyEvent(KeyEvent)
     */
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isDpadEvent(event)) {
            mSettings.setSpatialNavigationEnabled(true);
        }
        return mContentViewCore.dispatchKeyEvent(event);
    }

    /**
     * Clears the resource cache. Note that the cache is per-application, so this will clear the
     * cache for all WebViews used.
     *
     * @param includeDiskFiles if false, only the RAM cache is cleared
     */
    public void clearCache(boolean includeDiskFiles) {
        if (mNativeAwContents == 0) return;
        nativeClearCache(mNativeAwContents, includeDiskFiles);
    }

    public void documentHasImages(Message message) {
        if (mNativeAwContents == 0) return;
        nativeDocumentHasImages(mNativeAwContents, message);
    }

    public void saveWebArchive(
            final String basename, boolean autoname, final ValueCallback<String> callback) {
        if (!autoname) {
            saveWebArchiveInternal(basename, callback);
            return;
        }
        // If auto-generating the file name, handle the name generation on a background thread
        // as it will require I/O access for checking whether previous files existed.
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                return generateArchiveAutoNamePath(getOriginalUrl(), basename);
            }

            @Override
            protected void onPostExecute(String result) {
                saveWebArchiveInternal(result, callback);
            }
        }.execute();
    }

    public String getOriginalUrl() {
        NavigationHistory history = mContentViewCore.getNavigationHistory();
        int currentIndex = history.getCurrentEntryIndex();
        if (currentIndex >= 0 && currentIndex < history.getEntryCount()) {
            return history.getEntryAtIndex(currentIndex).getOriginalUrl();
        }
        return null;
    }

    /**
     * @see ContentViewCore#getNavigationHistory()
     */
    public NavigationHistory getNavigationHistory() {
        return mContentViewCore.getNavigationHistory();
    }

    /**
     * @see android.webkit.WebView#getTitle()
     */
    public String getTitle() {
        return mContentViewCore.getTitle();
    }

    /**
     * @see android.webkit.WebView#clearHistory()
     */
    public void clearHistory() {
        mContentViewCore.clearHistory();
    }

    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        return mBrowserContext.getHttpAuthDatabase(mContentViewCore.getContext())
                .getHttpAuthUsernamePassword(host, realm);
    }

    public void setHttpAuthUsernamePassword(String host, String realm, String username,
            String password) {
        mBrowserContext.getHttpAuthDatabase(mContentViewCore.getContext())
                .setHttpAuthUsernamePassword(host, realm, username, password);
    }

    /**
     * @see android.webkit.WebView#getCertificate()
     */
    public SslCertificate getCertificate() {
        if (mNativeAwContents == 0) return null;
        return SslUtil.getCertificateFromDerBytes(nativeGetCertificate(mNativeAwContents));
    }

    /**
     * @see android.webkit.WebView#clearSslPreferences()
     */
    public void clearSslPreferences() {
        mContentViewCore.clearSslPreferences();
    }

    /**
     * Method to return all hit test values relevant to public WebView API.
     * Note that this expose more data than needed for WebView.getHitTestResult.
     * Unsafely returning reference to mutable internal object to avoid excessive
     * garbage allocation on repeated calls.
     */
    public HitTestData getLastHitTestResult() {
        if (mNativeAwContents == 0) return null;
        nativeUpdateLastHitTestData(mNativeAwContents);
        return mPossiblyStaleHitTestData;
    }

    /**
     * @see android.webkit.WebView#requestFocusNodeHref()
     */
    public void requestFocusNodeHref(Message msg) {
        if (msg == null || mNativeAwContents == 0) return;

        nativeUpdateLastHitTestData(mNativeAwContents);
        Bundle data = msg.getData();

        // In order to maintain compatibility with the old WebView's implementation,
        // the absolute (full) url is passed in the |url| field, not only the href attribute.
        // Note: HitTestData could be cleaned up at this point. See http://crbug.com/290992.
        data.putString("url", mPossiblyStaleHitTestData.href);
        data.putString("title", mPossiblyStaleHitTestData.anchorText);
        data.putString("src", mPossiblyStaleHitTestData.imgSrc);
        msg.setData(data);
        msg.sendToTarget();
    }

    /**
     * @see android.webkit.WebView#requestImageRef()
     */
    public void requestImageRef(Message msg) {
        if (msg == null || mNativeAwContents == 0) return;

        nativeUpdateLastHitTestData(mNativeAwContents);
        Bundle data = msg.getData();
        data.putString("url", mPossiblyStaleHitTestData.imgSrc);
        msg.setData(data);
        msg.sendToTarget();
    }

    @VisibleForTesting
    public float getPageScaleFactor() {
        return mPageScaleFactor;
    }

    /**
     * @see android.webkit.WebView#getScale()
     *
     * Please note that the scale returned is the page scale multiplied by
     * the screen density factor. See CTS WebViewTest.testSetInitialScale.
     */
    public float getScale() {
        return (float)(mPageScaleFactor * mDIPScale);
    }

    /**
     * @see android.webkit.WebView#flingScroll(int, int)
     */
    public void flingScroll(int velocityX, int velocityY) {
        mScrollOffsetManager.flingScroll(velocityX, velocityY);
    }

    /**
     * @see android.webkit.WebView#pageUp(boolean)
     */
    public boolean pageUp(boolean top) {
        return mScrollOffsetManager.pageUp(top);
    }

    /**
     * @see android.webkit.WebView#pageDown(boolean)
     */
    public boolean pageDown(boolean bottom) {
        return mScrollOffsetManager.pageDown(bottom);
    }

    /**
     * @see android.webkit.WebView#canZoomIn()
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean canZoomIn() {
        final float zoomInExtent = mMaxPageScaleFactor - mPageScaleFactor;
        return zoomInExtent > ZOOM_CONTROLS_EPSILON;
    }

    /**
     * @see android.webkit.WebView#canZoomOut()
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean canZoomOut() {
        final float zoomOutExtent = mPageScaleFactor - mMinPageScaleFactor;
        return zoomOutExtent > ZOOM_CONTROLS_EPSILON;
    }

    /**
     * @see android.webkit.WebView#zoomIn()
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean zoomIn() {
        if (!canZoomIn()) {
            return false;
        }
        return mContentViewCore.pinchByDelta(1.25f);
    }

    /**
     * @see android.webkit.WebView#zoomOut()
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean zoomOut() {
        if (!canZoomOut()) {
            return false;
        }
        return mContentViewCore.pinchByDelta(0.8f);
    }

    /**
     * @see android.webkit.WebView#invokeZoomPicker()
     */
    public void invokeZoomPicker() {
        mContentViewCore.invokeZoomPicker();
    }

    /**
     * @see ContentViewCore.evaluateJavaScript(String, ContentViewCore.JavaScriptCallback)
     */
    public void evaluateJavaScript(String script, final ValueCallback<String> callback) {
        ContentViewCore.JavaScriptCallback jsCallback = null;
        if (callback != null) {
            jsCallback = new ContentViewCore.JavaScriptCallback() {
                @Override
                public void handleJavaScriptResult(String jsonResult) {
                    callback.onReceiveValue(jsonResult);
                }
            };
        }

        mContentViewCore.evaluateJavaScript(script, jsCallback);
    }

    /**
     * @see ContentViewCore.evaluateJavaScriptEvenIfNotYetNavigated(String)
     */
    public void evaluateJavaScriptEvenIfNotYetNavigated(String script) {
        mContentViewCore.evaluateJavaScriptEvenIfNotYetNavigated(script);
    }

    //--------------------------------------------------------------------------------------------
    //  View and ViewGroup method implementations
    //--------------------------------------------------------------------------------------------

    /**
     * @see android.webkit.View#onTouchEvent()
     */
    public boolean onTouchEvent(MotionEvent event) {
        if (mNativeAwContents == 0) return false;

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mSettings.setSpatialNavigationEnabled(false);
        }

        mScrollOffsetManager.setProcessingTouchEvent(true);
        boolean rv = mContentViewCore.onTouchEvent(event);
        mScrollOffsetManager.setProcessingTouchEvent(false);

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int actionIndex = event.getActionIndex();

            // Note this will trigger IPC back to browser even if nothing is hit.
            nativeRequestNewHitTestDataAt(mNativeAwContents,
                                          (int) Math.round(event.getX(actionIndex) / mDIPScale),
                                          (int) Math.round(event.getY(actionIndex) / mDIPScale));
        }

        if (mOverScrollGlow != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
            mOverScrollGlow.releaseAll();
        }

        return rv;
    }

    /**
     * @see android.view.View#onHoverEvent()
     */
    public boolean onHoverEvent(MotionEvent event) {
        return mContentViewCore.onHoverEvent(event);
    }

    /**
     * @see android.view.View#onGenericMotionEvent()
     */
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mContentViewCore.onGenericMotionEvent(event);
    }

    /**
     * @see android.view.View#onConfigurationChanged()
     */
    public void onConfigurationChanged(Configuration newConfig) {
        mContentViewCore.onConfigurationChanged(newConfig);
    }

    /**
     * @see android.view.View#onAttachedToWindow()
     *
     * Note that this is also called from receivePopupContents.
     */
    public void onAttachedToWindow() {
        if (mNativeAwContents == 0) return;
        mIsAttachedToWindow = true;

        mContentViewCore.onAttachedToWindow();
        nativeOnAttachedToWindow(mNativeAwContents, mContainerView.getWidth(),
                mContainerView.getHeight());
        mSettings.setEnableSupportedHardwareAcceleratedFeatures(
            mContainerView.isHardwareAccelerated());

        if (mComponentCallbacks != null) return;
        mComponentCallbacks = new AwComponentCallbacks();
        mContainerView.getContext().registerComponentCallbacks(mComponentCallbacks);
    }

    /**
     * @see android.view.View#onDetachedFromWindow()
     */
    public void onDetachedFromWindow() {
        mIsAttachedToWindow = false;
        hideAutofillPopup();
        if (mNativeAwContents != 0) {
            nativeOnDetachedFromWindow(mNativeAwContents);
        }

        mContentViewCore.onDetachedFromWindow();

        mSettings.setEnableSupportedHardwareAcceleratedFeatures(false);

        if (mComponentCallbacks != null) {
            mContainerView.getContext().unregisterComponentCallbacks(mComponentCallbacks);
            mComponentCallbacks = null;
        }

        mScrollAccessibilityHelper.removePostedCallbacks();

        if (mPendingDetachCleanupReferences != null) {
            for (int i = 0; i < mPendingDetachCleanupReferences.size(); ++i) {
                mPendingDetachCleanupReferences.get(i).cleanupNow();
            }
            mPendingDetachCleanupReferences = null;
        }
    }

    /**
     * @see android.view.View#onWindowFocusChanged()
     */
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        mWindowFocused = hasWindowFocus;
        mContentViewCore.onWindowFocusChanged(hasWindowFocus);
    }

    /**
     * @see android.view.View#onFocusChanged()
     */
    public void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        mContainerViewFocused = focused;
        mContentViewCore.onFocusChanged(focused);
    }

    /**
     * @see android.view.View#onSizeChanged()
     */
    public void onSizeChanged(int w, int h, int ow, int oh) {
        if (mNativeAwContents == 0) return;
        mScrollOffsetManager.setContainerViewSize(w, h);
        // The AwLayoutSizer needs to go first so that if we're in fixedLayoutSize mode the update
        // to enter fixedLayoutSize mode is sent before the first resize update.
        mLayoutSizer.onSizeChanged(w, h, ow, oh);
        mContentViewCore.onPhysicalBackingSizeChanged(w, h);
        mContentViewCore.onSizeChanged(w, h, ow, oh);
        nativeOnSizeChanged(mNativeAwContents, w, h, ow, oh);
    }

    /**
     * @see android.view.View#onVisibilityChanged()
     */
    public void onVisibilityChanged(View changedView, int visibility) {
        boolean viewVisible = mContainerView.getVisibility() == View.VISIBLE;
        if (mIsViewVisible == viewVisible) return;
        setViewVisibilityInternal(viewVisible);
    }

    /**
     * @see android.view.View#onWindowVisibilityChanged()
     */
    public void onWindowVisibilityChanged(int visibility) {
        boolean windowVisible = visibility == View.VISIBLE;
        if (mIsWindowVisible == windowVisible) return;
        setWindowVisibilityInternal(windowVisible);
    }

    private void setViewVisibilityInternal(boolean visible) {
        mIsViewVisible = visible;
        if (mNativeAwContents == 0) return;
        nativeSetViewVisibility(mNativeAwContents, mIsViewVisible);
    }

    private void setWindowVisibilityInternal(boolean visible) {
        mIsWindowVisible = visible;
        if (mNativeAwContents == 0) return;
        nativeSetWindowVisibility(mNativeAwContents, mIsWindowVisible);
    }

    /**
     * Key for opaque state in bundle. Note this is only public for tests.
     */
    public static final String SAVE_RESTORE_STATE_KEY = "WEBVIEW_CHROMIUM_STATE";

    /**
     * Save the state of this AwContents into provided Bundle.
     * @return False if saving state failed.
     */
    public boolean saveState(Bundle outState) {
        if (mNativeAwContents == 0 || outState == null) return false;

        byte[] state = nativeGetOpaqueState(mNativeAwContents);
        if (state == null) return false;

        outState.putByteArray(SAVE_RESTORE_STATE_KEY, state);
        return true;
    }

    /**
     * Restore the state of this AwContents into provided Bundle.
     * @param inState Must be a bundle returned by saveState.
     * @return False if restoring state failed.
     */
    public boolean restoreState(Bundle inState) {
        if (mNativeAwContents == 0 || inState == null) return false;

        byte[] state = inState.getByteArray(SAVE_RESTORE_STATE_KEY);
        if (state == null) return false;

        boolean result = nativeRestoreFromOpaqueState(mNativeAwContents, state);

        // The onUpdateTitle callback normally happens when a page is loaded,
        // but is optimized out in the restoreState case because the title is
        // already restored. See WebContentsImpl::UpdateTitleForEntry. So we
        // call the callback explicitly here.
        if (result) mContentsClient.onReceivedTitle(mContentViewCore.getTitle());

        return result;
    }

    /**
     * @see ContentViewCore#addPossiblyUnsafeJavascriptInterface(Object, String, Class)
     */
    public void addPossiblyUnsafeJavascriptInterface(Object object, String name,
            Class<? extends Annotation> requiredAnnotation) {
        mContentViewCore.addPossiblyUnsafeJavascriptInterface(object, name, requiredAnnotation);
    }

    /**
     * @see android.webkit.WebView#removeJavascriptInterface(String)
     */
    public void removeJavascriptInterface(String interfaceName) {
        mContentViewCore.removeJavascriptInterface(interfaceName);
    }

    /**
     * If native accessibility (not script injection) is enabled, and if this is
     * running on JellyBean or later, returns an AccessibilityNodeProvider that
     * implements native accessibility for this view. Returns null otherwise.
     * @return The AccessibilityNodeProvider, if available, or null otherwise.
     */
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return mContentViewCore.getAccessibilityNodeProvider();
    }

    /**
     * @see android.webkit.WebView#onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo)
     */
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        mContentViewCore.onInitializeAccessibilityNodeInfo(info);
    }

    /**
     * @see android.webkit.WebView#onInitializeAccessibilityEvent(AccessibilityEvent)
     */
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        mContentViewCore.onInitializeAccessibilityEvent(event);
    }

    public boolean supportsAccessibilityAction(int action) {
        return mContentViewCore.supportsAccessibilityAction(action);
    }

    /**
     * @see android.webkit.WebView#performAccessibilityAction(int, Bundle)
     */
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return mContentViewCore.performAccessibilityAction(action, arguments);
    }

    /**
     * @see android.webkit.WebView#clearFormData()
     */
    public void hideAutofillPopup() {
        if (mAwAutofillManagerDelegate != null)
            mAwAutofillManagerDelegate.hideAutofillPopup();
    }

    public void setNetworkAvailable(boolean networkUp) {
        if (mNativeAwContents == 0) return;
        nativeSetJsOnlineProperty(mNativeAwContents, networkUp);
    }

    //--------------------------------------------------------------------------------------------
    //  Methods called from native via JNI
    //--------------------------------------------------------------------------------------------

    @CalledByNative
    private static void onDocumentHasImagesResponse(boolean result, Message message) {
        message.arg1 = result ? 1 : 0;
        message.sendToTarget();
    }

    @CalledByNative
    private void onReceivedTouchIconUrl(String url, boolean precomposed) {
        mContentsClient.onReceivedTouchIconUrl(url, precomposed);
    }

    @CalledByNative
    private void onReceivedIcon(Bitmap bitmap) {
        mContentsClient.onReceivedIcon(bitmap);
        mFavicon = bitmap;
    }

    /** Callback for generateMHTML. */
    @CalledByNative
    private static void generateMHTMLCallback(
            String path, long size, ValueCallback<String> callback) {
        if (callback == null) return;
        callback.onReceiveValue(size < 0 ? null : path);
    }

    @CalledByNative
    private void onReceivedHttpAuthRequest(AwHttpAuthHandler handler, String host, String realm) {
        mContentsClient.onReceivedHttpAuthRequest(handler, host, realm);
    }

    private class AwGeolocationCallback implements GeolocationPermissions.Callback {

        @Override
        public void invoke(final String origin, final boolean allow, final boolean retain) {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (retain) {
                        if (allow) {
                            mBrowserContext.getGeolocationPermissions().allow(origin);
                        } else {
                            mBrowserContext.getGeolocationPermissions().deny(origin);
                        }
                    }
                    if (mNativeAwContents == 0) return;
                    nativeInvokeGeolocationCallback(mNativeAwContents, allow, origin);
                }
            });
        }
    }

    @CalledByNative
    private void onGeolocationPermissionsShowPrompt(String origin) {
        if (mNativeAwContents == 0) return;
        AwGeolocationPermissions permissions = mBrowserContext.getGeolocationPermissions();
        // Reject if geoloaction is disabled, or the origin has a retained deny
        if (!mSettings.getGeolocationEnabled()) {
            nativeInvokeGeolocationCallback(mNativeAwContents, false, origin);
            return;
        }
        // Allow if the origin has a retained allow
        if (permissions.hasOrigin(origin)) {
            nativeInvokeGeolocationCallback(mNativeAwContents, permissions.isOriginAllowed(origin),
                    origin);
            return;
        }
        mContentsClient.onGeolocationPermissionsShowPrompt(
                origin, new AwGeolocationCallback());
    }

    @CalledByNative
    private void onGeolocationPermissionsHidePrompt() {
        mContentsClient.onGeolocationPermissionsHidePrompt();
    }

    @CalledByNative
    public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
            boolean isDoneCounting) {
        mContentsClient.onFindResultReceived(activeMatchOrdinal, numberOfMatches, isDoneCounting);
    }

    @CalledByNative
    public void onNewPicture() {
        // Don't call capturePicture() here but instead defer it until the posted task runs within
        // the callback helper, to avoid doubling back into the renderer compositor in the middle
        // of the notification it is sending up to here.
        mContentsClient.getCallbackHelper().postOnNewPicture(mPictureListenerContentProvider);
    }

    // Called as a result of nativeUpdateLastHitTestData.
    @CalledByNative
    private void updateHitTestData(
            int type, String extra, String href, String anchorText, String imgSrc) {
        mPossiblyStaleHitTestData.hitTestResultType = type;
        mPossiblyStaleHitTestData.hitTestResultExtraData = extra;
        mPossiblyStaleHitTestData.href = href;
        mPossiblyStaleHitTestData.anchorText = anchorText;
        mPossiblyStaleHitTestData.imgSrc = imgSrc;
    }

    @CalledByNative
    private boolean requestDrawGL(Canvas canvas) {
        return mInternalAccessAdapter.requestDrawGL(canvas);
    }

    private static final boolean SUPPORTS_ON_ANIMATION =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;

    @CalledByNative
    private void postInvalidateOnAnimation() {
        if (SUPPORTS_ON_ANIMATION) {
            mContainerView.postInvalidateOnAnimation();
        } else {
            mContainerView.postInvalidate();
        }
    }

    @CalledByNative
    private int[] getLocationOnScreen() {
        int[] result = new int[2];
        mContainerView.getLocationOnScreen(result);
        return result;
    }

    @CalledByNative
    private void onWebLayoutPageScaleFactorChanged(float webLayoutPageScaleFactor) {
        // This change notification comes from the renderer thread, not from the cc/ impl thread.
        mLayoutSizer.onPageScaleChanged(webLayoutPageScaleFactor);
    }

    @CalledByNative
    private void onWebLayoutContentsSizeChanged(int widthCss, int heightCss) {
        // This change notification comes from the renderer thread, not from the cc/ impl thread.
        mLayoutSizer.onContentSizeChanged(widthCss, heightCss);
    }

    @CalledByNative
    private void setMaxContainerViewScrollOffset(int maxX, int maxY) {
        mScrollOffsetManager.setMaxScrollOffset(maxX, maxY);
    }

    @CalledByNative
    private void scrollContainerViewTo(int x, int y) {
        mScrollOffsetManager.scrollContainerViewTo(x, y);
    }

    @CalledByNative
    private boolean isFlingActive() {
        return mScrollOffsetManager.isFlingActive();
    }

    @CalledByNative
    private void setContentsSize(int widthDip, int heightDip) {
        mContentWidthDip = widthDip;
        mContentHeightDip = heightDip;
    }

    @CalledByNative
    private void setPageScaleFactorAndLimits(
            float pageScaleFactor, float minPageScaleFactor, float maxPageScaleFactor) {
        if (mPageScaleFactor == pageScaleFactor &&
            mMinPageScaleFactor == minPageScaleFactor &&
            mMaxPageScaleFactor == maxPageScaleFactor)
            return;
        mMinPageScaleFactor = minPageScaleFactor;
        mMaxPageScaleFactor = maxPageScaleFactor;
        if (mPageScaleFactor != pageScaleFactor) {
          float oldPageScaleFactor = mPageScaleFactor;
          mPageScaleFactor = pageScaleFactor;
          mContentsClient.getCallbackHelper().postOnScaleChangedScaled(
              (float)(oldPageScaleFactor * mDIPScale),
              (float)(mPageScaleFactor * mDIPScale));
        }
    }

    @CalledByNative
    private void setAwAutofillManagerDelegate(AwAutofillManagerDelegate delegate) {
        mAwAutofillManagerDelegate = delegate;
        delegate.init(mContentViewCore);
    }

    @CalledByNative
    private void didOverscroll(int deltaX, int deltaY) {
        if (mOverScrollGlow != null) {
            mOverScrollGlow.setOverScrollDeltas(deltaX, deltaY);
        }

        mScrollOffsetManager.overScrollBy(deltaX, deltaY);

        if (mOverScrollGlow != null && mOverScrollGlow.isAnimating()) {
            mContainerView.invalidate();
        }
    }

    // -------------------------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------------------------

    private void saveWebArchiveInternal(String path, final ValueCallback<String> callback) {
        if (path == null || mNativeAwContents == 0) {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onReceiveValue(null);
                }
            });
        } else {
            nativeGenerateMHTML(mNativeAwContents, path, callback);
        }
    }

    /**
     * Try to generate a pathname for saving an MHTML archive. This roughly follows WebView's
     * autoname logic.
     */
    private static String generateArchiveAutoNamePath(String originalUrl, String baseName) {
        String name = null;
        if (originalUrl != null && !originalUrl.isEmpty()) {
            try {
                String path = new URL(originalUrl).getPath();
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash > 0) {
                    name = path.substring(lastSlash + 1);
                } else {
                    name = path;
                }
            } catch (MalformedURLException e) {
                // If it fails parsing the URL, we'll just rely on the default name below.
            }
        }

        if (TextUtils.isEmpty(name)) name = "index";

        String testName = baseName + name + WEB_ARCHIVE_EXTENSION;
        if (!new File(testName).exists()) return testName;

        for (int i = 1; i < 100; i++) {
            testName = baseName + name + "-" + i + WEB_ARCHIVE_EXTENSION;
            if (!new File(testName).exists()) return testName;
        }

        Log.e(TAG, "Unable to auto generate archive name for path: " + baseName);
        return null;
    }

    public void extractSmartClipData(int x, int y, int width, int height) {
        mContentViewCore.extractSmartClipData(x, y, width, height);
    }

    public void setSmartClipDataListener(ContentViewCore.SmartClipDataListener listener) {
        mContentViewCore.setSmartClipDataListener(listener);
    }

    //--------------------------------------------------------------------------------------------
    //  Native methods
    //--------------------------------------------------------------------------------------------

    private static native long nativeInit(AwBrowserContext browserContext);
    private static native void nativeDestroy(long nativeAwContents);
    private static native void nativeSetAwDrawSWFunctionTable(int functionTablePointer);
    private static native void nativeSetAwDrawGLFunctionTable(int functionTablePointer);
    private static native int nativeGetAwDrawGLFunction();
    private static native int nativeGetNativeInstanceCount();
    private static native void nativeSetShouldDownloadFavicons();
    private native void nativeSetJavaPeers(long nativeAwContents, AwContents awContents,
            AwWebContentsDelegate webViewWebContentsDelegate,
            AwContentsClientBridge contentsClientBridge,
            AwContentsIoThreadClient ioThreadClient,
            InterceptNavigationDelegate navigationInterceptionDelegate);
    private native int nativeGetWebContents(long nativeAwContents);

    private native void nativeDocumentHasImages(long nativeAwContents, Message message);
    private native void nativeGenerateMHTML(
            long nativeAwContents, String path, ValueCallback<String> callback);

    private native void nativeAddVisitedLinks(long nativeAwContents, String[] visitedLinks);
    private native boolean nativeOnDraw(long nativeAwContents, Canvas canvas,
            boolean isHardwareAccelerated, int scrollX, int scrollY,
            int clipLeft, int clipTop, int clipRight, int clipBottom);
    private native void nativeSetGlobalVisibleRect(long nativeAwContents, int visibleLeft,
            int visibleTop, int visibleRight, int visibleBottom);
    private native void nativeFindAllAsync(long nativeAwContents, String searchString);
    private native void nativeFindNext(long nativeAwContents, boolean forward);
    private native void nativeClearMatches(long nativeAwContents);
    private native void nativeClearCache(long nativeAwContents, boolean includeDiskFiles);
    private native byte[] nativeGetCertificate(long nativeAwContents);

    // Coordinates in desity independent pixels.
    private native void nativeRequestNewHitTestDataAt(long nativeAwContents, int x, int y);
    private native void nativeUpdateLastHitTestData(long nativeAwContents);

    private native void nativeOnSizeChanged(long nativeAwContents, int w, int h, int ow, int oh);
    private native void nativeScrollTo(long nativeAwContents, int x, int y);
    private native void nativeSetViewVisibility(long nativeAwContents, boolean visible);
    private native void nativeSetWindowVisibility(long nativeAwContents, boolean visible);
    private native void nativeSetIsPaused(long nativeAwContents, boolean paused);
    private native void nativeOnAttachedToWindow(long nativeAwContents, int w, int h);
    private static native void nativeOnDetachedFromWindow(long nativeAwContents);
    private native void nativeSetDipScale(long nativeAwContents, float dipScale);
    private native void nativeSetFixedLayoutSize(long nativeAwContents,
            int widthDip, int heightDip);

    // Returns null if save state fails.
    private native byte[] nativeGetOpaqueState(long nativeAwContents);

    // Returns false if restore state fails.
    private native boolean nativeRestoreFromOpaqueState(long nativeAwContents, byte[] state);

    private native int nativeReleasePopupAwContents(long nativeAwContents);
    private native void nativeFocusFirstNode(long nativeAwContents);
    private native void nativeSetBackgroundColor(long nativeAwContents, int color);

    private native int nativeGetAwDrawGLViewContext(long nativeAwContents);
    private native long nativeCapturePicture(long nativeAwContents, int width, int height);
    private native void nativeEnableOnNewPicture(long nativeAwContents, boolean enabled);
    private native void nativeClearView(long nativeAwContents);
    private native void nativeSetExtraHeadersForUrl(long nativeAwContents,
            String url, String extraHeaders);

    private native void nativeInvokeGeolocationCallback(
            long nativeAwContents, boolean value, String requestingFrame);

    private native void nativeSetJsOnlineProperty(long nativeAwContents, boolean networkUp);

    private native void nativeTrimMemory(long nativeAwContents, int level);

    private native void nativeCreatePdfExporter(long nativeAwContents, AwPdfExporter awPdfExporter);
}

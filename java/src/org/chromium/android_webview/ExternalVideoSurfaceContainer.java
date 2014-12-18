// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.RenderCoordinates;

import java.lang.ref.WeakReference;

/**
 * This is a container for external video surfaces.
 * The object is owned by the native peer and it is owned by WebContents.
 *
 * The expected behavior of the media player on the video hole punching is as follows.
 * 1) If it requests the surface, it will call requestExternalVideoSurface().
 *    When the resolution of the video is changed, it'll call requestExternalVideoSurface().
 * 2) Whenever the size or the position of the video element is changed, it'll notify through
 *    onExternalVideoSurfacePositionChanged().
 * 3) Whenever the page that contains the video element is scrolled or zoomed,
 *    onFrameInfoUpdated() will be called.
 * 4) Usually steps 1) ~ 3) are repeated during the playback.
 * 5) If the player no longer needs the surface any more, it'll call
 *    releaseExternalVideoSurface().
 *
 * Please contact ycheo@chromium.org or wonsik@chromium.org if you have any
 * questions or issues for this class.
 */
@JNINamespace("android_webview")
public class ExternalVideoSurfaceContainer implements SurfaceHolder.Callback {
    protected static final int INVALID_PLAYER_ID = -1;

    // Because WebView does hole-punching by itself, instead, the hole-punching logic
    // in SurfaceView can clear out some web elements like media control or subtitle.
    // So we need to disable its hole-punching logic.
    private static class NoPunchingSurfaceView extends SurfaceView {
        public NoPunchingSurfaceView(Context context) {
            super(context);
        }
        // SurfaceView.dispatchDraw implementation punches a hole in the view hierarchy.
        // Disable this by making this a no-op.
        @Override
        protected void dispatchDraw(Canvas canvas) {}
    }

    // There can be at most 1 external video surface for now.
    // If there are the multiple requests for the surface, then the second video will
    // kick the first one off.
    // To support the mulitple video surfaces seems impractical, because z-order between
    // the multiple SurfaceViews is non-deterministic.
    private static WeakReference<ExternalVideoSurfaceContainer> sActiveContainer =
            new WeakReference<ExternalVideoSurfaceContainer>(null);

    private final long mNativeExternalVideoSurfaceContainer;
    private final ContentViewCore mContentViewCore;
    private int mPlayerId = INVALID_PLAYER_ID;
    private SurfaceView mSurfaceView;

    // The absolute CSS coordinates of the video element.
    private float mLeft;
    private float mTop;
    private float mRight;
    private float mBottom;

    // The physical location/size of the external video surface in pixels.
    private int mX;
    private int mY;
    private int mWidth;
    private int mHeight;

    /**
     * Factory class to facilitate dependency injection.
     */
    public static class Factory {
        public ExternalVideoSurfaceContainer create(
                long nativeExternalVideoSurfaceContainer, ContentViewCore contentViewCore) {
            return new ExternalVideoSurfaceContainer(
                    nativeExternalVideoSurfaceContainer, contentViewCore);
        }
    }
    private static Factory sFactory = new Factory();

    @VisibleForTesting
    public static void setFactory(Factory factory) {
        sFactory = factory;
    }

    @CalledByNative
    private static ExternalVideoSurfaceContainer create(
            long nativeExternalVideoSurfaceContainer, ContentViewCore contentViewCore) {
        return sFactory.create(nativeExternalVideoSurfaceContainer, contentViewCore);
    }

    protected ExternalVideoSurfaceContainer(
            long nativeExternalVideoSurfaceContainer, ContentViewCore contentViewCore) {
        assert contentViewCore != null;
        mNativeExternalVideoSurfaceContainer = nativeExternalVideoSurfaceContainer;
        mContentViewCore = contentViewCore;
        initializeCurrentPositionOfSurfaceView();
    }

    /**
     * Called when a media player wants to request an external video surface.
     * @param playerId The ID of the media player.
     */
    @CalledByNative
    protected void requestExternalVideoSurface(int playerId) {
        if (mPlayerId == playerId) return;

        if (mPlayerId == INVALID_PLAYER_ID) {
            setActiveContainer(this);
        }

        mPlayerId = playerId;
        initializeCurrentPositionOfSurfaceView();

        createSurfaceView();
    }

    /**
     * Called when a media player wants to release an external video surface.
     * @param playerId The ID of the media player.
     */
    @CalledByNative
    protected void releaseExternalVideoSurface(int playerId) {
        if (mPlayerId != playerId) return;

        releaseIfActiveContainer(this);

        mPlayerId = INVALID_PLAYER_ID;
    }

    @CalledByNative
    protected void destroy() {
        releaseExternalVideoSurface(mPlayerId);
    }

    private void initializeCurrentPositionOfSurfaceView() {
        mX = Integer.MIN_VALUE;
        mY = Integer.MIN_VALUE;
        mWidth = 0;
        mHeight = 0;
    }

    private static void setActiveContainer(ExternalVideoSurfaceContainer container) {
        ExternalVideoSurfaceContainer activeContainer = sActiveContainer.get();
        if (activeContainer != null) {
            activeContainer.removeSurfaceView();
        }
        sActiveContainer = new WeakReference<ExternalVideoSurfaceContainer>(container);
    }

    private static void releaseIfActiveContainer(ExternalVideoSurfaceContainer container) {
        ExternalVideoSurfaceContainer activeContainer = sActiveContainer.get();
        if (activeContainer == container) {
            setActiveContainer(null);
        }
    }

    private void createSurfaceView() {
        mSurfaceView = new NoPunchingSurfaceView(mContentViewCore.getContext());
        mSurfaceView.getHolder().addCallback(this);
        // SurfaceHoder.surfaceCreated() will be called after the SurfaceView is attached to
        // the Window and becomes visible.
        mContentViewCore.getContainerView().addView(mSurfaceView);
    }

    private void removeSurfaceView() {
        // SurfaceHoder.surfaceDestroyed() will be called in ViewGroup.removeView()
        // as soon as the SurfaceView is detached from the Window.
        mContentViewCore.getContainerView().removeView(mSurfaceView);
        mSurfaceView = null;
    }

    /**
     * Called when the position of the video element which uses the external
     * video surface is changed.
     * @param playerId The ID of the media player.
     * @param left The absolute CSS X coordinate of the left side of the video element.
     * @param top The absolute CSS Y coordinate of the top side of the video element.
     * @param right The absolute CSS X coordinate of the right side of the video element.
     * @param bottom The absolute CSS Y coordinate of the bottom side of the video element.
     */
    @CalledByNative
    protected void onExternalVideoSurfacePositionChanged(
            int playerId, float left, float top, float right, float bottom) {
        if (mPlayerId != playerId) return;

        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;

        layOutSurfaceView();
    }

    /**
     * Called when the page that contains the video element is scrolled or zoomed.
     */
    @CalledByNative
    protected void onFrameInfoUpdated() {
        if (mPlayerId == INVALID_PLAYER_ID) return;

        layOutSurfaceView();
    }

    private void layOutSurfaceView() {
        RenderCoordinates renderCoordinates = mContentViewCore.getRenderCoordinates();
        RenderCoordinates.NormalizedPoint topLeft = renderCoordinates.createNormalizedPoint();
        RenderCoordinates.NormalizedPoint bottomRight = renderCoordinates.createNormalizedPoint();
        topLeft.setAbsoluteCss(mLeft, mTop);
        bottomRight.setAbsoluteCss(mRight, mBottom);
        float top = topLeft.getYPix();
        float left = topLeft.getXPix();
        float bottom = bottomRight.getYPix();
        float right = bottomRight.getXPix();

        int x = Math.round(left + renderCoordinates.getScrollXPix());
        int y = Math.round(top + renderCoordinates.getScrollYPix());
        int width = Math.round(right - left);
        int height = Math.round(bottom - top);
        if (mX == x && mY == y && mWidth == width && mHeight == height) return;
        mX = x;
        mY = y;
        mWidth = width;
        mHeight = height;

        mSurfaceView.setX(x);
        mSurfaceView.setY(y);
        ViewGroup.LayoutParams layoutParams = mSurfaceView.getLayoutParams();
        layoutParams.width = width;
        layoutParams.height = height;
        mSurfaceView.requestLayout();
    }

    // SurfaceHolder.Callback methods.
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    // surfaceCreated() callback can be called regardless of requestExternalVideoSurface,
    // if the activity comes back from the background and becomes visible.
    public void surfaceCreated(SurfaceHolder holder) {
        if (mPlayerId != INVALID_PLAYER_ID) {
            nativeSurfaceCreated(
                    mNativeExternalVideoSurfaceContainer, mPlayerId, holder.getSurface());
        }
    }

    // surfaceDestroyed() callback can be called regardless of releaseExternalVideoSurface,
    // if the activity moves to the backgound and becomes invisible.
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mPlayerId != INVALID_PLAYER_ID) {
            nativeSurfaceDestroyed(mNativeExternalVideoSurfaceContainer, mPlayerId);
        }
    }

    private native void nativeSurfaceCreated(
            long nativeExternalVideoSurfaceContainerImpl, int playerId, Surface surface);

    private native void nativeSurfaceDestroyed(
            long nativeExternalVideoSurfaceContainerImpl, int playerId);
}


// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.ui.base.WindowAndroid;

/***
 * This view is used by a ContentView to render its content.
 * Call {@link #setCurrentContentViewCore(ContentViewCore)} with the contentViewCore that should be
 * managing the view.
 * Note that only one ContentViewCore can be shown at a time.
 */
@JNINamespace("content")
public class ContentViewRenderView extends FrameLayout {
    // The native side of this object.
    private long mNativeContentViewRenderView;
    private SurfaceHolder.Callback mSurfaceCallback;

    private final SurfaceView mSurfaceView;
    protected ContentViewCore mContentViewCore;

    private ContentReadbackHandler mContentReadbackHandler;

    /**
     * Constructs a new ContentViewRenderView.
     * This should be called and the {@link ContentViewRenderView} should be added to the view
     * hierarchy before the first draw to avoid a black flash that is seen every time a
     * {@link SurfaceView} is added.
     * @param context The context used to create this.
     */
    public ContentViewRenderView(Context context) {
        super(context);

        mSurfaceView = createSurfaceView(getContext());
        mSurfaceView.setZOrderMediaOverlay(true);

        setSurfaceViewBackgroundColor(Color.WHITE);
        addView(mSurfaceView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        mSurfaceView.setVisibility(GONE);
    }

    /**
     * Initialization that requires native libraries should be done here.
     * Native code should add/remove the layers to be rendered through the ContentViewLayerRenderer.
     * @param rootWindow The {@link WindowAndroid} this render view should be linked to.
     */
    public void onNativeLibraryLoaded(WindowAndroid rootWindow) {
        assert !mSurfaceView.getHolder().getSurface().isValid() :
                "Surface created before native library loaded.";
        assert rootWindow != null;
        mNativeContentViewRenderView = nativeInit(rootWindow.getNativePointer());
        assert mNativeContentViewRenderView != 0;
        mSurfaceCallback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                assert mNativeContentViewRenderView != 0;
                nativeSurfaceChanged(mNativeContentViewRenderView,
                        format, width, height, holder.getSurface());
                if (mContentViewCore != null) {
                    mContentViewCore.onPhysicalBackingSizeChanged(
                            width, height);
                }
            }

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                assert mNativeContentViewRenderView != 0;
                nativeSurfaceCreated(mNativeContentViewRenderView);

                onReadyToRender();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                assert mNativeContentViewRenderView != 0;
                nativeSurfaceDestroyed(mNativeContentViewRenderView);
            }
        };
        mSurfaceView.getHolder().addCallback(mSurfaceCallback);
        mSurfaceView.setVisibility(VISIBLE);

        mContentReadbackHandler = new ContentReadbackHandler() {
            @Override
            protected boolean readyForReadback() {
                return mNativeContentViewRenderView != 0 && mContentViewCore != null;
            }
        };
        mContentReadbackHandler.initNativeContentReadbackHandler();
    }

    /**
     * @return The content readback handler.
     */
    public ContentReadbackHandler getContentReadbackHandler() {
        return mContentReadbackHandler;
    }

    /**
     * Sets the background color of the surface view.  This method is necessary because the
     * background color of ContentViewRenderView itself is covered by the background of
     * SurfaceView.
     * @param color The color of the background.
     */
    public void setSurfaceViewBackgroundColor(int color) {
        if (mSurfaceView != null) {
            mSurfaceView.setBackgroundColor(color);
        }
    }

    /**
     * Should be called when the ContentViewRenderView is not needed anymore so its associated
     * native resource can be freed.
     */
    public void destroy() {
        mContentReadbackHandler.destroy();
        mContentReadbackHandler = null;
        mSurfaceView.getHolder().removeCallback(mSurfaceCallback);
        nativeDestroy(mNativeContentViewRenderView);
        mNativeContentViewRenderView = 0;
    }

    public void setCurrentContentViewCore(ContentViewCore contentViewCore) {
        assert mNativeContentViewRenderView != 0;
        mContentViewCore = contentViewCore;

        if (mContentViewCore != null) {
            mContentViewCore.onPhysicalBackingSizeChanged(getWidth(), getHeight());
            nativeSetCurrentContentViewCore(mNativeContentViewRenderView,
                                            mContentViewCore.getNativeContentViewCore());
        } else {
            nativeSetCurrentContentViewCore(mNativeContentViewRenderView, 0);
        }
    }

    /**
     * This method should be subclassed to provide actions to be performed once the view is ready to
     * render.
     */
    protected void onReadyToRender() {
    }

    /**
     * This method could be subclassed optionally to provide a custom SurfaceView object to
     * this ContentViewRenderView.
     * @param context The context used to create the SurfaceView object.
     * @return The created SurfaceView object.
     */
    protected SurfaceView createSurfaceView(Context context) {
        return new SurfaceView(context);
    }

    /**
     * @return whether the surface view is initialized and ready to render.
     */
    public boolean isInitialized() {
        return mSurfaceView.getHolder().getSurface() != null;
    }

    /**
     * Enter or leave overlay video mode.
     * @param enabled Whether overlay mode is enabled.
     */
    public void setOverlayVideoMode(boolean enabled) {
        int format = enabled ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
        mSurfaceView.getHolder().setFormat(format);
        nativeSetOverlayVideoMode(mNativeContentViewRenderView, enabled);
    }

    /**
     * Set the native layer tree helper for this {@link ContentViewRenderView}.
     * @param layerTreeBuildHelperNativePtr Native pointer to the layer tree build helper.
     */
    public void setLayerTreeBuildHelper(long layerTreeBuildHelperNativePtr) {
        nativeSetLayerTreeBuildHelper(mNativeContentViewRenderView, layerTreeBuildHelperNativePtr);
    }

    @CalledByNative
    protected void onCompositorLayout() {
    }

    @CalledByNative
    private void onSwapBuffersCompleted() {
        if (mSurfaceView.getBackground() != null) {
            post(new Runnable() {
                @Override public void run() {
                    mSurfaceView.setBackgroundResource(0);
                }
            });
        }
    }

    private native long nativeInit(long rootWindowNativePointer);
    private native void nativeDestroy(long nativeContentViewRenderView);
    private native void nativeSetCurrentContentViewCore(long nativeContentViewRenderView,
            long nativeContentViewCore);
    private native void nativeSetLayerTreeBuildHelper(long nativeContentViewRenderView,
            long buildHelperNativePtr);
    private native void nativeSurfaceCreated(long nativeContentViewRenderView);
    private native void nativeSurfaceDestroyed(long nativeContentViewRenderView);
    private native void nativeSurfaceChanged(long nativeContentViewRenderView,
            int format, int width, int height, Surface surface);
    private native void nativeSetOverlayVideoMode(long nativeContentViewRenderView,
            boolean enabled);
}

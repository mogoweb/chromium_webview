// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebView;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

import java.util.concurrent.Callable;

/**
 * Manages settings state for a ContentView. A ContentSettings instance is obtained
 * from ContentView.getContentSettings(). If ContentView is used in the
 * ContentView.PERSONALITY_VIEW role, all settings are read / write. If ContentView
 * is in the ContentView.PERSONALITY_CHROME role, setting can only be read.
 */
@JNINamespace("content")
public class ContentSettings {

    private static final String TAG = "ContentSettings";

    // This class must be created on the UI thread. Afterwards, it can be
    // used from any thread. Internally, the class uses a message queue
    // to call native code on the UI thread only.

    // The native side of this object. Ownership is retained native-side by the WebContents
    // instance that backs the associated ContentViewCore.
    private int mNativeContentSettings = 0;

    private ContentViewCore mContentViewCore;

    // Custom handler that queues messages to call native code on the UI thread.
    private final EventHandler mEventHandler;

    // Protects access to settings fields.
    private final Object mContentSettingsLock = new Object();

    private boolean mSupportZoom = true;
    private boolean mBuiltInZoomControls = false;
    private boolean mDisplayZoomControls = true;

    // Class to handle messages to be processed on the UI thread.
    private class EventHandler {
        // Message id for updating multi-touch zoom state in the view
        private static final int UPDATE_MULTI_TOUCH = 2;
        // Actual UI thread handler
        private Handler mHandler;

        EventHandler() {
            if (mContentViewCore.isPersonalityView()) {
                mHandler = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case UPDATE_MULTI_TOUCH:
                                if (mContentViewCore.isAlive()) {
                                    mContentViewCore.updateMultiTouchZoomSupport();
                                }
                                break;
                        }
                    }
                };
            }
        }

        private void sendUpdateMultiTouchMessageLocked() {
            assert Thread.holdsLock(mContentSettingsLock);
            if (mNativeContentSettings == 0) return;
            mHandler.sendMessage(Message.obtain(null, UPDATE_MULTI_TOUCH));
        }
    }

    /**
     * Package constructor to prevent clients from creating a new settings
     * instance. Must be called on the UI thread.
     */
    ContentSettings(ContentViewCore contentViewCore, int nativeContentView) {
        ThreadUtils.assertOnUiThread();
        mContentViewCore = contentViewCore;
        mNativeContentSettings = nativeInit(nativeContentView);
        assert mNativeContentSettings != 0;

        mEventHandler = new EventHandler();
        if (!mContentViewCore.isPersonalityView()) {
            mBuiltInZoomControls = true;
            mDisplayZoomControls = false;
        }
    }

    /**
     * Notification from the native side that it is being destroyed.
     * @param nativeContentSettings the native instance that is going away.
     */
    @CalledByNative
    private void onNativeContentSettingsDestroyed(int nativeContentSettings) {
        assert mNativeContentSettings == nativeContentSettings;
        mNativeContentSettings = 0;
    }

    /**
     * Sets whether the WebView should support zooming using its on-screen zoom
     * controls and gestures. The particular zoom mechanisms that should be used
     * can be set with {@link #setBuiltInZoomControls}. This setting does not
     * affect zooming performed using the {@link WebView#zoomIn()} and
     * {@link WebView#zoomOut()} methods. The default is true.
     *
     * @param support whether the WebView should support zoom
     */
    public void setSupportZoom(boolean support) {
        synchronized (mContentSettingsLock) {
            mSupportZoom = support;
            mEventHandler.sendUpdateMultiTouchMessageLocked();
        }
    }

    /**
     * Gets whether the WebView supports zoom.
     *
     * @return true if the WebView supports zoom
     * @see #setSupportZoom
     */
    public boolean supportZoom() {
        return mSupportZoom;
    }

   /**
     * Sets whether the WebView should use its built-in zoom mechanisms. The
     * built-in zoom mechanisms comprise on-screen zoom controls, which are
     * displayed over the WebView's content, and the use of a pinch gesture to
     * control zooming. Whether or not these on-screen controls are displayed
     * can be set with {@link #setDisplayZoomControls}. The default is false,
     * due to compatibility reasons.
     * <p>
     * The built-in mechanisms are the only currently supported zoom
     * mechanisms, so it is recommended that this setting is always enabled.
     * In other words, there is no point of calling this method other than
     * with the 'true' parameter.
     *
     * @param enabled whether the WebView should use its built-in zoom mechanisms
     */
     public void setBuiltInZoomControls(boolean enabled) {
        synchronized (mContentSettingsLock) {
            mBuiltInZoomControls = enabled;
            mEventHandler.sendUpdateMultiTouchMessageLocked();
        }
    }

    /**
     * Gets whether the zoom mechanisms built into WebView are being used.
     *
     * @return true if the zoom mechanisms built into WebView are being used
     * @see #setBuiltInZoomControls
     */
    public boolean getBuiltInZoomControls() {
        return mBuiltInZoomControls;
    }

    /**
     * Sets whether the WebView should display on-screen zoom controls when
     * using the built-in zoom mechanisms. See {@link #setBuiltInZoomControls}.
     * The default is true.
     *
     * @param enabled whether the WebView should display on-screen zoom controls
     */
    public void setDisplayZoomControls(boolean enabled) {
        synchronized (mContentSettingsLock) {
            mDisplayZoomControls = enabled;
            mEventHandler.sendUpdateMultiTouchMessageLocked();
        }
    }

    /**
     * Gets whether the WebView displays on-screen zoom controls when using
     * the built-in zoom mechanisms.
     *
     * @return true if the WebView displays on-screen zoom controls when using
     *         the built-in zoom mechanisms
     * @see #setDisplayZoomControls
     */
    public boolean getDisplayZoomControls() {
        return mDisplayZoomControls;
    }

    boolean supportsMultiTouchZoom() {
        return mSupportZoom && mBuiltInZoomControls;
    }

    boolean shouldDisplayZoomControls() {
        return supportsMultiTouchZoom() && mDisplayZoomControls;
    }

    /**
     * Return true if JavaScript is enabled.
     *
     * @return True if JavaScript is enabled.
     */
    public boolean getJavaScriptEnabled() {
        return ThreadUtils.runOnUiThreadBlockingNoException(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    if (mNativeContentSettings != 0) {
                        return nativeGetJavaScriptEnabled(mNativeContentSettings);
                    } else {
                        return false;
                    }
                }
            });
    }

    /**
     * Sets the settings in this object to those from another
     * ContentSettings.
     * Required by WebView when we swap a in a new ContentViewCore
     * to an existing AwContents (i.e. to support displaying popup
     * windows in an already created WebView)
     */
    public void initFrom(ContentSettings settings) {
        setSupportZoom(settings.supportZoom());
        setBuiltInZoomControls(settings.getBuiltInZoomControls());
        setDisplayZoomControls(settings.getDisplayZoomControls());
    }

    // Initialize the ContentSettings native side.
    private native int nativeInit(int contentViewPtr);

    private native boolean nativeGetJavaScriptEnabled(int nativeContentSettings);
}

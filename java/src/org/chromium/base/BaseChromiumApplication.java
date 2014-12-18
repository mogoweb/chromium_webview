// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Window;

/**
 * Basic application functionality that should be shared among all browser applications.
 */
public class BaseChromiumApplication extends Application {
    /**
     * Interface to be implemented by listeners for window focus events.
     */
    public interface WindowFocusChangedListener {
        /**
         * Called when the window focus changes for {@code activity}.
         * @param activity The {@link Activity} that has a window focus changed event.
         * @param hasFocus Whether or not {@code activity} gained or lost focus.
         */
        public void onWindowFocusChanged(Activity activity, boolean hasFocus);
    }

    private ObserverList<WindowFocusChangedListener> mWindowFocusListeners =
            new ObserverList<WindowFocusChangedListener>();

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationStatus.initialize(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(final Activity activity, Bundle savedInstanceState) {
                Window.Callback callback = activity.getWindow().getCallback();
                activity.getWindow().setCallback(new WindowCallbackWrapper(callback) {
                    @Override
                    public void onWindowFocusChanged(boolean hasFocus) {
                        super.onWindowFocusChanged(hasFocus);

                        for (WindowFocusChangedListener listener : mWindowFocusListeners) {
                            listener.onWindowFocusChanged(activity, hasFocus);
                        }
                    }
                });
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                assert activity.getWindow().getCallback() instanceof WindowCallbackWrapper;
            }

            @Override
            public void onActivityPaused(Activity activity) {
                assert activity.getWindow().getCallback() instanceof WindowCallbackWrapper;
            }

            @Override
            public void onActivityResumed(Activity activity) {
                assert activity.getWindow().getCallback() instanceof WindowCallbackWrapper;
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                assert activity.getWindow().getCallback() instanceof WindowCallbackWrapper;
            }

            @Override
            public void onActivityStarted(Activity activity) {
                assert activity.getWindow().getCallback() instanceof WindowCallbackWrapper;
            }

            @Override
            public void onActivityStopped(Activity activity) {
                assert activity.getWindow().getCallback() instanceof WindowCallbackWrapper;
            }
        });
    }

    /**
     * Registers a listener to receive window focus updates on activities in this application.
     * @param listener Listener to receive window focus events.
     */
    public void registerWindowFocusChangedListener(WindowFocusChangedListener listener) {
        mWindowFocusListeners.addObserver(listener);
    }

    /**
     * Unregisters a listener from receiving window focus updates on activities in this application.
     * @param listener Listener that doesn't want to receive window focus events.
     */
    public void unregisterWindowFocusChangedListener(WindowFocusChangedListener listener) {
        mWindowFocusListeners.removeObserver(listener);
    }
}

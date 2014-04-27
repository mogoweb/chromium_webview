// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides information about the current activity's status, and a way
 * to register / unregister listeners for state changes.
 */
@JNINamespace("base::android")
public class ActivityStatus {

    // Constants matching activity states reported to StateListener.onStateChange
    // As an implementation detail, these are now defined in the auto-generated
    // ActivityState interface, to be shared with C++.
    public static final int CREATED = ActivityState.CREATED;
    public static final int STARTED = ActivityState.STARTED;
    public static final int RESUMED = ActivityState.RESUMED;
    public static final int PAUSED = ActivityState.PAUSED;
    public static final int STOPPED = ActivityState.STOPPED;
    public static final int DESTROYED = ActivityState.DESTROYED;

    // Last activity that was shown (or null if none or it was destroyed).
    private static Activity sActivity;

    private static final Map<Activity, Integer> sActivityStates =
            new HashMap<Activity, Integer>();

    private static final ObserverList<StateListener> sStateListeners =
            new ObserverList<StateListener>();

    /**
     * Interface to be implemented by listeners.
     */
    public interface StateListener {
        /**
         * Called when the activity's state changes.
         * @param newState New activity state.
         */
        public void onActivityStateChange(int newState);
    }

    private ActivityStatus() {}

    /**
     * Initializes the activity status for a specified application.
     *
     * @param application The application whose status you wish to monitor.
     */
    public static void initialize(Application application) {
        application.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                onStateChange(activity, CREATED);
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                onStateChange(activity, DESTROYED);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                onStateChange(activity, PAUSED);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                onStateChange(activity, RESUMED);
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityStarted(Activity activity) {
                onStateChange(activity, STARTED);
            }

            @Override
            public void onActivityStopped(Activity activity) {
                onStateChange(activity, STOPPED);
            }
        });
    }

    /**
     * Must be called by the main activity when it changes state.
     *
     * @param activity Current activity.
     * @param newState New state value.
     */
    private static void onStateChange(Activity activity, int newState) {
        if (activity == null) throw new IllegalArgumentException("null activity is not supported");

        if (sActivity != activity) {
            // ActivityStatus is notified with the CREATED event very late during the main activity
            // creation to avoid making startup performance worse than it is by notifying observers
            // that could do some expensive work. This can lead to non-CREATED events being fired
            // before the CREATED event which is problematic.
            // TODO(pliard): fix http://crbug.com/176837.
            if (sActivity == null
                    || newState == CREATED || newState == RESUMED || newState == STARTED) {
                sActivity = activity;
            }
        }

        if (newState != DESTROYED) {
            sActivityStates.put(activity, newState);
        } else {
            sActivityStates.remove(activity);
        }

        if (sActivity == activity) {
            for (StateListener listener : sStateListeners) {
                listener.onActivityStateChange(newState);
            }
            if (newState == DESTROYED) {
                sActivity = null;
            }
        }
    }

    /**
     * Testing method to update the state of the specified activity.
     */
    public static void onStateChangeForTesting(Activity activity, int newState) {
        onStateChange(activity, newState);
    }

    /**
     * @return The current activity.
     */
    public static Activity getActivity() {
        return sActivity;
    }

    /**
     * @return The current activity's state (if no activity is registered, then DESTROYED will
     *         be returned).
     */
    public static int getState() {
        return getStateForActivity(sActivity);
    }

    /**
     * Query the state for a given activity.  If the activity is not being tracked, this will
     * return {@link #DESTROYED}.
     *
     * <p>
     * When relying on this method, be familiar with the expected life cycle state
     * transitions:
     * <a href="http://developer.android.com/guide/components/activities.html#Lifecycle">
     *   Activity Lifecycle
     * </a>
     *
     * <p>
     * During activity transitions (activity B launching in front of activity A), A will completely
     * paused before the creation of activity B begins.
     *
     * <p>
     * A basic flow for activity A starting, followed by activity B being opened and then closed:
     * <ul>
     *   <li> -- Starting Activity A --
     *   <li> Activity A - CREATED
     *   <li> Activity A - STARTED
     *   <li> Activity A - RESUMED
     *   <li> -- Starting Activity B --
     *   <li> Activity A - PAUSED
     *   <li> Activity B - CREATED
     *   <li> Activity B - STARTED
     *   <li> Activity B - RESUMED
     *   <li> Activity A - STOPPED
     *   <li> -- Closing Activity B, Activity A regaining focus --
     *   <li> Activity B - PAUSED
     *   <li> Activity A - STARTED
     *   <li> Activity A - RESUMED
     *   <li> Activity B - STOPPED
     *   <li> Activity B - DESTROYED
     * </ul>
     *
     * @param activity The activity whose state is to be returned.
     * @return The state of the specified activity.
     */
    public static int getStateForActivity(Activity activity) {
        Integer currentStatus = sActivityStates.get(activity);
        return currentStatus != null ? currentStatus.intValue() : DESTROYED;
    }

    /**
     * Registers the given listener to receive activity state changes.
     * @param listener Listener to receive state changes.
     */
    public static void registerStateListener(StateListener listener) {
        sStateListeners.addObserver(listener);
    }

    /**
     * Unregisters the given listener from receiving activity state changes.
     * @param listener Listener that doesn't want to receive state changes.
     */
    public static void unregisterStateListener(StateListener listener) {
        sStateListeners.removeObserver(listener);
    }

    /**
     * Registers the single thread-safe native activity status listener.
     * This handles the case where the caller is not on the main thread.
     * Note that this is used by a leaky singleton object from the native
     * side, hence lifecycle management is greatly simplified.
     */
    @CalledByNative
    private static void registerThreadSafeNativeStateListener() {
        ThreadUtils.runOnUiThread(new Runnable () {
            @Override
            public void run() {
                // Register a new listener that calls nativeOnActivityStateChange.
                sStateListeners.addObserver(new StateListener() {
                    @Override
                    public void onActivityStateChange(int newState) {
                        nativeOnActivityStateChange(newState);
                    }
                });
            }
        });
    }

    // Called to notify the native side of state changes.
    // IMPORTANT: This is always called on the main thread!
    private static native void nativeOnActivityStateChange(int newState);

    /**
     * Checks whether or not the Application's current Activity is visible to the user.  Note that
     * this includes the PAUSED state, which can happen when the Activity is temporarily covered
     * by another Activity's Fragment (e.g.).
     * @return True if the Activity is visible, false otherwise.
     */
    public static boolean isApplicationVisible() {
        int state = getState();
        return state != STOPPED && state != DESTROYED;
    }

    /**
     * Checks to see if there are any active Activity instances being watched by ActivityStatus.
     * @return True if all Activities have been destroyed.
     */
    public static boolean isEveryActivityDestroyed() {
        return sActivityStates.isEmpty();
    }
}

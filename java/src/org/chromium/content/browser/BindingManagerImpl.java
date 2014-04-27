// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.util.Log;
import android.util.SparseArray;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;

/**
 * Manages oom bindings used to bound child services.
 */
class BindingManagerImpl implements BindingManager {
    private static final String TAG = "BindingManager";

    // Delay of 1 second used when removing the initial oom binding of a process.
    private static final long REMOVE_INITIAL_BINDING_DELAY_MILLIS = 1 * 1000;

    // Delay of 1 second used when removing temporary strong binding of a process (only on
    // non-low-memory devices).
    private static final long DETACH_AS_ACTIVE_HIGH_END_DELAY_MILLIS = 1 * 1000;

    // These fields allow to override the parameters for testing - see
    // createBindingManagerForTesting().
    private final long mRemoveInitialBindingDelay;
    private final long mRemoveStrongBindingDelay;
    private final boolean mIsLowMemoryDevice;

    /**
     * Wraps ChildProcessConnection keeping track of additional information needed to manage the
     * bindings of the connection. The reference to ChildProcessConnection is cleared when the
     * connection goes away, but ManagedConnection itself is kept (until overwritten by a new entry
     * for the same pid).
     */
    private class ManagedConnection {
        // Set in constructor, cleared in clearConnection().
        private ChildProcessConnection mConnection;

        // True iff there is a strong binding kept on the service because it is working in
        // foreground.
        private boolean mInForeground;

        // True iff there is a strong binding kept on the service because it was bound for the
        // application background period.
        private boolean mBoundForBackgroundPeriod;

        // When mConnection is cleared, oom binding status is stashed here.
        private boolean mWasOomProtected;

        /** Removes the initial service binding. */
        private void removeInitialBinding() {
            final ChildProcessConnection connection = mConnection;
            if (connection == null || !connection.isInitialBindingBound()) return;

            ThreadUtils.postOnUiThreadDelayed(new Runnable() {
                @Override
                public void run() {
                    if (connection.isInitialBindingBound()) {
                        connection.removeInitialBinding();
                    }
                }
            }, mRemoveInitialBindingDelay);
        }

        /** Adds a strong service binding. */
        private void addStrongBinding() {
            ChildProcessConnection connection = mConnection;
            if (connection == null) return;

            connection.addStrongBinding();
        }

        /** Removes a strong service binding. */
        private void removeStrongBinding() {
            final ChildProcessConnection connection = mConnection;
            // We have to fail gracefully if the strong binding is not present, as on low-end the
            // binding could have been removed by dropOomBindings() when a new service was started.
            if (connection == null || !connection.isStrongBindingBound()) return;

            // This runnable performs the actual unbinding. It will be executed synchronously when
            // on low-end devices and posted with a delay otherwise.
            Runnable doUnbind = new Runnable() {
                @Override
                public void run() {
                    if (connection.isStrongBindingBound()) {
                        connection.removeStrongBinding();
                    }
                }
            };

            if (mIsLowMemoryDevice) {
                doUnbind.run();
            } else {
                ThreadUtils.postOnUiThreadDelayed(doUnbind, mRemoveStrongBindingDelay);
            }
        }

        /**
         * Drops the service bindings. This is used on low-end to drop bindings of the current
         * service when a new one is created.
         */
        private void dropBindings() {
            assert mIsLowMemoryDevice;
            ChildProcessConnection connection = mConnection;
            if (connection == null) return;

            connection.dropOomBindings();
        }

        ManagedConnection(ChildProcessConnection connection) {
            mConnection = connection;
        }

        /**
         * Sets the visibility of the service, adding or removing the strong binding as needed. This
         * also removes the initial binding, as the service visibility is now known.
         */
        void setInForeground(boolean nextInForeground) {
            if (!mInForeground && nextInForeground) {
                addStrongBinding();
            } else if (mInForeground && !nextInForeground) {
                removeStrongBinding();
            }

            removeInitialBinding();
            mInForeground = nextInForeground;
        }

        /**
         * Sets or removes additional binding when the service is main service during the embedder
         * background period.
         */
        void setBoundForBackgroundPeriod(boolean nextBound) {
            if (!mBoundForBackgroundPeriod && nextBound) {
                addStrongBinding();
            } else if (mBoundForBackgroundPeriod && !nextBound) {
                removeStrongBinding();
            }

            mBoundForBackgroundPeriod = nextBound;
        }

        boolean isOomProtected() {
            // When a process crashes, we can be queried about its oom status before or after the
            // connection is cleared. For the latter case, the oom status is stashed in
            // mWasOomProtected.
            return mConnection != null ?
                    mConnection.isOomProtectedOrWasWhenDied() : mWasOomProtected;
        }

        void clearConnection() {
            mWasOomProtected = mConnection.isOomProtectedOrWasWhenDied();
            mConnection = null;
        }

        /** @return true iff the reference to the connection is no longer held */
        @VisibleForTesting
        boolean isConnectionCleared() {
            return mConnection == null;
        }
    }

    // This can be manipulated on different threads, synchronize access on mManagedConnections.
    private final SparseArray<ManagedConnection> mManagedConnections =
            new SparseArray<ManagedConnection>();

    // The connection that was most recently set as foreground (using setInForeground()). This is
    // used to add additional binding on it when the embedder goes to background. On low-end, this
    // is also used to drop process bidnings when a new one is created, making sure that only one
    // renderer process at a time is protected from oom killing.
    private ManagedConnection mLastInForeground;

    // Synchronizes operations that access mLastInForeground: setInForeground() and
    // addNewConnection().
    private final Object mLastInForegroundLock = new Object();

    // The connection bound with additional binding in onSentToBackground().
    private ManagedConnection mBoundForBackgroundPeriod;

    /**
     * The constructor is private to hide parameters exposed for testing from the regular consumer.
     * Use factory methods to create an instance.
     */
    private BindingManagerImpl(boolean isLowMemoryDevice, long removeInitialBindingDelay,
            long removeStrongBindingDelay) {
        mIsLowMemoryDevice = isLowMemoryDevice;
        mRemoveInitialBindingDelay = removeInitialBindingDelay;
        mRemoveStrongBindingDelay = removeStrongBindingDelay;
    }

    public static BindingManagerImpl createBindingManager() {
        return new BindingManagerImpl(SysUtils.isLowEndDevice(),
                REMOVE_INITIAL_BINDING_DELAY_MILLIS, DETACH_AS_ACTIVE_HIGH_END_DELAY_MILLIS);
    }

    /**
     * Creates a testing instance of BindingManager. Testing instance will have the unbinding delays
     * set to 0, so that the tests don't need to deal with actual waiting.
     * @param isLowEndDevice true iff the created instance should apply low-end binding policies
     */
    public static BindingManagerImpl createBindingManagerForTesting(boolean isLowEndDevice) {
        return new BindingManagerImpl(isLowEndDevice, 0, 0);
    }

    @Override
    public void addNewConnection(int pid, ChildProcessConnection connection) {
        synchronized (mLastInForegroundLock) {
            if (mIsLowMemoryDevice && mLastInForeground != null) mLastInForeground.dropBindings();
        }

        // This will reset the previous entry for the pid in the unlikely event of the OS
        // reusing renderer pids.
        synchronized (mManagedConnections) {
            mManagedConnections.put(pid, new ManagedConnection(connection));
        }
    }

    @Override
    public void setInForeground(int pid, boolean inForeground) {
        ManagedConnection managedConnection;
        synchronized (mManagedConnections) {
            managedConnection = mManagedConnections.get(pid);
        }

        if (managedConnection == null) {
            Log.w(TAG, "Cannot setInForeground() - never saw a connection for the pid: " +
                    Integer.toString(pid));
            return;
        }

        synchronized (mLastInForegroundLock) {
            managedConnection.setInForeground(inForeground);
            if (inForeground) mLastInForeground = managedConnection;
        }
    }

    @Override
    public void onSentToBackground() {
        assert mBoundForBackgroundPeriod == null;
        synchronized (mLastInForegroundLock) {
            // mLastInForeground can be null at this point as the embedding application could be
            // used in foreground without spawning any renderers.
            if (mLastInForeground != null) {
                mLastInForeground.setBoundForBackgroundPeriod(true);
                mBoundForBackgroundPeriod = mLastInForeground;
            }
        }
    }

    @Override
    public void onBroughtToForeground() {
        if (mBoundForBackgroundPeriod != null) {
            mBoundForBackgroundPeriod.setBoundForBackgroundPeriod(false);
            mBoundForBackgroundPeriod = null;
        }
    }

    @Override
    public boolean isOomProtected(int pid) {
        // In the unlikely event of the OS reusing renderer pid, the call will refer to the most
        // recent renderer of the given pid. The binding state for a pid is being reset in
        // addNewConnection().
        ManagedConnection managedConnection;
        synchronized (mManagedConnections) {
            managedConnection = mManagedConnections.get(pid);
        }
        return managedConnection != null ? managedConnection.isOomProtected() : false;
    }

    @Override
    public void clearConnection(int pid) {
        ManagedConnection managedConnection;
        synchronized (mManagedConnections) {
            managedConnection = mManagedConnections.get(pid);
        }
        if (managedConnection != null) managedConnection.clearConnection();
    }

    /** @return true iff the connection reference is no longer held */
    @VisibleForTesting
    public boolean isConnectionCleared(int pid) {
        synchronized (mManagedConnections) {
            return mManagedConnections.get(pid).isConnectionCleared();
        }
    }
}

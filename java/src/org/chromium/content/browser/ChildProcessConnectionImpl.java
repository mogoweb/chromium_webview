// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CpuFeatures;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.library_loader.Linker;
import org.chromium.content.app.ChildProcessService;
import org.chromium.content.app.ChromiumLinkerParams;
import org.chromium.content.common.IChildProcessCallback;
import org.chromium.content.common.IChildProcessService;

import java.io.IOException;

/**
 * Manages a connection between the browser activity and a child service.
 */
public class ChildProcessConnectionImpl implements ChildProcessConnection {
    private final Context mContext;
    private final int mServiceNumber;
    private final boolean mInSandbox;
    private final ChildProcessConnection.DeathCallback mDeathCallback;
    private final Class<? extends ChildProcessService> mServiceClass;

    // Synchronization: While most internal flow occurs on the UI thread, the public API
    // (specifically start and stop) may be called from any thread, hence all entry point methods
    // into the class are synchronized on the lock to protect access to these members.
    private final Object mLock = new Object();
    private IChildProcessService mService = null;
    // Set to true when the service connected successfully.
    private boolean mServiceConnectComplete = false;
    // Set to true when the service disconnects, as opposed to being properly closed. This happens
    // when the process crashes or gets killed by the system out-of-memory killer.
    private boolean mServiceDisconnected = false;
    // When the service disconnects (i.e. mServiceDisconnected is set to true), the status of the
    // oom bindings is stashed here for future inspection.
    private boolean mWasOomProtected = false;
    private int mPid = 0;  // Process ID of the corresponding child process.
    // Initial binding protects the newly spawned process from being killed before it is put to use,
    // it is maintained between calls to start() and removeInitialBinding().
    private ChildServiceConnection mInitialBinding = null;
    // Strong binding will make the service priority equal to the priority of the activity. We want
    // the OS to be able to kill background renderers as it kills other background apps, so strong
    // bindings are maintained only for services that are active at the moment (between
    // addStrongBinding() and removeStrongBinding()).
    private ChildServiceConnection mStrongBinding = null;
    // Low priority binding maintained in the entire lifetime of the connection, i.e. between calls
    // to start() and stop().
    private ChildServiceConnection mWaivedBinding = null;
    // Incremented on addStrongBinding(), decremented on removeStrongBinding().
    private int mStrongBindingCount = 0;

    // Linker-related parameters.
    private ChromiumLinkerParams mLinkerParams = null;

    private static final String TAG = "ChildProcessConnection";

    private static class ConnectionParams {
        final String[] mCommandLine;
        final FileDescriptorInfo[] mFilesToBeMapped;
        final IChildProcessCallback mCallback;
        final Bundle mSharedRelros;

        ConnectionParams(String[] commandLine, FileDescriptorInfo[] filesToBeMapped,
                IChildProcessCallback callback, Bundle sharedRelros) {
            mCommandLine = commandLine;
            mFilesToBeMapped = filesToBeMapped;
            mCallback = callback;
            mSharedRelros = sharedRelros;
        }
    }

    // This is set in setupConnection() and is later used in doConnectionSetupLocked(), after which
    // the variable is cleared. Therefore this is only valid while the connection is being set up.
    private ConnectionParams mConnectionParams;

    // Callback provided in setupConnection() that will communicate the result to the caller. This
    // has to be called exactly once after setupConnection(), even if setup fails, so that the
    // caller can free up resources associated with the setup attempt. This is set to null after the
    // call.
    private ChildProcessConnection.ConnectionCallback mConnectionCallback;

    private class ChildServiceConnection implements ServiceConnection {
        private boolean mBound = false;

        private final int mBindFlags;

        private Intent createServiceBindIntent() {
            Intent intent = new Intent();
            intent.setClassName(mContext, mServiceClass.getName() + mServiceNumber);
            intent.setPackage(mContext.getPackageName());
            return intent;
        }

        public ChildServiceConnection(int bindFlags) {
            mBindFlags = bindFlags;
        }

        boolean bind(String[] commandLine) {
            if (!mBound) {
                TraceEvent.begin();
                final Intent intent = createServiceBindIntent();
                if (commandLine != null) {
                    intent.putExtra(EXTRA_COMMAND_LINE, commandLine);
                }
                if (mLinkerParams != null)
                    mLinkerParams.addIntentExtras(intent);
                mBound = mContext.bindService(intent, this, mBindFlags);
                TraceEvent.end();
            }
            return mBound;
        }

        void unbind() {
            if (mBound) {
                mContext.unbindService(this);
                mBound = false;
            }
        }

        boolean isBound() {
            return mBound;
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            synchronized (mLock) {
                // A flag from the parent class ensures we run the post-connection logic only once
                // (instead of once per each ChildServiceConnection).
                if (mServiceConnectComplete) {
                    return;
                }
                TraceEvent.begin();
                mServiceConnectComplete = true;
                mService = IChildProcessService.Stub.asInterface(service);
                // Run the setup if the connection parameters have already been provided. If not,
                // doConnectionSetupLocked() will be called from setupConnection().
                if (mConnectionParams != null) {
                    doConnectionSetupLocked();
                }
                TraceEvent.end();
            }
        }


        // Called on the main thread to notify that the child service did not disconnect gracefully.
        @Override
        public void onServiceDisconnected(ComponentName className) {
            synchronized (mLock) {
                // Ensure that the disconnection logic runs only once (instead of once per each
                // ChildServiceConnection).
                if (mServiceDisconnected) {
                    return;
                }
                mServiceDisconnected = true;
                // Stash the status of the oom bindings, since stop() will release all bindings.
                mWasOomProtected = mInitialBinding.isBound() || mStrongBinding.isBound();
                Log.w(TAG, "onServiceDisconnected (crash or killed by oom): pid=" + mPid);
                stop();  // We don't want to auto-restart on crash. Let the browser do that.
                mDeathCallback.onChildProcessDied(ChildProcessConnectionImpl.this);
                // If we have a pending connection callback, we need to communicate the failure to
                // the caller.
                if (mConnectionCallback != null) {
                    mConnectionCallback.onConnected(0);
                }
                mConnectionCallback = null;
            }
        }
    }

    ChildProcessConnectionImpl(Context context, int number, boolean inSandbox,
            ChildProcessConnection.DeathCallback deathCallback,
            Class<? extends ChildProcessService> serviceClass,
            ChromiumLinkerParams chromiumLinkerParams) {
        mContext = context;
        mServiceNumber = number;
        mInSandbox = inSandbox;
        mDeathCallback = deathCallback;
        mServiceClass = serviceClass;
        mLinkerParams = chromiumLinkerParams;
        mInitialBinding = new ChildServiceConnection(Context.BIND_AUTO_CREATE);
        mStrongBinding = new ChildServiceConnection(
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        mWaivedBinding = new ChildServiceConnection(
                Context.BIND_AUTO_CREATE | Context.BIND_WAIVE_PRIORITY);
    }

    @Override
    public int getServiceNumber() {
        return mServiceNumber;
    }

    @Override
    public boolean isInSandbox() {
        return mInSandbox;
    }

    @Override
    public IChildProcessService getService() {
        synchronized (mLock) {
            return mService;
        }
    }

    @Override
    public int getPid() {
        synchronized (mLock) {
            return mPid;
        }
    }

    @Override
    public void start(String[] commandLine) {
        synchronized (mLock) {
            TraceEvent.begin();
            assert !ThreadUtils.runningOnUiThread();
            assert mConnectionParams == null :
                    "setupConnection() called before start() in ChildProcessConnectionImpl.";

            if (!mInitialBinding.bind(commandLine)) {
                Log.e(TAG, "Failed to establish the service connection.");
                // We have to notify the caller so that they can free-up associated resources.
                // TODO(ppi): Can we hard-fail here?
                mDeathCallback.onChildProcessDied(ChildProcessConnectionImpl.this);
            } else {
                mWaivedBinding.bind(null);
            }
            TraceEvent.end();
        }
    }

    @Override
    public void setupConnection(
            String[] commandLine,
            FileDescriptorInfo[] filesToBeMapped,
            IChildProcessCallback processCallback,
            ConnectionCallback connectionCallback,
            Bundle sharedRelros) {
        synchronized (mLock) {
            assert mConnectionParams == null;
            if (mServiceDisconnected) {
                Log.w(TAG, "Tried to setup a connection that already disconnected.");
                connectionCallback.onConnected(0);
                return;
            }

            TraceEvent.begin();
            mConnectionCallback = connectionCallback;
            mConnectionParams = new ConnectionParams(
                    commandLine, filesToBeMapped, processCallback, sharedRelros);
            // Run the setup if the service is already connected. If not, doConnectionSetupLocked()
            // will be called from onServiceConnected().
            if (mServiceConnectComplete) {
                doConnectionSetupLocked();
            }
            TraceEvent.end();
        }
    }

    @Override
    public void stop() {
        synchronized (mLock) {
            mInitialBinding.unbind();
            mStrongBinding.unbind();
            mWaivedBinding.unbind();
            mStrongBindingCount = 0;
            if (mService != null) {
                mService = null;
            }
            mConnectionParams = null;
        }
    }

    /**
     * Called after the connection parameters have been set (in setupConnection()) *and* a
     * connection has been established (as signaled by onServiceConnected()). These two events can
     * happen in any order. Has to be called with mLock.
     */
    private void doConnectionSetupLocked() {
        TraceEvent.begin();
        assert mServiceConnectComplete && mService != null;
        assert mConnectionParams != null;

        Bundle bundle = new Bundle();
        bundle.putStringArray(EXTRA_COMMAND_LINE, mConnectionParams.mCommandLine);

        FileDescriptorInfo[] fileInfos = mConnectionParams.mFilesToBeMapped;
        ParcelFileDescriptor[] parcelFiles = new ParcelFileDescriptor[fileInfos.length];
        for (int i = 0; i < fileInfos.length; i++) {
            if (fileInfos[i].mFd == -1) {
                // If someone provided an invalid FD, they are doing something wrong.
                Log.e(TAG, "Invalid FD (id=" + fileInfos[i].mId + ") for process connection, "
                      + "aborting connection.");
                return;
            }
            String idName = EXTRA_FILES_PREFIX + i + EXTRA_FILES_ID_SUFFIX;
            String fdName = EXTRA_FILES_PREFIX + i + EXTRA_FILES_FD_SUFFIX;
            if (fileInfos[i].mAutoClose) {
                // Adopt the FD, it will be closed when we close the ParcelFileDescriptor.
                parcelFiles[i] = ParcelFileDescriptor.adoptFd(fileInfos[i].mFd);
            } else {
                try {
                    parcelFiles[i] = ParcelFileDescriptor.fromFd(fileInfos[i].mFd);
                } catch (IOException e) {
                    Log.e(TAG,
                          "Invalid FD provided for process connection, aborting connection.",
                          e);
                    return;
                }

            }
            bundle.putParcelable(fdName, parcelFiles[i]);
            bundle.putInt(idName, fileInfos[i].mId);
        }
        // Add the CPU properties now.
        bundle.putInt(EXTRA_CPU_COUNT, CpuFeatures.getCount());
        bundle.putLong(EXTRA_CPU_FEATURES, CpuFeatures.getMask());

        bundle.putBundle(Linker.EXTRA_LINKER_SHARED_RELROS,
                         mConnectionParams.mSharedRelros);

        try {
            mPid = mService.setupConnection(bundle, mConnectionParams.mCallback);
            assert mPid != 0 : "Child service claims to be run by a process of pid=0.";
        } catch (android.os.RemoteException re) {
            Log.e(TAG, "Failed to setup connection.", re);
        }
        // We proactively close the FDs rather than wait for GC & finalizer.
        try {
            for (ParcelFileDescriptor parcelFile : parcelFiles) {
                if (parcelFile != null) parcelFile.close();
            }
        } catch (IOException ioe) {
            Log.w(TAG, "Failed to close FD.", ioe);
        }
        mConnectionParams = null;

        if (mConnectionCallback != null) {
            mConnectionCallback.onConnected(mPid);
        }
        mConnectionCallback = null;
        TraceEvent.end();
    }

    @Override
    public boolean isInitialBindingBound() {
        synchronized (mLock) {
            return mInitialBinding.isBound();
        }
    }

    @Override
    public boolean isStrongBindingBound() {
        synchronized (mLock) {
            return mStrongBinding.isBound();
        }
    }

    @Override
    public void removeInitialBinding() {
        synchronized (mLock) {
            mInitialBinding.unbind();
        }
    }

    @Override
    public boolean isOomProtectedOrWasWhenDied() {
        synchronized (mLock) {
            if (mServiceDisconnected) {
                return mWasOomProtected;
            } else {
                return mInitialBinding.isBound() || mStrongBinding.isBound();
            }
        }
    }

    @Override
    public void dropOomBindings() {
        synchronized (mLock) {
            mInitialBinding.unbind();

            mStrongBindingCount = 0;
            mStrongBinding.unbind();
        }
    }

    @Override
    public void addStrongBinding() {
        synchronized (mLock) {
            if (mService == null) {
                Log.w(TAG, "The connection is not bound for " + mPid);
                return;
            }
            if (mStrongBindingCount == 0) {
                mStrongBinding.bind(null);
            }
            mStrongBindingCount++;
        }
    }

    @Override
    public void removeStrongBinding() {
        synchronized (mLock) {
            if (mService == null) {
                Log.w(TAG, "The connection is not bound for " + mPid);
                return;
            }
            assert mStrongBindingCount > 0;
            mStrongBindingCount--;
            if (mStrongBindingCount == 0) {
                mStrongBinding.unbind();
            }
        }
    }

    @VisibleForTesting
    public boolean crashServiceForTesting() throws RemoteException {
        try {
            mService.crashIntentionallyForTesting();
        } catch (DeadObjectException e) {
            return true;
        }
        return false;
    }

    @VisibleForTesting
    public boolean isConnected() {
        return mService != null;
    }
}

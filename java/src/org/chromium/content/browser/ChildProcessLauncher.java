// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.util.Log;
import android.view.Surface;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;
import org.chromium.base.library_loader.Linker;
import org.chromium.content.app.ChildProcessService;
import org.chromium.content.app.ChromiumLinkerParams;
import org.chromium.content.app.PrivilegedProcessService;
import org.chromium.content.app.SandboxedProcessService;
import org.chromium.content.common.IChildProcessCallback;
import org.chromium.content.common.IChildProcessService;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class provides the method to start/stop ChildProcess called by native.
 */
@JNINamespace("content")
public class ChildProcessLauncher {
    private static final String TAG = "ChildProcessLauncher";

    private static final int CALLBACK_FOR_UNKNOWN_PROCESS = 0;
    private static final int CALLBACK_FOR_GPU_PROCESS = 1;
    private static final int CALLBACK_FOR_RENDERER_PROCESS = 2;

    private static final String SWITCH_PROCESS_TYPE = "type";
    private static final String SWITCH_PPAPI_BROKER_PROCESS = "ppapi-broker";
    private static final String SWITCH_RENDERER_PROCESS = "renderer";
    private static final String SWITCH_GPU_PROCESS = "gpu-process";

    // The upper limit on the number of simultaneous sandboxed and privileged child service process
    // instances supported. Each limit must not exceed total number of SandboxedProcessServiceX
    // classes and PrivilegedProcessServiceX classes declared in this package and defined as
    // services in the embedding application's manifest file.
    // (See {@link ChildProcessService} for more details on defining the services.)
    /* package */ static final int MAX_REGISTERED_SANDBOXED_SERVICES = 13;
    /* package */ static final int MAX_REGISTERED_PRIVILEGED_SERVICES = 3;

    private static class ChildConnectionAllocator {
        // Connections to services. Indices of the array correspond to the service numbers.
        private final ChildProcessConnection[] mChildProcessConnections;

        // The list of free (not bound) service indices. When looking for a free service, the first
        // index in that list should be used. When a service is unbound, its index is added to the
        // end of the list. This is so that we avoid immediately reusing the freed service (see
        // http://crbug.com/164069): the framework might keep a service process alive when it's been
        // unbound for a short time. If a new connection to the same service is bound at that point,
        // the process is reused and bad things happen (mostly static variables are set when we
        // don't expect them to).
        // SHOULD BE ACCESSED WITH mConnectionLock.
        private final ArrayList<Integer> mFreeConnectionIndices;
        private final Object mConnectionLock = new Object();

        private Class<? extends ChildProcessService> mChildClass;
        private final boolean mInSandbox;

        public ChildConnectionAllocator(boolean inSandbox) {
            int numChildServices = inSandbox ?
                    MAX_REGISTERED_SANDBOXED_SERVICES : MAX_REGISTERED_PRIVILEGED_SERVICES;
            mChildProcessConnections = new ChildProcessConnectionImpl[numChildServices];
            mFreeConnectionIndices = new ArrayList<Integer>(numChildServices);
            for (int i = 0; i < numChildServices; i++) {
                mFreeConnectionIndices.add(i);
            }
            setServiceClass(inSandbox ?
                    SandboxedProcessService.class : PrivilegedProcessService.class);
            mInSandbox = inSandbox;
        }

        public void setServiceClass(Class<? extends ChildProcessService> childClass) {
            mChildClass = childClass;
        }

        public ChildProcessConnection allocate(
                Context context, ChildProcessConnection.DeathCallback deathCallback,
                ChromiumLinkerParams chromiumLinkerParams) {
            synchronized (mConnectionLock) {
                if (mFreeConnectionIndices.isEmpty()) {
                    Log.w(TAG, "Ran out of service.");
                    return null;
                }
                int slot = mFreeConnectionIndices.remove(0);
                assert mChildProcessConnections[slot] == null;
                mChildProcessConnections[slot] = new ChildProcessConnectionImpl(context, slot,
                        mInSandbox, deathCallback, mChildClass, chromiumLinkerParams);
                return mChildProcessConnections[slot];
            }
        }

        public void free(ChildProcessConnection connection) {
            synchronized (mConnectionLock) {
                int slot = connection.getServiceNumber();
                if (mChildProcessConnections[slot] != connection) {
                    int occupier = mChildProcessConnections[slot] == null ?
                            -1 : mChildProcessConnections[slot].getServiceNumber();
                    Log.e(TAG, "Unable to find connection to free in slot: " + slot +
                            " already occupied by service: " + occupier);
                    assert false;
                } else {
                    mChildProcessConnections[slot] = null;
                    assert !mFreeConnectionIndices.contains(slot);
                    mFreeConnectionIndices.add(slot);
                }
            }
        }
    }

    // Service class for child process. As the default value it uses SandboxedProcessService0 and
    // PrivilegedProcessService0.
    private static final ChildConnectionAllocator sSandboxedChildConnectionAllocator =
            new ChildConnectionAllocator(true);
    private static final ChildConnectionAllocator sPrivilegedChildConnectionAllocator =
            new ChildConnectionAllocator(false);

    private static boolean sConnectionAllocated = false;

    /**
     * Sets service class for sandboxed service and privileged service.
     */
    public static void setChildProcessClass(
            Class<? extends SandboxedProcessService> sandboxedServiceClass,
            Class<? extends PrivilegedProcessService> privilegedServiceClass) {
        // We should guarantee this is called before allocating connection.
        assert !sConnectionAllocated;
        sSandboxedChildConnectionAllocator.setServiceClass(sandboxedServiceClass);
        sPrivilegedChildConnectionAllocator.setServiceClass(privilegedServiceClass);
    }

    private static ChildConnectionAllocator getConnectionAllocator(boolean inSandbox) {
        return inSandbox ?
                sSandboxedChildConnectionAllocator : sPrivilegedChildConnectionAllocator;
    }

    private static ChildProcessConnection allocateConnection(Context context,
            boolean inSandbox, ChromiumLinkerParams chromiumLinkerParams) {
        ChildProcessConnection.DeathCallback deathCallback =
            new ChildProcessConnection.DeathCallback() {
                @Override
                public void onChildProcessDied(int pid) {
                    stop(pid);
                }
            };
        sConnectionAllocated = true;
        return getConnectionAllocator(inSandbox).allocate(context, deathCallback,
                chromiumLinkerParams);
    }

    private static boolean sLinkerInitialized = false;
    private static long sLinkerLoadAddress = 0;

    private static ChromiumLinkerParams getLinkerParamsForNewConnection() {
        if (!sLinkerInitialized) {
            if (Linker.isUsed()) {
                sLinkerLoadAddress = Linker.getBaseLoadAddress();
                if (sLinkerLoadAddress == 0) {
                    Log.i(TAG, "Shared RELRO support disabled!");
                }
            }
            sLinkerInitialized = true;
        }

        if (sLinkerLoadAddress == 0)
            return null;

        // Always wait for the shared RELROs in service processes.
        final boolean waitForSharedRelros = true;
        return new ChromiumLinkerParams(sLinkerLoadAddress,
                                waitForSharedRelros,
                                Linker.getTestRunnerClassName());
    }

    private static ChildProcessConnection allocateBoundConnection(Context context,
            String[] commandLine, boolean inSandbox) {
        ChromiumLinkerParams chromiumLinkerParams = getLinkerParamsForNewConnection();
        ChildProcessConnection connection =
                allocateConnection(context, inSandbox, chromiumLinkerParams);
        if (connection != null) {
            connection.start(commandLine);
        }
        return connection;
    }

    private static void freeConnection(ChildProcessConnection connection) {
        if (connection == null) {
            return;
        }
        getConnectionAllocator(connection.isInSandbox()).free(connection);
        return;
    }

    // Represents an invalid process handle; same as base/process/process.h kNullProcessHandle.
    private static final int NULL_PROCESS_HANDLE = 0;

    // Map from pid to ChildService connection.
    private static Map<Integer, ChildProcessConnection> sServiceMap =
            new ConcurrentHashMap<Integer, ChildProcessConnection>();

    // A pre-allocated and pre-bound connection ready for connection setup, or null.
    private static ChildProcessConnection sSpareSandboxedConnection = null;

    // Manages oom bindings used to bind chind services.
    private static BindingManager sBindingManager = BindingManagerImpl.createBindingManager();

    static BindingManager getBindingManager() {
        return sBindingManager;
    }

    @VisibleForTesting
    public static void setBindingManagerForTesting(BindingManager manager) {
        sBindingManager = manager;
    }

    @CalledByNative
    private static boolean isOomProtected(int pid) {
        return sBindingManager.isOomProtected(pid);
    }

    /**
     * Called when the embedding application is sent to background.
     */
    public static void onSentToBackground() {
        sBindingManager.onSentToBackground();
    }

    /**
     * Called when the embedding application is brought to foreground.
     */
    public static void onBroughtToForeground() {
        sBindingManager.onBroughtToForeground();
    }

    /**
     * Returns the child process service interface for the given pid. This may be called on
     * any thread, but the caller must assume that the service can disconnect at any time. All
     * service calls should catch and handle android.os.RemoteException.
     *
     * @param pid The pid (process handle) of the service obtained from {@link #start}.
     * @return The IChildProcessService or null if the service no longer exists.
     */
    public static IChildProcessService getChildService(int pid) {
        ChildProcessConnection connection = sServiceMap.get(pid);
        if (connection != null) {
            return connection.getService();
        }
        return null;
    }

    /**
     * Should be called early in startup so the work needed to spawn the child process can be done
     * in parallel to other startup work. Must not be called on the UI thread. Spare connection is
     * created in sandboxed child process.
     * @param context the application context used for the connection.
     */
    public static void warmUp(Context context) {
        synchronized (ChildProcessLauncher.class) {
            assert !ThreadUtils.runningOnUiThread();
            if (sSpareSandboxedConnection == null) {
                sSpareSandboxedConnection = allocateBoundConnection(context, null, true);
            }
        }
    }

    private static String getSwitchValue(final String[] commandLine, String switchKey) {
        if (commandLine == null || switchKey == null) {
            return null;
        }
        // This format should be matched with the one defined in command_line.h.
        final String switchKeyPrefix = "--" + switchKey + "=";
        for (String command : commandLine) {
            if (command != null && command.startsWith(switchKeyPrefix)) {
                return command.substring(switchKeyPrefix.length());
            }
        }
        return null;
    }

    /**
     * Spawns and connects to a child process. May be called on any thread. It will not block, but
     * will instead callback to {@link #nativeOnChildProcessStarted} when the connection is
     * established. Note this callback will not necessarily be from the same thread (currently it
     * always comes from the main thread).
     *
     * @param context Context used to obtain the application context.
     * @param commandLine The child process command line argv.
     * @param fileIds The ID that should be used when mapping files in the created process.
     * @param fileFds The file descriptors that should be mapped in the created process.
     * @param fileAutoClose Whether the file descriptors should be closed once they were passed to
     * the created process.
     * @param clientContext Arbitrary parameter used by the client to distinguish this connection.
     */
    @CalledByNative
    static void start(
            Context context,
            final String[] commandLine,
            int[] fileIds,
            int[] fileFds,
            boolean[] fileAutoClose,
            final long clientContext) {
        assert fileIds.length == fileFds.length && fileFds.length == fileAutoClose.length;
        FileDescriptorInfo[] filesToBeMapped = new FileDescriptorInfo[fileFds.length];
        for (int i = 0; i < fileFds.length; i++) {
            filesToBeMapped[i] =
                    new FileDescriptorInfo(fileIds[i], fileFds[i], fileAutoClose[i]);
        }
        assert clientContext != 0;

        int callbackType = CALLBACK_FOR_UNKNOWN_PROCESS;
        boolean inSandbox = true;
        String processType = getSwitchValue(commandLine, SWITCH_PROCESS_TYPE);
        if (SWITCH_RENDERER_PROCESS.equals(processType)) {
            callbackType = CALLBACK_FOR_RENDERER_PROCESS;
        } else if (SWITCH_GPU_PROCESS.equals(processType)) {
            callbackType = CALLBACK_FOR_GPU_PROCESS;
        } else if (SWITCH_PPAPI_BROKER_PROCESS.equals(processType)) {
            inSandbox = false;
        }

        ChildProcessConnection allocatedConnection = null;
        synchronized (ChildProcessLauncher.class) {
            if (inSandbox) {
                allocatedConnection = sSpareSandboxedConnection;
                sSpareSandboxedConnection = null;
            }
        }
        if (allocatedConnection == null) {
            allocatedConnection = allocateBoundConnection(context, commandLine, inSandbox);
            if (allocatedConnection == null) {
                // Notify the native code so it can free the heap allocated callback.
                nativeOnChildProcessStarted(clientContext, 0);
                return;
            }
        }
        final ChildProcessConnection connection = allocatedConnection;
        Log.d(TAG, "Setting up connection to process: slot=" + connection.getServiceNumber());

        ChildProcessConnection.ConnectionCallback connectionCallback =
                new ChildProcessConnection.ConnectionCallback() {
                    @Override
                    public void onConnected(int pid) {
                        Log.d(TAG, "on connect callback, pid=" + pid + " context=" + clientContext);
                        if (pid != NULL_PROCESS_HANDLE) {
                            sBindingManager.addNewConnection(pid, connection);
                            sServiceMap.put(pid, connection);
                        } else {
                            freeConnection(connection);
                        }
                        nativeOnChildProcessStarted(clientContext, pid);
                    }
                };

        // TODO(sievers): Revisit this as it doesn't correctly handle the utility process
        // assert callbackType != CALLBACK_FOR_UNKNOWN_PROCESS;

        connection.setupConnection(commandLine,
                                   filesToBeMapped,
                                   createCallback(callbackType),
                                   connectionCallback,
                                   Linker.getSharedRelros());
    }

    /**
     * Terminates a child process. This may be called from any thread.
     *
     * @param pid The pid (process handle) of the service connection obtained from {@link #start}.
     */
    @CalledByNative
    static void stop(int pid) {
        Log.d(TAG, "stopping child connection: pid=" + pid);
        ChildProcessConnection connection = sServiceMap.remove(pid);
        if (connection == null) {
            logPidWarning(pid, "Tried to stop non-existent connection");
            return;
        }
        sBindingManager.clearConnection(pid);
        connection.stop();
        freeConnection(connection);
    }

    /**
     * This implementation is used to receive callbacks from the remote service.
     */
    private static IChildProcessCallback createCallback(final int callbackType) {
        return new IChildProcessCallback.Stub() {
            /**
             * This is called by the remote service regularly to tell us about new values. Note that
             * IPC calls are dispatched through a thread pool running in each process, so the code
             * executing here will NOT be running in our main thread -- so, to update the UI, we
             * need to use a Handler.
             */
            @Override
            public void establishSurfacePeer(
                    int pid, Surface surface, int primaryID, int secondaryID) {
                // Do not allow a malicious renderer to connect to a producer. This is only used
                // from stream textures managed by the GPU process.
                if (callbackType != CALLBACK_FOR_GPU_PROCESS) {
                    Log.e(TAG, "Illegal callback for non-GPU process.");
                    return;
                }

                nativeEstablishSurfacePeer(pid, surface, primaryID, secondaryID);
            }

            @Override
            public Surface getViewSurface(int surfaceId) {
                // Do not allow a malicious renderer to get to our view surface.
                if (callbackType != CALLBACK_FOR_GPU_PROCESS) {
                    Log.e(TAG, "Illegal callback for non-GPU process.");
                    return null;
                }

                return nativeGetViewSurface(surfaceId);
            }
        };
    }

     static void logPidWarning(int pid, String message) {
        // This class is effectively a no-op in single process mode, so don't log warnings there.
        if (pid > 0 && !nativeIsSingleProcess()) {
            Log.w(TAG, message + ", pid=" + pid);
        }
    }

    private static native void nativeOnChildProcessStarted(long clientContext, int pid);
    private static native Surface nativeGetViewSurface(int surfaceId);
    private static native void nativeEstablishSurfacePeer(
            int pid, Surface surface, int primaryID, int secondaryID);
    private static native boolean nativeIsSingleProcess();
}

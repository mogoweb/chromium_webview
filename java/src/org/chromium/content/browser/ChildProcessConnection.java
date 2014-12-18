// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.os.Bundle;

import org.chromium.content.common.IChildProcessCallback;
import org.chromium.content.common.IChildProcessService;

/**
 * Manages a connection between the browser activity and a child service. ChildProcessConnection is
 * responsible for estabilishing the connection (start()), closing it (stop()) and manipulating the
 * bindings held onto the service (addStrongBinding(), removeStrongBinding(),
 * removeInitialBinding()).
 */
public interface ChildProcessConnection {
    /**
     * Used to notify the consumer about disconnection of the service. This callback is provided
     * earlier than ConnectionCallbacks below, as a child process might die before the connection is
     * fully set up.
     */
    interface DeathCallback {
        void onChildProcessDied(ChildProcessConnection connection);
    }

    /**
     * Used to notify the consumer about the connection being established.
     */
    interface ConnectionCallback {
        /**
         * Called when the connection to the service is established.
         * @param pid the pid of the child process
         */
        void onConnected(int pid);
    }

    // Names of items placed in the bind intent or connection bundle.
    public static final String EXTRA_COMMAND_LINE =
            "com.google.android.apps.chrome.extra.command_line";
    // Note the FDs may only be passed in the connection bundle.
    public static final String EXTRA_FILES_PREFIX =
            "com.google.android.apps.chrome.extra.extraFile_";
    public static final String EXTRA_FILES_ID_SUFFIX = "_id";
    public static final String EXTRA_FILES_FD_SUFFIX = "_fd";

    // Used to pass the CPU core count to child processes.
    public static final String EXTRA_CPU_COUNT =
            "com.google.android.apps.chrome.extra.cpu_count";
    // Used to pass the CPU features mask to child processes.
    public static final String EXTRA_CPU_FEATURES =
            "com.google.android.apps.chrome.extra.cpu_features";

    int getServiceNumber();

    boolean isInSandbox();

    IChildProcessService getService();

    /**
     * @return the connection pid, or 0 if not yet connected
     */
    int getPid();

    /**
     * Starts a connection to an IChildProcessService. This must be followed by a call to
     * setupConnection() to setup the connection parameters. start() and setupConnection() are
     * separate to allow to pass whatever parameters are available in start(), and complete the
     * remainder later while reducing the connection setup latency.
     * @param commandLine (optional) command line for the child process. If omitted, then
     *                    the command line parameters must instead be passed to setupConnection().
     */
    void start(String[] commandLine);

    /**
     * Setups the connection after it was started with start().
     * @param commandLine (optional) will be ignored if the command line was already sent in start()
     * @param filesToBeMapped a list of file descriptors that should be registered
     * @param processCallback used for status updates regarding this process connection
     * @param connectionCallback will be called exactly once after the connection is set up or the
     *                           setup fails
     */
    void setupConnection(
            String[] commandLine,
            FileDescriptorInfo[] filesToBeMapped,
            IChildProcessCallback processCallback,
            ConnectionCallback connectionCallback,
            Bundle sharedRelros);

    /**
     * Terminates the connection to IChildProcessService, closing all bindings. It is safe to call
     * this multiple times.
     */
    void stop();

    /** @return true iff the initial oom binding is currently bound. */
    boolean isInitialBindingBound();

    /** @return true iff the strong oom binding is currently bound. */
    boolean isStrongBindingBound();

    /**
     * Called to remove the strong binding estabilished when the connection was started. It is safe
     * to call this multiple times.
     */
    void removeInitialBinding();

    /**
     * For live connections, this returns true iff either the initial or the strong binding is
     * bound, i.e. the connection has at least one oom binding. For connections that disconnected
     * (did not exit properly), this returns true iff the connection had at least one oom binding
     * when it disconnected.
     */
    boolean isOomProtectedOrWasWhenDied();

    /**
     * Unbinds the bindings that protect the process from oom killing. It is safe to call this
     * multiple times, before as well as after stop().
     */
    void dropOomBindings();

    /**
     * Attaches a strong binding that will make the service as important as the main process. Each
     * call should be succeeded by removeStrongBinding(), but multiple strong bindings can be
     * requested and released independently.
     */
    void addStrongBinding();

    /**
     * Called when the service is no longer in active use of the consumer.
     */
    void removeStrongBinding();
}

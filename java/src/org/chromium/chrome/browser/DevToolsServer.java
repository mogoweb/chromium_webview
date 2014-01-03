// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

/**
 * Controller for Remote Web Debugging (Developer Tools).
 */
public class DevToolsServer {

    private int mNativeDevToolsServer = 0;

    public DevToolsServer(String socketNamePrefix) {
        mNativeDevToolsServer = nativeInitRemoteDebugging(socketNamePrefix);
    }

    public void destroy() {
        nativeDestroyRemoteDebugging(mNativeDevToolsServer);
        mNativeDevToolsServer = 0;
    }

    public boolean isRemoteDebuggingEnabled() {
        return nativeIsRemoteDebuggingEnabled(mNativeDevToolsServer);
    }

    public void setRemoteDebuggingEnabled(boolean enabled) {
        nativeSetRemoteDebuggingEnabled(mNativeDevToolsServer, enabled);
    }

    private native int nativeInitRemoteDebugging(String socketNamePrefix);
    private native void nativeDestroyRemoteDebugging(int devToolsServer);
    private native boolean nativeIsRemoteDebuggingEnabled(int devToolsServer);
    private native void nativeSetRemoteDebuggingEnabled(int devToolsServer, boolean enabled);
}

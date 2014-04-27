// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

/**
 * Interface for communication from the remote authentication service back to the client.
 */
interface IRemoteAndroidKeyStoreCallbacks {
    /**
      * A critical failure has occurred and the service won't be able to recover.
      * The client should unbind and optionally rebind at a later time.
      */
    void onDisabled();

    /**
     * The service has started up and is fully initialized. This allows for the
     * service to take some time to initialize. Remote calls shouldn't be invoked
     * until this call has fired.
     */
    void onInitComplete();
}

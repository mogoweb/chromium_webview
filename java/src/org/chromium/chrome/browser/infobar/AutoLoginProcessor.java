// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

/**
 * Process the result of an autologin.
 */
public interface AutoLoginProcessor {

    /**
     * Process the result of an autologin.
     *
     * @param accountName The name of the account request is being accessed for.
     * @param authToken The authentication token access is being requested for.
     * @param success Whether or not the authentication attempt was successful.
     * @param result The resulting token for the auto login request
     */
    public void processAutoLoginResult(
            String accountName, String authToken, boolean success, String result);
}

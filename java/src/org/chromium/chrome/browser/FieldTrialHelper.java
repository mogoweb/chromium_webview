// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

/**
 * Helper to get field trial information.
 */
public class FieldTrialHelper {

    private FieldTrialHelper() {}

    /**
     * @param trialName The name of the trial to get the group for.
     * @return The group name chosen for the named trial, or the empty string if the trial does
     *         not exist.
     */
    public static String getFieldTrialFullName(String trialName) {
        return nativeGetFieldTrialFullName(trialName);
    }

    private static native String nativeGetFieldTrialFullName(String trialName);
}

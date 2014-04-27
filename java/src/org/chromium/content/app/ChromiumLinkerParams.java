// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.app;

import android.content.Intent;

/**
 * A class to hold information passed from the browser process to each
 * service one when using the chromium linker. For more information, read the
 * technical notes in Linker.java.
 */
public class ChromiumLinkerParams {
    // Use this base address to load native shared libraries. If 0, ignore other members.
    public final long mBaseLoadAddress;

    // If true, wait for a shared RELRO Bundle just after loading the libraries.
    public final boolean mWaitForSharedRelro;

    // If not empty, name of Linker.TestRunner implementation that needs to be
    // registered in the service process.
    public final String mTestRunnerClassName;

    private static final String EXTRA_LINKER_PARAMS_BASE_LOAD_ADDRESS =
        "org.chromium.content.common.linker_params.base_load_address";

    private static final String EXTRA_LINKER_PARAMS_WAIT_FOR_SHARED_RELRO =
        "org.chromium.content.common.linker_params.wait_for_shared_relro";

    private static final String EXTRA_LINKER_PARAMS_TEST_RUNNER_CLASS_NAME =
        "org.chromium.content.common.linker_params.test_runner_class_name";

    public ChromiumLinkerParams(long baseLoadAddress,
                        boolean waitForSharedRelro,
                        String testRunnerClassName) {
        mBaseLoadAddress = baseLoadAddress;
        mWaitForSharedRelro = waitForSharedRelro;
        mTestRunnerClassName = testRunnerClassName;
    }

    /**
     * Use this constructor to recreate a LinkerParams instance from an Intent.
     * @param intent An Intent, its content must have been populated by a previous
     * call to addIntentExtras().
     */
    public ChromiumLinkerParams(Intent intent) {
        mBaseLoadAddress = intent.getLongExtra(EXTRA_LINKER_PARAMS_BASE_LOAD_ADDRESS, 0);
        mWaitForSharedRelro = intent.getBooleanExtra(
                EXTRA_LINKER_PARAMS_WAIT_FOR_SHARED_RELRO, false);
        mTestRunnerClassName = intent.getStringExtra(
                EXTRA_LINKER_PARAMS_TEST_RUNNER_CLASS_NAME);
    }

    /**
     * Ensure this LinkerParams instance is sent to a service process by adding
     * it to an intent's extras.
     * @param intent An Intent use to start or connect to the child service process.
     */
    public void addIntentExtras(Intent intent) {
        intent.putExtra(EXTRA_LINKER_PARAMS_BASE_LOAD_ADDRESS, mBaseLoadAddress);
        intent.putExtra(EXTRA_LINKER_PARAMS_WAIT_FOR_SHARED_RELRO, mWaitForSharedRelro);
        intent.putExtra(EXTRA_LINKER_PARAMS_TEST_RUNNER_CLASS_NAME, mTestRunnerClassName);
    }

    // For debugging traces only.
    public String toString() {
        return String.format(
                "LinkerParams(baseLoadAddress:0x%x, waitForSharedRelro:%s, " +
                        "testRunnerClassName:%s",
                mBaseLoadAddress,
                mWaitForSharedRelro ? "true" : "false",
                mTestRunnerClassName);
    }
}

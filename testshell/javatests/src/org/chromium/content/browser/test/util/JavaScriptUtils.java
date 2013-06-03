// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import junit.framework.Assert;

import org.chromium.base.ThreadUtils;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentViewCore;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Collection of JavaScript utilities.
 */
public class JavaScriptUtils {
    private static final long EVALUATION_TIMEOUT_SECONDS = 5;

    /**
     * Executes the given snippet of JavaScript code within the given ContentView.
     * Returns the result of its execution in JSON format.
     */
    public static String executeJavaScriptAndWaitForResult(
            final ContentView view, TestCallbackHelperContainer viewClient,
            final String code) throws InterruptedException, TimeoutException {
        return executeJavaScriptAndWaitForResult(
                view.getContentViewCore(),
                viewClient.getOnEvaluateJavaScriptResultHelper(),
                code);
    }

    /**
     * Executes the given snippet of JavaScript code within the given ContentViewCore.
     * Does not depend on ContentView and TestCallbackHelperContainer.
     * Returns the result of its execution in JSON format.
     */
    public static String executeJavaScriptAndWaitForResult(
            final ContentViewCore viewCore,
            final TestCallbackHelperContainer.OnEvaluateJavaScriptResultHelper helper,
            final String code) throws InterruptedException, TimeoutException {
        return executeJavaScriptAndWaitForResult(
                viewCore, helper, code, EVALUATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Executes the given snippet of JavaScript code within the given ContentViewCore.
     * Does not depend on ContentView and TestCallbackHelperContainer.
     * Returns the result of its execution in JSON format.
     */
    public static String executeJavaScriptAndWaitForResult(
            final ContentViewCore viewCore,
            final TestCallbackHelperContainer.OnEvaluateJavaScriptResultHelper helper,
            final String code,
            final long timeout, final TimeUnit timeoutUnits)
                    throws InterruptedException, TimeoutException {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                helper.evaluateJavaScript(viewCore, code);
            }
        });
        helper.waitUntilHasValue(timeout, timeoutUnits);
        Assert.assertTrue("Failed to retrieve JavaScript evaluation results.", helper.hasValue());
        return helper.getJsonResultAndClear();
    }

    /**
     * Executes the given snippet of JavaScript code but does not wait for the result.
     */
    public static void executeJavaScript(final ContentView view, final String code) {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.evaluateJavaScript(code);
            }
        });
    }
}

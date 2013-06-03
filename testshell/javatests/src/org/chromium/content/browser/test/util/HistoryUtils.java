// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

import android.app.Instrumentation;

import org.chromium.base.test.util.InstrumentationUtils;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentViewCore;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Collection of utilities related to the UiThread for navigating
 * through and working with browser forward and back history.
 */
public class HistoryUtils {

    protected final static int WAIT_TIMEOUT_SECONDS = 15;

    /**
     * Calls {@link ContentView#canGoBack()} on UI thread.
     *
     * @param instrumentation an Instrumentation instance.
     * @param contentViewCore a ContentViewCore instance.
     * @return result of {@link ContentView#canGoBack()}
     * @throws Throwable
     */
    public static boolean canGoBackOnUiThread(Instrumentation instrumentation,
            final ContentViewCore contentViewCore) throws Throwable {
        return InstrumentationUtils.runOnMainSyncAndGetResult(
                instrumentation, new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return contentViewCore.canGoBack();
            }
        });
    }

    /**
     * Calls {@link ContentView#canGoToOffset(int)} on UI thread.
     *
     * @param instrumentation an Instrumentation instance.
     * @param contentViewCore a ContentViewCore instance.
     * @param offset The number of steps to go on the UI thread, with negative
     *      representing going back.
     * @return result of {@link ContentView#canGoToOffset(int)}
     * @throws Throwable
     */
    public static boolean canGoToOffsetOnUiThread(Instrumentation instrumentation,
            final ContentViewCore contentViewCore, final int offset) throws Throwable {
        return InstrumentationUtils.runOnMainSyncAndGetResult(
                instrumentation, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return contentViewCore.canGoToOffset(offset);
            }
        });
    }

    /**
     * Calls {@link ContentView#canGoForward()} on UI thread.
     *
     * @param instrumentation an Instrumentation instance.
     * @param contentViewCore a ContentViewCore instance.
     * @return result of {@link ContentView#canGoForward()}
     * @throws Throwable
     */
    public static boolean canGoForwardOnUiThread(Instrumentation instrumentation,
            final ContentViewCore contentViewCore) throws Throwable {
        return InstrumentationUtils.runOnMainSyncAndGetResult(
                instrumentation, new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return contentViewCore.canGoForward();
            }
        });
    }

    /**
     * Calls {@link ContentView#clearHistory()} on UI thread.
     *
     * @param instrumentation an Instrumentation instance.
     * @param contentViewCore a ContentViewCore instance.
     * @throws Throwable
     */
    public static void clearHistoryOnUiThread(Instrumentation instrumentation,
            final ContentViewCore contentViewCore) throws Throwable {
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                contentViewCore.clearHistory();
            }
        });
    }

    /**
     * Calls {@link ContentView#getUrl()} on UI Thread to get the current URL.
     *
     * @param instrumentation an Instrumentation instance.
     * @param contentViewCore a ContentViewCore instance.
     * @return the URL of the current page
     * @throws Throwable
     */
    public static String getUrlOnUiThread(Instrumentation instrumentation,
            final ContentViewCore contentViewCore) throws Throwable {
        return InstrumentationUtils.runOnMainSyncAndGetResult(
                instrumentation, new Callable<String>() {
            @Override
            public String call() throws Exception {
                return contentViewCore.getUrl();
            }
        });
    }

    /**
     * Performs navigation in the history on UI thread and waits until
     * onPageFinished is called.
     *
     * @param instrumentation an Instrumentation instance.
     * @param contentViewCore a ContentViewCore instance.
     * @param onPageFinishedHelper the CallbackHelper instance associated with the onPageFinished
     *                             callback of contentViewCore.
     * @param offset
     * @throws Throwable
     */
    public static void goToOffsetSync(Instrumentation instrumentation,
            final ContentViewCore contentViewCore, CallbackHelper onPageFinishedHelper,
            final int offset) throws Throwable {
        int currentCallCount = onPageFinishedHelper.getCallCount();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                contentViewCore.goToOffset(offset);
            }
        });

        // Wait for onPageFinished event or timeout after 30s
        onPageFinishedHelper.waitForCallback(currentCallCount, 1, 30, TimeUnit.SECONDS);
    }

    /**
     * Goes back on UI thread and waits until onPageFinished is called or until
     * it times out.
     *
     * @param instrumentation an Instrumentation instance.
     * @param contentViewCore a ContentViewCore instance.
     * @param onPageFinishedHelper the CallbackHelper instance associated with the onPageFinished
     *                             callback of contentViewCore.
     * @throws Throwable
     */
    public static void goBackSync(Instrumentation instrumentation,
            final ContentViewCore contentViewCore,
            CallbackHelper onPageFinishedHelper) throws Throwable {
        int currentCallCount = onPageFinishedHelper.getCallCount();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                contentViewCore.goBack();
            }
        });

        onPageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    /**
     * Goes forward on UI thread and waits until onPageFinished is called or until
     * it times out.
     *
     * @param instrumentation an Instrumentation instance.
     * @param contentViewCore a ContentViewCore instance.
     * @throws Throwable
     */
    public static void goForwardSync(Instrumentation instrumentation,
            final ContentViewCore contentViewCore,
            CallbackHelper onPageFinishedHelper) throws Throwable {
        int currentCallCount = onPageFinishedHelper.getCallCount();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                contentViewCore.goForward();
            }
        });

        onPageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }
}

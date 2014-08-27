// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogoweb.chrome.impl;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.chromium.android_webview.AwBrowserContext;
import org.chromium.android_webview.AwBrowserProcess;
import org.chromium.android_webview.AwContents;
import org.chromium.android_webview.AwCookieManager;
import org.chromium.android_webview.AwDevToolsServer;
import org.chromium.android_webview.AwQuotaManagerBridge;
import org.chromium.android_webview.AwResource;
import org.chromium.android_webview.AwSettings;
import org.chromium.base.CommandLine;
import org.chromium.base.ThreadUtils;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.content.browser.ResourceExtractor;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import com.mogoweb.chrome.CookieManager;
import com.mogoweb.chrome.GeolocationPermissions;
import com.mogoweb.chrome.R;
import com.mogoweb.chrome.WebIconDatabase;
import com.mogoweb.chrome.WebStorage;
import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.WebViewDatabase;

public class WebViewChromiumFactoryProvider implements WebViewFactoryProvider {

    private static final String[] MANDATORY_PAKS = { "webviewchromium.pak" };
    private static final String CHROMIUM_PREFS_NAME = "WebViewChromiumPrefs";
    private static final String COMMAND_LINE_FILE = "/data/local/chromeview-command-line";

    // Guards accees to the other members, and is notifyAll() signalled on the UI thread
    // when the chromium process has been started.
    private final Object mLock = new Object();

    // Initialization guarded by mLock.
    private AwBrowserContext mBrowserContext;
    private Statics mStaticMethods;
    private GeolocationPermissionsAdapter mGeolocationPermissions;
    private CookieManagerAdapter mCookieManager;
    private WebIconDatabaseAdapter mWebIconDatabase;
    private WebStorageAdapter mWebStorage;
    private WebViewDatabaseAdapter mWebViewDatabase;
    private AwDevToolsServer mDevToolsServer;

    private ArrayList<WeakReference<WebViewChromium>> mWebViewsToStart =
              new ArrayList<WeakReference<WebViewChromium>>();

    // Read/write protected by mLock.
    private boolean mStarted;

    private Class<?> mClassTypeOfActivityThread;
    private Method mCurrentApplication;

    private Context mContext;

    public WebViewChromiumFactoryProvider() {
        // Load chromium library.
        AwBrowserProcess.loadLibrary();
        ThreadUtils.setWillOverrideUiThread();
        try {
            mClassTypeOfActivityThread = Class.forName("android.app.ActivityThread");
            mCurrentApplication = mClassTypeOfActivityThread.getMethod("currentApplication", new Class[]{});
            mContext = (Context)mCurrentApplication.invoke(null, new Object[]{});
        } catch (ClassNotFoundException exception) {
            exception.printStackTrace();
        } catch (NoSuchMethodException exception) {
            exception.printStackTrace();
        } catch (IllegalAccessException exception) {
            exception.printStackTrace();
        } catch (IllegalArgumentException exception) {
            exception.printStackTrace();
        } catch (InvocationTargetException exception) {
            exception.printStackTrace();
        }
    }

    private void initPlatSupportLibrary() {
        DrawGLFunctor.setChromiumAwDrawGLFunction(AwContents.getAwDrawGLFunction());
    }

    private void ensureChromiumStartedLocked(boolean onMainThread) {
        assert Thread.holdsLock(mLock);

        if (mStarted) {  // Early-out for the common case.
            return;
        }

        Looper looper = !onMainThread ? Looper.myLooper() : Looper.getMainLooper();
        Log.v("WebViewChromium", "Binding Chromium to the " +
                (onMainThread ? "main":"background") + " looper " + looper);
        ThreadUtils.setUiThread(looper);

        if (ThreadUtils.runningOnUiThread()) {
            startChromiumLocked();
            return;
        }

        // We must post to the UI thread to cover the case that the user has invoked Chromium
        // startup by using the (thread-safe) CookieManager rather than creating a WebView.
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    startChromiumLocked();
                }
            }
        });
        while (!mStarted) {
            try {
                // Important: wait() releases |mLock| so the UI thread can take it :-)
                mLock.wait();
            } catch (InterruptedException e) {
                // Keep trying... eventually the UI thread will process the task we sent it.
            }
        }
    }

    private void startChromiumLocked() {
        assert Thread.holdsLock(mLock) && ThreadUtils.runningOnUiThread();

        // The post-condition of this method is everything is ready, so notify now to cover all
        // return paths. (Other threads will not wake-up until we release |mLock|, whatever).
        mLock.notifyAll();

        if (mStarted) {
            return;
        }

        CommandLine.initFromFile(COMMAND_LINE_FILE);

        CommandLine cl = CommandLine.getInstance();
        // TODO: currently in a relase build the DCHECKs only log. We either need to insall
        // a report handler with SetLogReportHandler to make them assert, or else compile
        // them out of the build altogether (b/8284203). Either way, so long they're
        // compiled in, we may as unconditionally enable them here.
        cl.appendSwitch("enable-dcheck");

        // TODO: Remove when GL is supported by default in the upstream code.
        if (!cl.hasSwitch("disable-webview-gl-mode")) {
            cl.appendSwitch("testing-webview-gl-mode");
        }

        if (mContext.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.KITKAT) {
            cl.appendSwitch("enable-webview-classic-workarounds");
        }

        ResourceExtractor.setMandatoryPaksToExtract(MANDATORY_PAKS);
        ResourceExtractor.setExtractImplicitLocaleForTesting(false);

        try {
            LibraryLoader.ensureInitialized(null);
        } catch (ProcessInitException e) {
            throw new RuntimeException("Error initializing WebView library", e);
        }

        AwBrowserProcess.start(mContext);
        initPlatSupportLibrary();

        setWebContentsDebuggingEnabled(true);
        mStarted = true;

        for (WeakReference<WebViewChromium> wvc : mWebViewsToStart) {
            WebViewChromium w = wvc.get();
            if (w != null) {
                w.startYourEngine();
            }
        }
        mWebViewsToStart.clear();
        mWebViewsToStart = null;
    }

    @Override
    public Statics getStatics() {
        synchronized (mLock) {
            if (mStaticMethods == null) {
                // TODO: Optimization potential: most these methods only need the native library
                // loaded and initialized, not the entire browser process started.
                // See also http://b/7009882
                ensureChromiumStartedLocked(true);
                mStaticMethods = new WebViewFactoryProvider.Statics() {
                    @Override
                    public String findAddress(String addr) {
                        return ContentViewStatics.findAddress(addr);
                    }

                    @Override
                    public void setPlatformNotificationsEnabled(boolean enable) {
                        // noop
                    }

                    @Override
                    public String getDefaultUserAgent(Context context) {
                        return AwSettings.getDefaultUserAgent();
                    }

                    @Override
                    public void setWebContentsDebuggingEnabled(boolean enable) {
                        // Web Contents debugging is always enabled on debug builds.
                        WebViewChromiumFactoryProvider.this.
                                setWebContentsDebuggingEnabled(enable);
                    }
                };
            }
        }
        return mStaticMethods;
    }

    @Override
    public WebViewProvider createWebView(WebView webView, WebView.PrivateAccess privateAccess) {
        WebViewChromium wvc = new WebViewChromium(this, webView, privateAccess);

        synchronized (mLock) {
            if (mWebViewsToStart != null) {
                mWebViewsToStart.add(new WeakReference<WebViewChromium>(wvc));
            }
        }
        ResourceProvider.registerResources(webView.getContext());
        return wvc;
    }

    boolean hasStarted() {
        return mStarted;
    }

    void startYourEngines(boolean onMainThread) {
        synchronized (mLock) {
            ensureChromiumStartedLocked(onMainThread);

        }
    }

    @Override
    public GeolocationPermissions getGeolocationPermissions() {
        synchronized (mLock) {
            if (mGeolocationPermissions == null) {
                ensureChromiumStartedLocked(true);
                mGeolocationPermissions = new GeolocationPermissionsAdapter(
                        getBrowserContextLocked().getGeolocationPermissions());
            }
        }
        return mGeolocationPermissions;
    }

    AwBrowserContext getBrowserContext() {
        synchronized (mLock) {
            return getBrowserContextLocked();
        }
    }

    private AwBrowserContext getBrowserContextLocked() {
        assert Thread.holdsLock(mLock);
        assert mStarted;
        if (mBrowserContext == null) {
            mBrowserContext = new AwBrowserContext(
                    mContext.getSharedPreferences(
                            CHROMIUM_PREFS_NAME, Context.MODE_PRIVATE));
        }
        return mBrowserContext;
    }

    @Override
    public CookieManager getCookieManager() {
        synchronized (mLock) {
            if (mCookieManager == null) {
                mCookieManager = new CookieManagerAdapter(new AwCookieManager());
            }
        }
        return mCookieManager;
    }

    @Override
    public WebIconDatabase getWebIconDatabase() {
        synchronized (mLock) {
            if (mWebIconDatabase == null) {
                ensureChromiumStartedLocked(true);
                mWebIconDatabase = new WebIconDatabaseAdapter();
            }
        }
        return mWebIconDatabase;
    }

    @Override
    public WebStorage getWebStorage() {
        synchronized (mLock) {
            if (mWebStorage == null) {
                ensureChromiumStartedLocked(true);
                mWebStorage = new WebStorageAdapter(AwQuotaManagerBridge.getInstance());
            }
        }
        return mWebStorage;
    }

    @Override
    public WebViewDatabase getWebViewDatabase(Context context) {
        synchronized (mLock) {
            if (mWebViewDatabase == null) {
                ensureChromiumStartedLocked(true);
                AwBrowserContext browserContext = getBrowserContextLocked();
                mWebViewDatabase = new WebViewDatabaseAdapter(
                        browserContext.getFormDatabase(),
                        browserContext.getHttpAuthDatabase(context));
            }
        }
        return mWebViewDatabase;
    }

    private void setWebContentsDebuggingEnabled(boolean enable) {
        if (Looper.myLooper() != ThreadUtils.getUiThreadLooper()) {
            throw new RuntimeException(
                    "Toggling of Web Contents Debugging must be done on the UI thread");
        }
        if (mDevToolsServer == null) {
            if (!enable) return;
            mDevToolsServer = new AwDevToolsServer();
        }
        mDevToolsServer.setRemoteDebuggingEnabled(enable);
    }
}

// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Proxy;
import android.os.Build;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.NativeClassQualifiedName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This class partners with native ProxyConfigServiceAndroid to listen for
 * proxy change notifications from Android.
 */
@JNINamespace("net")
public class ProxyChangeListener {
    private static final String TAG = "ProxyChangeListener";
    private static boolean sEnabled = true;

    private long mNativePtr;
    private Context mContext;
    private ProxyReceiver mProxyReceiver;
    private Delegate mDelegate;

    private static class ProxyConfig {
        public ProxyConfig(String host, int port) {
            mHost = host;
            mPort = port;
        }
        public final String mHost;
        public final int mPort;
    }

    public interface Delegate {
        public void proxySettingsChanged();
    }

    private ProxyChangeListener(Context context) {
        mContext = context;
    }

    public static void setEnabled(boolean enabled) {
        sEnabled = enabled;
    }

    public void setDelegateForTesting(Delegate delegate) {
        mDelegate = delegate;
    }

    @CalledByNative
    public static ProxyChangeListener create(Context context) {
        return new ProxyChangeListener(context);
    }

    @CalledByNative
    public static String getProperty(String property) {
        return System.getProperty(property);
    }

    @CalledByNative
    public void start(long nativePtr) {
        assert mNativePtr == 0;
        mNativePtr = nativePtr;
        registerReceiver();
    }

    @CalledByNative
    public void stop() {
        mNativePtr = 0;
        unregisterReceiver();
    }

    private class ProxyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Proxy.PROXY_CHANGE_ACTION)) {
                proxySettingsChanged(extractNewProxy(intent));
            }
        }

        // Extract a ProxyConfig object from the supplied Intent's extra data
        // bundle. The android.net.ProxyProperties class is not exported from
        // tne Android SDK, so we have to use reflection to get at it and invoke
        // methods on it. If we fail, return an empty proxy config (meaning
        // 'direct').
        // TODO(sgurun): once android.net.ProxyInfo is public, rewrite this.
        private ProxyConfig extractNewProxy(Intent intent) {
            try {
                final String GET_HOST_NAME = "getHost";
                final String GET_PORT_NAME = "getPort";
                String className;
                String proxyInfo;
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                    className = "android.net.ProxyProperties";
                    proxyInfo = "proxy";
                } else {
                    className = "android.net.ProxyInfo";
                    proxyInfo = "android.intent.extra.PROXY_INFO";
                }

                Object props = intent.getExtras().get(proxyInfo);
                if (props == null) {
                    return null;
                }

                Class<?> cls = Class.forName(className);
                Method getHostMethod = cls.getDeclaredMethod(GET_HOST_NAME);
                Method getPortMethod = cls.getDeclaredMethod(GET_PORT_NAME);

                String host = (String) getHostMethod.invoke(props);
                int port = (Integer) getPortMethod.invoke(props);

                return new ProxyConfig(host, port);
            } catch (ClassNotFoundException ex) {
                Log.e(TAG, "Using no proxy configuration due to exception:" + ex);
                return null;
            } catch (NoSuchMethodException ex) {
                Log.e(TAG, "Using no proxy configuration due to exception:" + ex);
                return null;
            } catch (IllegalAccessException ex) {
                Log.e(TAG, "Using no proxy configuration due to exception:" + ex);
                return null;
            } catch (InvocationTargetException ex) {
                Log.e(TAG, "Using no proxy configuration due to exception:" + ex);
                return null;
            } catch (NullPointerException ex) {
                Log.e(TAG, "Using no proxy configuration due to exception:" + ex);
                return null;
            }
        }
    }

    private void proxySettingsChanged(ProxyConfig cfg) {
        if (!sEnabled) {
            return;
        }
        if (mDelegate != null) {
            mDelegate.proxySettingsChanged();
        }
        if (mNativePtr == 0) {
            return;
        }
        // Note that this code currently runs on a MESSAGE_LOOP_UI thread, but
        // the C++ code must run the callbacks on the network thread.
        if (cfg != null) {
            nativeProxySettingsChangedTo(mNativePtr, cfg.mHost, cfg.mPort);
        } else {
            nativeProxySettingsChanged(mNativePtr);
        }
    }

    private void registerReceiver() {
        if (mProxyReceiver != null) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Proxy.PROXY_CHANGE_ACTION);
        mProxyReceiver = new ProxyReceiver();
        mContext.getApplicationContext().registerReceiver(mProxyReceiver, filter);
    }

    private void unregisterReceiver() {
        if (mProxyReceiver == null) {
            return;
        }
        mContext.unregisterReceiver(mProxyReceiver);
        mProxyReceiver = null;
    }

    /**
     * See net/proxy/proxy_config_service_android.cc
     */
    @NativeClassQualifiedName("ProxyConfigServiceAndroid::JNIDelegate")
    private native void nativeProxySettingsChangedTo(long nativePtr,
                                                     String host,
                                                     int port);
    @NativeClassQualifiedName("ProxyConfigServiceAndroid::JNIDelegate")
    private native void nativeProxySettingsChanged(long nativePtr);
}

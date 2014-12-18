// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;

/**
 * Used by the NetworkChangeNotifier to listens to platform changes in connectivity.
 * Note that use of this class requires that the app have the platform
 * ACCESS_NETWORK_STATE permission.
 */
public class NetworkChangeNotifierAutoDetect extends BroadcastReceiver
        implements ApplicationStatus.ApplicationStateListener {

    /** Queries the ConnectivityManager for information about the current connection. */
    static class ConnectivityManagerDelegate {
        private final ConnectivityManager mConnectivityManager;

        ConnectivityManagerDelegate(Context context) {
            mConnectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        // For testing.
        ConnectivityManagerDelegate() {
            // All the methods below should be overridden.
            mConnectivityManager = null;
        }

        boolean activeNetworkExists() {
            return mConnectivityManager.getActiveNetworkInfo() != null;
        }

        boolean isConnected() {
            return mConnectivityManager.getActiveNetworkInfo().isConnected();
        }

        int getNetworkType() {
            return mConnectivityManager.getActiveNetworkInfo().getType();
        }

        int getNetworkSubtype() {
            return mConnectivityManager.getActiveNetworkInfo().getSubtype();
        }
    }

    /** Queries the WifiManager for SSID of the current Wifi connection. */
    static class WifiManagerDelegate {
        private final WifiManager mWifiManager;

        WifiManagerDelegate(Context context) {
            mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        }

        // For testing.
        WifiManagerDelegate() {
            // All the methods below should be overridden.
            mWifiManager = null;
        }

        String getWifiSSID() {
            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            if (wifiInfo == null)
                return "";
            String ssid = wifiInfo.getSSID();
            return ssid == null ? "" : ssid;
        }
    }

    private static final String TAG = "NetworkChangeNotifierAutoDetect";

    private final NetworkConnectivityIntentFilter mIntentFilter =
            new NetworkConnectivityIntentFilter();

    private final Observer mObserver;

    private final Context mContext;
    private ConnectivityManagerDelegate mConnectivityManagerDelegate;
    private WifiManagerDelegate mWifiManagerDelegate;
    private boolean mRegistered;
    private int mConnectionType;
    private String mWifiSSID;

    /**
     * Observer notified on the UI thread whenever a new connection type was detected.
     */
    public static interface Observer {
        public void onConnectionTypeChanged(int newConnectionType);
    }

    public NetworkChangeNotifierAutoDetect(Observer observer, Context context) {
        mObserver = observer;
        mContext = context.getApplicationContext();
        mConnectivityManagerDelegate = new ConnectivityManagerDelegate(context);
        mWifiManagerDelegate = new WifiManagerDelegate(context);
        mConnectionType = getCurrentConnectionType();
        mWifiSSID = getCurrentWifiSSID();
        ApplicationStatus.registerApplicationStateListener(this);
    }

    /**
     * Allows overriding the ConnectivityManagerDelegate for tests.
     */
    void setConnectivityManagerDelegateForTests(ConnectivityManagerDelegate delegate) {
        mConnectivityManagerDelegate = delegate;
    }

    /**
     * Allows overriding the WifiManagerDelegate for tests.
     */
    void setWifiManagerDelegateForTests(WifiManagerDelegate delegate) {
        mWifiManagerDelegate = delegate;
    }

    public void destroy() {
        unregisterReceiver();
    }

    /**
     * Register a BroadcastReceiver in the given context.
     */
    private void registerReceiver() {
        if (!mRegistered) {
            mRegistered = true;
            mContext.registerReceiver(this, mIntentFilter);
        }
    }

    /**
     * Unregister the BroadcastReceiver in the given context.
     */
    private void unregisterReceiver() {
        if (mRegistered) {
            mRegistered = false;
            mContext.unregisterReceiver(this);
        }
    }

    public int getCurrentConnectionType() {
        // Track exactly what type of connection we have.
        if (!mConnectivityManagerDelegate.activeNetworkExists() ||
                !mConnectivityManagerDelegate.isConnected()) {
            return NetworkChangeNotifier.CONNECTION_NONE;
        }

        switch (mConnectivityManagerDelegate.getNetworkType()) {
            case ConnectivityManager.TYPE_ETHERNET:
                return NetworkChangeNotifier.CONNECTION_ETHERNET;
            case ConnectivityManager.TYPE_WIFI:
                return NetworkChangeNotifier.CONNECTION_WIFI;
            case ConnectivityManager.TYPE_WIMAX:
                return NetworkChangeNotifier.CONNECTION_4G;
            case ConnectivityManager.TYPE_BLUETOOTH:
                return NetworkChangeNotifier.CONNECTION_BLUETOOTH;
            case ConnectivityManager.TYPE_MOBILE:
                // Use information from TelephonyManager to classify the connection.
                switch (mConnectivityManagerDelegate.getNetworkSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        return NetworkChangeNotifier.CONNECTION_2G;
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                        return NetworkChangeNotifier.CONNECTION_3G;
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        return NetworkChangeNotifier.CONNECTION_4G;
                    default:
                        return NetworkChangeNotifier.CONNECTION_UNKNOWN;
                }
            default:
                return NetworkChangeNotifier.CONNECTION_UNKNOWN;
        }
    }

    private String getCurrentWifiSSID() {
        if (getCurrentConnectionType() != NetworkChangeNotifier.CONNECTION_WIFI)
            return "";
        return mWifiManagerDelegate.getWifiSSID();
    }

    // BroadcastReceiver
    @Override
    public void onReceive(Context context, Intent intent) {
        connectionTypeChanged();
    }

    // ApplicationStatus.ApplicationStateListener
    @Override
    public void onApplicationStateChange(int newState) {
        if (newState == ApplicationState.HAS_RUNNING_ACTIVITIES) {
            connectionTypeChanged();
            registerReceiver();
        } else if (newState == ApplicationState.HAS_PAUSED_ACTIVITIES) {
            unregisterReceiver();
        }
    }

    private void connectionTypeChanged() {
        int newConnectionType = getCurrentConnectionType();
        String newWifiSSID = getCurrentWifiSSID();
        if (newConnectionType == mConnectionType && newWifiSSID.equals(mWifiSSID))
            return;

        mConnectionType = newConnectionType;
        mWifiSSID = newWifiSSID;
        Log.d(TAG, "Network connectivity changed, type is: " + mConnectionType);
        mObserver.onConnectionTypeChanged(newConnectionType);
    }

    private static class NetworkConnectivityIntentFilter extends IntentFilter {
        NetworkConnectivityIntentFilter() {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        }
    }
}

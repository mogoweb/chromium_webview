///*
// * Copyright (C) 2010 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.mogoweb.browser;
//
//import android.app.Activity;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.net.ConnectivityManager;
//import android.net.NetworkInfo;
//import android.webkit.WebView;
//import android.webkit.WebViewClassic;
//
//import com.mogoweb.browser.BrowserSettings;
//
///**
// * Handle network state changes
// */
//public class NetworkStateHandler {
//
//    Activity mActivity;
//    Controller mController;
//
//    // monitor platform changes
//    private IntentFilter mNetworkStateChangedFilter;
//    private BroadcastReceiver mNetworkStateIntentReceiver;
//    private boolean mIsNetworkUp;
//
//    public NetworkStateHandler(Activity activity, Controller controller) {
//        mActivity = activity;
//        mController = controller;
//        // Find out if the network is currently up.
//        ConnectivityManager cm = (ConnectivityManager) mActivity
//                .getSystemService(Context.CONNECTIVITY_SERVICE);
//        NetworkInfo info = cm.getActiveNetworkInfo();
//        if (info != null) {
//            mIsNetworkUp = info.isAvailable();
//        }
//
//        /*
//         * enables registration for changes in network status from http stack
//         */
//        mNetworkStateChangedFilter = new IntentFilter();
//        mNetworkStateChangedFilter.addAction(
//                ConnectivityManager.CONNECTIVITY_ACTION);
//        mNetworkStateIntentReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                if (intent.getAction().equals(
//                        ConnectivityManager.CONNECTIVITY_ACTION)) {
//
//                    NetworkInfo info = intent.getParcelableExtra(
//                            ConnectivityManager.EXTRA_NETWORK_INFO);
//                    String typeName = info.getTypeName();
//                    String subtypeName = info.getSubtypeName();
//                    sendNetworkType(typeName.toLowerCase(),
//                            (subtypeName != null ? subtypeName.toLowerCase() : ""));
//                    BrowserSettings.getInstance().updateConnectionType();
//
//                    boolean noConnection = intent.getBooleanExtra(
//                            ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
//
//                    onNetworkToggle(!noConnection);
//                }
//            }
//        };
//
//    }
//
//    void onPause() {
//        // unregister network state listener
//        mActivity.unregisterReceiver(mNetworkStateIntentReceiver);
//    }
//
//    void onResume() {
//        mActivity.registerReceiver(mNetworkStateIntentReceiver,
//                mNetworkStateChangedFilter);
//        BrowserSettings.getInstance().updateConnectionType();
//    }
//
//    /**
//     * connectivity manager says net has come or gone... inform the user
//     * @param up true if net has come up, false if net has gone down
//     */
//    void onNetworkToggle(boolean up) {
//        if (up == mIsNetworkUp) {
//            return;
//        }
//        mIsNetworkUp = up;
//        WebView w = mController.getCurrentWebView();
//        if (w != null) {
//            w.setNetworkAvailable(up);
//        }
//    }
//
//    boolean isNetworkUp() {
//        return mIsNetworkUp;
//    }
//
//    private void sendNetworkType(String type, String subtype) {
//        WebView w = mController.getCurrentWebView();
//        if (w != null) {
//            WebViewClassic.fromWebView(w).setNetworkType(type, subtype);
//        }
//    }
//}

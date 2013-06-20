/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.mogoweb.browser;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

/** This class implements sharing the URL of the currently
  * shown browser page over NFC. Sharing is only active
  * when the activity is in the foreground and resumed.
  * Incognito tabs will not be shared over NFC.
  */
public class NfcHandler implements NfcAdapter.CreateNdefMessageCallback {
    static final String TAG = "BrowserNfcHandler";
    static final int GET_PRIVATE_BROWSING_STATE_MSG = 100;

    final Controller mController;

    Tab mCurrentTab;
    boolean mIsPrivate;
    CountDownLatch mPrivateBrowsingSignal;

    public static void register(Activity activity, Controller controller) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity.getApplicationContext());
        if (adapter == null) {
            return;  // NFC not available on this device
        }
        NfcHandler handler = null;
        if (controller != null) {
            handler = new NfcHandler(controller);
        }

        adapter.setNdefPushMessageCallback(handler, activity);
    }

    public static void unregister(Activity activity) {
        // Passing a null controller causes us to disable
        // the callback and release the ref to out activity.
        register(activity, null);
    }

    public NfcHandler(Controller controller) {
        mController = controller;
    }

    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == GET_PRIVATE_BROWSING_STATE_MSG) {
                mIsPrivate = mCurrentTab.getWebView().isPrivateBrowsingEnabled();
                mPrivateBrowsingSignal.countDown();
            }
        }
    };

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        mCurrentTab = mController.getCurrentTab();
        if ((mCurrentTab != null) && (mCurrentTab.getWebView() != null)) {
            // We can only read the WebView state on the UI thread, so post
            // a message and wait.
            mPrivateBrowsingSignal = new CountDownLatch(1);
            mHandler.sendMessage(mHandler.obtainMessage(GET_PRIVATE_BROWSING_STATE_MSG));
            try {
                mPrivateBrowsingSignal.await();
            } catch (InterruptedException e) {
                return null;
            }
        }

        if ((mCurrentTab == null) || mIsPrivate) {
            return null;
        }

        String currentUrl = mCurrentTab.getUrl();
        if (currentUrl != null) {
            try {
                return new NdefMessage(NdefRecord.createUri(currentUrl));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "IllegalArgumentException creating URI NdefRecord", e);
                return null;
            }
        } else {
            return null;
        }
    }
}

package com.mogoweb.chrome.impl;

import org.chromium.android_webview.JsResultReceiver;

import com.mogoweb.chrome.JsResult;

/**
 * Proxies from android_webkit's JsResultReceiver to JsResult.
 *
 * @hide
 */
public class ChromeJsResultReceiverProxy implements JsResult.ResultReceiver {

    /** The proxy target. */
    private JsResultReceiver mTarget;

    public ChromeJsResultReceiverProxy(JsResultReceiver target) {
        mTarget = target;
    }
    @Override
    public void onJsResultComplete(JsResult result) {
        if (result.getResult()) {
            mTarget.confirm();
        } else {
            mTarget.cancel();
        }
    }
}

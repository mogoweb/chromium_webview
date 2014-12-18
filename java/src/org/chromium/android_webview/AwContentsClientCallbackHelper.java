// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.graphics.Picture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.google.common.annotations.VisibleForTesting;

import java.util.concurrent.Callable;

/**
 * This class is responsible for calling certain client callbacks on the UI thread.
 *
 * Most callbacks do no go through here, but get forwarded to AwContentsClient directly. The
 * messages processed here may originate from the IO or UI thread.
 */
@VisibleForTesting
public class AwContentsClientCallbackHelper {

    // TODO(boliu): Consider removing DownloadInfo and LoginRequestInfo by using native
    // MessageLoop to post directly to AwContents.

    private static class DownloadInfo {
        final String mUrl;
        final String mUserAgent;
        final String mContentDisposition;
        final String mMimeType;
        final long mContentLength;

        DownloadInfo(String url,
                     String userAgent,
                     String contentDisposition,
                     String mimeType,
                     long contentLength) {
            mUrl = url;
            mUserAgent = userAgent;
            mContentDisposition = contentDisposition;
            mMimeType = mimeType;
            mContentLength = contentLength;
        }
    }

    private static class LoginRequestInfo {
        final String mRealm;
        final String mAccount;
        final String mArgs;

        LoginRequestInfo(String realm, String account, String args) {
            mRealm = realm;
            mAccount = account;
            mArgs = args;
        }
    }

    private static class OnReceivedErrorInfo {
        final int mErrorCode;
        final String mDescription;
        final String mFailingUrl;

        OnReceivedErrorInfo(int errorCode, String description, String failingUrl) {
            mErrorCode = errorCode;
            mDescription = description;
            mFailingUrl = failingUrl;
        }
    }

    private static final int MSG_ON_LOAD_RESOURCE = 1;
    private static final int MSG_ON_PAGE_STARTED = 2;
    private static final int MSG_ON_DOWNLOAD_START = 3;
    private static final int MSG_ON_RECEIVED_LOGIN_REQUEST = 4;
    private static final int MSG_ON_RECEIVED_ERROR = 5;
    private static final int MSG_ON_NEW_PICTURE = 6;
    private static final int MSG_ON_SCALE_CHANGED_SCALED = 7;

    // Minimum period allowed between consecutive onNewPicture calls, to rate-limit the callbacks.
    private static final long ON_NEW_PICTURE_MIN_PERIOD_MILLIS = 500;
    // Timestamp of the most recent onNewPicture callback.
    private long mLastPictureTime = 0;
    // True when a onNewPicture callback is currenly in flight.
    private boolean mHasPendingOnNewPicture = false;

    private final AwContentsClient mContentsClient;

    private final Handler mHandler;

    private class MyHandler extends Handler {
        private MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_ON_LOAD_RESOURCE: {
                    final String url = (String) msg.obj;
                    mContentsClient.onLoadResource(url);
                    break;
                }
                case MSG_ON_PAGE_STARTED: {
                    final String url = (String) msg.obj;
                    mContentsClient.onPageStarted(url);
                    break;
                }
                case MSG_ON_DOWNLOAD_START: {
                    DownloadInfo info = (DownloadInfo) msg.obj;
                    mContentsClient.onDownloadStart(info.mUrl, info.mUserAgent,
                            info.mContentDisposition, info.mMimeType, info.mContentLength);
                    break;
                }
                case MSG_ON_RECEIVED_LOGIN_REQUEST: {
                    LoginRequestInfo info = (LoginRequestInfo) msg.obj;
                    mContentsClient.onReceivedLoginRequest(info.mRealm, info.mAccount, info.mArgs);
                    break;
                }
                case MSG_ON_RECEIVED_ERROR: {
                    OnReceivedErrorInfo info = (OnReceivedErrorInfo) msg.obj;
                    mContentsClient.onReceivedError(info.mErrorCode, info.mDescription,
                            info.mFailingUrl);
                    break;
                }
                case MSG_ON_NEW_PICTURE: {
                    Picture picture = null;
                    try {
                        if (msg.obj != null) picture = (Picture) ((Callable<?>) msg.obj).call();
                    } catch (Exception e) {
                        throw new RuntimeException("Error getting picture", e);
                    }
                    mContentsClient.onNewPicture(picture);
                    mLastPictureTime = SystemClock.uptimeMillis();
                    mHasPendingOnNewPicture = false;
                    break;
                }
                case MSG_ON_SCALE_CHANGED_SCALED: {
                    float oldScale = Float.intBitsToFloat(msg.arg1);
                    float newScale = Float.intBitsToFloat(msg.arg2);
                    mContentsClient.onScaleChangedScaled(oldScale, newScale);
                    break;
                }
                default:
                    throw new IllegalStateException(
                            "AwContentsClientCallbackHelper: unhandled message " + msg.what);
            }
        }
    }

    public AwContentsClientCallbackHelper(Looper looper, AwContentsClient contentsClient) {
        mHandler = new MyHandler(looper);
        mContentsClient = contentsClient;
    }

    public void postOnLoadResource(String url) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_LOAD_RESOURCE, url));
    }

    public void postOnPageStarted(String url) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_PAGE_STARTED, url));
    }

    public void postOnDownloadStart(String url, String userAgent, String contentDisposition,
            String mimeType, long contentLength) {
        DownloadInfo info = new DownloadInfo(url, userAgent, contentDisposition, mimeType,
                contentLength);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_DOWNLOAD_START, info));
    }

    public void postOnReceivedLoginRequest(String realm, String account, String args) {
        LoginRequestInfo info = new LoginRequestInfo(realm, account, args);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_RECEIVED_LOGIN_REQUEST, info));
    }

    public void postOnReceivedError(int errorCode, String description, String failingUrl) {
        OnReceivedErrorInfo info = new OnReceivedErrorInfo(errorCode, description, failingUrl);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_RECEIVED_ERROR, info));
    }

    public void postOnNewPicture(Callable<Picture> pictureProvider) {
        if (mHasPendingOnNewPicture) return;
        mHasPendingOnNewPicture = true;
        long pictureTime = java.lang.Math.max(mLastPictureTime + ON_NEW_PICTURE_MIN_PERIOD_MILLIS,
                SystemClock.uptimeMillis());
        mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_ON_NEW_PICTURE, pictureProvider),
                pictureTime);
    }

    public void postOnScaleChangedScaled(float oldScale, float newScale) {
        // The float->int->float conversion here is to avoid unnecessary allocations. The
        // documentation states that intBitsToFloat(floatToIntBits(a)) == a for all values of a
        // (except for NaNs which are collapsed to a single canonical NaN, but we don't care for
        // that case).
        mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_SCALE_CHANGED_SCALED,
                    Float.floatToIntBits(oldScale), Float.floatToIntBits(newScale)));
    }
}

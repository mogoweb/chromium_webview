// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;

import org.chromium.content.browser.ContentViewCore;

/**
 * Adapts the AwWebContentsDelegate interface to the AwContentsClient interface.
 * This class also serves a secondary function of routing certain callbacks from the content layer
 * to specific listener interfaces.
 */
class AwWebContentsDelegateAdapter extends AwWebContentsDelegate {
    private static final String TAG = "AwWebContentsDelegateAdapter";

    final AwContentsClient mContentsClient;
    final View mContainerView;

    public AwWebContentsDelegateAdapter(AwContentsClient contentsClient,
            View containerView) {
        mContentsClient = contentsClient;
        mContainerView = containerView;
    }

    @Override
    public void onLoadProgressChanged(int progress) {
        mContentsClient.onProgressChanged(progress);
    }

    @Override
    public void handleKeyboardEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int direction;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    direction = View.FOCUS_DOWN;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    direction = View.FOCUS_UP;
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    direction = View.FOCUS_LEFT;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    direction = View.FOCUS_RIGHT;
                    break;
                default:
                    direction = 0;
                    break;
            }
            if (direction != 0 && tryToMoveFocus(direction)) return;
        }
        mContentsClient.onUnhandledKeyEvent(event);
    }

    @Override
    public boolean takeFocus(boolean reverse) {
        int direction =
            (reverse == (mContainerView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL)) ?
            View.FOCUS_RIGHT : View.FOCUS_LEFT;
        if (tryToMoveFocus(direction)) return true;
        direction = reverse ? View.FOCUS_UP : View.FOCUS_DOWN;
        return tryToMoveFocus(direction);
    }

    private boolean tryToMoveFocus(int direction) {
        View focus = mContainerView.focusSearch(direction);
        return focus != null && focus != mContainerView && focus.requestFocus();
    }

    @Override
    public boolean addMessageToConsole(int level, String message, int lineNumber,
            String sourceId) {
        ConsoleMessage.MessageLevel messageLevel = ConsoleMessage.MessageLevel.DEBUG;
        switch(level) {
            case LOG_LEVEL_TIP:
                messageLevel = ConsoleMessage.MessageLevel.TIP;
                break;
            case LOG_LEVEL_LOG:
                messageLevel = ConsoleMessage.MessageLevel.LOG;
                break;
            case LOG_LEVEL_WARNING:
                messageLevel = ConsoleMessage.MessageLevel.WARNING;
                break;
            case LOG_LEVEL_ERROR:
                messageLevel = ConsoleMessage.MessageLevel.ERROR;
                break;
            default:
                Log.w(TAG, "Unknown message level, defaulting to DEBUG");
                break;
        }

        return mContentsClient.onConsoleMessage(
                new ConsoleMessage(message, sourceId, lineNumber, messageLevel));
    }

    @Override
    public void onUpdateUrl(String url) {
        // TODO: implement
    }

    @Override
    public void openNewTab(String url, String extraHeaders, byte[] postData, int disposition) {
        // This is only called in chrome layers.
        assert false;
    }

    @Override
    public void closeContents() {
        mContentsClient.onCloseWindow();
    }

    @Override
    public void showRepostFormWarningDialog(final ContentViewCore contentViewCore) {
        // TODO(mkosiba) We should be using something akin to the JsResultReceiver as the
        // callback parameter (instead of ContentViewCore) and implement a way of converting
        // that to a pair of messages.
        final int MSG_CONTINUE_PENDING_RELOAD = 1;
        final int MSG_CANCEL_PENDING_RELOAD = 2;

        // TODO(sgurun) Remember the URL to cancel the reload behavior
        // if it is different than the most recent NavigationController entry.
        final Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case MSG_CONTINUE_PENDING_RELOAD: {
                        contentViewCore.continuePendingReload();
                        break;
                    }
                    case MSG_CANCEL_PENDING_RELOAD: {
                        contentViewCore.cancelPendingReload();
                        break;
                    }
                    default:
                        throw new IllegalStateException(
                                "WebContentsDelegateAdapter: unhandled message " + msg.what);
                }
            }
        };

        Message resend = handler.obtainMessage(MSG_CONTINUE_PENDING_RELOAD);
        Message dontResend = handler.obtainMessage(MSG_CANCEL_PENDING_RELOAD);
        mContentsClient.onFormResubmission(dontResend, resend);
    }

    @Override
    public void runFileChooser(final int processId, final int renderId, final int mode_flags,
            String acceptTypes, String title, String defaultFilename, boolean capture) {
        AwContentsClient.FileChooserParams params = new AwContentsClient.FileChooserParams();
        params.mode = mode_flags;
        params.acceptTypes = acceptTypes;
        params.title = title;
        params.defaultFilename = defaultFilename;
        params.capture = capture;

        mContentsClient.showFileChooser(new ValueCallback<String[]>() {
            boolean completed = false;
            @Override
            public void onReceiveValue(String[] results) {
                if (completed) {
                    throw new IllegalStateException("Duplicate showFileChooser result");
                }
                completed = true;
                nativeFilesSelectedInChooser(processId, renderId, mode_flags, results);
            }
        }, params);
    }

    @Override
    public boolean addNewContents(boolean isDialog, boolean isUserGesture) {
        return mContentsClient.onCreateWindow(isDialog, isUserGesture);
    }

    @Override
    public void activateContents() {
        mContentsClient.onRequestFocus();
    }
}

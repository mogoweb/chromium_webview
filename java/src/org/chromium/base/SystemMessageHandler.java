// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.os.Handler;
import android.os.Message;

class SystemMessageHandler extends Handler {

    private static final int SCHEDULED_WORK = 1;
    private static final int DELAYED_SCHEDULED_WORK = 2;

    // Native class pointer set by the constructor of the SharedClient native class.
    private long mMessagePumpDelegateNative = 0;
    private long mDelayedScheduledTimeTicks = 0;

    private SystemMessageHandler(long messagePumpDelegateNative) {
        mMessagePumpDelegateNative = messagePumpDelegateNative;
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.what == DELAYED_SCHEDULED_WORK) {
            mDelayedScheduledTimeTicks = 0;
        }
        nativeDoRunLoopOnce(mMessagePumpDelegateNative, mDelayedScheduledTimeTicks);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void scheduleWork() {
        sendEmptyMessage(SCHEDULED_WORK);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void scheduleDelayedWork(long delayedTimeTicks, long millis) {
        if (mDelayedScheduledTimeTicks != 0) {
            removeMessages(DELAYED_SCHEDULED_WORK);
        }
        mDelayedScheduledTimeTicks = delayedTimeTicks;
        sendEmptyMessageDelayed(DELAYED_SCHEDULED_WORK, millis);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void removeAllPendingMessages() {
        removeMessages(SCHEDULED_WORK);
        removeMessages(DELAYED_SCHEDULED_WORK);
    }

    @CalledByNative
    private static SystemMessageHandler create(long messagePumpDelegateNative) {
        return new SystemMessageHandler(messagePumpDelegateNative);
    }

    private native void nativeDoRunLoopOnce(
            long messagePumpDelegateNative, long delayedScheduledTimeTicks);
}

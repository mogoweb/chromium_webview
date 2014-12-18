// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeProvider;

/**
 * A version of {@link ContentView} that supports JellyBean features.
 */
class JellyBeanContentView extends ContentView {
    JellyBeanContentView(Context context, ContentViewCore cvc) {
        super(context, cvc);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (mContentViewCore.supportsAccessibilityAction(action)) {
            return mContentViewCore.performAccessibilityAction(action, arguments);
        }

        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        AccessibilityNodeProvider provider = mContentViewCore.getAccessibilityNodeProvider();
        if (provider != null) {
            return provider;
        } else {
            return super.getAccessibilityNodeProvider();
        }
    }
}

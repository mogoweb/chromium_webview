// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

/**
 * Criteria used to know when an orientation change happens.
 */
public class OrientationChangeObserverCriteria implements Criteria {

    private final MockOrientationObserver mObserver;
    private final int mTarget;
    private final boolean mCheckTarget;

    // Constructor to be used when the criteria is that there is an
    // orientation change but the new orientation value does not matter.
    public OrientationChangeObserverCriteria(MockOrientationObserver observer) {
        mObserver = observer;
        mObserver.mHasChanged = false;

        mCheckTarget = false;
        mTarget = -1;
    }

    // Constructor to be used when the criteria cares about a change
    // happening to a specific orientation value.
    public OrientationChangeObserverCriteria(MockOrientationObserver observer, int target) {
        mObserver = observer;
        mObserver.mHasChanged = false;

        mTarget = target;
        mCheckTarget = true;
    }

    @Override
    public boolean isSatisfied() {
        if (!mObserver.mHasChanged)
            return false;

        return !mCheckTarget || mObserver.mOrientation == mTarget;
    }
}

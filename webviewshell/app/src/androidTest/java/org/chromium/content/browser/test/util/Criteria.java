// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.test.util;

/**
 * Provides a means for validating whether some condition/criteria has been met.
 */
public interface Criteria {

    /**
     * @return Whether the criteria this is testing has been satisfied.
     */
    public boolean isSatisfied();

}

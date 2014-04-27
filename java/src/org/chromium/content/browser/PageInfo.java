// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.view.View;

/**
 * A minimal interface for a View to implement to be shown in a Tab. The main implementation of
 * this is ContentView but other Views can also implement this, enabling them to be shown in a Tab
 * as well.
 */
public interface PageInfo {
    /**
     * @return The title of the page.
     */
    String getTitle();

    /**
     * @return The background color of the page.
     */
    int getBackgroundColor();

    /**
     * @return The View to display the page. This is always non-null.
     */
    View getView();
}

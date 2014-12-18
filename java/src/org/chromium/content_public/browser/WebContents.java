// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

/**
 * The WebContents Java wrapper to allow communicating with the native WebContents object.
 */
public interface WebContents {
    /**
     * @return The navigation controller associated with this WebContents.
     */
    NavigationController getNavigationController();

    /**
     * @return The title for the current visible page.
     */
    String getTitle();

    /**
     * @return The URL for the current visible page.
     */
    String getVisibleUrl();

    /**
     * Stop any pending navigation.
     */
    void stop();
}

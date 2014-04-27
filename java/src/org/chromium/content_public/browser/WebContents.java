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
}

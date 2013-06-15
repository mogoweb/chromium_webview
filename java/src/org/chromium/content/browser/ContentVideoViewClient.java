// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.view.View;

/**
 *  Main callback class used by ContentVideoView.
 *
 *  This contains the superset of callbacks must be implemented by the emmbedder.
 *
 *  onShowCustomView and onDestoryContentVideoView must be implemented,
 *  getVideoLoadingProgressView() is optional, and may return null if not required.
 *
 *  The implementer is responsible for displaying the Android view when
 *  {@link #onShowCustomView(View)} is called.
 */
public interface ContentVideoViewClient {
    public void onShowCustomView(View view);
    public void onDestroyContentVideoView();
    public void keepScreenOn(boolean screenOn);
    public View getVideoLoadingProgressView();
}

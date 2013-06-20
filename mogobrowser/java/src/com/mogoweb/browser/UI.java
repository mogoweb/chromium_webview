/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogoweb.browser;

import java.util.List;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.mogoweb.chrome.WebChromeClient.CustomViewCallback;
import com.mogoweb.chrome.WebView;

/**
 * UI interface definitions
 */
public interface UI {

    public static enum ComboViews {
        History,
        Bookmarks,
        Snapshots,
    }

    public void onPause();

    public void onResume();

    public void onDestroy();

    public void onConfigurationChanged(Configuration config);

    public boolean onBackKey();

    public boolean onMenuKey();

    public boolean needsRestoreAllTabs();

    public void addTab(Tab tab);

    public void removeTab(Tab tab);

    public void setActiveTab(Tab tab);

    public void updateTabs(List<Tab> tabs);

    public void detachTab(Tab tab);

    public void attachTab(Tab tab);

    public void onSetWebView(Tab tab, WebView view);

    public void createSubWindow(Tab tab, WebView subWebView);

    public void attachSubWindow(View subContainer);

    public void removeSubWindow(View subContainer);

    public void onTabDataChanged(Tab tab);

    public void onPageStopped(Tab tab);

    public void onProgressChanged(Tab tab);

    public void showActiveTabsPage();

    public void removeActiveTabsPage();

    public void showComboView(ComboViews startingView, Bundle extra);

    public void showCustomView(View view, int requestedOrientation,
            CustomViewCallback callback);

    public void onHideCustomView();

    public boolean isCustomViewShowing();

    public boolean onPrepareOptionsMenu(Menu menu);

    public void updateMenuState(Tab tab, Menu menu);

    public void onOptionsMenuOpened();

    public void onExtendedMenuOpened();

    public boolean onOptionsItemSelected(MenuItem item);

    public void onOptionsMenuClosed(boolean inLoad);

    public void onExtendedMenuClosed(boolean inLoad);

    public void onContextMenuCreated(Menu menu);

    public void onContextMenuClosed(Menu menu, boolean inLoad);

    public void onActionModeStarted(ActionMode mode);

    public void onActionModeFinished(boolean inLoad);

    public void setShouldShowErrorConsole(Tab tab, boolean show);

    // returns if the web page is clear of any overlays (not including sub windows)
    public boolean isWebShowing();

    public void showWeb(boolean animate);

    Bitmap getDefaultVideoPoster();

    View getVideoLoadingProgressView();

    void bookmarkedStatusHasChanged(Tab tab);

    void showMaxTabsWarning();

    void editUrl(boolean clearInput, boolean forceIME);

    boolean isEditingUrl();

    boolean dispatchKey(int code, KeyEvent event);

    void showAutoLogin(Tab tab);

    void hideAutoLogin(Tab tab);

    void setFullscreen(boolean enabled);

    void setUseQuickControls(boolean enabled);

    public boolean shouldCaptureThumbnails();

    boolean blockFocusAnimations();

    void onVoiceResult(String result);
}

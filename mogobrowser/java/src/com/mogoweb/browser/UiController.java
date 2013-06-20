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

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import com.mogoweb.browser.UI.ComboViews;
import com.mogoweb.chrome.WebView;


/**
 * UI aspect of the controller
 */
public interface UiController {

    UI getUi();

    WebView getCurrentWebView();

    WebView getCurrentTopWebView();

    Tab getCurrentTab();

    TabControl getTabControl();

    List<Tab> getTabs();

    Tab openTabToHomePage();

    Tab openIncognitoTab();

    Tab openTab(String url, boolean incognito, boolean setActive,
            boolean useCurrent);

    void setActiveTab(Tab tab);

    boolean switchToTab(Tab tab);

    void closeCurrentTab();

    void closeTab(Tab tab);

    void closeOtherTabs();

    void stopLoading();

    Intent createBookmarkCurrentPageIntent(boolean canBeAnEdit);

    void bookmarksOrHistoryPicker(ComboViews startView);

    void bookmarkCurrentPage();

    void editUrl();

    void handleNewIntent(Intent intent);

    boolean shouldShowErrorConsole();

    void hideCustomView();

    void attachSubWindow(Tab tab);

    void removeSubWindow(Tab tab);

    boolean isInCustomActionMode();

    void endActionMode();

    void shareCurrentPage();

    void updateMenuState(Tab tab, Menu menu);

    boolean onOptionsItemSelected(MenuItem item);

//    SnapshotTab createNewSnapshotTab(long snapshotId, boolean setActive);

    void loadUrl(Tab tab, String url);

    void setBlockEvents(boolean block);

    Activity getActivity();

    void showPageInfo();

    void openPreferences();

    void findOnPage();

    void toggleUserAgent();

    BrowserSettings getSettings();

    boolean supportsVoice();

    void startVoiceRecognizer();

}

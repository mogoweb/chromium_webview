// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mogoweb.chrome.impl;

import org.chromium.android_webview.AwContents;
import org.chromium.android_webview.AwSettings;
import org.chromium.content.browser.ContentSettings;

import android.annotation.SuppressLint;

import com.mogoweb.chrome.WebSettings;

/**
 * Proxies between ChromeSettings and ContentsSettings / WebSettings.
 */
public class ChromeSettingsProxy extends WebSettings {

    /** The AwContents powering the ChromeView whose settings we're proxying. */
    private AwContents mAwContents;

    /** ContentsSettings proxy target. */
    private ContentSettings mContentSettings;

    /** WebSettings proxy target. */
    private AwSettings mAwSettings;

    public ChromeSettingsProxy(AwContents awContents) {
        mAwContents = awContents;
        mContentSettings = mAwContents.getContentSettings();
        mAwSettings = mAwContents.getSettings();
    }

    @Override
    public void setSupportZoom(boolean support) {
        mAwSettings.setSupportZoom(support);
    }

    @Override
    public boolean supportZoom() {
        return mAwSettings.supportZoom();
    }

    @Override
    public void setMediaPlaybackRequiresUserGesture(boolean require) {
        mAwSettings.setMediaPlaybackRequiresUserGesture(require);
    }

    @Override
    public boolean getMediaPlaybackRequiresUserGesture() {
        return mAwSettings.getMediaPlaybackRequiresUserGesture();
    }

    @Override
    public void setBuiltInZoomControls(boolean enabled) {
        mAwSettings.setBuiltInZoomControls(enabled);
    }

    @Override
    public boolean getBuiltInZoomControls() {
        return mAwSettings.getBuiltInZoomControls();
    }

    @Override
    public void setDisplayZoomControls(boolean enabled) {
        mAwSettings.setDisplayZoomControls(enabled);
    }

    @Override
    public boolean getDisplayZoomControls() {
        return mAwSettings.getDisplayZoomControls();
    }

    @Override
    public void setAllowFileAccess(boolean allow) {
        mAwSettings.setAllowFileAccess(allow);
    }

    @Override
    public boolean getAllowFileAccess() {
        return mAwSettings.getAllowFileAccess();
    }

    @Override
    public void setAllowContentAccess(boolean allow) {
        mAwSettings.setAllowContentAccess(allow);
    }

    @Override
    public boolean getAllowContentAccess() {
        return mAwSettings.getAllowContentAccess();
    }

    @Override
    public void setLoadWithOverviewMode(boolean overview) {
        mAwSettings.setLoadWithOverviewMode(overview);
    }

    @Override
    public boolean getLoadWithOverviewMode() {
        return mAwSettings.getLoadWithOverviewMode();
    }

    @Override
    public void setSaveFormData(boolean save) {
        // mAwSettings.setSaveFormData(save);
    }

    @Override
    public boolean getSaveFormData() {
        return false;
        // return mAwSettings.getSaveFormData();
    }

    @Override
    public void setSavePassword(boolean save) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean getSavePassword() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setTextZoom(int textZoom) {
        mAwSettings.setTextZoom(textZoom);
    }

    @Override
    public int getTextZoom() {
        return mAwSettings.getTextZoom();
    }

    @Override
    public void setDefaultZoom(ZoomDensity zoom) {
        // TODO Auto-generated method stub
    }

    @Override
    public ZoomDensity getDefaultZoom() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setLightTouchEnabled(boolean enabled) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean getLightTouchEnabled() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setUseWideViewPort(boolean use) {
        mAwSettings.setUseWideViewPort(use);
    }

    @Override
    public boolean getUseWideViewPort() {
        return mAwSettings.getUseWideViewPort();
    }

    @Override
    public void setSupportMultipleWindows(boolean support) {
        mAwSettings.setSupportMultipleWindows(support);
    }

    @Override
    public boolean supportMultipleWindows() {
        return mAwSettings.supportMultipleWindows();
    }

    @Override
    public void setLayoutAlgorithm(LayoutAlgorithm l) {
        AwSettings.LayoutAlgorithm algorithm = AwSettings.LayoutAlgorithm.NORMAL;
        switch(l) {
        case NORMAL:
            algorithm = AwSettings.LayoutAlgorithm.NORMAL;
        case SINGLE_COLUMN:
            algorithm = AwSettings.LayoutAlgorithm.SINGLE_COLUMN;
        case NARROW_COLUMNS:
            algorithm = AwSettings.LayoutAlgorithm.NARROW_COLUMNS;
        }
        mAwSettings.setLayoutAlgorithm(algorithm);
    }

    @Override
    public LayoutAlgorithm getLayoutAlgorithm() {
        switch (mAwSettings.getLayoutAlgorithm()) {
        case NORMAL:
            return LayoutAlgorithm.NORMAL;
        case SINGLE_COLUMN:
            return LayoutAlgorithm.SINGLE_COLUMN;
        case NARROW_COLUMNS:
            return LayoutAlgorithm.NARROW_COLUMNS;
        case TEXT_AUTOSIZING:
            return LayoutAlgorithm.NORMAL;
        default:
            return LayoutAlgorithm.NORMAL;
        }
    }

    @Override
    public void setStandardFontFamily(String font) {
        mAwSettings.setStandardFontFamily(font);
    }

    @Override
    public String getStandardFontFamily() {
        return mAwSettings.getStandardFontFamily();
    }

    @Override
    public void setFixedFontFamily(String font) {
        mAwSettings.setFixedFontFamily(font);
    }

    @Override
    public String getFixedFontFamily() {
        return getFixedFontFamily();
    }

    @Override
    public void setSansSerifFontFamily(String font) {
        mAwSettings.setSansSerifFontFamily(font);
    }

    @Override
    public String getSansSerifFontFamily() {
        return mAwSettings.getSansSerifFontFamily();
    }

    @Override
    public void setSerifFontFamily(String font) {
        mAwSettings.setSerifFontFamily(font);
    }

    @Override
    public String getSerifFontFamily() {
        return mAwSettings.getSerifFontFamily();
    }

    @Override
    public void setCursiveFontFamily(String font) {
        mAwSettings.setCursiveFontFamily(font);
    }

    @Override
    public String getCursiveFontFamily() {
        return mAwSettings.getCursiveFontFamily();
    }

    @Override
    public void setFantasyFontFamily(String font) {
        mAwSettings.setFantasyFontFamily(font);
    }

    @Override
    public String getFantasyFontFamily() {
        return mAwSettings.getFantasyFontFamily();
    }

    @Override
    public void setMinimumFontSize(int size) {
        mAwSettings.setMinimumFontSize(size);
    }

    @Override
    public int getMinimumFontSize() {
        return mAwSettings.getMinimumFontSize();
    }

    @Override
    public void setMinimumLogicalFontSize(int size) {
        mAwSettings.setMinimumLogicalFontSize(size);
    }

    @Override
    public int getMinimumLogicalFontSize() {
        return mAwSettings.getMinimumLogicalFontSize();
    }

    @Override
    public void setDefaultFontSize(int size) {
        mAwSettings.setDefaultFontSize(size);
    }

    @Override
    public int getDefaultFontSize() {
        return mAwSettings.getDefaultFontSize();
    }

    @Override
    public void setDefaultFixedFontSize(int size) {
        mAwSettings.setDefaultFixedFontSize(size);
    }

    @Override
    public int getDefaultFixedFontSize() {
        return mAwSettings.getDefaultFixedFontSize();
    }

    @Override
    public void setLoadsImagesAutomatically(boolean flag) {
        mAwSettings.setLoadsImagesAutomatically(flag);
    }
    @Override
    public boolean getLoadsImagesAutomatically() {
        return mAwSettings.getLoadsImagesAutomatically();
    }

    @Override
    public void setBlockNetworkImage(boolean flag) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean getBlockNetworkImage() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setBlockNetworkLoads(boolean flag) {
        mAwSettings.setBlockNetworkLoads(flag);
    }

    @Override
    public boolean getBlockNetworkLoads() {
        return mAwSettings.getBlockNetworkLoads();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void setJavaScriptEnabled(boolean flag) {
        mAwSettings.setJavaScriptEnabled(flag);
    }

    @Override
    public void setAllowUniversalAccessFromFileURLs(boolean flag) {
        mAwSettings.setAllowUniversalAccessFromFileURLs(flag);
    }

    @Override
    public void setAllowFileAccessFromFileURLs(boolean flag) {
        mAwSettings.setAllowFileAccessFromFileURLs(flag);
    }

    @Override
    public void setDatabasePath(String databasePath) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setGeolocationDatabasePath(String databasePath) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setAppCacheEnabled(boolean flag) {
        mAwSettings.setAppCacheEnabled(flag);
    }

    @Override
    public void setAppCachePath(String appCachePath) {
        mAwSettings.setAppCachePath(appCachePath);
    }

    @Override
    public void setAppCacheMaxSize(long appCacheMaxSize) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setDatabaseEnabled(boolean flag) {
        mAwSettings.setDatabaseEnabled(flag);
    }

    @Override
    public void setDomStorageEnabled(boolean flag) {
        mAwSettings.setDomStorageEnabled(flag);
    }

    @Override
    public boolean getDomStorageEnabled() {
        return mAwSettings.getDomStorageEnabled();
    }

    @Override
    public String getDatabasePath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean getDatabaseEnabled() {
        return mAwSettings.getDatabaseEnabled();
    }

    @Override
    public void setGeolocationEnabled(boolean flag) {
        mAwSettings.setGeolocationEnabled(flag);
    }

    @Override
    public boolean getJavaScriptEnabled() {
        return mContentSettings.getJavaScriptEnabled();
    }
    @Override
    public boolean getAllowUniversalAccessFromFileURLs() {
        return mAwSettings.getAllowUniversalAccessFromFileURLs();
    }

    @Override
    public boolean getAllowFileAccessFromFileURLs() {
        return mAwSettings.getAllowFileAccessFromFileURLs();
    }

    @Override
    public void setJavaScriptCanOpenWindowsAutomatically(boolean flag) {
        mAwSettings.setJavaScriptCanOpenWindowsAutomatically(flag);
    }

    @Override
    public boolean getJavaScriptCanOpenWindowsAutomatically() {
        return mAwSettings.getJavaScriptCanOpenWindowsAutomatically();
    }

    @Override
    public void setDefaultTextEncodingName(String encoding) {
        mAwSettings.setDefaultTextEncodingName(encoding);
    }

    @Override
    public String getDefaultTextEncodingName() {
        return mAwSettings.getDefaultTextEncodingName();
    }

    @Override
    public void setUserAgentString(String ua) {
        mAwSettings.setUserAgentString(ua);
    }

    @Override
    public String getUserAgentString() {
        return mAwSettings.getUserAgentString();
    }

    @Override
    public void setNeedInitialFocus(boolean flag) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setRenderPriority(RenderPriority priority) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setCacheMode(int mode) {
        mAwSettings.setCacheMode(mode);
    }

    @Override
    public int getCacheMode() {
        return mAwSettings.getCacheMode();
    }
}

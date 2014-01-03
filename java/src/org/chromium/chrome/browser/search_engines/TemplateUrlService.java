// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.search_engines;

import org.chromium.base.CalledByNative;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Android wrapper of the TemplateUrlService which provides access from the Java
 * layer.
 *
 * Only usable from the UI thread as it's primary purpose is for supporting the Android
 * preferences UI.
 *
 * See chrome/browser/search_engines/template_url_service.h for more details.
 */
public class TemplateUrlService {

    /**
     * This listener will be notified when template url service is done loading.
     */
    public interface LoadListener {
        public abstract void onTemplateUrlServiceLoaded();
    }

    public static class TemplateUrl {
        private final int mIndex;
        private final String mShortName;
        private final String mKeyword;

        @CalledByNative("TemplateUrl")
        public static TemplateUrl create(int id, String shortName, String keyword) {
            return new TemplateUrl(id, shortName, keyword);
        }

        public TemplateUrl(int index, String shortName, String keyword) {
          mIndex = index;
          mShortName = shortName;
          mKeyword = keyword;
        }

        public int getIndex() {
            return mIndex;
        }

        public String getShortName() {
            return mShortName;
        }

        public String getKeyword() {
            return mKeyword;
        }
    }

    private static TemplateUrlService sService;

    public static TemplateUrlService getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sService == null) {
            sService = new TemplateUrlService();
        }
        return sService;
    }

    private final int mNativeTemplateUrlServiceAndroid;
    private final ObserverList<LoadListener> mLoadListeners = new ObserverList<LoadListener>();

    private TemplateUrlService() {
        // Note that this technically leaks the native object, however, TemlateUrlService
        // is a singleton that lives forever and there's no clean shutdown of Chrome on Android
        mNativeTemplateUrlServiceAndroid = nativeInit();
    }

    public boolean isLoaded() {
        ThreadUtils.assertOnUiThread();
        return nativeIsLoaded(mNativeTemplateUrlServiceAndroid);
    }

    public void load() {
        ThreadUtils.assertOnUiThread();
        nativeLoad(mNativeTemplateUrlServiceAndroid);
    }

    /**
     * Get the collection of localized search engines.
     */
    public List<TemplateUrl> getLocalizedSearchEngines() {
        ThreadUtils.assertOnUiThread();
        int templateUrlCount = nativeGetTemplateUrlCount(mNativeTemplateUrlServiceAndroid);
        List<TemplateUrl> templateUrls = new ArrayList<TemplateUrl>(templateUrlCount);
        for (int i = 0; i < templateUrlCount; i++) {
            TemplateUrl templateUrl = nativeGetPrepopulatedTemplateUrlAt(
                    mNativeTemplateUrlServiceAndroid, i);
            if (templateUrl != null) {
              templateUrls.add(templateUrl);
            }
        }
        return templateUrls;
    }

    /**
     * Called from native when template URL service is done loading.
     */
    @CalledByNative
    private void templateUrlServiceLoaded() {
        ThreadUtils.assertOnUiThread();
        for (LoadListener listener : mLoadListeners) {
            listener.onTemplateUrlServiceLoaded();
        }
    }

    /**
     * @return The default search engine index (e.g., 0, 1, 2,...).
     */
    public int getDefaultSearchEngineIndex() {
        ThreadUtils.assertOnUiThread();
        return nativeGetDefaultSearchProvider(mNativeTemplateUrlServiceAndroid);
    }

    /**
     * @return {@link TemplateUrlService.TemplateUrl} for the default search engine.
     */
    public TemplateUrl getDefaultSearchEngineTemplateUrl() {
        if (!isLoaded()) return null;

        int defaultSearchEngineIndex = getDefaultSearchEngineIndex();
        assert defaultSearchEngineIndex >= 0;

        return nativeGetPrepopulatedTemplateUrlAt(
                mNativeTemplateUrlServiceAndroid, defaultSearchEngineIndex);
    }

    public void setSearchEngine(int selectedIndex) {
        ThreadUtils.assertOnUiThread();
        nativeSetDefaultSearchProvider(mNativeTemplateUrlServiceAndroid, selectedIndex);
    }

    public boolean isSearchProviderManaged() {
        return nativeIsSearchProviderManaged(mNativeTemplateUrlServiceAndroid);
    }

    /**
     * @return Whether or not the default search engine has search by image support.
     */
    public boolean isSearchByImageAvailable() {
        ThreadUtils.assertOnUiThread();
        return nativeIsSearchByImageAvailable(mNativeTemplateUrlServiceAndroid);
    }

    /**
     * @return Whether the default configured search engine is for a Google property.
     */
    public boolean isDefaultSearchEngineGoogle() {
        return nativeIsDefaultSearchEngineGoogle(mNativeTemplateUrlServiceAndroid);
    }

    /**
     * Registers a listener for the callback that indicates that the
     * TemplateURLService has loaded.
     */
    public void registerLoadListener(LoadListener listener) {
        ThreadUtils.assertOnUiThread();
        assert !mLoadListeners.hasObserver(listener);
        mLoadListeners.addObserver(listener);
    }

    /**
     * Unregisters a listener for the callback that indicates that the
     * TemplateURLService has loaded.
     */
    public void unregisterLoadListener(LoadListener listener) {
        ThreadUtils.assertOnUiThread();
        assert (mLoadListeners.hasObserver(listener));
        mLoadListeners.removeObserver(listener);
    }

    /**
     * Finds the default search engine for the default provider and returns the url query
     * {@link String} for {@code query}.
     * @param query The {@link String} that represents the text query the search url should
     *              represent.
     * @return      A {@link String} that contains the url of the default search engine with
     *              {@code query} inserted as the search parameter.
     */
    public String getUrlForSearchQuery(String query) {
        return nativeGetUrlForSearchQuery(mNativeTemplateUrlServiceAndroid, query);
    }

    /**
     * Replaces the search terms from {@code query} in {@code url}.
     * @param query The {@link String} that represents the text query that should replace the
     *              existing query in {@code url}.
     * @param url   The {@link String} that contains the search url with another search query that
     *              will be replaced with {@code query}.
     * @return      A new version of {@code url} with the search term replaced with {@code query}.
     */
    public String replaceSearchTermsInUrl(String query, String url) {
        return nativeReplaceSearchTermsInUrl(mNativeTemplateUrlServiceAndroid, query, url);
    }

    private native int nativeInit();
    private native void nativeLoad(int nativeTemplateUrlServiceAndroid);
    private native boolean nativeIsLoaded(int nativeTemplateUrlServiceAndroid);
    private native int nativeGetTemplateUrlCount(int nativeTemplateUrlServiceAndroid);
    private native TemplateUrl nativeGetPrepopulatedTemplateUrlAt(
            int nativeTemplateUrlServiceAndroid, int i);
    private native void nativeSetDefaultSearchProvider(int nativeTemplateUrlServiceAndroid,
            int selectedIndex);
    private native int nativeGetDefaultSearchProvider(int nativeTemplateUrlServiceAndroid);
    private native boolean nativeIsSearchProviderManaged(int nativeTemplateUrlServiceAndroid);
    private native boolean nativeIsSearchByImageAvailable(int nativeTemplateUrlServiceAndroid);
    private native boolean nativeIsDefaultSearchEngineGoogle(int nativeTemplateUrlServiceAndroid);
    private native String nativeGetUrlForSearchQuery(int nativeTemplateUrlServiceAndroid,
            String query);
    private native String nativeReplaceSearchTermsInUrl(int nativeTemplateUrlServiceAndroid,
            String query, String currentUrl);
}

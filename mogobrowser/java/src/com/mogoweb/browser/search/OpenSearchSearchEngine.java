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
package com.mogoweb.browser.search;

import com.mogoweb.browser.R;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.provider.Browser;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;

/**
 * Provides search suggestions, if any, for a given web search provider.
 */
public class OpenSearchSearchEngine implements SearchEngine {

    private static final String TAG = "OpenSearchSearchEngine";

    private static final String USER_AGENT = "Android/1.0";
    private static final int HTTP_TIMEOUT_MS = 1000;

    // TODO: this should be defined somewhere
    private static final String HTTP_TIMEOUT = "http.connection-manager.timeout";

    // Indices of the columns in the below arrays.
    private static final int COLUMN_INDEX_ID = 0;
    private static final int COLUMN_INDEX_QUERY = 1;
    private static final int COLUMN_INDEX_ICON = 2;
    private static final int COLUMN_INDEX_TEXT_1 = 3;
    private static final int COLUMN_INDEX_TEXT_2 = 4;

    // The suggestion columns used. If you are adding a new entry to these arrays make sure to
    // update the list of indices declared above.
    private static final String[] COLUMNS = new String[] {
        "_id",
        SearchManager.SUGGEST_COLUMN_QUERY,
        SearchManager.SUGGEST_COLUMN_ICON_1,
        SearchManager.SUGGEST_COLUMN_TEXT_1,
        SearchManager.SUGGEST_COLUMN_TEXT_2,
    };

    private static final String[] COLUMNS_WITHOUT_DESCRIPTION = new String[] {
        "_id",
        SearchManager.SUGGEST_COLUMN_QUERY,
        SearchManager.SUGGEST_COLUMN_ICON_1,
        SearchManager.SUGGEST_COLUMN_TEXT_1,
    };

    private final SearchEngineInfo mSearchEngineInfo;

    private final AndroidHttpClient mHttpClient;

    public OpenSearchSearchEngine(Context context, SearchEngineInfo searchEngineInfo) {
        mSearchEngineInfo = searchEngineInfo;
        mHttpClient = AndroidHttpClient.newInstance(USER_AGENT);
        HttpParams params = mHttpClient.getParams();
        params.setLongParameter(HTTP_TIMEOUT, HTTP_TIMEOUT_MS);
    }

    public String getName() {
        return mSearchEngineInfo.getName();
    }

    public CharSequence getLabel() {
        return mSearchEngineInfo.getLabel();
    }

    public void startSearch(Context context, String query, Bundle appData, String extraData) {
        String uri = mSearchEngineInfo.getSearchUriForQuery(query);
        if (uri == null) {
            Log.e(TAG, "Unable to get search URI for " + mSearchEngineInfo);
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            // Make sure the intent goes to the Browser itself
            intent.setPackage(context.getPackageName());
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra(SearchManager.QUERY, query);
            if (appData != null) {
                intent.putExtra(SearchManager.APP_DATA, appData);
            }
            if (extraData != null) {
                intent.putExtra(SearchManager.EXTRA_DATA_KEY, extraData);
            }
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
            context.startActivity(intent);
        }
    }

    /**
     * Queries for a given search term and returns a cursor containing
     * suggestions ordered by best match.
     */
    public Cursor getSuggestions(Context context, String query) {
        if (TextUtils.isEmpty(query)) {
            return null;
        }
        if (!isNetworkConnected(context)) {
            Log.i(TAG, "Not connected to network.");
            return null;
        }

        String suggestUri = mSearchEngineInfo.getSuggestUriForQuery(query);
        if (TextUtils.isEmpty(suggestUri)) {
            // No suggest URI available for this engine
            return null;
        }

        try {
            String content = readUrl(suggestUri);
            if (content == null) return null;
            /* The data format is a JSON array with items being regular strings or JSON arrays
             * themselves. We are interested in the second and third elements, both of which
             * should be JSON arrays. The second element/array contains the suggestions and the
             * third element contains the descriptions. Some search engines don't support
             * suggestion descriptions so the third element is optional.
             */
            JSONArray results = new JSONArray(content);
            JSONArray suggestions = results.getJSONArray(1);
            JSONArray descriptions = null;
            if (results.length() > 2) {
                descriptions = results.getJSONArray(2);
                // Some search engines given an empty array "[]" for descriptions instead of
                // not including it in the response.
                if (descriptions.length() == 0) {
                    descriptions = null;
                }
            }
            return new SuggestionsCursor(suggestions, descriptions);
        } catch (JSONException e) {
            Log.w(TAG, "Error", e);
        }
        return null;
    }

    /**
     * Executes a GET request and returns the response content.
     *
     * @param url Request URI.
     * @return The response content. This is the empty string if the response
     *         contained no content.
     */
    public String readUrl(String url) {
        try {
            HttpGet method = new HttpGet(url);
            HttpResponse response = mHttpClient.execute(method);
            if (response.getStatusLine().getStatusCode() == 200) {
                return EntityUtils.toString(response.getEntity());
            } else {
                Log.i(TAG, "Suggestion request failed");
                return null;
            }
        } catch (IOException e) {
            Log.w(TAG, "Error", e);
            return null;
        }
    }

    public boolean supportsSuggestions() {
        return mSearchEngineInfo.supportsSuggestions();
    }

    public void close() {
        mHttpClient.close();
    }

    private boolean isNetworkConnected(Context context) {
        NetworkInfo networkInfo = getActiveNetworkInfo(context);
        return networkInfo != null && networkInfo.isConnected();
    }

    private NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return null;
        }
        return connectivity.getActiveNetworkInfo();
    }

    private static class SuggestionsCursor extends AbstractCursor {

        private final JSONArray mSuggestions;

        private final JSONArray mDescriptions;

        public SuggestionsCursor(JSONArray suggestions, JSONArray descriptions) {
            mSuggestions = suggestions;
            mDescriptions = descriptions;
        }

        @Override
        public int getCount() {
            return mSuggestions.length();
        }

        @Override
        public String[] getColumnNames() {
            return (mDescriptions != null ? COLUMNS : COLUMNS_WITHOUT_DESCRIPTION);
        }

        @Override
        public String getString(int column) {
            if (mPos != -1) {
                if ((column == COLUMN_INDEX_QUERY) || (column == COLUMN_INDEX_TEXT_1)) {
                    try {
                        return mSuggestions.getString(mPos);
                    } catch (JSONException e) {
                        Log.w(TAG, "Error", e);
                    }
                } else if (column == COLUMN_INDEX_TEXT_2) {
                    try {
                        return mDescriptions.getString(mPos);
                    } catch (JSONException e) {
                        Log.w(TAG, "Error", e);
                    }
                } else if (column == COLUMN_INDEX_ICON) {
                    return String.valueOf(R.drawable.magnifying_glass);
                }
            }
            return null;
        }

        @Override
        public double getDouble(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getFloat(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong(int column) {
            if (column == COLUMN_INDEX_ID) {
                return mPos;        // use row# as the _Id
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public short getShort(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isNull(int column) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String toString() {
        return "OpenSearchSearchEngine{" + mSearchEngineInfo + "}";
    }

    @Override
    public boolean wantsEmptyQuery() {
        return false;
    }

}

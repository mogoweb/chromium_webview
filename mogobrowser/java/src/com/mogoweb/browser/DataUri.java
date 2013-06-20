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

import java.net.MalformedURLException;

import android.util.Base64;

/**
 * Class extracts the mime type and data from a data uri.
 * A data URI is of the form:
 * <pre>
 * data:[&lt;MIME-type&gt;][;charset=&lt;encoding&gt;][;base64],&lt;data&gt;
 * </pre>
 */
public class DataUri {
    private static final String DATA_URI_PREFIX = "data:";
    private static final String BASE_64_ENCODING = ";base64";

    private String mMimeType;
    private byte[] mData;

    public DataUri(String uri) throws MalformedURLException {
        if (!isDataUri(uri)) {
            throw new MalformedURLException("Not a data URI");
        }

        int commaIndex = uri.indexOf(',', DATA_URI_PREFIX.length());
        if (commaIndex < 0) {
            throw new MalformedURLException("Comma expected in data URI");
        }
        String contentType = uri.substring(DATA_URI_PREFIX.length(),
                commaIndex);
        mData = uri.substring(commaIndex + 1).getBytes();
        if (contentType.contains(BASE_64_ENCODING)) {
            mData = Base64.decode(mData, Base64.DEFAULT);
        }
        int semiIndex = contentType.indexOf(';');
        if (semiIndex > 0) {
            mMimeType = contentType.substring(0, semiIndex);
        } else {
            mMimeType = contentType;
        }
    }

    /**
     * Returns true if the text passed in appears to be a data URI.
     */
    public static boolean isDataUri(String text)
    {
        return text.startsWith(DATA_URI_PREFIX);
    }

    public String getMimeType() {
        return mMimeType;
    }

    public byte[] getData() {
        return mData;
    }
}

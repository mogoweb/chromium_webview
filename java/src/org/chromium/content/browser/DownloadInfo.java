// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

/**
 * Class representing the state of a single download.
 */
public final class DownloadInfo {
    private final String mUrl;
    private final String mUserAgent;
    private final String mMimeType;
    private final String mCookie;
    private final String mFileName;
    private final String mDescription;
    private final String mFilePath;
    private final String mReferer;
    private final long mContentLength;
    private final boolean mHasDownloadId;
    private final int mDownloadId;
    private final String mContentDisposition;
    private final boolean mIsGETRequest;
    private final boolean mIsSuccessful;
    private final int mPercentCompleted;
    private final long mTimeRemainingInMillis;

    private DownloadInfo(Builder builder) {
        mUrl = builder.mUrl;
        mUserAgent = builder.mUserAgent;
        mMimeType = builder.mMimeType;
        mCookie = builder.mCookie;
        mFileName = builder.mFileName;
        mDescription = builder.mDescription;
        mFilePath = builder.mFilePath;
        mReferer = builder.mReferer;
        mContentLength = builder.mContentLength;
        mHasDownloadId = builder.mHasDownloadId;
        mDownloadId = builder.mDownloadId;
        mIsSuccessful = builder.mIsSuccessful;
        mIsGETRequest = builder.mIsGETRequest;
        mContentDisposition = builder.mContentDisposition;
        mPercentCompleted = builder.mPercentCompleted;
        mTimeRemainingInMillis = builder.mTimeRemainingInMillis;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getUserAgent() {
        return mUserAgent;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public String getCookie() {
        return mCookie;
    }

    public String getFileName() {
        return mFileName;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getFilePath() {
        return mFilePath;
    }

    public String getReferer() {
        return mReferer;
    }

    public long getContentLength() {
        return mContentLength;
    }

    public boolean isGETRequest() {
        return mIsGETRequest;
    }

    public boolean hasDownloadId() {
        return mHasDownloadId;
    }

    public int getDownloadId() {
        return mDownloadId;
    }

    public boolean isSuccessful() {
        return mIsSuccessful;
    }

    public String getContentDisposition() {
        return mContentDisposition;
    }

    /**
     * @return percent completed as an integer, -1 if there is no download progress.
     */
    public int getPercentCompleted() {
        return mPercentCompleted;
    }

    public long getTimeRemainingInMillis() {
        return mTimeRemainingInMillis;
    }

    public static class Builder {
        private String mUrl;
        private String mUserAgent;
        private String mMimeType;
        private String mCookie;
        private String mFileName;
        private String mDescription;
        private String mFilePath;
        private String mReferer;
        private long mContentLength;
        private boolean mIsGETRequest;
        private boolean mHasDownloadId;
        private int mDownloadId;
        private boolean mIsSuccessful;
        private String mContentDisposition;
        private int mPercentCompleted = -1;
        private long mTimeRemainingInMillis;

        public Builder setUrl(String url) {
            mUrl = url;
            return this;
        }

        public Builder setUserAgent(String userAgent) {
            mUserAgent = userAgent;
            return this;
        }

        public Builder setMimeType(String mimeType) {
            mMimeType = mimeType;
            return this;
        }

        public Builder setCookie(String cookie) {
            mCookie = cookie;
            return this;
        }

        public Builder setFileName(String fileName) {
            mFileName = fileName;
            return this;
        }

        public Builder setDescription(String description) {
            mDescription = description;
            return this;
        }

        public Builder setFilePath(String filePath) {
            mFilePath = filePath;
            return this;
        }

        public Builder setReferer(String referer) {
            mReferer = referer;
            return this;
        }

        public Builder setContentLength(long contentLength) {
            mContentLength = contentLength;
            return this;
        }

        public Builder setIsGETRequest(boolean isGETRequest) {
            mIsGETRequest = isGETRequest;
            return this;
        }

        public Builder setHasDownloadId(boolean hasDownloadId) {
            mHasDownloadId = hasDownloadId;
            return this;
        }

        public Builder setDownloadId(int downloadId) {
            mDownloadId = downloadId;
            return this;
        }

        public Builder setIsSuccessful(boolean isSuccessful) {
            mIsSuccessful = isSuccessful;
            return this;
        }

        public Builder setContentDisposition(String contentDisposition) {
            mContentDisposition = contentDisposition;
            return this;
        }

        public Builder setPercentCompleted(int percentCompleted) {
            assert percentCompleted <= 100;
            mPercentCompleted = percentCompleted;
            return this;
        }

        public Builder setTimeRemainingInMillis(long timeRemainingInMillis) {
            mTimeRemainingInMillis = timeRemainingInMillis;
            return this;
        }

        public DownloadInfo build() {
            return new DownloadInfo(this);
        }

        /**
         * Create a builder from the DownloadInfo object.
         * @param downloadInfo DownloadInfo object from which builder fields are populated.
         * @return A builder initialized with fields from downloadInfo object.
         */
        public static Builder fromDownloadInfo(final DownloadInfo downloadInfo) {
            Builder builder = new Builder();
            builder
                    .setUrl(downloadInfo.getUrl())
                    .setUserAgent(downloadInfo.getUserAgent())
                    .setMimeType(downloadInfo.getMimeType())
                    .setCookie(downloadInfo.getCookie())
                    .setFileName(downloadInfo.getFileName())
                    .setDescription(downloadInfo.getDescription())
                    .setFilePath(downloadInfo.getFilePath())
                    .setReferer(downloadInfo.getReferer())
                    .setContentLength(downloadInfo.getContentLength())
                    .setHasDownloadId(downloadInfo.hasDownloadId())
                    .setDownloadId(downloadInfo.getDownloadId())
                    .setContentDisposition(downloadInfo.getContentDisposition())
                    .setIsGETRequest(downloadInfo.isGETRequest())
                    .setIsSuccessful(downloadInfo.isSuccessful())
                    .setPercentCompleted(downloadInfo.getPercentCompleted())
                    .setTimeRemainingInMillis(downloadInfo.getTimeRemainingInMillis());
            return builder;
        }

    }
}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.base.PathUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java counterpart of android MediaResourceGetter.
 */
@JNINamespace("content")
class MediaResourceGetter {

    private static final String TAG = "MediaResourceGetter";
    private final MediaMetadata EMPTY_METADATA = new MediaMetadata(0,0,0,false);

    private final MediaMetadataRetriever mRetriever = new MediaMetadataRetriever();

    @VisibleForTesting
    static class MediaMetadata {
        private final int mDurationInMilliseconds;
        private final int mWidth;
        private final int mHeight;
        private final boolean mSuccess;

        MediaMetadata(int durationInMilliseconds, int width, int height, boolean success) {
            mDurationInMilliseconds = durationInMilliseconds;
            mWidth = width;
            mHeight = height;
            mSuccess = success;
        }

        // TODO(andrewhayden): according to the spec, if duration is unknown
        // then we must return NaN. If it is unbounded, then positive infinity.
        // http://www.w3.org/html/wg/drafts/html/master/embedded-content-0.html
        @CalledByNative("MediaMetadata")
        int getDurationInMilliseconds() { return mDurationInMilliseconds; }

        @CalledByNative("MediaMetadata")
        int getWidth() { return mWidth; }

        @CalledByNative("MediaMetadata")
        int getHeight() { return mHeight; }

        @CalledByNative("MediaMetadata")
        boolean isSuccess() { return mSuccess; }

        @Override
        public String toString() {
            return "MediaMetadata["
                    + "durationInMilliseconds=" + mDurationInMilliseconds
                    + ", width=" + mWidth
                    + ", height=" + mHeight
                    + ", success=" + mSuccess
                    + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + mDurationInMilliseconds;
            result = prime * result + mHeight;
            result = prime * result + (mSuccess ? 1231 : 1237);
            result = prime * result + mWidth;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            MediaMetadata other = (MediaMetadata)obj;
            if (mDurationInMilliseconds != other.mDurationInMilliseconds)
                return false;
            if (mHeight != other.mHeight)
                return false;
            if (mSuccess != other.mSuccess)
                return false;
            if (mWidth != other.mWidth)
                return false;
            return true;
        }
    }

    @CalledByNative
    private static MediaMetadata extractMediaMetadata(final Context context,
                                                      final String url,
                                                      final String cookies,
                                                      final String userAgent) {
        return new MediaResourceGetter().extract(
                context, url, cookies, userAgent);
    }

    @CalledByNative
    private static MediaMetadata extractMediaMetadataFromFd(int fd,
                                                            long offset,
                                                            long length) {
        return new MediaResourceGetter().extract(fd, offset, length);
    }

    @VisibleForTesting
    MediaMetadata extract(int fd, long offset, long length) {
        if (!androidDeviceOk(android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT)) {
            return EMPTY_METADATA;
        }

        configure(fd, offset, length);
        return doExtractMetadata();
    }

    @VisibleForTesting
    MediaMetadata extract(final Context context, final String url,
                          final String cookies, final String userAgent) {
        if (!androidDeviceOk(android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT)) {
            return EMPTY_METADATA;
        }

        if (!configure(context, url, cookies, userAgent)) {
            Log.e(TAG, "Unable to configure metadata extractor");
            return EMPTY_METADATA;
        }
        return doExtractMetadata();
    }

    private MediaMetadata doExtractMetadata() {
        try {
            String durationString = extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationString == null) {
                Log.w(TAG, "missing duration metadata");
                return EMPTY_METADATA;
            }

            int durationMillis = 0;
            try {
                durationMillis = Integer.parseInt(durationString);
            } catch (NumberFormatException e) {
                Log.w(TAG, "non-numeric duration: " + durationString);
                return EMPTY_METADATA;
            }

            int width = 0;
            int height = 0;
            boolean hasVideo = "yes".equals(extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
            Log.d(TAG, (hasVideo ? "resource has video" : "resource doesn't have video"));
            if (hasVideo) {
                String widthString = extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                if (widthString == null) {
                    Log.w(TAG, "missing video width metadata");
                    return EMPTY_METADATA;
                }
                try {
                    width = Integer.parseInt(widthString);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "non-numeric width: " + widthString);
                    return EMPTY_METADATA;
                }

                String heightString = extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                if (heightString == null) {
                    Log.w(TAG, "missing video height metadata");
                    return EMPTY_METADATA;
                }
                try {
                    height = Integer.parseInt(heightString);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "non-numeric height: " + heightString);
                    return EMPTY_METADATA;
                }
            }
            MediaMetadata result = new MediaMetadata(durationMillis, width, height, true);
            Log.d(TAG, "extracted valid metadata: " + result.toString());
            return result;
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to extract medata", e);
            return EMPTY_METADATA;
        }
    }

    @VisibleForTesting
    boolean configure(Context context, String url, String cookies, String userAgent) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException  e) {
            Log.e(TAG, "Cannot parse uri.", e);
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("file")) {
            File file = uriToFile(uri.getPath());
            if (!file.exists()) {
                Log.e(TAG, "File does not exist.");
                return false;
            }
            if (!filePathAcceptable(file)) {
                Log.e(TAG, "Refusing to read from unsafe file location.");
                return false;
            }
            try {
                configure(file.getAbsolutePath());
                return true;
            } catch (RuntimeException e) {
                Log.e(TAG, "Error configuring data source", e);
                return false;
            }
        } else {
            final String host = uri.getHost();
            if (!isLoopbackAddress(host) && !isNetworkReliable(context)) {
                Log.w(TAG, "non-file URI can't be read due to unsuitable network conditions");
                return false;
            }
            Map<String, String> headersMap = new HashMap<String, String>();
            if (!TextUtils.isEmpty(cookies)) {
                headersMap.put("Cookie", cookies);
            }
            if (!TextUtils.isEmpty(userAgent)) {
                headersMap.put("User-Agent", userAgent);
            }
            try {
                configure(url, headersMap);
                return true;
            } catch (RuntimeException e) {
                Log.e(TAG, "Error configuring data source", e);
                return false;
            }
        }
    }

    /**
     * @return true if the device is on an ethernet or wifi network.
     * If anything goes wrong (e.g., permission denied while trying to access
     * the network state), returns false.
     */
    @VisibleForTesting
    boolean isNetworkReliable(Context context) {
        if (context.checkCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_NETWORK_STATE) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "permission denied to access network state");
            return false;
        }

        Integer networkType = getNetworkType(context);
        if (networkType == null) {
            return false;
        }
        switch (networkType.intValue()) {
            case ConnectivityManager.TYPE_ETHERNET:
            case ConnectivityManager.TYPE_WIFI:
                Log.d(TAG, "ethernet/wifi connection detected");
                return true;
            case ConnectivityManager.TYPE_WIMAX:
            case ConnectivityManager.TYPE_MOBILE:
            default:
                Log.d(TAG, "no ethernet/wifi connection detected");
                return false;
        }
    }

    // This method covers only typcial expressions for the loopback address
    // to resolve the hostname without a DNS loopup.
    private boolean isLoopbackAddress(String host) {
        return host != null && (host.equalsIgnoreCase("localhost")  // typical hostname
                || host.equals("127.0.0.1")  // typical IP v4 expression
                || host.equals("[::1]"));  // typical IP v6 expression
    }

    /**
     * @param file the file whose path should be checked
     * @return true if and only if the file is in a location that we consider
     * safe to read from, such as /mnt/sdcard.
     */
    @VisibleForTesting
    boolean filePathAcceptable(File file) {
        final String path;
        try {
            path = file.getCanonicalPath();
        } catch (IOException e) {
            // Canonicalization has failed. Assume malicious, give up.
            Log.w(TAG, "canonicalization of file path failed");
            return false;
        }
        // In order to properly match the roots we must also canonicalize the
        // well-known paths we are matching against. If we don't, then we can
        // get unusual results in testing systems or possibly on rooted devices.
        // Note that canonicalized directory paths always end with '/'.
        List<String> acceptablePaths = canonicalize(getRawAcceptableDirectories());
        acceptablePaths.add(getExternalStorageDirectory());
        Log.d(TAG, "canonicalized file path: " + path);
        for (String acceptablePath : acceptablePaths) {
            if (path.startsWith(acceptablePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Special case handling for device/OS combos that simply do not work.
     * @param model the model of device being examined
     * @param sdkVersion the version of the SDK installed on the device
     * @return true if the device can be used correctly, otherwise false
     */
    @VisibleForTesting
    static boolean androidDeviceOk(final String model, final int sdkVersion) {
        return !("GT-I9100".contentEquals(model) &&
                 sdkVersion < android.os.Build.VERSION_CODES.JELLY_BEAN);
    }

    // The methods below can be used by unit tests to fake functionality.
    @VisibleForTesting
    File uriToFile(String path) {
        return new File(path);
    }

    @VisibleForTesting
    Integer getNetworkType(Context context) {
        // TODO(qinmin): use ConnectionTypeObserver to listen to the network type change.
        ConnectivityManager mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (mConnectivityManager == null) {
            Log.w(TAG, "no connectivity manager available");
            return null;
        }
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        if (info == null) {
            Log.d(TAG, "no active network");
            return null;
        }
        return info.getType();
    }

    private List<String> getRawAcceptableDirectories() {
        List<String> result = new ArrayList<String>();
        result.add("/mnt/sdcard/");
        result.add("/sdcard/");
        return result;
    }

    private List<String> canonicalize(List<String> paths) {
        List<String> result = new ArrayList<String>(paths.size());
        try {
            for (String path : paths) {
                result.add(new File(path).getCanonicalPath());
            }
            return result;
        } catch (IOException e) {
            // Canonicalization has failed. Assume malicious, give up.
            Log.w(TAG, "canonicalization of file path failed");
        }
        return result;
    }

    @VisibleForTesting
    String getExternalStorageDirectory() {
        return PathUtils.getExternalStorageDirectory();
    }

    @VisibleForTesting
    void configure(int fd, long offset, long length) {
        ParcelFileDescriptor parcelFd = ParcelFileDescriptor.adoptFd(fd);
        try {
            mRetriever.setDataSource(parcelFd.getFileDescriptor(),
                    offset, length);
        } finally {
            try {
                parcelFd.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close file descriptor: " + e);
            }
        }
    }

    @VisibleForTesting
    void configure(String url, Map<String,String> headers) {
        mRetriever.setDataSource(url, headers);
    }

    @VisibleForTesting
    void configure(String path) {
        mRetriever.setDataSource(path);
    }

    @VisibleForTesting
    String extractMetadata(int key) {
        return mRetriever.extractMetadata(key);
    }
}

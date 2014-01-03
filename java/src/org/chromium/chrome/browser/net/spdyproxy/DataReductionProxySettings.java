// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.net.spdyproxy;

import org.chromium.base.CalledByNative;
import org.chromium.base.ThreadUtils;

public class DataReductionProxySettings {

    public static class ContentLengths {
        private final long mOriginal;
        private final long mReceived;

        @CalledByNative("ContentLengths")
        public static ContentLengths create(long original, long received) {
            return new ContentLengths(original, received);
        }

        private ContentLengths(long original, long received) {
            mOriginal = original;
            mReceived = received;
        }

        public long getOriginal() {
            return mOriginal;
        }

        public long getReceived() {
            return mReceived;
        }
    }

    private static DataReductionProxySettings sSettings;

    public static DataReductionProxySettings getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sSettings == null) {
            sSettings = new DataReductionProxySettings();
        }
        return sSettings;
    }

    private final int mNativeDataReductionProxySettings;

    private DataReductionProxySettings() {
        // Note that this technically leaks the native object, however,
        // DataReductionProxySettings is a singleton that lives forever and there's no clean
        // shutdown of Chrome on Android
        mNativeDataReductionProxySettings = nativeInit();
        initDataReductionProxySettings();
    }

    /**
     * Initializes the data reduction proxy at Chrome startup.
     */
    public void initDataReductionProxySettings() {
        nativeInitDataReductionProxySettings(mNativeDataReductionProxySettings);
    }

    /**
     * Add a pattern for hosts to bypass. Wildcards should be compatible with the JavaScript
     * function shExpMatch, which can be used in proxy PAC resolution. This should be called
     * before the proxy is used.
     * @param pattern A pattern to match.
     */
    public void bypassHostPattern(String pattern) {
        nativeBypassHostPattern(mNativeDataReductionProxySettings, pattern);
    }

    /**
     * Add a pattern for URLs to bypass. Wildcards should be compatible with the JavaScript
     * function shExpMatch, which can be used in proxy PAC resolution. This should be called
     * before the proxy is used.
     * @param pattern A pattern to match.
     */
    public void bypassURLPattern(String pattern) {
        nativeBypassURLPattern(mNativeDataReductionProxySettings, pattern);
    }


    /** Returns true if the SPDY proxy is allowed to be used. */
    public boolean isDataReductionProxyAllowed() {
        return nativeIsDataReductionProxyAllowed(mNativeDataReductionProxySettings);
    }

    /** Returns true if the SPDY proxy promo is allowed to be shown. */
    public boolean isDataReductionProxyPromoAllowed() {
        return nativeIsDataReductionProxyPromoAllowed(mNativeDataReductionProxySettings);
    }

    /**
     * Returns the current data reduction proxy origin.
     */
    public String getDataReductionProxyOrigin() {
        return nativeGetDataReductionProxyOrigin(mNativeDataReductionProxySettings);
    }

    /**
     * Sets the preference on whether to enable/disable the SPDY proxy. This will zero out the
     * data reduction statistics if this is the first time the SPDY proxy has been enabled.
     */
    public void setDataReductionProxyEnabled(boolean enabled) {
        nativeSetDataReductionProxyEnabled(mNativeDataReductionProxySettings, enabled);
    }

    /** Returns true if the SPDY proxy is enabled. */
    public boolean isDataReductionProxyEnabled() {
        return nativeIsDataReductionProxyEnabled(mNativeDataReductionProxySettings);
    }

    /** Returns true if the SPDY proxy is managed by an administrator's policy. */
    public boolean isDataReductionProxyManaged() {
        return nativeIsDataReductionProxyManaged(mNativeDataReductionProxySettings);
    }

    /**
     * Returns the time that the data reduction statistics were last updated.
     * @return The last update time in milliseconds since the epoch.
     */
    public long getDataReductionLastUpdateTime()  {
        return nativeGetDataReductionLastUpdateTime(mNativeDataReductionProxySettings);
    }

    /**
     * Returns aggregate original and received content lengths.
     * @return The content lengths.
     */
    public ContentLengths getContentLengths() {
        return nativeGetContentLengths(mNativeDataReductionProxySettings);
    }

    /**
     * Returns true if the host and realm (as passed in to Tab.onReceivedHttpAuthRequest()) are such
     * that a authentication token can be generated. The host must match one of the configured proxy
     * hosts, and the realm must be prefixed with the authentication realm string used by the data
     * reduction proxies.
     * @param host The host requesting authentication.
     * @param realm The authentication realm.
     * @return True if host and realm can be authenticated.
     */
    public boolean isAcceptableAuthChallenge(String host, String realm) {
        return nativeIsAcceptableAuthChallenge(mNativeDataReductionProxySettings, host, realm);
    }

    /**
     * Returns an authentication token for the data reduction proxy. If the token cannot be
     * generated, an empty string is returned.
     * @param host The host requesting authentication.
     * @param realm The authentication realm.
     * @return The generated token.
     */
    public String getTokenForAuthChallenge(String host, String realm) {
        return nativeGetTokenForAuthChallenge(mNativeDataReductionProxySettings, host, realm);
    }

    /**
     * Retrieves the history of daily totals of bytes that would have been
     * received if no data reducing mechanism had been applied.
     * @return The history of daily totals
     */
    public long[] getOriginalNetworkStatsHistory() {
        return nativeGetDailyOriginalContentLengths(mNativeDataReductionProxySettings);
    }

    /**
     * Retrieves the history of daily totals of bytes that were received after
     * applying a data reducing mechanism.
     * @return The history of daily totals
     */
    public long[] getReceivedNetworkStatsHistory() {
        return nativeGetDailyReceivedContentLengths(mNativeDataReductionProxySettings);
    }

    /**
     * @return The data reduction settings as a string percentage.
     */
    public String getContentLengthPercentSavings() {
        ContentLengths length = getContentLengths();
        String percent = "0%";
        if (length.getOriginal() > 0L  && length.getOriginal() > length.getReceived()) {
            percent = String.format(
                    "%.0f%%", 100.0 *
                    (length.getOriginal() - length.getReceived()) / length.getOriginal());
        }
        return percent;
    }

    private native int nativeInit();
    private native void nativeInitDataReductionProxySettings(
            int nativeDataReductionProxySettingsAndroid);
    private native void nativeBypassHostPattern(
            int nativeDataReductionProxySettingsAndroid, String pattern);
    private native void nativeBypassURLPattern(
            int nativeDataReductionProxySettingsAndroid, String pattern);
    private native boolean nativeIsDataReductionProxyAllowed(
            int nativeDataReductionProxySettingsAndroid);
    private native boolean nativeIsDataReductionProxyPromoAllowed(
            int nativeDataReductionProxySettingsAndroid);
    private native String nativeGetDataReductionProxyOrigin(
            int nativeDataReductionProxySettingsAndroid);
    private native boolean nativeIsDataReductionProxyEnabled(
            int nativeDataReductionProxySettingsAndroid);
    private native boolean nativeIsDataReductionProxyManaged(
            int nativeDataReductionProxySettingsAndroid);
    private native void nativeSetDataReductionProxyEnabled(
            int nativeDataReductionProxySettingsAndroid, boolean enabled);
    private native long nativeGetDataReductionLastUpdateTime(
            int nativeDataReductionProxySettingsAndroid);
    private native ContentLengths nativeGetContentLengths(
            int nativeDataReductionProxySettingsAndroid);
    private native boolean nativeIsAcceptableAuthChallenge(
            int nativeDataReductionProxySettingsAndroid, String host, String realm);
    private native String nativeGetTokenForAuthChallenge(
            int nativeDataReductionProxySettingsAndroid, String host, String realm);
    private native long[] nativeGetDailyOriginalContentLengths(
            int nativeDataReductionProxySettingsAndroid);
    private native long[] nativeGetDailyReceivedContentLengths(
            int nativeDataReductionProxySettingsAndroid);
}

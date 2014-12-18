// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.base;

import android.content.res.Configuration;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.util.Locale;

/**
 * This class provides the locale related methods for the native library.
 */
@JNINamespace("l10n_util")
public class LocalizationUtils {

    // This is mirrored from base/i18n/rtl.h. Please keep in sync.
    public static final int UNKNOWN_DIRECTION = 0;
    public static final int RIGHT_TO_LEFT = 1;
    public static final int LEFT_TO_RIGHT = 2;

    private static Boolean sIsLayoutRtl;

    private LocalizationUtils() { /* cannot be instantiated */ }

    /**
     * @return the default locale, translating Android deprecated
     * language codes into the modern ones used by Chromium.
     */
    @CalledByNative
    public static String getDefaultLocale() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        String country = locale.getCountry();

        // Android uses deprecated lanuages codes for Hebrew and Indonesian but Chromium uses the
        // updated codes. Also, Android uses "tl" while Chromium uses "fil" for Tagalog/Filipino.
        // So apply a mapping.
        // See http://developer.android.com/reference/java/util/Locale.html
        if ("iw".equals(language)) {
            language = "he";
        } else if ("in".equals(language)) {
            language = "id";
        } else if ("tl".equals(language)) {
            language = "fil";
        }
        return country.isEmpty() ? language : language + "-" + country;
    }

    @CalledByNative
    private static Locale getJavaLocale(String language, String country, String variant) {
        return new Locale(language, country, variant);
    }

    @CalledByNative
    private static String getDisplayNameForLocale(Locale locale, Locale displayLocale) {
        return locale.getDisplayName(displayLocale);
    }

    /**
     * Returns whether the Android layout direction is RTL.
     *
     * Note that the locale direction can be different from layout direction. Two known cases:
     * - RTL languages on Android 4.1, due to the lack of RTL layout support on 4.1.
     * - When user turned on force RTL layout option under developer options.
     *
     * Therefore, only this function should be used to query RTL for layout purposes.
     */
    @CalledByNative
    public static boolean isLayoutRtl() {
        if (sIsLayoutRtl == null) {
            Configuration configuration =
                    ApplicationStatus.getApplicationContext().getResources().getConfiguration();
            sIsLayoutRtl = Boolean.valueOf(
                    ApiCompatibilityUtils.getLayoutDirection(configuration) ==
                    View.LAYOUT_DIRECTION_RTL);
        }

        return sIsLayoutRtl.booleanValue();
    }

    /**
     * Jni binding to base::i18n::GetFirstStrongCharacterDirection
     * @param string String to decide the direction.
     * @return One of the UNKNOWN_DIRECTION, RIGHT_TO_LEFT, and LEFT_TO_RIGHT.
     */
    public static int getFirstStrongCharacterDirection(String string) {
        return nativeGetFirstStrongCharacterDirection(string);
    }

    /**
     * Jni binding to ui::TimeFormat::TimeRemaining. Converts milliseconds to
     * time remaining format : "3 mins left", "2 days left".
     * @param timeInMillis time in milliseconds
     * @return time remaining
     */
    public static String getDurationString(long timeInMillis) {
        return nativeGetDurationString(timeInMillis);
    }

    private static native int nativeGetFirstStrongCharacterDirection(String string);

    private static native String nativeGetDurationString(long timeInMillis);
}

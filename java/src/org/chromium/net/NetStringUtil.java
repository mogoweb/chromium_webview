// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.text.Normalizer;

/**
 * Utility functions for converting strings between formats when not built with
 * icu.
 */
@JNINamespace("net::android")
public class NetStringUtil {
    /**
     * Attempts to convert text in a given character set to a Unicode string.
     * Returns null on failure.
     * @param text ByteBuffer containing the character array to convert.
     * @param charset Character set it's in encoded in.
     * @return: Unicode string on success, null on failure.
     */
    @CalledByNative
    private static String convertToUnicode(
            ByteBuffer text,
            String charset_name) {
        try {
            Charset charset = Charset.forName(charset_name);
            CharsetDecoder decoder = charset.newDecoder();
            // On invalid characters, this will throw an exception.
            return decoder.decode(text).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempts to convert text in a given character set to a Unicode string,
     * and normalize it.  Returns null on failure.
     * @param text ByteBuffer containing the character array to convert.
     * @param charset Character set it's in encoded in.
     * @return: Unicode string on success, null on failure.
     */
    @CalledByNative
    private static String convertToUnicodeAndNormalize(
            ByteBuffer text,
            String charset_name) {
        String unicodeString = convertToUnicode(text, charset_name);
        if (unicodeString == null)
            return unicodeString;
        return Normalizer.normalize(unicodeString, Normalizer.Form.NFC);
    }

    /**
     * Convert text in a given character set to a Unicode string.  Any invalid
     * characters are replaced with U+FFFD.  Returns null if the character set
     * is not recognized.
     * @param text ByteBuffer containing the character array to convert.
     * @param charset Character set it's in encoded in.
     * @return: Unicode string on success, null on failure.
     */
    @CalledByNative
    private static String convertToUnicodeWithSubstitutions(
            ByteBuffer text,
            String charset_name) {
        try {
            Charset charset = Charset.forName(charset_name);

            // TODO(mmenke):  Investigate if Charset.decode() can be used
            // instead.  The question is whether it uses the proper replace
            // character.  JDK CharsetDecoder docs say U+FFFD is the default,
            // but Charset.decode() docs say it uses the "charset's default
            // replacement byte array".
            CharsetDecoder decoder = charset.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPLACE);
            decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
            decoder.replaceWith("\uFFFD");
            return decoder.decode(text).toString();
        } catch (Exception e) {
            return null;
        }
    }
}

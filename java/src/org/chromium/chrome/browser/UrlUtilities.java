// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.text.TextUtils;

import org.chromium.base.CollectionUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;

/**
 * Utilities for working with URIs (and URLs). These methods may be used in security-sensitive
 * contexts (after all, origins are the security boundary on the web), and so the correctness bar
 * must be high.
 */
public class UrlUtilities {
    /**
     * URI schemes that ContentView can handle.
     */
    private static final HashSet<String> ACCEPTED_SCHEMES = CollectionUtil.newHashSet(
        "about", "data", "file", "http", "https", "inline", "javascript");

    /**
     * URI schemes that Chrome can download.
     */
    private static final HashSet<String> DOWNLOADABLE_SCHEMES = CollectionUtil.newHashSet(
        "data", "filesystem", "http", "https");

    /**
     * @param uri A URI.
     *
     * @return True if the URI's scheme is one that ContentView can handle.
     */
    public static boolean isAcceptedScheme(URI uri) {
        return ACCEPTED_SCHEMES.contains(uri.getScheme());
    }

    /**
     * @param uri A URI.
     *
     * @return True if the URI's scheme is one that ContentView can handle.
     */
    public static boolean isAcceptedScheme(String uri) {
        try {
            return isAcceptedScheme(new URI(uri));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * @param uri A URI.
     *
     * @return True if the URI's scheme is one that Chrome can download.
     */
    public static boolean isDownloadableScheme(URI uri) {
        return DOWNLOADABLE_SCHEMES.contains(uri.getScheme());
    }

    /**
     * @param uri A URI.
     *
     * @return True if the URI's scheme is one that Chrome can download.
     */
    public static boolean isDownloadableScheme(String uri) {
        try {
            return isDownloadableScheme(new URI(uri));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * @param uri A URI to repair.
     *
     * @return A String representation of a URI that will be valid for loading in a ContentView.
     */
    public static String fixUrl(String uri) {
        if (uri == null) return null;

        try {
            String fixedUri = uri.trim();
            if (fixedUri.indexOf("://") == 0) {
                return "http" + fixedUri;
            }
            if (fixedUri.indexOf(":") == -1) {
                return "http://" + fixedUri;
            }

            URI parsed = new URI(fixedUri);
            if (parsed.getScheme() == null) {
                parsed = new URI(
                        "http",
                        null,  // userInfo
                        parsed.getHost(),
                        parsed.getPort(),
                        parsed.getRawPath(),
                        parsed.getRawQuery(),
                        parsed.getRawFragment());
            }
            return parsed.toString();
        } catch (URISyntaxException e) {
            // Can't do anything.
            return uri;
        }
    }

    /**
     * Builds a String that strips down the URL to the its scheme, host, and port.
     * @param uri URI to break down.
     * @param showScheme Whether or not to show the scheme.  If the URL can't be parsed, this value
     *                   is ignored.
     * @return Stripped-down String containing the essential bits of the URL, or the original URL if
     *         it fails to parse it.
     */
    public static String getOriginForDisplay(URI uri, boolean showScheme) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        String displayUrl;
        if (TextUtils.isEmpty(scheme) || TextUtils.isEmpty(host)) {
            displayUrl = uri.toString();
        } else {
            if (showScheme) {
                scheme += "://";
            } else {
                scheme = "";
            }

            if (port == -1 || (port == 80 && "http".equals(scheme))
                    || (port == 443 && "https".equals(scheme))) {
                displayUrl = scheme + host;
            } else {
                displayUrl = scheme + host + ":" + port;
            }
        }

        return displayUrl;
    }

    /**
     * Determines whether or not the given URLs belong to the same broad domain or host.
     * "Broad domain" is defined as the TLD + 1 or the host.
     *
     * For example, the TLD + 1 for http://news.google.com would be "google.com" and would be shared
     * with other Google properties like http://finance.google.com.
     *
     * If {@code includePrivateRegistries} is marked as true, then private domain registries (like
     * appspot.com) are considered "effective TLDs" -- all subdomains of appspot.com would be
     * considered distinct (effective TLD = ".appspot.com" + 1).
     * This means that http://chromiumreview.appspot.com and http://example.appspot.com would not
     * belong to the same host.
     * If {@code includePrivateRegistries} is false, all subdomains of appspot.com
     * would be considered to be the same domain (TLD = ".com" + 1).
     *
     * @param primaryUrl First URL
     * @param secondaryUrl Second URL
     * @param includePrivateRegistries Whether or not to consider private registries.
     * @return True iff the two URIs belong to the same domain or host.
     */
    public static boolean sameDomainOrHost(String primaryUrl, String secondaryUrl,
            boolean includePrivateRegistries) {
        return nativeSameDomainOrHost(primaryUrl, secondaryUrl, includePrivateRegistries);
    }

    /**
     * This function works by calling net::registry_controlled_domains::GetDomainAndRegistry
     *
     * @param uri A URI
     * @param includePrivateRegistries Whether or not to consider private registries.
     *
     * @return The registered, organization-identifying host and all its registry information, but
     * no subdomains, from the given URI. Returns an empty string if the URI is invalid, has no host
     * (e.g. a file: URI), has multiple trailing dots, is an IP address, has only one subcomponent
     * (i.e. no dots other than leading/trailing ones), or is itself a recognized registry
     * identifier.
     */
    public static String getDomainAndRegistry(String uri, boolean includePrivateRegistries) {
        return nativeGetDomainAndRegistry(uri, includePrivateRegistries);
    }

    private static native boolean nativeSameDomainOrHost(String primaryUrl, String secondaryUrl,
            boolean includePrivateRegistries);
    private static native String nativeGetDomainAndRegistry(String url,
            boolean includePrivateRegistries);
    public static native boolean nativeIsGoogleSearchUrl(String url);
    public static native boolean nativeIsGoogleHomePageUrl(String url);
}

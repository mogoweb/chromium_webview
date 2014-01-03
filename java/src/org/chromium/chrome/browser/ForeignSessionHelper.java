// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.CalledByNative;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class exposes to Java information about sessions, windows, and tabs on the user's synced
 * devices.
 */
public class ForeignSessionHelper {
    private int mNativeForeignSessionHelper;

    /**
     * Callback interface for getting notified when foreign session sync is updated.
     */
    public interface ForeignSessionCallback {
        /**
         * This method will be called every time foreign session sync is updated.
         *
         * It's a good place to call {@link ForeignSessionHelper#getForeignSessions()} to get the
         * updated information.
         */
        @CalledByNative("ForeignSessionCallback")
        public void onUpdated();
    }

    /**
     * Represents synced foreign session.
     */
    public static class ForeignSession {
        // Please keep in sync with synced_session.h
        public static final int DEVICE_TYPE_UNSET = 0;
        public static final int DEVICE_TYPE_WIN = 1;
        public static final int DEVICE_TYPE_MACOSX = 2;
        public static final int DEVICE_TYPE_LINUX = 3;
        public static final int DEVICE_TYPE_CHROMEOS = 4;
        public static final int DEVICE_TYPE_OTHER = 5;
        public static final int DEVICE_TYPE_PHONE = 6;
        public static final int DEVICE_TYPE_TABLET = 7;

        public final String tag;
        public final String name;
        public final int deviceType;
        public final long modifiedTime;
        public final List<ForeignSessionWindow> windows = new ArrayList<ForeignSessionWindow>();

        private ForeignSession(String tag, String name, int deviceType, long modifiedTime) {
            this.tag = tag;
            this.name = name;
            this.deviceType = deviceType;
            this.modifiedTime = modifiedTime;
        }
    }

    /**
     * Represents synced foreign window. Note that desktop Chrome can have multiple windows in a
     * session.
     */
    public static class ForeignSessionWindow {
        public final long timestamp;
        public final int sessionId;
        public final List<ForeignSessionTab> tabs = new ArrayList<ForeignSessionTab>();

        private ForeignSessionWindow(long timestamp, int sessionId) {
            this.timestamp = timestamp;
            this.sessionId = sessionId;
        }
    }

    /**
     * Represents synced foreign tab.
     */
    public static class ForeignSessionTab {
        public final String url;
        public final String title;
        public final long timestamp;
        public final int id;

        private ForeignSessionTab(String url, String title, long timestamp, int id) {
            this.url = url;
            this.title = title;
            this.timestamp = timestamp;
            this.id = id;
        }
    }

    @CalledByNative
    private static ForeignSession pushSession(
            List<ForeignSession> sessions, String tag, String name, int deviceType,
            long modifiedTime) {
        ForeignSession session = new ForeignSession(tag, name, deviceType, modifiedTime);
        sessions.add(session);
        return session;
    }

    @CalledByNative
    private static ForeignSessionWindow pushWindow(
            ForeignSession session, long timestamp, int sessionId) {
        ForeignSessionWindow window = new ForeignSessionWindow(timestamp, sessionId);
        session.windows.add(window);
        return window;
    }

    @CalledByNative
    private static void pushTab(
            ForeignSessionWindow window, String url, String title, long timestamp, int sessionId) {
        ForeignSessionTab tab = new ForeignSessionTab(url, title, timestamp, sessionId);
        window.tabs.add(tab);
    }

    /**
     * Initialize this class with the given profile.
     * @param profile Profile that will be used for syncing.
     */
    public ForeignSessionHelper(Profile profile) {
        mNativeForeignSessionHelper = nativeInit(profile);
    }

    /**
     * Clean up the C++ side of this class. After the call, this class instance shouldn't be used.
     */
    public void destroy() {
        assert mNativeForeignSessionHelper != 0;
        nativeDestroy(mNativeForeignSessionHelper);
    }

    @Override
    protected void finalize() {
        // Just to make sure that we called destroy() before the java garbage collection picks up.
        assert mNativeForeignSessionHelper == 0;
    }

    /**
     * @return {@code True} iff Tab sync is enabled.
     */
    public boolean isTabSyncEnabled() {
        return nativeIsTabSyncEnabled(mNativeForeignSessionHelper);
    }

    /**
     * Sets callback instance that will be called on every foreign session sync update.
     */
    public void setOnForeignSessionCallback(ForeignSessionCallback callback) {
        nativeSetOnForeignSessionCallback(mNativeForeignSessionHelper, callback);
    }

    /**
     * @return The list of synced foreign sessions. {@code null} iff it fails to get them for some
     *         reason.
     */
    public List<ForeignSession> getForeignSessions() {
        List<ForeignSession> result = new ArrayList<ForeignSession>();
        boolean received = nativeGetForeignSessions(mNativeForeignSessionHelper, result);
        if (received) {
            // Sort sessions from most recent to least recent.
            Collections.sort(result, new Comparator<ForeignSession>() {
                @Override
                public int compare(ForeignSession lhs, ForeignSession rhs) {
                    return lhs.modifiedTime < rhs.modifiedTime ? 1 :
                        (lhs.modifiedTime == rhs.modifiedTime ? 0: -1);
                }
            });
        } else {
            result = null;
        }

        return result;
    }

    /**
     * Opens the given foreign tab in a new tab.
     * @param session Session that the target tab belongs to.
     * @param tab     Target tab to open.
     * @return        {@code True} iff the tab is successfully opened.
     */
    public boolean openForeignSessionTab(ForeignSession session, ForeignSessionTab tab) {
        return nativeOpenForeignSessionTab(mNativeForeignSessionHelper, session.tag, tab.id);
    }

    /**
     * Set the given session collapsed or uncollapsed in preferences.
     * @param session     Session to set collapsed or uncollapsed.
     * @param isCollapsed {@code True} iff we want the session to be collapsed.
     */
    public void setForeignSessionCollapsed(ForeignSession session, boolean isCollapsed) {
        nativeSetForeignSessionCollapsed(mNativeForeignSessionHelper, session.tag, isCollapsed);
    }

    /**
     * Get the given session collapsed or uncollapsed state in preferences.
     * @param  session Session to fetch collapsed state.
     * @return         {@code True} if the session is collapsed, false if expanded.
     */
    public boolean getForeignSessionCollapsed(ForeignSession session) {
        return nativeGetForeignSessionCollapsed(mNativeForeignSessionHelper, session.tag);
    }

    /**
     * Remove Foreign session to display. Note that it will be reappear on the next sync.
     *
     * This is mainly for when user wants to delete very old session that won't be used or syned in
     * the future.
     * @param session Session to be deleted.
     */
    public void deleteForeignSession(ForeignSession session) {
        nativeDeleteForeignSession(mNativeForeignSessionHelper, session.tag);
    }

    private static native int nativeInit(Profile profile);
    private static native void nativeDestroy(int nativeForeignSessionHelper);
    private static native boolean nativeIsTabSyncEnabled(int nativeForeignSessionHelper);
    private static native void nativeSetOnForeignSessionCallback(
            int nativeForeignSessionHelper, ForeignSessionCallback callback);
    private static native boolean nativeGetForeignSessions(int nativeForeignSessionHelper,
            List<ForeignSession> resultSessions);
    private static native boolean nativeOpenForeignSessionTab(
            int nativeForeignSessionHelper, String sessionTag, int tabId);
    private static native void nativeSetForeignSessionCollapsed(
            int nativeForeignSessionHelper, String sessionTag, boolean isCollapsed);
    private static native boolean nativeGetForeignSessionCollapsed(
            int nativeForeignSessionHelper, String sessionTag);
    private static native void nativeDeleteForeignSession(
            int nativeForeignSessionHelper, String sessionTag);
}

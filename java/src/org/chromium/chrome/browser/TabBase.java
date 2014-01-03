// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;

import org.chromium.base.CalledByNative;
import org.chromium.base.ObserverList;
import org.chromium.chrome.browser.RepostFormWarningDialog;
import org.chromium.chrome.browser.infobar.AutoLoginProcessor;
import org.chromium.chrome.browser.infobar.InfoBarContainer;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.ui.toolbar.ToolbarModelSecurityLevel;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.NavigationClient;
import org.chromium.content.browser.NavigationHistory;
import org.chromium.content.browser.PageInfo;
import org.chromium.content.browser.WebContentsObserverAndroid;
import org.chromium.ui.WindowAndroid;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The basic Java representation of a tab.  Contains and manages a {@link ContentView}.
 *
 * TabBase provides common functionality for ChromiumTestshell's Tab as well as Chrome on Android's
 * tab. It's intended to be extended both on Java and C++, with ownership managed by the subclass.
 * Because of the inner-workings of JNI, the subclass is responsible for constructing the native
 * subclass which in turn constructs TabAndroid (the native counterpart to TabBase) which in turn
 * sets the native pointer for TabBase. The same is true for destruction. The Java subclass must be
 * destroyed which will call into the native subclass and finally lead to the destruction of the
 * parent classes.
 */
public abstract class TabBase implements NavigationClient {
    public static final int INVALID_TAB_ID = -1;

    /** Used for automatically generating tab ids. */
    private static final AtomicInteger sIdCounter = new AtomicInteger();

    private int mNativeTabAndroid;

    /** Unique id of this tab (within its container). */
    private final int mId;

    /** Whether or not this tab is an incognito tab. */
    private final boolean mIncognito;

    /** An Application {@link Context}.  Unlike {@link #mContext}, this is the only one that is
     * publicly exposed to help prevent leaking the {@link Activity}. */
    private final Context mApplicationContext;

    /** The {@link Context} used to create {@link View}s and other Android components.  Unlike
     * {@link #mApplicationContext}, this is not publicly exposed to help prevent leaking the
     * {@link Activity}. */
    private final Context mContext;

    /** Gives {@link TabBase} a way to interact with the Android window. */
    private final WindowAndroid mWindowAndroid;

    /** The current native page (e.g. chrome-native://newtab), or {@code null} if there is none. */
    private NativePage mNativePage;

    /** The {@link ContentView} showing the current page or {@code null} if the tab is frozen. */
    private ContentView mContentView;

    /** InfoBar container to show InfoBars for this tab. */
    private InfoBarContainer mInfoBarContainer;

    /** The sync id of the TabBase if session sync is enabled. */
    private int mSyncId;

    /**
     * The {@link ContentViewCore} for the current page, provided for convenience. This always
     * equals {@link ContentView#getContentViewCore()}, or {@code null} if mContentView is
     * {@code null}.
     */
    private ContentViewCore mContentViewCore;

    /**
     * A list of TabBase observers.  These are used to broadcast TabBase events to listeners.
     */
    private final ObserverList<TabObserver> mObservers = new ObserverList<TabObserver>();

    // Content layer Observers and Delegates
    private ContentViewClient mContentViewClient;
    private WebContentsObserverAndroid mWebContentsObserver;
    private TabBaseChromeWebContentsDelegateAndroid mWebContentsDelegate;

    /**
     * A basic {@link ChromeWebContentsDelegateAndroid} that forwards some calls to the registered
     * {@link TabObserver}s.  Meant to be overridden by subclasses.
     */
    public class TabBaseChromeWebContentsDelegateAndroid
            extends ChromeWebContentsDelegateAndroid {
        @Override
        public void onLoadProgressChanged(int progress) {
            for (TabObserver observer : mObservers) {
                observer.onLoadProgressChanged(TabBase.this, progress);
            }
        }

        @Override
        public void onUpdateUrl(String url) {
            for (TabObserver observer : mObservers) observer.onUpdateUrl(TabBase.this, url);
        }

        @Override
        public void showRepostFormWarningDialog(final ContentViewCore contentViewCore) {
            RepostFormWarningDialog warningDialog = new RepostFormWarningDialog(
                    new Runnable() {
                        @Override
                        public void run() {
                            contentViewCore.cancelPendingReload();
                        }
                    }, new Runnable() {
                        @Override
                        public void run() {
                            contentViewCore.continuePendingReload();
                        }
                    });
            Activity activity = (Activity)mContext;
            warningDialog.show(activity.getFragmentManager(), null);
        }

        @Override
        public void toggleFullscreenModeForTab(boolean enableFullscreen) {
            for (TabObserver observer: mObservers) {
                observer.onToggleFullscreenMode(TabBase.this, enableFullscreen);
            }
        }
    }

    private class TabBaseWebContentsObserverAndroid extends WebContentsObserverAndroid {
        public TabBaseWebContentsObserverAndroid(ContentViewCore contentViewCore) {
            super(contentViewCore);
        }

        @Override
        public void navigationEntryCommitted() {
            if (getNativePage() != null) {
                pushNativePageStateToNavigationEntry();
            }
        }

        @Override
        public void didFailLoad(boolean isProvisionalLoad, boolean isMainFrame, int errorCode,
                String description, String failingUrl) {
            for (TabObserver observer : mObservers) {
                observer.onDidFailLoad(TabBase.this, isProvisionalLoad, isMainFrame, errorCode,
                        description, failingUrl);
            }
        }
    }

    /**
     * Creates an instance of a {@link TabBase} with no id.
     * @param incognito Whether or not this tab is incognito.
     * @param context   An instance of a {@link Context}.
     * @param window    An instance of a {@link WindowAndroid}.
     */
    public TabBase(boolean incognito, Context context, WindowAndroid window) {
        this(INVALID_TAB_ID, incognito, context, window);
    }

    /**
     * Creates an instance of a {@link TabBase}.
     * @param id        The id this tab should be identified with.
     * @param incognito Whether or not this tab is incognito.
     * @param context   An instance of a {@link Context}.
     * @param window    An instance of a {@link WindowAndroid}.
     */
    public TabBase(int id, boolean incognito, Context context, WindowAndroid window) {
        // We need a valid Activity Context to build the ContentView with.
        assert context == null || context instanceof Activity;

        mId = generateValidId(id);
        mIncognito = incognito;
        // TODO(dtrainor): Only store application context here.
        mContext = context;
        mApplicationContext = context != null ? context.getApplicationContext() : null;
        mWindowAndroid = window;
    }

    /**
     * Adds a {@link TabObserver} to be notified on {@link TabBase} changes.
     * @param observer The {@link TabObserver} to add.
     */
    public final void addObserver(TabObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes a {@link TabObserver}.
     * @param observer The {@link TabObserver} to remove.
     */
    public final void removeObserver(TabObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * @return Whether or not this tab has a previous navigation entry.
     */
    public boolean canGoBack() {
        return mContentViewCore != null && mContentViewCore.canGoBack();
    }

    /**
     * @return Whether or not this tab has a navigation entry after the current one.
     */
    public boolean canGoForward() {
        return mContentViewCore != null && mContentViewCore.canGoForward();
    }

    /**
     * Goes to the navigation entry before the current one.
     */
    public void goBack() {
        if (mContentViewCore != null) mContentViewCore.goBack();
    }

    /**
     * Goes to the navigation entry after the current one.
     */
    public void goForward() {
        if (mContentViewCore != null) mContentViewCore.goForward();
    }

    @Override
    public NavigationHistory getDirectedNavigationHistory(boolean isForward, int itemLimit) {
        if (mContentViewCore != null) {
            return mContentViewCore.getDirectedNavigationHistory(isForward, itemLimit);
        } else {
            return new NavigationHistory();
        }
    }

    @Override
    public void goToNavigationIndex(int index) {
        if (mContentViewCore != null) mContentViewCore.goToNavigationIndex(index);
    }

    /**
     * Loads the current navigation if there is a pending lazy load (after tab restore).
     */
    public void loadIfNecessary() {
        if (mContentViewCore != null) mContentViewCore.loadIfNecessary();
    }

    /**
     * Requests the current navigation to be loaded upon the next call to loadIfNecessary().
     */
    protected void requestRestoreLoad() {
        if (mContentViewCore != null) mContentViewCore.requestRestoreLoad();
    }

    /**
     * @return Whether or not the {@link TabBase} is currently showing an interstitial page, such as
     *         a bad HTTPS page.
     */
    public boolean isShowingInterstitialPage() {
        ContentViewCore contentViewCore = getContentViewCore();
        return contentViewCore != null && contentViewCore.isShowingInterstitialPage();
    }

    /**
     * @return Whether or not the tab has something valid to render.
     */
    public boolean isReady() {
        return mNativePage != null || (mContentViewCore != null && mContentViewCore.isReady());
    }

    /**
     * @return The {@link View} displaying the current page in the tab. This might be a
     *         {@link ContentView} but could potentially be any instance of {@link View}. This can
     *         be {@code null}, if the tab is frozen or being initialized or destroyed.
     */
    public View getView() {
        PageInfo pageInfo = getPageInfo();
        return pageInfo != null ? pageInfo.getView() : null;
    }

    /**
     * @return The width of the content of this tab.  Can be 0 if there is no content.
     */
    public int getWidth() {
        View view = getView();
        return view != null ? view.getWidth() : 0;
    }

    /**
     * @return The height of the content of this tab.  Can be 0 if there is no content.
     */
    public int getHeight() {
        View view = getView();
        return view != null ? view.getHeight() : 0;
    }

    /**
     * @return The application {@link Context} associated with this tab.
     */
    protected Context getApplicationContext() {
        return mApplicationContext;
    }

    /**
     *
     * @return The infobar container.
     */
    public final InfoBarContainer getInfoBarContainer() {
        return mInfoBarContainer;
    }

    /**
     * Create an {@code AutoLoginProcessor} to decide how to handle login
     * requests.
     */
    protected abstract AutoLoginProcessor createAutoLoginProcessor();

    /**
     * Reloads the current page content if it is a {@link ContentView}.
     */
    public void reload() {
        // TODO(dtrainor): Should we try to rebuild the ContentView if it's frozen?
        if (mContentViewCore != null) mContentViewCore.reload();
    }

    /** Stop the current navigation. */
    public void stopLoading() {
        if (mContentViewCore != null) mContentViewCore.stopLoading();
    }

    /**
     * @return The background color of the tab.
     */
    public int getBackgroundColor() {
        return getPageInfo() != null ? getPageInfo().getBackgroundColor() : Color.WHITE;
    }

    /**
     * @return The profile associated with this tab.
     */
    public Profile getProfile() {
        if (mNativeTabAndroid == 0) return null;
        return nativeGetProfileAndroid(mNativeTabAndroid);
    }

    /**
     * @return The id representing this tab.
     */
    @CalledByNative
    public int getId() {
        return mId;
    }

    /**
     * @return Whether or not this tab is incognito.
     */
    public boolean isIncognito() {
        return mIncognito;
    }

    /**
     * @return The {@link ContentView} associated with the current page, or {@code null} if
     *         there is no current page or the current page is displayed using something besides a
     *         {@link ContentView}.
     */
    public ContentView getContentView() {
        return mNativePage == null ? mContentView : null;
    }

    /**
     * @return The {@link ContentViewCore} associated with the current page, or {@code null} if
     *         there is no current page or the current page is displayed using something besides a
     *         {@link ContentView}.
     */
    public ContentViewCore getContentViewCore() {
        return mNativePage == null ? mContentViewCore : null;
    }

    /**
     * @return A {@link PageInfo} describing the current page.  This is always not {@code null}
     *         except during initialization, destruction, and when the tab is frozen.
     */
    public PageInfo getPageInfo() {
        return mNativePage != null ? mNativePage : mContentView;
    }

    /**
     * @return The {@link NativePage} associated with the current page, or {@code null} if there is
     *         no current page or the current page is displayed using something besides
     *         {@link NativePage}.
     */
    public NativePage getNativePage() {
        return mNativePage;
    }

    /**
     * @return Whether or not the {@link TabBase} represents a {@link NativePage}.
     */
    public boolean isNativePage() {
        return mNativePage != null;
    }

    /**
     * Set whether or not the {@link ContentViewCore} should be using a desktop user agent for the
     * currently loaded page.
     * @param useDesktop     If {@code true}, use a desktop user agent.  Otherwise use a mobile one.
     * @param reloadOnChange Reload the page if the user agent has changed.
     */
    public void setUseDesktopUserAgent(boolean useDesktop, boolean reloadOnChange) {
        if (mContentViewCore != null) {
            mContentViewCore.setUseDesktopUserAgent(useDesktop, reloadOnChange);
        }
    }

    /**
     * @return Whether or not the {@link ContentViewCore} is using a desktop user agent.
     */
    public boolean getUseDesktopUserAgent() {
        return mContentViewCore != null && mContentViewCore.getUseDesktopUserAgent();
    }

    /**
     * @return The current {ToolbarModelSecurityLevel} for the tab.
     */
    public int getSecurityLevel() {
        if (mNativeTabAndroid == 0) return ToolbarModelSecurityLevel.NONE;
        return nativeGetSecurityLevel(mNativeTabAndroid);
    }

    /**
     * @return The sync id of the tab if session sync is enabled, {@code 0} otherwise.
     */
    @CalledByNative
    protected int getSyncId() {
        return mSyncId;
    }

    /**
     * @param syncId The sync id of the tab if session sync is enabled.
     */
    @CalledByNative
    protected void setSyncId(int syncId) {
        mSyncId = syncId;
    }

    /**
     * @return An {@link ObserverList.RewindableIterator} instance that points to all of
     *         the current {@link TabObserver}s on this class.  Note that calling
     *         {@link Iterator#remove()} will throw an {@link UnsupportedOperationException}.
     */
    protected ObserverList.RewindableIterator<TabObserver> getTabObservers() {
        return mObservers.rewindableIterator();
    }

    /**
     * @return The {@link ContentViewClient} currently bound to any {@link ContentViewCore}
     *         associated with the current page.  There can still be a {@link ContentViewClient}
     *         even when there is no {@link ContentViewCore}.
     */
    protected ContentViewClient getContentViewClient() {
        return mContentViewClient;
    }

    /**
     * @param client The {@link ContentViewClient} to be bound to any current or new
     *               {@link ContentViewCore}s associated with this {@link TabBase}.
     */
    protected void setContentViewClient(ContentViewClient client) {
        if (mContentViewClient == client) return;

        ContentViewClient oldClient = mContentViewClient;
        mContentViewClient = client;

        if (mContentViewCore == null) return;

        if (mContentViewClient != null) {
            mContentViewCore.setContentViewClient(mContentViewClient);
        } else if (oldClient != null) {
            // We can't set a null client, but we should clear references to the last one.
            mContentViewCore.setContentViewClient(new ContentViewClient());
        }
    }

    /**
     * Shows the given {@code nativePage} if it's not already showing.
     * @param nativePage The {@link NativePage} to show.
     */
    protected void showNativePage(NativePage nativePage) {
        if (mNativePage == nativePage) return;
        destroyNativePageInternal();
        mNativePage = nativePage;
        pushNativePageStateToNavigationEntry();
        for (TabObserver observer : mObservers) observer.onContentChanged(this);
    }

    /**
     * Hides the current {@link NativePage}, if any, and shows the {@link ContentView}.
     */
    protected void showRenderedPage() {
        if (mNativePage == null) return;
        destroyNativePageInternal();
        for (TabObserver observer : mObservers) observer.onContentChanged(this);
    }

    /**
     * Initializes this {@link TabBase}.
     */
    public void initialize() { }

    /**
     * A helper method to initialize a {@link ContentView} without any native WebContents pointer.
     */
    protected final void initContentView() {
        initContentView(ContentViewUtil.createNativeWebContents(mIncognito));
    }

    /**
     * Completes the {@link ContentView} specific initialization around a native WebContents
     * pointer.  {@link #getPageInfo()} will still return the {@link NativePage} if there is one.
     * All initialization that needs to reoccur after a web contents swap should be added here.
     * <p />
     * NOTE: If you attempt to pass a native WebContents that does not have the same incognito
     * state as this tab this call will fail.
     *
     * @param nativeWebContents The native web contents pointer.
     */
    protected void initContentView(int nativeWebContents) {
        destroyNativePageInternal();

        mContentView = ContentView.newInstance(mContext, nativeWebContents, getWindowAndroid());

        mContentViewCore = mContentView.getContentViewCore();
        mWebContentsDelegate = createWebContentsDelegate();
        mWebContentsObserver = new TabBaseWebContentsObserverAndroid(mContentViewCore);

        if (mContentViewClient != null) mContentViewCore.setContentViewClient(mContentViewClient);

        assert mNativeTabAndroid != 0;
        nativeInitWebContents(
                mNativeTabAndroid, mIncognito, mContentViewCore, mWebContentsDelegate);

        // In the case where restoring a Tab or showing a prerendered one we already have a
        // valid infobar container, no need to recreate one.
        if (mInfoBarContainer == null) {
            // The InfoBarContainer needs to be created after the ContentView has been natively
            // initialized.
            mInfoBarContainer = new InfoBarContainer(
                    (Activity) mContext, createAutoLoginProcessor(), getId(), getContentView(),
                    nativeWebContents);
        } else {
            mInfoBarContainer.onParentViewChanged(getId(), getContentView());
        }
    }

    /**
     * Cleans up all internal state, destroying any {@link NativePage} or {@link ContentView}
     * currently associated with this {@link TabBase}.  Typically, pnce this call is made this
     * {@link TabBase} should no longer be used as subclasses usually destroy the native component.
     */
    public void destroy() {
        for (TabObserver observer : mObservers) observer.onDestroyed(this);

        destroyNativePageInternal();
        destroyContentView(true);
        if (mInfoBarContainer != null) {
            mInfoBarContainer.destroy();
            mInfoBarContainer = null;
        }
    }

    /**
     * @return Whether or not this Tab has a live native component.
     */
    public boolean isInitialized() {
        return mNativeTabAndroid != 0;
    }

    /**
     * @return The url associated with the tab.
     */
    @CalledByNative
    public String getUrl() {
        return mContentView != null ? mContentView.getUrl() : "";
    }

    /**
     * @return The tab title.
     */
    @CalledByNative
    public String getTitle() {
        return getPageInfo() != null ? getPageInfo().getTitle() : "";
    }

    /**
     * Restores the tab if it is frozen or crashed.
     * @return true iff tab restore was triggered.
     */
    @CalledByNative
    public boolean restoreIfNeeded() {
        return false;
    }

    private void destroyNativePageInternal() {
        if (mNativePage == null) return;

        mNativePage.destroy();
        mNativePage = null;
    }

    /**
     * Destroys the current {@link ContentView}.
     * @param deleteNativeWebContents Whether or not to delete the native WebContents pointer.
     */
    protected final void destroyContentView(boolean deleteNativeWebContents) {
        if (mContentView == null) return;

        destroyContentViewInternal(mContentView);

        if (mInfoBarContainer != null && mInfoBarContainer.getParent() != null) {
            mInfoBarContainer.removeFromParentView();
        }
        if (mContentViewCore != null) mContentViewCore.destroy();

        mContentView = null;
        mContentViewCore = null;
        mWebContentsDelegate = null;
        mWebContentsObserver = null;

        assert mNativeTabAndroid != 0;
        nativeDestroyWebContents(mNativeTabAndroid, deleteNativeWebContents);
    }

    /**
     * Gives subclasses the chance to clean up some state associated with this {@link ContentView}.
     * This is because {@link #getContentView()} can return {@code null} if a {@link NativePage}
     * is showing.
     * @param contentView The {@link ContentView} that should have associated state cleaned up.
     */
    protected void destroyContentViewInternal(ContentView contentView) {
    }

    /**
     * A helper method to allow subclasses to build their own delegate.
     * @return An instance of a {@link TabBaseChromeWebContentsDelegateAndroid}.
     */
    protected TabBaseChromeWebContentsDelegateAndroid createWebContentsDelegate() {
        return new TabBaseChromeWebContentsDelegateAndroid();
    }

    /**
     * @return The {@link WindowAndroid} associated with this {@link TabBase}.
     */
    protected WindowAndroid getWindowAndroid() {
        return mWindowAndroid;
    }

    /**
     * @return The current {@link TabBaseChromeWebContentsDelegateAndroid} instance.
     */
    protected TabBaseChromeWebContentsDelegateAndroid getChromeWebContentsDelegateAndroid() {
        return mWebContentsDelegate;
    }

    /**
     * Launches all currently blocked popups that were spawned by the content of this tab.
     */
    protected void launchBlockedPopups() {
        assert mContentViewCore != null;

        nativeLaunchBlockedPopups(mNativeTabAndroid);
    }

    /**
     * Called when the number of blocked popups has changed.
     * @param numPopups The current number of blocked popups.
     */
    @CalledByNative
    protected void onBlockedPopupsStateChanged(int numPopups) { }

    /**
     * Called when the favicon of the content this tab represents changes.
     */
    @CalledByNative
    protected void onFaviconUpdated() {
        for (TabObserver observer : mObservers) observer.onFaviconUpdated(this);
    }

    /**
     * @return The native pointer representing the native side of this {@link TabBase} object.
     */
    @CalledByNative
    protected int getNativePtr() {
        return mNativeTabAndroid;
    }

    /** This is currently called when committing a pre-rendered page. */
    @CalledByNative
    private void swapWebContents(final int newWebContents) {
        if (mContentViewCore != null) mContentViewCore.onHide();
        destroyContentView(false);
        initContentView(newWebContents);
        mContentViewCore.onShow();
        mContentViewCore.attachImeAdapter();
        for (TabObserver observer : mObservers) observer.onContentChanged(this);
    }

    @CalledByNative
    private void clearNativePtr() {
        assert mNativeTabAndroid != 0;
        mNativeTabAndroid = 0;
    }

    @CalledByNative
    private void setNativePtr(int nativePtr) {
        assert mNativeTabAndroid == 0;
        mNativeTabAndroid = nativePtr;
    }

    @CalledByNative
    private int getNativeInfoBarContainer() {
        return getInfoBarContainer().getNative();
    }

    /**
     * Validates {@code id} and increments the internal counter to make sure future ids don't
     * collide.
     * @param id The current id.  Maybe {@link #INVALID_TAB_ID}.
     * @return   A new id if {@code id} was {@link #INVALID_TAB_ID}, or {@code id}.
     */
    private static int generateValidId(int id) {
        if (id == INVALID_TAB_ID) id = generateNextId();
        incrementIdCounterTo(id + 1);

        return id;
    }

    /**
     * @return An unused id.
     */
    private static int generateNextId() {
        return sIdCounter.getAndIncrement();
    }

    private void pushNativePageStateToNavigationEntry() {
        assert mNativeTabAndroid != 0 && getNativePage() != null;
        nativeSetActiveNavigationEntryTitleForUrl(mNativeTabAndroid, getNativePage().getUrl(),
                getNativePage().getTitle());
    }

    /**
     * Ensures the counter is at least as high as the specified value.  The counter should always
     * point to an unused ID (which will be handed out next time a request comes in).  Exposed so
     * that anything externally loading tabs and ids can set enforce new tabs start at the correct
     * id.
     * TODO(aurimas): Investigate reducing the visiblity of this method.
     * @param id The minimum id we should hand out to the next new tab.
     */
    public static void incrementIdCounterTo(int id) {
        int diff = id - sIdCounter.get();
        if (diff <= 0) return;
        // It's possible idCounter has been incremented between the get above and the add below
        // but that's OK, because in the worst case we'll overly increment idCounter.
        sIdCounter.addAndGet(diff);
    }

    private native void nativeInitWebContents(int nativeTabAndroid, boolean incognito,
            ContentViewCore contentViewCore, ChromeWebContentsDelegateAndroid delegate);
    private native void nativeDestroyWebContents(int nativeTabAndroid, boolean deleteNative);
    private native Profile nativeGetProfileAndroid(int nativeTabAndroid);
    private native void nativeLaunchBlockedPopups(int nativeTabAndroid);
    private native int nativeGetSecurityLevel(int nativeTabAndroid);
    private native void nativeSetActiveNavigationEntryTitleForUrl(int nativeTabAndroid, String url,
            String title);
}

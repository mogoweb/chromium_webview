/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebStorage;

import com.mogoweb.chrome.WebBackForwardList;
import com.mogoweb.chrome.WebChromeClient;
import com.mogoweb.chrome.WebView;
import com.mogoweb.chrome.WebViewClient;

/**
 * Class for maintaining Tabs with a main WebView and a subwindow.
 */
class Tab /*implements PictureListener*/ {

    // Log Tag
    private static final String LOGTAG = "Tab";
    private static final boolean LOGD_ENABLED = com.mogoweb.browser.Browser.LOGD_ENABLED;
    // Special case the logtag for messages for the Console to make it easier to
    // filter them and match the logtag used for these messages in older versions
    // of the browser.
    private static final String CONSOLE_LOGTAG = "browser";

    private static final int MSG_CAPTURE = 42;
    private static final int CAPTURE_DELAY = 100;
    private static final int INITIAL_PROGRESS = 5;

    private static Bitmap sDefaultFavicon;

    private static Paint sAlphaPaint = new Paint();
    static {
        sAlphaPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        sAlphaPaint.setColor(Color.TRANSPARENT);
    }

    public enum SecurityState {
        // The page's main resource does not use SSL. Note that we use this
        // state irrespective of the SSL authentication state of sub-resources.
        SECURITY_STATE_NOT_SECURE,
        // The page's main resource uses SSL and the certificate is good. The
        // same is true of all sub-resources.
        SECURITY_STATE_SECURE,
        // The page's main resource uses SSL and the certificate is good, but
        // some sub-resources either do not use SSL or have problems with their
        // certificates.
        SECURITY_STATE_MIXED,
        // The page's main resource uses SSL but there is a problem with its
        // certificate.
        SECURITY_STATE_BAD_CERTIFICATE,
    }

    Context mContext;
    protected WebViewController mWebViewController;

    // The tab ID
    private long mId = -1;

    // The Geolocation permissions prompt
    private GeolocationPermissionsPrompt mGeolocationPermissionsPrompt;
    // Main WebView wrapper
    private View mContainer;
    // Main WebView
    private WebView mMainView;
    // Subwindow container
    private View mSubViewContainer;
    // Subwindow WebView
    private WebView mSubView;
    // Saved bundle for when we are running low on memory. It contains the
    // information needed to restore the WebView if the user goes back to the
    // tab.
    private Bundle mSavedState;
    // Parent Tab. This is the Tab that created this Tab, or null if the Tab was
    // created by the UI
    private Tab mParent;
    // Tab that constructed by this Tab. This is used when this Tab is
    // destroyed, it clears all mParentTab values in the children.
    private Vector<Tab> mChildren;
    // If true, the tab is in the foreground of the current activity.
    private boolean mInForeground;
    // If true, the tab is in page loading state (after onPageStarted,
    // before onPageFinsihed)
    private boolean mInPageLoad;
    private boolean mDisableOverrideUrlLoading;
    // The last reported progress of the current page
    private int mPageLoadProgress;
    // The time the load started, used to find load page time
    private long mLoadStartTime;
    // Application identifier used to find tabs that another application wants
    // to reuse.
    private String mAppId;
    // flag to indicate if tab should be closed on back
    private boolean mCloseOnBack;
    // Keep the original url around to avoid killing the old WebView if the url
    // has not changed.
    // Error console for the tab
    private ErrorConsoleView mErrorConsole;
    // The listener that gets invoked when a download is started from the
    // mMainView
//    private final BrowserDownloadListener mDownloadListener;
    // Listener used to know when we move forward or back in the history list.
//    private final WebBackForwardListClient mWebBackForwardListClient;
    private DataController mDataController;
    // State of the auto-login request.
    private DeviceAccountLogin mDeviceAccountLogin;

    // AsyncTask for downloading touch icons
//    DownloadTouchIcon mTouchIconLoader;

    private BrowserSettings mSettings;
    private int mCaptureWidth;
    private int mCaptureHeight;
    private Bitmap mCapture;
    private Handler mHandler;
    private boolean mUpdateThumbnail;

    /**
     * See {@link #clearBackStackWhenItemAdded(String)}.
     */
    private Pattern mClearHistoryUrlPattern;

    private static synchronized Bitmap getDefaultFavicon(Context context) {
        if (sDefaultFavicon == null) {
            sDefaultFavicon = BitmapFactory.decodeResource(
                    context.getResources(), R.drawable.app_web_browser_sm);
        }
        return sDefaultFavicon;
    }

    // All the state needed for a page
    protected static class PageState {
        String mUrl;
        String mOriginalUrl;
        String mTitle;
        SecurityState mSecurityState;
        // This is non-null only when mSecurityState is SECURITY_STATE_BAD_CERTIFICATE.
        SslError mSslCertificateError;
        Bitmap mFavicon;
        boolean mIsBookmarkedSite;
        boolean mIncognito;

        PageState(Context c, boolean incognito) {
            mIncognito = incognito;
            if (mIncognito) {
                mOriginalUrl = mUrl = "browser:incognito";
                mTitle = c.getString(R.string.new_incognito_tab);
            } else {
                mOriginalUrl = mUrl = "";
                mTitle = c.getString(R.string.new_tab);
            }
            mSecurityState = SecurityState.SECURITY_STATE_NOT_SECURE;
        }

        PageState(Context c, boolean incognito, String url, Bitmap favicon) {
            mIncognito = incognito;
            mOriginalUrl = mUrl = url;
            if (URLUtil.isHttpsUrl(url)) {
                mSecurityState = SecurityState.SECURITY_STATE_SECURE;
            } else {
                mSecurityState = SecurityState.SECURITY_STATE_NOT_SECURE;
            }
            mFavicon = favicon;
        }

    }

    // The current/loading page's state
    protected PageState mCurrentState;

    // Used for saving and restoring each Tab
    static final String ID = "ID";
    static final String CURRURL = "currentUrl";
    static final String CURRTITLE = "currentTitle";
    static final String PARENTTAB = "parentTab";
    static final String APPID = "appid";
    static final String INCOGNITO = "privateBrowsingEnabled";
    static final String USERAGENT = "useragent";
    static final String CLOSEFLAG = "closeOnBack";

    // Container class for the next error dialog that needs to be displayed
    private class ErrorDialog {
        public final int mTitle;
        public final String mDescription;
        public final int mError;
        ErrorDialog(int title, String desc, int error) {
            mTitle = title;
            mDescription = desc;
            mError = error;
        }
    }

    private void processNextError() {
        if (mQueuedErrors == null) {
            return;
        }
        // The first one is currently displayed so just remove it.
        mQueuedErrors.removeFirst();
        if (mQueuedErrors.size() == 0) {
            mQueuedErrors = null;
            return;
        }
        showError(mQueuedErrors.getFirst());
    }

    private DialogInterface.OnDismissListener mDialogListener =
            new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface d) {
                    processNextError();
                }
            };
    private LinkedList<ErrorDialog> mQueuedErrors;

    private void queueError(int err, String desc) {
        if (mQueuedErrors == null) {
            mQueuedErrors = new LinkedList<ErrorDialog>();
        }
        for (ErrorDialog d : mQueuedErrors) {
            if (d.mError == err) {
                // Already saw a similar error, ignore the new one.
                return;
            }
        }
        ErrorDialog errDialog = new ErrorDialog(
                err == WebViewClient.ERROR_FILE_NOT_FOUND ?
                R.string.browserFrameFileErrorLabel :
                R.string.browserFrameNetworkErrorLabel,
                desc, err);
        mQueuedErrors.addLast(errDialog);

        // Show the dialog now if the queue was empty and it is in foreground
        if (mQueuedErrors.size() == 1 && mInForeground) {
            showError(errDialog);
        }
    }

    private void showError(ErrorDialog errDialog) {
        if (mInForeground) {
            AlertDialog d = new AlertDialog.Builder(mContext)
                    .setTitle(errDialog.mTitle)
                    .setMessage(errDialog.mDescription)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            d.setOnDismissListener(mDialogListener);
            d.show();
        }
    }

    // -------------------------------------------------------------------------
    // WebViewClient implementation for the main WebView
    // -------------------------------------------------------------------------

//    private final WebViewClientClassicExt mWebViewClient = new WebViewClientClassicExt() {
//        private Message mDontResend;
//        private Message mResend;
//
//        private boolean providersDiffer(String url, String otherUrl) {
//            Uri uri1 = Uri.parse(url);
//            Uri uri2 = Uri.parse(otherUrl);
//            return !uri1.getEncodedAuthority().equals(uri2.getEncodedAuthority());
//        }
//
//        @Override
//        public void onPageStarted(WebView view, String url, Bitmap favicon) {
//            mInPageLoad = true;
//            mUpdateThumbnail = true;
//            mPageLoadProgress = INITIAL_PROGRESS;
//            mCurrentState = new PageState(mContext,
//                    view.isPrivateBrowsingEnabled(), url, favicon);
//            mLoadStartTime = SystemClock.uptimeMillis();
//
//            // If we start a touch icon load and then load a new page, we don't
//            // want to cancel the current touch icon loader. But, we do want to
//            // create a new one when the touch icon url is known.
//            if (mTouchIconLoader != null) {
//                mTouchIconLoader.mTab = null;
//                mTouchIconLoader = null;
//            }
//
//            // reset the error console
//            if (mErrorConsole != null) {
//                mErrorConsole.clearErrorMessages();
//                if (mWebViewController.shouldShowErrorConsole()) {
//                    mErrorConsole.showConsole(ErrorConsoleView.SHOW_NONE);
//                }
//            }
//
//            // Cancel the auto-login process.
//            if (mDeviceAccountLogin != null) {
//                mDeviceAccountLogin.cancel();
//                mDeviceAccountLogin = null;
//                mWebViewController.hideAutoLogin(Tab.this);
//            }
//
//            // finally update the UI in the activity if it is in the foreground
//            mWebViewController.onPageStarted(Tab.this, view, favicon);
//
//            updateBookmarkedStatus();
//        }
//
//        @Override
//        public void onPageFinished(WebView view, String url) {
//            mDisableOverrideUrlLoading = false;
//            if (!isPrivateBrowsingEnabled()) {
//                LogTag.logPageFinishedLoading(
//                        url, SystemClock.uptimeMillis() - mLoadStartTime);
//            }
//            syncCurrentState(view, url);
//            mWebViewController.onPageFinished(Tab.this);
//        }
//
//        // return true if want to hijack the url to let another app to handle it
//        @Override
//        public boolean shouldOverrideUrlLoading(WebView view, String url) {
//            if (!mDisableOverrideUrlLoading && mInForeground) {
//                return mWebViewController.shouldOverrideUrlLoading(Tab.this,
//                        view, url);
//            } else {
//                return false;
//            }
//        }
//
//        /**
//         * Updates the security state. This method is called when we discover
//         * another resource to be loaded for this page (for example,
//         * javascript). While we update the security state, we do not update
//         * the lock icon until we are done loading, as it is slightly more
//         * secure this way.
//         */
//        @Override
//        public void onLoadResource(WebView view, String url) {
//            if (url != null && url.length() > 0) {
//                // It is only if the page claims to be secure that we may have
//                // to update the security state:
//                if (mCurrentState.mSecurityState == SecurityState.SECURITY_STATE_SECURE) {
//                    // If NOT a 'safe' url, change the state to mixed content!
//                    if (!(URLUtil.isHttpsUrl(url) || URLUtil.isDataUrl(url)
//                            || URLUtil.isAboutUrl(url))) {
//                        mCurrentState.mSecurityState = SecurityState.SECURITY_STATE_MIXED;
//                    }
//                }
//            }
//        }
//
//        /**
//         * Show a dialog informing the user of the network error reported by
//         * WebCore if it is in the foreground.
//         */
//        @Override
//        public void onReceivedError(WebView view, int errorCode,
//                String description, String failingUrl) {
//            if (errorCode != WebViewClient.ERROR_HOST_LOOKUP &&
//                    errorCode != WebViewClient.ERROR_CONNECT &&
//                    errorCode != WebViewClient.ERROR_BAD_URL &&
//                    errorCode != WebViewClient.ERROR_UNSUPPORTED_SCHEME &&
//                    errorCode != WebViewClient.ERROR_FILE) {
//                queueError(errorCode, description);
//
//                // Don't log URLs when in private browsing mode
//                if (!isPrivateBrowsingEnabled()) {
//                    Log.e(LOGTAG, "onReceivedError " + errorCode + " " + failingUrl
//                        + " " + description);
//                }
//            }
//        }
//
//        /**
//         * Check with the user if it is ok to resend POST data as the page they
//         * are trying to navigate to is the result of a POST.
//         */
//        @Override
//        public void onFormResubmission(WebView view, final Message dontResend,
//                                       final Message resend) {
//            if (!mInForeground) {
//                dontResend.sendToTarget();
//                return;
//            }
//            if (mDontResend != null) {
//                Log.w(LOGTAG, "onFormResubmission should not be called again "
//                        + "while dialog is still up");
//                dontResend.sendToTarget();
//                return;
//            }
//            mDontResend = dontResend;
//            mResend = resend;
//            new AlertDialog.Builder(mContext).setTitle(
//                    R.string.browserFrameFormResubmitLabel).setMessage(
//                    R.string.browserFrameFormResubmitMessage)
//                    .setPositiveButton(R.string.ok,
//                            new DialogInterface.OnClickListener() {
//                                public void onClick(DialogInterface dialog,
//                                        int which) {
//                                    if (mResend != null) {
//                                        mResend.sendToTarget();
//                                        mResend = null;
//                                        mDontResend = null;
//                                    }
//                                }
//                            }).setNegativeButton(R.string.cancel,
//                            new DialogInterface.OnClickListener() {
//                                public void onClick(DialogInterface dialog,
//                                        int which) {
//                                    if (mDontResend != null) {
//                                        mDontResend.sendToTarget();
//                                        mResend = null;
//                                        mDontResend = null;
//                                    }
//                                }
//                            }).setOnCancelListener(new OnCancelListener() {
//                        public void onCancel(DialogInterface dialog) {
//                            if (mDontResend != null) {
//                                mDontResend.sendToTarget();
//                                mResend = null;
//                                mDontResend = null;
//                            }
//                        }
//                    }).show();
//        }
//
//        /**
//         * Insert the url into the visited history database.
//         * @param url The url to be inserted.
//         * @param isReload True if this url is being reloaded.
//         * FIXME: Not sure what to do when reloading the page.
//         */
//        @Override
//        public void doUpdateVisitedHistory(WebView view, String url,
//                boolean isReload) {
//            mWebViewController.doUpdateVisitedHistory(Tab.this, isReload);
//        }
//
//        /**
//         * Displays SSL error(s) dialog to the user.
//         */
//        @Override
//        public void onReceivedSslError(final WebView view,
//                final SslErrorHandler handler, final SslError error) {
//            if (!mInForeground) {
//                handler.cancel();
//                setSecurityState(SecurityState.SECURITY_STATE_NOT_SECURE);
//                return;
//            }
//            if (mSettings.showSecurityWarnings()) {
//                new AlertDialog.Builder(mContext)
//                    .setTitle(R.string.security_warning)
//                    .setMessage(R.string.ssl_warnings_header)
//                    .setIconAttribute(android.R.attr.alertDialogIcon)
//                    .setPositiveButton(R.string.ssl_continue,
//                        new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog,
//                                    int whichButton) {
//                                handler.proceed();
//                                handleProceededAfterSslError(error);
//                            }
//                        })
//                    .setNeutralButton(R.string.view_certificate,
//                        new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog,
//                                    int whichButton) {
//                                mWebViewController.showSslCertificateOnError(
//                                        view, handler, error);
//                            }
//                        })
//                    .setNegativeButton(R.string.ssl_go_back,
//                        new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog,
//                                    int whichButton) {
//                                dialog.cancel();
//                            }
//                        })
//                    .setOnCancelListener(
//                        new DialogInterface.OnCancelListener() {
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                handler.cancel();
//                                setSecurityState(SecurityState.SECURITY_STATE_NOT_SECURE);
//                                mWebViewController.onUserCanceledSsl(Tab.this);
//                            }
//                        })
//                    .show();
//            } else {
//                handler.proceed();
//            }
//        }
//
//        /**
//         * Called when an SSL error occurred while loading a resource, but the
//         * WebView but chose to proceed anyway based on a decision retained
//         * from a previous response to onReceivedSslError(). We update our
//         * security state to reflect this.
//         */
//        @Override
//        public void onProceededAfterSslError(WebView view, SslError error) {
//            handleProceededAfterSslError(error);
//        }
//
//        /**
//         * Displays client certificate request to the user.
//         */
//        @Override
//        public void onReceivedClientCertRequest(final WebView view,
//                final ClientCertRequestHandler handler, final String host_and_port) {
//            if (!mInForeground) {
//                handler.ignore();
//                return;
//            }
//            int colon = host_and_port.lastIndexOf(':');
//            String host;
//            int port;
//            if (colon == -1) {
//                host = host_and_port;
//                port = -1;
//            } else {
//                String portString = host_and_port.substring(colon + 1);
//                try {
//                    port = Integer.parseInt(portString);
//                    host = host_and_port.substring(0, colon);
//                } catch  (NumberFormatException e) {
//                    host = host_and_port;
//                    port = -1;
//                }
//            }
//            KeyChain.choosePrivateKeyAlias(
//                    mWebViewController.getActivity(), new KeyChainAliasCallback() {
//                @Override public void alias(String alias) {
//                    if (alias == null) {
//                        handler.cancel();
//                        return;
//                    }
//                    new KeyChainLookup(mContext, handler, alias).execute();
//                }
//            }, null, null, host, port, null);
//        }
//
//        /**
//         * Handles an HTTP authentication request.
//         *
//         * @param handler The authentication handler
//         * @param host The host
//         * @param realm The realm
//         */
//        @Override
//        public void onReceivedHttpAuthRequest(WebView view,
//                final HttpAuthHandler handler, final String host,
//                final String realm) {
//            mWebViewController.onReceivedHttpAuthRequest(Tab.this, view, handler, host, realm);
//        }
//
//        @Override
//        public WebResourceResponse shouldInterceptRequest(WebView view,
//                String url) {
//            WebResourceResponse res = HomeProvider.shouldInterceptRequest(
//                    mContext, url);
//            return res;
//        }
//
//        @Override
//        public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
//            if (!mInForeground) {
//                return false;
//            }
//            return mWebViewController.shouldOverrideKeyEvent(event);
//        }
//
//        @Override
//        public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
//            if (!mInForeground) {
//                return;
//            }
//            if (!mWebViewController.onUnhandledKeyEvent(event)) {
//                super.onUnhandledKeyEvent(view, event);
//            }
//        }
//
//        @Override
//        public void onReceivedLoginRequest(WebView view, String realm,
//                String account, String args) {
//            new DeviceAccountLogin(mWebViewController.getActivity(), view, Tab.this, mWebViewController)
//                    .handleLogin(realm, account, args);
//        }
//
//    };

    private void syncCurrentState(WebView view, String url) {
        // Sync state (in case of stop/timeout)
        mCurrentState.mUrl = view.getUrl();
        if (mCurrentState.mUrl == null) {
            mCurrentState.mUrl = "";
        }
        mCurrentState.mOriginalUrl = view.getOriginalUrl();
        mCurrentState.mTitle = view.getTitle();
        mCurrentState.mFavicon = view.getFavicon();
        if (!URLUtil.isHttpsUrl(mCurrentState.mUrl)) {
            // In case we stop when loading an HTTPS page from an HTTP page
            // but before a provisional load occurred
            mCurrentState.mSecurityState = SecurityState.SECURITY_STATE_NOT_SECURE;
            mCurrentState.mSslCertificateError = null;
        }
        mCurrentState.mIncognito = view.isPrivateBrowsingEnabled();
    }

    // Called by DeviceAccountLogin when the Tab needs to have the auto-login UI
    // displayed.
    void setDeviceAccountLogin(DeviceAccountLogin login) {
        mDeviceAccountLogin = login;
    }

    // Returns non-null if the title bar should display the auto-login UI.
    DeviceAccountLogin getDeviceAccountLogin() {
        return mDeviceAccountLogin;
    }

    // -------------------------------------------------------------------------
    // WebChromeClient implementation for the main WebView
    // -------------------------------------------------------------------------

    private final WebChromeClient mWebChromeClient = new WebChromeClient() {
        // Helper method to create a new tab or sub window.
        private void createWindow(final boolean dialog, final Message msg) {
//            WebView.WebViewTransport transport =
//                    (WebView.WebViewTransport) msg.obj;
//            if (dialog) {
//                createSubWindow();
//                mWebViewController.attachSubWindow(Tab.this);
//                transport.setWebView(mSubView);
//            } else {
//                final Tab newTab = mWebViewController.openTab(null,
//                        Tab.this, true, true);
//                transport.setWebView(newTab.getWebView());
//            }
//            msg.sendToTarget();
        }

        @Override
        public boolean onCreateWindow(WebView view, final boolean dialog,
                final boolean userGesture, final Message resultMsg) {
            // only allow new window or sub window for the foreground case
            if (!mInForeground) {
                return false;
            }
            // Short-circuit if we can't create any more tabs or sub windows.
            if (dialog && mSubView != null) {
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.too_many_subwindows_dialog_title)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.too_many_subwindows_dialog_message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                return false;
            } else if (!mWebViewController.getTabControl().canCreateNewTab()) {
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.too_many_windows_dialog_title)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.too_many_windows_dialog_message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                return false;
            }

            // Short-circuit if this was a user gesture.
            if (userGesture) {
                createWindow(dialog, resultMsg);
                return true;
            }

            // Allow the popup and create the appropriate window.
            final AlertDialog.OnClickListener allowListener =
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface d,
                                int which) {
                            createWindow(dialog, resultMsg);
                        }
                    };

            // Block the popup by returning a null WebView.
            final AlertDialog.OnClickListener blockListener =
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            resultMsg.sendToTarget();
                        }
                    };

            // Build a confirmation dialog to display to the user.
            final AlertDialog d =
                    new AlertDialog.Builder(mContext)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.popup_window_attempt)
                    .setPositiveButton(R.string.allow, allowListener)
                    .setNegativeButton(R.string.block, blockListener)
                    .setCancelable(false)
                    .create();

            // Show the confirmation dialog.
            d.show();
            return true;
        }

        @Override
        public void onRequestFocus(WebView view) {
            if (!mInForeground) {
                mWebViewController.switchToTab(Tab.this);
            }
        }

        @Override
        public void onCloseWindow(WebView window) {
            if (mParent != null) {
                // JavaScript can only close popup window.
                if (mInForeground) {
                    mWebViewController.switchToTab(mParent);
                }
                mWebViewController.closeTab(Tab.this);
            }
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            mPageLoadProgress = newProgress;
            if (newProgress == 100) {
                mInPageLoad = false;
            }
            mWebViewController.onProgressChanged(Tab.this);
            if (mUpdateThumbnail && newProgress == 100) {
                mUpdateThumbnail = false;
            }
        }

        @Override
        public void onReceivedTitle(WebView view, final String title) {
            mCurrentState.mTitle = title;
            mWebViewController.onReceivedTitle(Tab.this, title);
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            mCurrentState.mFavicon = icon;
            mWebViewController.onFavicon(Tab.this, view, icon);
        }

        @Override
        public void onReceivedTouchIconUrl(WebView view, String url,
                boolean precomposed) {
//            final ContentResolver cr = mContext.getContentResolver();
//            // Let precomposed icons take precedence over non-composed
//            // icons.
//            if (precomposed && mTouchIconLoader != null) {
//                mTouchIconLoader.cancel(false);
//                mTouchIconLoader = null;
//            }
//            // Have only one async task at a time.
//            if (mTouchIconLoader == null) {
//                mTouchIconLoader = new DownloadTouchIcon(Tab.this,
//                        mContext, cr, view);
//                mTouchIconLoader.execute(url);
//            }
        }

        @Override
        public void onShowCustomView(View view,
                WebChromeClient.CustomViewCallback callback) {
            Activity activity = mWebViewController.getActivity();
            if (activity != null) {
                onShowCustomView(view, activity.getRequestedOrientation(), callback);
            }
        }

        @Override
        public void onShowCustomView(View view, int requestedOrientation,
                WebChromeClient.CustomViewCallback callback) {
            if (mInForeground) mWebViewController.showCustomView(Tab.this, view,
                    requestedOrientation, callback);
        }

        @Override
        public void onHideCustomView() {
            if (mInForeground) mWebViewController.hideCustomView();
        }

        /**
         * The origin has exceeded its database quota.
         * @param url the URL that exceeded the quota
         * @param databaseIdentifier the identifier of the database on which the
         *            transaction that caused the quota overflow was run
         * @param currentQuota the current quota for the origin.
         * @param estimatedSize the estimated size of the database.
         * @param totalUsedQuota is the sum of all origins' quota.
         * @param quotaUpdater The callback to run when a decision to allow or
         *            deny quota has been made. Don't forget to call this!
         */
        @Override
        public void onExceededDatabaseQuota(String url,
            String databaseIdentifier, long currentQuota, long estimatedSize,
            long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
//            mSettings.getWebStorageSizeManager()
//                    .onExceededDatabaseQuota(url, databaseIdentifier,
//                            currentQuota, estimatedSize, totalUsedQuota,
//                            quotaUpdater);
        }

        /**
         * The Application Cache has exceeded its max size.
         * @param spaceNeeded is the amount of disk space that would be needed
         *            in order for the last appcache operation to succeed.
         * @param totalUsedQuota is the sum of all origins' quota.
         * @param quotaUpdater A callback to inform the WebCore thread that a
         *            new app cache size is available. This callback must always
         *            be executed at some point to ensure that the sleeping
         *            WebCore thread is woken up.
         */
        @Override
        public void onReachedMaxAppCacheSize(long spaceNeeded,
                long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
//            mSettings.getWebStorageSizeManager()
//                    .onReachedMaxAppCacheSize(spaceNeeded, totalUsedQuota,
//                            quotaUpdater);
        }

        /**
         * Instructs the browser to show a prompt to ask the user to set the
         * Geolocation permission state for the specified origin.
         * @param origin The origin for which Geolocation permissions are
         *     requested.
         * @param callback The callback to call once the user has set the
         *     Geolocation permission state.
         */
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                GeolocationPermissions.Callback callback) {
            if (mInForeground) {
                getGeolocationPermissionsPrompt().show(origin, callback);
            }
        }

        /**
         * Instructs the browser to hide the Geolocation permissions prompt.
         */
        @Override
        public void onGeolocationPermissionsHidePrompt() {
            if (mInForeground && mGeolocationPermissionsPrompt != null) {
                mGeolocationPermissionsPrompt.hide();
            }
        }

        /* Adds a JavaScript error message to the system log and if the JS
         * console is enabled in the about:debug options, to that console
         * also.
         * @param consoleMessage the message object.
         */
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            if (mInForeground) {
                // call getErrorConsole(true) so it will create one if needed
                ErrorConsoleView errorConsole = getErrorConsole(true);
                errorConsole.addErrorMessage(consoleMessage);
                if (mWebViewController.shouldShowErrorConsole()
                        && errorConsole.getShowState() !=
                            ErrorConsoleView.SHOW_MAXIMIZED) {
                    errorConsole.showConsole(ErrorConsoleView.SHOW_MINIMIZED);
                }
            }

            // Don't log console messages in private browsing mode
            if (isPrivateBrowsingEnabled()) return true;

            String message = "Console: " + consoleMessage.message() + " "
                    + consoleMessage.sourceId() +  ":"
                    + consoleMessage.lineNumber();

            switch (consoleMessage.messageLevel()) {
                case TIP:
                    Log.v(CONSOLE_LOGTAG, message);
                    break;
                case LOG:
                    Log.i(CONSOLE_LOGTAG, message);
                    break;
                case WARNING:
                    Log.w(CONSOLE_LOGTAG, message);
                    break;
                case ERROR:
                    Log.e(CONSOLE_LOGTAG, message);
                    break;
                case DEBUG:
                    Log.d(CONSOLE_LOGTAG, message);
                    break;
            }

            return true;
        }

        /**
         * Ask the browser for an icon to represent a <video> element.
         * This icon will be used if the Web page did not specify a poster attribute.
         * @return Bitmap The icon or null if no such icon is available.
         */
        @Override
        public Bitmap getDefaultVideoPoster() {
            if (mInForeground) {
                return mWebViewController.getDefaultVideoPoster();
            }
            return null;
        }

        /**
         * Ask the host application for a custom progress view to show while
         * a <video> is loading.
         * @return View The progress view.
         */
        @Override
        public View getVideoLoadingProgressView() {
            if (mInForeground) {
                return mWebViewController.getVideoLoadingProgressView();
            }
            return null;
        }

//        @Override
//        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
//            if (mInForeground) {
//                mWebViewController.openFileChooser(uploadMsg, acceptType, capture);
//            } else {
//                uploadMsg.onReceiveValue(null);
//            }
//        }

        /**
         * Deliver a list of already-visited URLs
         */
        @Override
        public void getVisitedHistory(final ValueCallback<String[]> callback) {
            mWebViewController.getVisitedHistory(callback);
        }

//        @Override
//        public void setupAutoFill(Message message) {
//            // Prompt the user to set up their profile.
//            final Message msg = message;
//            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
//            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
//                    Context.LAYOUT_INFLATER_SERVICE);
//            final View layout = inflater.inflate(R.layout.setup_autofill_dialog, null);
//
//            builder.setView(layout)
//                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int id) {
//                        CheckBox disableAutoFill = (CheckBox) layout.findViewById(
//                                R.id.setup_autofill_dialog_disable_autofill);
//
//                        if (disableAutoFill.isChecked()) {
//                            // Disable autofill and show a toast with how to turn it on again.
//                            mSettings.setAutofillEnabled(false);
//                            Toast.makeText(mContext,
//                                    R.string.autofill_setup_dialog_negative_toast,
//                                    Toast.LENGTH_LONG).show();
//                        } else {
//                            // Take user to the AutoFill profile editor. When they return,
//                            // we will send the message that we pass here which will trigger
//                            // the form to get filled out with their new profile.
//                            mWebViewController.setupAutoFill(msg);
//                        }
//                    }
//                })
//                .setNegativeButton(R.string.cancel, null)
//                .show();
//        }
    };

    // -------------------------------------------------------------------------
    // WebViewClient implementation for the sub window
    // -------------------------------------------------------------------------

    // Subclass of WebViewClient used in subwindows to notify the main
    // WebViewClient of certain WebView activities.
//    private static class SubWindowClient extends WebViewClientClassicExt {
//        // The main WebViewClient.
//        private final WebViewClientClassicExt mClient;
//        private final WebViewController mController;
//
//        SubWindowClient(WebViewClientClassicExt client, WebViewController controller) {
//            mClient = client;
//            mController = controller;
//        }
//        @Override
//        public void onPageStarted(WebView view, String url, Bitmap favicon) {
//            // Unlike the others, do not call mClient's version, which would
//            // change the progress bar.  However, we do want to remove the
//            // find or select dialog.
//            mController.endActionMode();
//        }
//        @Override
//        public void doUpdateVisitedHistory(WebView view, String url,
//                boolean isReload) {
//            mClient.doUpdateVisitedHistory(view, url, isReload);
//        }
//        @Override
//        public boolean shouldOverrideUrlLoading(WebView view, String url) {
//            return mClient.shouldOverrideUrlLoading(view, url);
//        }
//        @Override
//        public void onReceivedSslError(WebView view, SslErrorHandler handler,
//                SslError error) {
//            mClient.onReceivedSslError(view, handler, error);
//        }
//        @Override
//        public void onReceivedClientCertRequest(WebView view,
//                ClientCertRequestHandler handler, String host_and_port) {
//            mClient.onReceivedClientCertRequest(view, handler, host_and_port);
//        }
//        @Override
//        public void onReceivedHttpAuthRequest(WebView view,
//                HttpAuthHandler handler, String host, String realm) {
//            mClient.onReceivedHttpAuthRequest(view, handler, host, realm);
//        }
//        @Override
//        public void onFormResubmission(WebView view, Message dontResend,
//                Message resend) {
//            mClient.onFormResubmission(view, dontResend, resend);
//        }
//        @Override
//        public void onReceivedError(WebView view, int errorCode,
//                String description, String failingUrl) {
//            mClient.onReceivedError(view, errorCode, description, failingUrl);
//        }
//        @Override
//        public boolean shouldOverrideKeyEvent(WebView view,
//                android.view.KeyEvent event) {
//            return mClient.shouldOverrideKeyEvent(view, event);
//        }
//        @Override
//        public void onUnhandledKeyEvent(WebView view,
//                android.view.KeyEvent event) {
//            mClient.onUnhandledKeyEvent(view, event);
//        }
//    }

    // -------------------------------------------------------------------------
    // WebChromeClient implementation for the sub window
    // -------------------------------------------------------------------------

    private class SubWindowChromeClient extends WebChromeClient {
        // The main WebChromeClient.
        private final WebChromeClient mClient;

        SubWindowChromeClient(WebChromeClient client) {
            mClient = client;
        }
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            mClient.onProgressChanged(view, newProgress);
        }
        @Override
        public boolean onCreateWindow(WebView view, boolean dialog,
                boolean userGesture, android.os.Message resultMsg) {
            return mClient.onCreateWindow(view, dialog, userGesture, resultMsg);
        }
        @Override
        public void onCloseWindow(WebView window) {
            if (window != mSubView) {
                Log.e(LOGTAG, "Can't close the window");
            }
            mWebViewController.dismissSubWindow(Tab.this);
        }
    }

    // -------------------------------------------------------------------------

    // Construct a new tab
    Tab(WebViewController wvcontroller, WebView w) {
        this(wvcontroller, w, null);
    }

    Tab(WebViewController wvcontroller, Bundle state) {
        this(wvcontroller, null, state);
    }

    Tab(WebViewController wvcontroller, WebView w, Bundle state) {
        mWebViewController = wvcontroller;
        mContext = mWebViewController.getContext();
        mSettings = BrowserSettings.getInstance();
        mDataController = DataController.getInstance(mContext);
        mCurrentState = new PageState(mContext, /*w != null
                ? w.isPrivateBrowsingEnabled() : */false);
        mInPageLoad = false;
        mInForeground = false;

//        mDownloadListener = new BrowserDownloadListener() {
//            public void onDownloadStart(String url, String userAgent,
//                    String contentDisposition, String mimetype, String referer,
//                    long contentLength) {
//                mWebViewController.onDownloadStart(Tab.this, url, userAgent, contentDisposition,
//                        mimetype, referer, contentLength);
//            }
//        };
//        mWebBackForwardListClient = new WebBackForwardListClient() {
//            @Override
//            public void onNewHistoryItem(WebHistoryItem item) {
//                if (mClearHistoryUrlPattern != null) {
//                    boolean match =
//                        mClearHistoryUrlPattern.matcher(item.getOriginalUrl()).matches();
//                    if (LOGD_ENABLED) {
//                        Log.d(LOGTAG, "onNewHistoryItem: match=" + match + "\n\t"
//                                + item.getUrl() + "\n\t"
//                                + mClearHistoryUrlPattern);
//                    }
//                    if (match) {
//                        if (mMainView != null) {
//                            mMainView.clearHistory();
//                        }
//                    }
//                    mClearHistoryUrlPattern = null;
//                }
//            }
//        };

        mCaptureWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.tab_thumbnail_width);
        mCaptureHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.tab_thumbnail_height);
        updateShouldCaptureThumbnails();
        restoreState(state);
        if (getId() == -1) {
            mId = TabControl.getNextId();
        }
        setWebView(w);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message m) {
                switch (m.what) {
                case MSG_CAPTURE:
                    capture();
                    break;
                }
            }
        };
    }

    public boolean shouldUpdateThumbnail() {
        return mUpdateThumbnail;
    }

    /**
     * This is used to get a new ID when the tab has been preloaded, before it is displayed and
     * added to TabControl. Preloaded tabs can be created before restoreInstanceState, leading
     * to overlapping IDs between the preloaded and restored tabs.
     */
    public void refreshIdAfterPreload() {
        mId = TabControl.getNextId();
    }

    public void updateShouldCaptureThumbnails() {
        if (mWebViewController.shouldCaptureThumbnails()) {
            synchronized (Tab.this) {
                if (mCapture == null) {
                    mCapture = Bitmap.createBitmap(mCaptureWidth, mCaptureHeight,
                            Bitmap.Config.RGB_565);
                    mCapture.eraseColor(Color.WHITE);
                    if (mInForeground) {
                        postCapture();
                    }
                }
            }
        } else {
            synchronized (Tab.this) {
                mCapture = null;
                deleteThumbnail();
            }
        }
    }

    public void setController(WebViewController ctl) {
        mWebViewController = ctl;
        updateShouldCaptureThumbnails();
    }

    public long getId() {
        return mId;
    }

    void setWebView(WebView w) {
        setWebView(w, true);
    }

    /**
     * Sets the WebView for this tab, correctly removing the old WebView from
     * the container view.
     */
    void setWebView(WebView w, boolean restore) {
        if (mMainView == w) {
            return;
        }

        // If the WebView is changing, the page will be reloaded, so any ongoing
        // Geolocation permission requests are void.
        if (mGeolocationPermissionsPrompt != null) {
            mGeolocationPermissionsPrompt.hide();
        }

        mWebViewController.onSetWebView(this, w);

        if (mMainView != null) {
//            mMainView.setPictureListener(null);
            if (w != null) {
                syncCurrentState(w, null);
            } else {
                mCurrentState = new PageState(mContext, false);
            }
        }
        // set the new one
        mMainView = w;
        // attach the WebViewClient, WebChromeClient and DownloadListener
        if (mMainView != null) {
//            mMainView.setWebViewClient(mWebViewClient);
//            mMainView.setWebChromeClient(mWebChromeClient);
//            // Attach DownloadManager so that downloads can start in an active
//            // or a non-active window. This can happen when going to a site that
//            // does a redirect after a period of time. The user could have
//            // switched to another tab while waiting for the download to start.
//            mMainView.setDownloadListener(mDownloadListener);
//            getWebViewClassic().setWebBackForwardListClient(mWebBackForwardListClient);
//            TabControl tc = mWebViewController.getTabControl();
//            if (tc != null && tc.getOnThumbnailUpdatedListener() != null) {
//                mMainView.setPictureListener(this);
//            }
//            if (restore && (mSavedState != null)) {
//                restoreUserAgent();
//                WebBackForwardList restoredState
//                        = mMainView.restoreState(mSavedState);
//                if (restoredState == null || restoredState.getSize() == 0) {
//                    Log.w(LOGTAG, "Failed to restore WebView state!");
//                    loadUrl(mCurrentState.mOriginalUrl, null);
//                }
//                mSavedState = null;
//            }
        }
    }

    /**
     * Destroy the tab's main WebView and subWindow if any
     */
    void destroy() {
        if (mMainView != null) {
            dismissSubWindow();
            // save the WebView to call destroy() after detach it from the tab
            WebView webView = mMainView;
            setWebView(null);
            webView.destroy();
        }
    }

    /**
     * Remove the tab from the parent
     */
    void removeFromTree() {
        // detach the children
        if (mChildren != null) {
            for(Tab t : mChildren) {
                t.setParent(null);
            }
        }
        // remove itself from the parent list
        if (mParent != null) {
            mParent.mChildren.remove(this);
        }
        deleteThumbnail();
    }

    /**
     * Create a new subwindow unless a subwindow already exists.
     * @return True if a new subwindow was created. False if one already exists.
     */
    boolean createSubWindow() {
//        if (mSubView == null) {
//            mWebViewController.createSubWindow(this);
//            mSubView.setWebViewClient(new SubWindowClient(mWebViewClient,
//                    mWebViewController));
//            mSubView.setWebChromeClient(new SubWindowChromeClient(
//                    mWebChromeClient));
//            // Set a different DownloadListener for the mSubView, since it will
//            // just need to dismiss the mSubView, rather than close the Tab
//            mSubView.setDownloadListener(new BrowserDownloadListener() {
//                public void onDownloadStart(String url, String userAgent,
//                        String contentDisposition, String mimetype, String referer,
//                        long contentLength) {
//                    mWebViewController.onDownloadStart(Tab.this, url, userAgent,
//                            contentDisposition, mimetype, referer, contentLength);
//                    if (mSubView.copyBackForwardList().getSize() == 0) {
//                        // This subwindow was opened for the sole purpose of
//                        // downloading a file. Remove it.
//                        mWebViewController.dismissSubWindow(Tab.this);
//                    }
//                }
//            });
//            mSubView.setOnCreateContextMenuListener(mWebViewController.getActivity());
//            return true;
//        }
        return false;
    }

    /**
     * Dismiss the subWindow for the tab.
     */
    void dismissSubWindow() {
        if (mSubView != null) {
            mWebViewController.endActionMode();
            mSubView.destroy();
            mSubView = null;
            mSubViewContainer = null;
        }
    }


    /**
     * Set the parent tab of this tab.
     */
    void setParent(Tab parent) {
        if (parent == this) {
            throw new IllegalStateException("Cannot set parent to self!");
        }
        mParent = parent;
        // This tab may have been freed due to low memory. If that is the case,
        // the parent tab id is already saved. If we are changing that id
        // (most likely due to removing the parent tab) we must update the
        // parent tab id in the saved Bundle.
        if (mSavedState != null) {
            if (parent == null) {
                mSavedState.remove(PARENTTAB);
            } else {
                mSavedState.putLong(PARENTTAB, parent.getId());
            }
        }

        // Sync the WebView useragent with the parent
        if (parent != null && mSettings.hasDesktopUseragent(parent.getWebView())
                != mSettings.hasDesktopUseragent(getWebView())) {
            mSettings.toggleDesktopUseragent(getWebView());
        }

        if (parent != null && parent.getId() == getId()) {
            throw new IllegalStateException("Parent has same ID as child!");
        }
    }

    /**
     * If this Tab was created through another Tab, then this method returns
     * that Tab.
     * @return the Tab parent or null
     */
    public Tab getParent() {
        return mParent;
    }

    /**
     * When a Tab is created through the content of another Tab, then we
     * associate the Tabs.
     * @param child the Tab that was created from this Tab
     */
    void addChildTab(Tab child) {
        if (mChildren == null) {
            mChildren = new Vector<Tab>();
        }
        mChildren.add(child);
        child.setParent(this);
    }

    Vector<Tab> getChildren() {
        return mChildren;
    }

    void resume() {
        if (mMainView != null) {
            setupHwAcceleration(mMainView);
            mMainView.onResume();
            if (mSubView != null) {
                mSubView.onResume();
            }
        }
    }

    private void setupHwAcceleration(View web) {
        if (web == null || Build.VERSION.SDK_INT < 11) return;
        BrowserSettings settings = BrowserSettings.getInstance();
        if (settings.isHardwareAccelerated()) {
            web.setLayerType(View.LAYER_TYPE_NONE, null);
        } else {
            web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    void pause() {
        if (mMainView != null) {
            mMainView.onPause();
            if (mSubView != null) {
                mSubView.onPause();
            }
        }
    }

    void putInForeground() {
        if (mInForeground) {
            return;
        }
        mInForeground = true;
        resume();
        Activity activity = mWebViewController.getActivity();
        mMainView.setOnCreateContextMenuListener(activity);
        if (mSubView != null) {
            mSubView.setOnCreateContextMenuListener(activity);
        }
        // Show the pending error dialog if the queue is not empty
        if (mQueuedErrors != null && mQueuedErrors.size() >  0) {
            showError(mQueuedErrors.getFirst());
        }
        mWebViewController.bookmarkedStatusHasChanged(this);
    }

    void putInBackground() {
        if (!mInForeground) {
            return;
        }
        capture();
        mInForeground = false;
        pause();
        mMainView.setOnCreateContextMenuListener(null);
        if (mSubView != null) {
            mSubView.setOnCreateContextMenuListener(null);
        }
    }

    boolean inForeground() {
        return mInForeground;
    }

    /**
     * Return the top window of this tab; either the subwindow if it is not
     * null or the main window.
     * @return The top window of this tab.
     */
    WebView getTopWindow() {
        if (mSubView != null) {
            return mSubView;
        }
        return mMainView;
    }

    /**
     * Return the main window of this tab. Note: if a tab is freed in the
     * background, this can return null. It is only guaranteed to be
     * non-null for the current tab.
     * @return The main WebView of this tab.
     */
    WebView getWebView() {
        return mMainView;
    }

//    /**
//     * Return the underlying WebViewClassic implementation. As with getWebView,
//     * this maybe null for background tabs.
//     * @return The main WebView of this tab.
//     */
//    WebViewClassic getWebViewClassic() {
//        return WebViewClassic.fromWebView(mMainView);
//    }

    void setViewContainer(View container) {
        mContainer = container;
    }

    View getViewContainer() {
        return mContainer;
    }

    /**
     * Return whether private browsing is enabled for the main window of
     * this tab.
     * @return True if private browsing is enabled.
     */
    boolean isPrivateBrowsingEnabled() {
        return mCurrentState.mIncognito;
    }

    /**
     * Return the subwindow of this tab or null if there is no subwindow.
     * @return The subwindow of this tab or null.
     */
    WebView getSubWebView() {
        return mSubView;
    }

    void setSubWebView(WebView subView) {
        mSubView = subView;
    }

    View getSubViewContainer() {
        return mSubViewContainer;
    }

    void setSubViewContainer(View subViewContainer) {
        mSubViewContainer = subViewContainer;
    }

    /**
     * @return The geolocation permissions prompt for this tab.
     */
    GeolocationPermissionsPrompt getGeolocationPermissionsPrompt() {
        if (mGeolocationPermissionsPrompt == null) {
            ViewStub stub = (ViewStub) mContainer
                    .findViewById(R.id.geolocation_permissions_prompt);
            mGeolocationPermissionsPrompt = (GeolocationPermissionsPrompt) stub
                    .inflate();
        }
        return mGeolocationPermissionsPrompt;
    }

    /**
     * @return The application id string
     */
    String getAppId() {
        return mAppId;
    }

    /**
     * Set the application id string
     * @param id
     */
    void setAppId(String id) {
        mAppId = id;
    }

    boolean closeOnBack() {
        return mCloseOnBack;
    }

    void setCloseOnBack(boolean close) {
        mCloseOnBack = close;
    }

    String getUrl() {
        return UrlUtils.filteredUrl(mCurrentState.mUrl);
    }

    String getOriginalUrl() {
        if (mCurrentState.mOriginalUrl == null) {
            return getUrl();
        }
        return UrlUtils.filteredUrl(mCurrentState.mOriginalUrl);
    }

    /**
     * Get the title of this tab.
     */
    String getTitle() {
        if (mCurrentState.mTitle == null && mInPageLoad) {
            return mContext.getString(R.string.title_bar_loading);
        }
        return mCurrentState.mTitle;
    }

    /**
     * Get the favicon of this tab.
     */
    Bitmap getFavicon() {
        if (mCurrentState.mFavicon != null) {
            return mCurrentState.mFavicon;
        }
        return getDefaultFavicon(mContext);
    }

    public boolean isBookmarkedSite() {
        return mCurrentState.mIsBookmarkedSite;
    }

    /**
     * Return the tab's error console. Creates the console if createIfNEcessary
     * is true and we haven't already created the console.
     * @param createIfNecessary Flag to indicate if the console should be
     *            created if it has not been already.
     * @return The tab's error console, or null if one has not been created and
     *         createIfNecessary is false.
     */
    ErrorConsoleView getErrorConsole(boolean createIfNecessary) {
        if (createIfNecessary && mErrorConsole == null) {
            mErrorConsole = new ErrorConsoleView(mContext);
            mErrorConsole.setWebView(mMainView);
        }
        return mErrorConsole;
    }

    /**
     * Sets the security state, clears the SSL certificate error and informs
     * the controller.
     */
    private void setSecurityState(SecurityState securityState) {
        mCurrentState.mSecurityState = securityState;
        mCurrentState.mSslCertificateError = null;
        mWebViewController.onUpdatedSecurityState(this);
    }

    /**
     * @return The tab's security state.
     */
    SecurityState getSecurityState() {
        return mCurrentState.mSecurityState;
    }

    /**
     * Gets the SSL certificate error, if any, for the page's main resource.
     * This is only non-null when the security state is
     * SECURITY_STATE_BAD_CERTIFICATE.
     */
    SslError getSslCertificateError() {
        return mCurrentState.mSslCertificateError;
    }

    int getLoadProgress() {
        if (mInPageLoad) {
            return mPageLoadProgress;
        }
        return 100;
    }

    /**
     * @return TRUE if onPageStarted is called while onPageFinished is not
     *         called yet.
     */
    boolean inPageLoad() {
        return mInPageLoad;
    }

    /**
     * @return The Bundle with the tab's state if it can be saved, otherwise null
     */
    public Bundle saveState() {
        // If the WebView is null it means we ran low on memory and we already
        // stored the saved state in mSavedState.
        if (mMainView == null) {
            return mSavedState;
        }

        if (TextUtils.isEmpty(mCurrentState.mUrl)) {
            return null;
        }

        mSavedState = new Bundle();
        WebBackForwardList savedList = mMainView.saveState(mSavedState);
        if (savedList == null || savedList.getSize() == 0) {
            Log.w(LOGTAG, "Failed to save back/forward list for "
                    + mCurrentState.mUrl);
        }

        mSavedState.putLong(ID, mId);
        mSavedState.putString(CURRURL, mCurrentState.mUrl);
        mSavedState.putString(CURRTITLE, mCurrentState.mTitle);
        mSavedState.putBoolean(INCOGNITO, mMainView.isPrivateBrowsingEnabled());
        if (mAppId != null) {
            mSavedState.putString(APPID, mAppId);
        }
        mSavedState.putBoolean(CLOSEFLAG, mCloseOnBack);
        // Remember the parent tab so the relationship can be restored.
        if (mParent != null) {
            mSavedState.putLong(PARENTTAB, mParent.mId);
        }
        mSavedState.putBoolean(USERAGENT,
                mSettings.hasDesktopUseragent(getWebView()));
        return mSavedState;
    }

    /*
     * Restore the state of the tab.
     */
    private void restoreState(Bundle b) {
        mSavedState = b;
        if (mSavedState == null) {
            return;
        }
        // Restore the internal state even if the WebView fails to restore.
        // This will maintain the app id, original url and close-on-exit values.
        mId = b.getLong(ID);
        mAppId = b.getString(APPID);
        mCloseOnBack = b.getBoolean(CLOSEFLAG);
        restoreUserAgent();
        String url = b.getString(CURRURL);
        String title = b.getString(CURRTITLE);
        boolean incognito = b.getBoolean(INCOGNITO);
        mCurrentState = new PageState(mContext, incognito, url, null);
        mCurrentState.mTitle = title;
        synchronized (Tab.this) {
            if (mCapture != null) {
                DataController.getInstance(mContext).loadThumbnail(this);
            }
        }
    }

    private void restoreUserAgent() {
        if (mMainView == null || mSavedState == null) {
            return;
        }
        if (mSavedState.getBoolean(USERAGENT)
                != mSettings.hasDesktopUseragent(mMainView)) {
            mSettings.toggleDesktopUseragent(mMainView);
        }
    }

    public void updateBookmarkedStatus() {
        mDataController.queryBookmarkStatus(getUrl(), mIsBookmarkCallback);
    }

    private DataController.OnQueryUrlIsBookmark mIsBookmarkCallback
            = new DataController.OnQueryUrlIsBookmark() {
        @Override
        public void onQueryUrlIsBookmark(String url, boolean isBookmark) {
            if (mCurrentState.mUrl.equals(url)) {
                mCurrentState.mIsBookmarkedSite = isBookmark;
                mWebViewController.bookmarkedStatusHasChanged(Tab.this);
            }
        }
    };

    public Bitmap getScreenshot() {
        synchronized (Tab.this) {
            return mCapture;
        }
    }

    public boolean isSnapshot() {
        return false;
    }

    private static class SaveCallback implements ValueCallback<Boolean> {
        boolean mResult;

        @Override
        public void onReceiveValue(Boolean value) {
            mResult = value;
            synchronized (this) {
                notifyAll();
            }
        }

    }

    /**
     * Must be called on the UI thread
     */
    public ContentValues createSnapshotValues() {
//        WebViewClassic web = getWebViewClassic();
//        if (web == null) return null;
        ContentValues values = new ContentValues();
//        values.put(Snapshots.TITLE, mCurrentState.mTitle);
//        values.put(Snapshots.URL, mCurrentState.mUrl);
//        values.put(Snapshots.BACKGROUND, web.getPageBackgroundColor());
//        values.put(Snapshots.DATE_CREATED, System.currentTimeMillis());
//        values.put(Snapshots.FAVICON, compressBitmap(getFavicon()));
//        Bitmap screenshot = Controller.createScreenshot(mMainView,
//                Controller.getDesiredThumbnailWidth(mContext),
//                Controller.getDesiredThumbnailHeight(mContext));
//        values.put(Snapshots.THUMBNAIL, compressBitmap(screenshot));
        return values;
    }

    /**
     * Probably want to call this on a background thread
     */
    public boolean saveViewState(ContentValues values) {
//        WebViewClassic web = getWebViewClassic();
//        if (web == null) return false;
//        String path = UUID.randomUUID().toString();
//        SaveCallback callback = new SaveCallback();
//        OutputStream outs = null;
//        try {
//            outs = mContext.openFileOutput(path, Context.MODE_PRIVATE);
//            GZIPOutputStream stream = new GZIPOutputStream(outs);
//            synchronized (callback) {
//                web.saveViewState(stream, callback);
//                callback.wait();
//            }
//            stream.flush();
//            stream.close();
//        } catch (Exception e) {
//            Log.w(LOGTAG, "Failed to save view state", e);
//            if (outs != null) {
//                try {
//                    outs.close();
//                } catch (IOException ignore) {}
//            }
//            File file = mContext.getFileStreamPath(path);
//            if (file.exists() && !file.delete()) {
//                file.deleteOnExit();
//            }
//            return false;
//        }
//        File savedFile = mContext.getFileStreamPath(path);
//        if (!callback.mResult) {
//            if (!savedFile.delete()) {
//                savedFile.deleteOnExit();
//            }
//            return false;
//        }
//        long size = savedFile.length();
//        values.put(Snapshots.VIEWSTATE_PATH, path);
//        values.put(Snapshots.VIEWSTATE_SIZE, size);
        return true;
    }

    public byte[] compressBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    public void loadUrl(String url, Map<String, String> headers) {
        if (mMainView != null) {
            mPageLoadProgress = INITIAL_PROGRESS;
            mInPageLoad = true;
            mCurrentState = new PageState(mContext, false, url, null);
            mWebViewController.onPageStarted(this, mMainView, null);
            mMainView.loadUrl(url, headers);
        }
    }

    public void disableUrlOverridingForLoad() {
        mDisableOverrideUrlLoading = true;
    }

    protected void capture() {
//        if (mMainView == null || mCapture == null) return;
//        if (mMainView.getContentWidth() <= 0 || mMainView.getContentHeight() <= 0) {
//            return;
//        }
//        Canvas c = new Canvas(mCapture);
//        final int left = mMainView.getScrollX();
//        final int top = mMainView.getScrollY() + mMainView.getVisibleTitleHeight();
//        int state = c.save();
//        c.translate(-left, -top);
//        float scale = mCaptureWidth / (float) mMainView.getWidth();
//        c.scale(scale, scale, left, top);
//        if (mMainView instanceof BrowserWebView) {
//            ((BrowserWebView)mMainView).drawContent(c);
//        } else {
//            mMainView.draw(c);
//        }
//        c.restoreToCount(state);
//        // manually anti-alias the edges for the tilt
//        c.drawRect(0, 0, 1, mCapture.getHeight(), sAlphaPaint);
//        c.drawRect(mCapture.getWidth() - 1, 0, mCapture.getWidth(),
//                mCapture.getHeight(), sAlphaPaint);
//        c.drawRect(0, 0, mCapture.getWidth(), 1, sAlphaPaint);
//        c.drawRect(0, mCapture.getHeight() - 1, mCapture.getWidth(),
//                mCapture.getHeight(), sAlphaPaint);
//        c.setBitmap(null);
//        mHandler.removeMessages(MSG_CAPTURE);
//        persistThumbnail();
//        TabControl tc = mWebViewController.getTabControl();
//        if (tc != null) {
//            OnThumbnailUpdatedListener updateListener
//                    = tc.getOnThumbnailUpdatedListener();
//            if (updateListener != null) {
//                updateListener.onThumbnailUpdated(this);
//            }
//        }
    }

//    @Override
//    public void onNewPicture(WebView view, Picture picture) {
//        postCapture();
//    }

    private void postCapture() {
        if (!mHandler.hasMessages(MSG_CAPTURE)) {
            mHandler.sendEmptyMessageDelayed(MSG_CAPTURE, CAPTURE_DELAY);
        }
    }

    public boolean canGoBack() {
        return mMainView != null ? mMainView.canGoBack() : false;
    }

    public boolean canGoForward() {
        return mMainView != null ? mMainView.canGoForward() : false;
    }

    public void goBack() {
        if (mMainView != null) {
            mMainView.goBack();
        }
    }

    public void goForward() {
        if (mMainView != null) {
            mMainView.goForward();
        }
    }

    /**
     * Causes the tab back/forward stack to be cleared once, if the given URL is the next URL
     * to be added to the stack.
     *
     * This is used to ensure that preloaded URLs that are not subsequently seen by the user do
     * not appear in the back stack.
     */
    public void clearBackStackWhenItemAdded(Pattern urlPattern) {
        mClearHistoryUrlPattern = urlPattern;
    }

    protected void persistThumbnail() {
        DataController.getInstance(mContext).saveThumbnail(this);
    }

    protected void deleteThumbnail() {
        DataController.getInstance(mContext).deleteThumbnail(this);
    }

    void updateCaptureFromBlob(byte[] blob) {
        synchronized (Tab.this) {
            if (mCapture == null) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.wrap(blob);
            try {
                mCapture.copyPixelsFromBuffer(buffer);
            } catch (RuntimeException rex) {
                Log.e(LOGTAG, "Load capture has mismatched sizes; buffer: "
                        + buffer.capacity() + " blob: " + blob.length
                        + "capture: " + mCapture.getByteCount());
                throw rex;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(100);
        builder.append(mId);
        builder.append(") has parent: ");
        if (getParent() != null) {
            builder.append("true[");
            builder.append(getParent().getId());
            builder.append("]");
        } else {
            builder.append("false");
        }
        builder.append(", incog: ");
        builder.append(isPrivateBrowsingEnabled());
        if (!isPrivateBrowsingEnabled()) {
            builder.append(", title: ");
            builder.append(getTitle());
            builder.append(", url: ");
            builder.append(getUrl());
        }
        return builder.toString();
    }

    private void handleProceededAfterSslError(SslError error) {
        if (error.getUrl().equals(mCurrentState.mUrl)) {
            // The security state should currently be SECURITY_STATE_SECURE.
            setSecurityState(SecurityState.SECURITY_STATE_BAD_CERTIFICATE);
            mCurrentState.mSslCertificateError = error;
        } else if (getSecurityState() == SecurityState.SECURITY_STATE_SECURE) {
            // The page's main resource is secure and this error is for a
            // sub-resource.
            setSecurityState(SecurityState.SECURITY_STATE_MIXED);
        }
    }
}

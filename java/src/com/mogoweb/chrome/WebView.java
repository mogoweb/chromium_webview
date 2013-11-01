// Copyright (c) 2013 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.mogoweb.chrome;

import java.util.Map;

import org.chromium.android_webview.AwBrowserContext;
import org.chromium.android_webview.AwContents;
import org.chromium.android_webview.AwLayoutSizer;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.ContentViewRenderView;
import org.chromium.content.browser.LoadUrlParams;
import org.chromium.content.browser.NavigationHistory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Rect;
import android.net.http.SslCertificate;
import android.os.Bundle;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.ValueCallback;
import android.webkit.WebViewDatabase;
import android.widget.FrameLayout;

import com.mogoweb.chrome.impl.ChromeAwContentsClientProxy;
import com.mogoweb.chrome.impl.ChromeSettingsProxy;
import com.mogoweb.chrome.impl.WebBackForwardListImpl;

/**
 * <p>A View that displays web pages. This class is the basis upon which you
 * can roll your own web browser or simply display some online content within your Activity.
 * It uses the WebKit rendering engine to display
 * web pages and includes methods to navigate forward and backward
 * through a history, zoom in and out, perform text searches and more.</p>
 * <p>To enable the built-in zoom, set
 * {@link #getSettings() WebSettings}.{@link WebSettings#setBuiltInZoomControls(boolean)}
 * (introduced in API level {@link android.os.Build.VERSION_CODES#CUPCAKE}).
 * <p>Note that, in order for your Activity to access the Internet and load web pages
 * in a WebView, you must add the {@code INTERNET} permissions to your
 * Android Manifest file:</p>
 * <pre>&lt;uses-permission android:name="android.permission.INTERNET" /></pre>
 *
 * <p>This must be a child of the <a
 * href="{@docRoot}guide/topics/manifest/manifest-element.html">{@code <manifest>}</a>
 * element.</p>
 *
 * <p>For more information, read
 * <a href="{@docRoot}guide/webapps/webview.html">Building Web Apps in WebView</a>.</p>
 *
 * <h3>Basic usage</h3>
 *
 * <p>By default, a WebView provides no browser-like widgets, does not
 * enable JavaScript and web page errors are ignored. If your goal is only
 * to display some HTML as a part of your UI, this is probably fine;
 * the user won't need to interact with the web page beyond reading
 * it, and the web page won't need to interact with the user. If you
 * actually want a full-blown web browser, then you probably want to
 * invoke the Browser application with a URL Intent rather than show it
 * with a WebView. For example:
 * <pre>
 * Uri uri = Uri.parse("http://www.example.com");
 * Intent intent = new Intent(Intent.ACTION_VIEW, uri);
 * startActivity(intent);
 * </pre>
 * <p>See {@link android.content.Intent} for more information.</p>
 *
 * <p>To provide a WebView in your own Activity, include a {@code <WebView>} in your layout,
 * or set the entire Activity window as a WebView during {@link
 * android.app.Activity#onCreate(Bundle) onCreate()}:</p>
 * <pre class="prettyprint">
 * WebView webview = new WebView(this);
 * setContentView(webview);
 * </pre>
 *
 * <p>Then load the desired web page:</p>
 * <pre>
 * // Simplest usage: note that an exception will NOT be thrown
 * // if there is an error loading this page (see below).
 * webview.loadUrl("http://slashdot.org/");
 *
 * // OR, you can also load from an HTML string:
 * String summary = "&lt;html>&lt;body>You scored &lt;b>192&lt;/b> points.&lt;/body>&lt;/html>";
 * webview.loadData(summary, "text/html", null);
 * // ... although note that there are restrictions on what this HTML can do.
 * // See the JavaDocs for {@link #loadData(String,String,String) loadData()} and {@link
 * #loadDataWithBaseURL(String,String,String,String,String) loadDataWithBaseURL()} for more info.
 * </pre>
 *
 * <p>A WebView has several customization points where you can add your
 * own behavior. These are:</p>
 *
 * <ul>
 *   <li>Creating and setting a {@link android.webkit.WebChromeClient} subclass.
 *       This class is called when something that might impact a
 *       browser UI happens, for instance, progress updates and
 *       JavaScript alerts are sent here (see <a
 * href="{@docRoot}guide/developing/debug-tasks.html#DebuggingWebPages">Debugging Tasks</a>).
 *   </li>
 *   <li>Creating and setting a {@link android.webkit.WebViewClient} subclass.
 *       It will be called when things happen that impact the
 *       rendering of the content, eg, errors or form submissions. You
 *       can also intercept URL loading here (via {@link
 * android.webkit.WebViewClient#shouldOverrideUrlLoading(WebView,String)
 * shouldOverrideUrlLoading()}).</li>
 *   <li>Modifying the {@link android.webkit.WebSettings}, such as
 * enabling JavaScript with {@link android.webkit.WebSettings#setJavaScriptEnabled(boolean)
 * setJavaScriptEnabled()}. </li>
 *   <li>Injecting Java objects into the WebView using the
 *       {@link android.webkit.WebView#addJavascriptInterface} method. This
 *       method allows you to inject Java objects into a page's JavaScript
 *       context, so that they can be accessed by JavaScript in the page.</li>
 * </ul>
 *
 * <p>Here's a more complicated example, showing error handling,
 *    settings, and progress notification:</p>
 *
 * <pre class="prettyprint">
 * // Let's display the progress in the activity title bar, like the
 * // browser app does.
 * getWindow().requestFeature(Window.FEATURE_PROGRESS);
 *
 * webview.getSettings().setJavaScriptEnabled(true);
 *
 * final Activity activity = this;
 * webview.setWebChromeClient(new WebChromeClient() {
 *   public void onProgressChanged(WebView view, int progress) {
 *     // Activities and WebViews measure progress with different scales.
 *     // The progress meter will automatically disappear when we reach 100%
 *     activity.setProgress(progress * 1000);
 *   }
 * });
 * webview.setWebViewClient(new WebViewClient() {
 *   public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
 *     Toast.makeText(activity, "Oh no! " + description, Toast.LENGTH_SHORT).show();
 *   }
 * });
 *
 * webview.loadUrl("http://slashdot.org/");
 * </pre>
 *
 * <h3>Cookie and window management</h3>
 *
 * <p>For obvious security reasons, your application has its own
 * cache, cookie store etc.&mdash;it does not share the Browser
 * application's data.
 * </p>
 *
 * <p>By default, requests by the HTML to open new windows are
 * ignored. This is true whether they be opened by JavaScript or by
 * the target attribute on a link. You can customize your
 * {@link WebChromeClient} to provide your own behaviour for opening multiple windows,
 * and render them in whatever manner you want.</p>
 *
 * <p>The standard behavior for an Activity is to be destroyed and
 * recreated when the device orientation or any other configuration changes. This will cause
 * the WebView to reload the current page. If you don't want that, you
 * can set your Activity to handle the {@code orientation} and {@code keyboardHidden}
 * changes, and then just leave the WebView alone. It'll automatically
 * re-orient itself as appropriate. Read <a
 * href="{@docRoot}guide/topics/resources/runtime-changes.html">Handling Runtime Changes</a> for
 * more information about how to handle configuration changes during runtime.</p>
 *
 *
 * <h3>Building web pages to support different screen densities</h3>
 *
 * <p>The screen density of a device is based on the screen resolution. A screen with low density
 * has fewer available pixels per inch, where a screen with high density
 * has more &mdash; sometimes significantly more &mdash; pixels per inch. The density of a
 * screen is important because, other things being equal, a UI element (such as a button) whose
 * height and width are defined in terms of screen pixels will appear larger on the lower density
 * screen and smaller on the higher density screen.
 * For simplicity, Android collapses all actual screen densities into three generalized densities:
 * high, medium, and low.</p>
 * <p>By default, WebView scales a web page so that it is drawn at a size that matches the default
 * appearance on a medium density screen. So, it applies 1.5x scaling on a high density screen
 * (because its pixels are smaller) and 0.75x scaling on a low density screen (because its pixels
 * are bigger).
 * Starting with API level {@link android.os.Build.VERSION_CODES#ECLAIR}, WebView supports DOM, CSS,
 * and meta tag features to help you (as a web developer) target screens with different screen
 * densities.</p>
 * <p>Here's a summary of the features you can use to handle different screen densities:</p>
 * <ul>
 * <li>The {@code window.devicePixelRatio} DOM property. The value of this property specifies the
 * default scaling factor used for the current device. For example, if the value of {@code
 * window.devicePixelRatio} is "1.0", then the device is considered a medium density (mdpi) device
 * and default scaling is not applied to the web page; if the value is "1.5", then the device is
 * considered a high density device (hdpi) and the page content is scaled 1.5x; if the
 * value is "0.75", then the device is considered a low density device (ldpi) and the content is
 * scaled 0.75x. However, if you specify the {@code "target-densitydpi"} meta property
 * (discussed below), then you can stop this default scaling behavior.</li>
 * <li>The {@code -webkit-device-pixel-ratio} CSS media query. Use this to specify the screen
 * densities for which this style sheet is to be used. The corresponding value should be either
 * "0.75", "1", or "1.5", to indicate that the styles are for devices with low density, medium
 * density, or high density screens, respectively. For example:
 * <pre>
 * &lt;link rel="stylesheet" media="screen and (-webkit-device-pixel-ratio:1.5)" href="hdpi.css" /&gt;</pre>
 * <p>The {@code hdpi.css} stylesheet is only used for devices with a screen pixel ration of 1.5,
 * which is the high density pixel ratio.</p>
 * </li>
 * <li>The {@code target-densitydpi} property for the {@code viewport} meta tag. You can use
 * this to specify the target density for which the web page is designed, using the following
 * values:
 * <ul>
 * <li>{@code device-dpi} - Use the device's native dpi as the target dpi. Default scaling never
 * occurs.</li>
 * <li>{@code high-dpi} - Use hdpi as the target dpi. Medium and low density screens scale down
 * as appropriate.</li>
 * <li>{@code medium-dpi} - Use mdpi as the target dpi. High density screens scale up and
 * low density screens scale down. This is also the default behavior.</li>
 * <li>{@code low-dpi} - Use ldpi as the target dpi. Medium and high density screens scale up
 * as appropriate.</li>
 * <li><em>{@code <value>}</em> - Specify a dpi value to use as the target dpi (accepted
 * values are 70-400).</li>
 * </ul>
 * <p>Here's an example meta tag to specify the target density:</p>
 * <pre>&lt;meta name="viewport" content="target-densitydpi=device-dpi" /&gt;</pre></li>
 * </ul>
 * <p>If you want to modify your web page for different densities, by using the {@code
 * -webkit-device-pixel-ratio} CSS media query and/or the {@code
 * window.devicePixelRatio} DOM property, then you should set the {@code target-densitydpi} meta
 * property to {@code device-dpi}. This stops Android from performing scaling in your web page and
 * allows you to make the necessary adjustments for each density via CSS and JavaScript.</p>
 *
 * <h3>HTML5 Video support</h3>
 *
 * <p>In order to support inline HTML5 video in your application, you need to have hardware
 * acceleration turned on, and set a {@link android.webkit.WebChromeClient}. For full screen support,
 * implementations of {@link WebChromeClient#onShowCustomView(View, WebChromeClient.CustomViewCallback)}
 * and {@link WebChromeClient#onHideCustomView()} are required,
 * {@link WebChromeClient#getVideoLoadingProgressView()} is optional.
 * </p>
 */
public class WebView extends FrameLayout {

    /**
     * URI scheme for telephone number.
     */
    public static final String SCHEME_TEL = "tel:";
    /**
     * URI scheme for email address.
     */
    public static final String SCHEME_MAILTO = "mailto:";
    /**
     * URI scheme for map address.
     */
    public static final String SCHEME_GEO = "geo:0,0?q=";

    /**
     * Interface to listen for find results.
     */
    public interface FindListener {
        /**
         * Notifies the listener about progress made by a find operation.
         *
         * @param activeMatchOrdinal the zero-based ordinal of the currently selected match
         * @param numberOfMatches how many matches have been found
         * @param isDoneCounting whether the find operation has actually completed. The listener
         *                       may be notified multiple times while the
         *                       operation is underway, and the numberOfMatches
         *                       value should not be considered final unless
         *                       isDoneCounting is true.
         */
        public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
            boolean isDoneCounting);
    }

    public static class HitTestResult {
        /**
         * Default HitTestResult, where the target is unknown.
         */
        public static final int UNKNOWN_TYPE = 0;
        /**
         * @deprecated This type is no longer used.
         */
        @Deprecated
        public static final int ANCHOR_TYPE = 1;
        /**
         * HitTestResult for hitting a phone number.
         */
        public static final int PHONE_TYPE = 2;
        /**
         * HitTestResult for hitting a map address.
         */
        public static final int GEO_TYPE = 3;
        /**
         * HitTestResult for hitting an email address.
         */
        public static final int EMAIL_TYPE = 4;
        /**
         * HitTestResult for hitting an HTML::img tag.
         */
        public static final int IMAGE_TYPE = 5;
        /**
         * @deprecated This type is no longer used.
         */
        @Deprecated
        public static final int IMAGE_ANCHOR_TYPE = 6;
        /**
         * HitTestResult for hitting a HTML::a tag with src=http.
         */
        public static final int SRC_ANCHOR_TYPE = 7;
        /**
         * HitTestResult for hitting a HTML::a tag with src=http + HTML::img.
         */
        public static final int SRC_IMAGE_ANCHOR_TYPE = 8;
        /**
         * HitTestResult for hitting an edit text area.
         */
        public static final int EDIT_TEXT_TYPE = 9;

        private int mType;
        private String mExtra;

        /**
         * @hide Only for use by WebViewProvider implementations
         */
        public HitTestResult() {
            mType = UNKNOWN_TYPE;
        }

        /**
         * @hide Only for use by WebViewProvider implementations
         */
        public void setType(int type) {
            mType = type;
        }

        /**
         * @hide Only for use by WebViewProvider implementations
         */
        public void setExtra(String extra) {
            mExtra = extra;
        }

        /**
         * Gets the type of the hit test result. See the XXX_TYPE constants
         * defined in this class.
         *
         * @return the type of the hit test result
         */
        public int getType() {
            return mType;
        }

        /**
         * Gets additional type-dependant information about the result. See
         * {@link WebView#getHitTestResult()} for details. May either be null
         * or contain extra information about this result.
         *
         * @return additional type-dependant information about the result
         */
        public String getExtra() {
            return mExtra;
        }
    }

    /** The closest thing to a WebView that Chromium has to offer. */
    private AwContents mAwContents;

    /** Implements some of AwContents. */
    private ContentViewCore mContentViewCore;

    /** Glue that passes calls from the Chromium view to a WebChromeClient. */
    private ChromeAwContentsClientProxy mAwContentsClient;

    /** Everything pertaining to the user's browsing session. */
    private AwBrowserContext mBrowserContext;

    /** Glue that passes calls from the Chromium view to its parent (us).  */
    private ChromeInternalAcccessAdapter mInternalAccessAdapter;

    // The target for content rendering.
    private ContentViewRenderView mContentViewRenderView;

    /**
     * Constructs a new WebView with a Context object.
     *
     * @param context a Context object used to access application assets
     */
    public WebView(Context context) {
        this(context, null);
    }

    /**
     * Constructs a new WebView with layout parameters.
     *
     * @param context a Context object used to access application assets
     * @param attrs an AttributeSet passed to our parent
     */
    public WebView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.webViewStyle);
    }

    /**
     * Constructs a new WebView with layout parameters and a default style.
     *
     * @param context a Context object used to access application assets
     * @param attrs an AttributeSet passed to our parent
     * @param defStyle the default style resource ID
     */
    public WebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (isInEditMode()) {
            return;  // Chromium isn't loaded in edit mode.
        }
// TODO(alex): chromium webview not support hardware accelerated yet.
//        try {
//            Activity activity = (Activity)context;
//            activity.getWindow().setFlags(
//                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
//                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
//
//        } catch(ClassCastException e) {
//            // Hope that hardware acceleration is enabled.
//        }

        SharedPreferences sharedPreferences = context.getSharedPreferences(
                "chromeview", Context.MODE_PRIVATE);
        // TODO(pwnall): is there a better way to get an AwBrowserContext?
        mBrowserContext = new AwBrowserContext(sharedPreferences);

        mInternalAccessAdapter = new ChromeInternalAcccessAdapter();
        mAwContentsClient = new ChromeAwContentsClientProxy(this);
        mAwContents = new AwContents(mBrowserContext, this, mInternalAccessAdapter,
                mAwContentsClient, false, new AwLayoutSizer(), true);
        mContentViewCore = mAwContents.getContentViewCore();

        mContentViewRenderView = new ContentViewRenderView(context) {
            @Override
            protected void onReadyToRender() {

            }
        };
    }

    //// Methods from android.webkit.WebView

    /**
     * Specifies whether the horizontal scrollbar has overlay style.
     *
     * @param overlay true if horizontal scrollbar should have overlay style
     */
    public void setHorizontalScrollbarOverlay(boolean overlay) {

    }

    /**
     * Specifies whether the vertical scrollbar has overlay style.
     *
     * @param overlay true if vertical scrollbar should have overlay style
     */
    public void setVerticalScrollbarOverlay(boolean overlay) {

    }

    /**
     * Gets whether horizontal scrollbar has overlay style.
     *
     * @return true if horizontal scrollbar has overlay style
     */
    public boolean overlayHorizontalScrollbar() {
        return false;
    }

    /**
     * Gets whether vertical scrollbar has overlay style.
     *
     * @return true if vertical scrollbar has overlay style
     */
    public boolean overlayVerticalScrollbar() {
        return false;
    }

    /**
     * Gets the SSL certificate for the main top-level page or null if there is
     * no certificate (the site is not secure).
     *
     * @return the SSL certificate for the main top-level page
     */
    public SslCertificate getCertificate() {
        return mAwContents.getCertificate();
    }

    /**
     * Sets a username and password pair for the specified host. This data is
     * used by the Webview to autocomplete username and password fields in web
     * forms. Note that this is unrelated to the credentials used for HTTP
     * authentication.
     *
     * @param host the host that required the credentials
     * @param username the username for the given host
     * @param password the password for the given host
     * @see WebViewDatabase#clearUsernamePassword
     * @see WebViewDatabase#hasUsernamePassword
     */
    public void savePassword(String host, String username, String password) {

    }

    /**
     * Stores HTTP authentication credentials for a given host and realm. This
     * method is intended to be used with
     * {@link WebViewClient#onReceivedHttpAuthRequest}.
     *
     * @param host the host to which the credentials apply
     * @param realm the realm to which the credentials apply
     * @param username the username
     * @param password the password
     * @see getHttpAuthUsernamePassword
     * @see WebViewDatabase#hasHttpAuthUsernamePassword
     * @see WebViewDatabase#clearHttpAuthUsernamePassword
     */
    public void setHttpAuthUsernamePassword(String host, String realm,
            String username, String password) {
        mAwContents.setHttpAuthUsernamePassword(host, realm, username, password);
    }

    /**
     * Retrieves HTTP authentication credentials for a given host and realm.
     * This method is intended to be used with
     * {@link WebViewClient#onReceivedHttpAuthRequest}.
     *
     * @param host the host to which the credentials apply
     * @param realm the realm to which the credentials apply
     * @return the credentials as a String array, if found. The first element
     *         is the username and the second element is the password. Null if
     *         no credentials are found.
     * @see setHttpAuthUsernamePassword
     * @see WebViewDatabase#hasHttpAuthUsernamePassword
     * @see WebViewDatabase#clearHttpAuthUsernamePassword
     */
    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        return mAwContents.getHttpAuthUsernamePassword(host, realm);
    }

    /**
     * Destroys the internal state of this WebView. This method should be called
     * after this WebView has been removed from the view system. No other
     * methods may be called on this WebView after destroy.
     */
    public void destroy() {
        mAwContents.destroy();
    }

    /**
     * Informs WebView of the network state. This is used to set
     * the JavaScript property window.navigator.isOnline and
     * generates the online/offline event as specified in HTML5, sec. 5.7.7
     *
     * @param networkUp a boolean indicating if network is available
     */
    public void setNetworkAvailable(boolean networkUp) {

    }

    /**
     * Saves the state of this WebView used in
     * {@link android.app.Activity#onSaveInstanceState}. Please note that this
     * method no longer stores the display data for this WebView. The previous
     * behavior could potentially leak files if {@link #restoreState} was never
     * called.
     *
     * @param outState the Bundle to store this WebView's state
     * @return the same copy of the back/forward list used to save the state. If
     *         saveState fails, the returned list will be null.
     */
    public WebBackForwardList saveState(Bundle outState) {
        mAwContents.saveState(outState);
        return copyBackForwardList();
    }

    /**
     * Restores the state of this WebView from the given Bundle. This method is
     * intended for use in {@link android.app.Activity#onRestoreInstanceState}
     * and should be called to restore the state of this WebView. If
     * it is called after this WebView has had a chance to build state (load
     * pages, create a back/forward list, etc.) there may be undesirable
     * side-effects. Please note that this method no longer restores the
     * display data for this WebView.
     *
     * @param inState the incoming Bundle of state
     * @return the restored back/forward list or null if restoreState failed
     */
    public WebBackForwardList restoreState(Bundle inState) {
        mAwContents.restoreState(inState);
        return copyBackForwardList();
    }

    /**
     * Loads the given URL with the specified additional HTTP headers.
     *
     * @param url the URL of the resource to load
     * @param additionalHttpHeaders the additional headers to be used in the
     *            HTTP request for this URL, specified as a map from name to
     *            value. Note that if this map contains any of the headers
     *            that are set by default by this WebView, such as those
     *            controlling caching, accept types or the User-Agent, their
     *            values may be overriden by this WebView's defaults.
     */
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setExtraHeaders(additionalHttpHeaders);
        mAwContents.loadUrl(loadUrlParams);
    }

    /**
     * Loads the given URL.
     *
     * @param url the URL of the resource to load
     */
    public void loadUrl(String url) {
        mAwContents.loadUrl(new LoadUrlParams(url));
    }

    /**
     * Loads the URL with postData using "POST" method into this WebView. If url
     * is not a network URL, it will be loaded with {link
     * {@link #loadUrl(String)} instead.
     *
     * @param url the URL of the resource to load
     * @param postData the data will be passed to "POST" request
     */
    public void postUrl(String url, byte[] postData) {
        mAwContents.loadUrl(LoadUrlParams.createLoadHttpPostParams(url, postData));
    }

    /**
     * Loads the given data into this WebView using a 'data' scheme URL.
     * <p>
     * Note that JavaScript's same origin policy means that script running in a
     * page loaded using this method will be unable to access content loaded
     * using any scheme other than 'data', including 'http(s)'. To avoid this
     * restriction, use {@link
     * #loadDataWithBaseURL(String,String,String,String,String)
     * loadDataWithBaseURL()} with an appropriate base URL.
     * <p>
     * The encoding parameter specifies whether the data is base64 or URL
     * encoded. If the data is base64 encoded, the value of the encoding
     * parameter must be 'base64'. For all other values of the parameter,
     * including null, it is assumed that the data uses ASCII encoding for
     * octets inside the range of safe URL characters and use the standard %xx
     * hex encoding of URLs for octets outside that range. For example, '#',
     * '%', '\', '?' should be replaced by %23, %25, %27, %3f respectively.
     * <p>
     * The 'data' scheme URL formed by this method uses the default US-ASCII
     * charset. If you need need to set a different charset, you should form a
     * 'data' scheme URL which explicitly specifies a charset parameter in the
     * mediatype portion of the URL and call {@link #loadUrl(String)} instead.
     * Note that the charset obtained from the mediatype portion of a data URL
     * always overrides that specified in the HTML or XML document itself.
     *
     * @param data a String of data in the given encoding
     * @param mimeType the MIME type of the data, e.g. 'text/html'
     * @param encoding the encoding of the data
     */
    public void loadData(String data, String mimeType, String encoding) {
        LoadUrlParams loadUrlParams = LoadUrlParams.createLoadDataParams(data,
                mimeType, encoding.equals("base64"));
        mAwContents.loadUrl(loadUrlParams);
    }

    /**
     * Loads the given data into this WebView, using baseUrl as the base URL for
     * the content. The base URL is used both to resolve relative URLs and when
     * applying JavaScript's same origin policy. The historyUrl is used for the
     * history entry.
     * <p>
     * Note that content specified in this way can access local device files
     * (via 'file' scheme URLs) only if baseUrl specifies a scheme other than
     * 'http', 'https', 'ftp', 'ftps', 'about' or 'javascript'.
     * <p>
     * If the base URL uses the data scheme, this method is equivalent to
     * calling {@link #loadData(String,String,String) loadData()} and the
     * historyUrl is ignored.
     *
     * @param baseUrl the URL to use as the page's base URL. If null defaults to
     *                'about:blank'.
     * @param data a String of data in the given encoding
     * @param mimeType the MIMEType of the data, e.g. 'text/html'. If null,
     *                 defaults to 'text/html'.
     * @param encoding the encoding of the data
     * @param historyUrl the URL to use as the history entry. If null defaults
     *                   to 'about:blank'.
     */
    public void loadDataWithBaseURL(String baseUrl, String data,
            String mimeType, String encoding, String historyUrl) {
        LoadUrlParams loadUrlParams =
                LoadUrlParams.createLoadDataParamsWithBaseUrl(data, mimeType,
                        encoding.equals("base64"), baseUrl, historyUrl);
        mAwContents.loadUrl(loadUrlParams);
    }

    /**
     * Saves the current view as a web archive.
     *
     * @param filename the filename where the archive should be placed
     */
    public void saveWebArchive(String filename) {
        ValueCallback<String> callback = new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) { }
        };
        mAwContents.saveWebArchive(filename, false, callback);
    }

    /**
     * Saves the current view as a web archive.
     *
     * @param basename the filename where the archive should be placed
     * @param autoname if false, takes basename to be a file. If true, basename
     *                 is assumed to be a directory in which a filename will be
     *                 chosen according to the URL of the current page.
     * @param callback called after the web archive has been saved. The
     *                 parameter for onReceiveValue will either be the filename
     *                 under which the file was saved, or null if saving the
     *                 file failed.
     */
    public void saveWebArchive(String basename, boolean autoname, ValueCallback<String> callback) {
        mAwContents.saveWebArchive(basename, autoname, callback);
    }

    /**
     * Stops the current load.
     */
    public void stopLoading() {
        mAwContents.stopLoading();
    }

    /**
     * Reloads the current URL.
     */
    public void reload() {
        mAwContents.reload();
    }

    /**
     * Gets whether this WebView has a back history item.
     *
     * @return true iff this WebView has a back history item
     */
    public boolean canGoBack() {
        return mAwContents.canGoBack();
    }

    /**
     * Goes back in the history of this WebView.
     */
    public void goBack() {
        mAwContents.goBack();
    }

    /**
     * Gets whether this WebView has a forward history item.
     *
     * @return true iff this Webview has a forward history item
     */
    public boolean canGoForward() {
        return mAwContents.canGoForward();
    }

    /**
     * Goes forward in the history of this WebView.
     */
    public void goForward() {
        mAwContents.goForward();
    }

    /**
     * Gets whether the page can go back or forward the given
     * number of steps.
     *
     * @param steps the negative or positive number of steps to move the
     *              history
     */
    public boolean canGoBackOrForward(int steps) {
        return mAwContents.canGoBackOrForward(steps);
    }

    /**
     * Goes to the history item that is the number of steps away from
     * the current item. Steps is negative if backward and positive
     * if forward.
     *
     * @param steps the number of steps to take back or forward in the back
     *              forward list
     */
    public void goBackOrForward(int steps) {
        mAwContents.goBackOrForward(steps);
    }

    /**
     * Gets whether private browsing is enabled in this WebView.
     */
    public boolean isPrivateBrowsingEnabled() {
        // TODO
        return false;
    }

    /**
     * Scrolls the contents of this WebView up by half the view size.
     *
     * @param top true to jump to the top of the page
     * @return true if the page was scrolled
     */
    public boolean pageUp(boolean top) {
        return mAwContents.pageUp(top);
    }

    /**
     * Scrolls the contents of this WebView down by half the page size.
     *
     * @param bottom true to jump to bottom of page
     * @return true if the page was scrolled
     */
    public boolean pageDown(boolean bottom) {
        return mAwContents.pageDown(bottom);
    }

    /**
     * Gets a new picture that captures the current contents of this WebView.
     * The picture is of the entire document being displayed, and is not
     * limited to the area currently displayed by this WebView. Also, the
     * picture is a static copy and is unaffected by later changes to the
     * content being displayed.
     * <p>
     * Note that due to internal changes, for API levels between
     * {@link android.os.Build.VERSION_CODES#HONEYCOMB} and
     * {@link android.os.Build.VERSION_CODES#ICE_CREAM_SANDWICH} inclusive, the
     * picture does not include fixed position elements or scrollable divs.
     *
     * @return a picture that captures the current contents of this WebView
     */
    public Picture capturePicture() {
        return mAwContents.capturePicture();
    }

    /**
     * Sets the initial scale for this WebView. 0 means default. If
     * {@link WebSettings#getUseWideViewPort()} is true, it zooms out all the
     * way. Otherwise it starts with 100%. If initial scale is greater than 0,
     * WebView starts with this value as initial scale.
     * Please note that unlike the scale properties in the viewport meta tag,
     * this method doesn't take the screen density into account.
     *
     * @param scaleInPercent the initial scale in percent
     */
    public void setInitialScale(int scaleInPercent) {
        //TODO
    }

    /**
     * Invokes the graphical zoom picker widget for this WebView. This will
     * result in the zoom widget appearing on the screen to control the zoom
     * level of this WebView.
     */
    public void invokeZoomPicker() {
        mAwContents.invokeZoomPicker();
    }

    /**
     * Gets a HitTestResult based on the current cursor node. If a HTML::a
     * tag is found and the anchor has a non-JavaScript URL, the HitTestResult
     * type is set to SRC_ANCHOR_TYPE and the URL is set in the "extra" field.
     * If the anchor does not have a URL or if it is a JavaScript URL, the type
     * will be UNKNOWN_TYPE and the URL has to be retrieved through
     * {@link #requestFocusNodeHref} asynchronously. If a HTML::img tag is
     * found, the HitTestResult type is set to IMAGE_TYPE and the URL is set in
     * the "extra" field. A type of
     * SRC_IMAGE_ANCHOR_TYPE indicates an anchor with a URL that has an image as
     * a child node. If a phone number is found, the HitTestResult type is set
     * to PHONE_TYPE and the phone number is set in the "extra" field of
     * HitTestResult. If a map address is found, the HitTestResult type is set
     * to GEO_TYPE and the address is set in the "extra" field of HitTestResult.
     * If an email address is found, the HitTestResult type is set to EMAIL_TYPE
     * and the email is set in the "extra" field of HitTestResult. Otherwise,
     * HitTestResult type is set to UNKNOWN_TYPE.
     */
    public HitTestResult getHitTestResult() {
        // TODO
        return null;
    }

    /**
     * Requests the anchor or image element URL at the last tapped point.
     * If hrefMsg is null, this method returns immediately and does not
     * dispatch hrefMsg to its target. If the tapped point hits an image,
     * an anchor, or an image in an anchor, the message associates
     * strings in named keys in its data. The value paired with the key
     * may be an empty string.
     *
     * @param hrefMsg the message to be dispatched with the result of the
     *                request. The message data contains three keys. "url"
     *                returns the anchor's href attribute. "title" returns the
     *                anchor's text. "src" returns the image's src attribute.
     */
    public void requestFocusNodeHref(Message hrefMsg) {
        mAwContents.requestFocusNodeHref(hrefMsg);
    }

    /**
     * Requests the URL of the image last touched by the user. msg will be sent
     * to its target with a String representing the URL as its object.
     *
     * @param msg the message to be dispatched with the result of the request
     *            as the data member with "url" as key. The result can be null.
     */
    public void requestImageRef(Message msg) {
        mAwContents.requestImageRef(msg);
    }

    /**
     * Gets the URL for the current page. This is not always the same as the URL
     * passed to WebViewClient.onPageStarted because although the load for
     * that URL has begun, the current page may not have changed.
     *
     * @return the URL for the current page
     */
//    @ViewDebug.ExportedProperty(category = "webview")
    public String getUrl() {
        return mAwContents.getUrl();
    }

    /**
     * Gets the original URL for the current page. This is not always the same
     * as the URL passed to WebViewClient.onPageStarted because although the
     * load for that URL has begun, the current page may not have changed.
     * Also, there may have been redirects resulting in a different URL to that
     * originally requested.
     *
     * @return the URL that was originally requested for the current page
     */
//    @ViewDebug.ExportedProperty(category = "webview")
    public String getOriginalUrl() {
        return mAwContents.getOriginalUrl();
    }

    /**
     * Gets the title for the current page. This is the title of the current page
     * until WebViewClient.onReceivedTitle is called.
     *
     * @return the title for the current page
     */
//    @ViewDebug.ExportedProperty(category = "webview")
    public String getTitle() {
        return mAwContents.getTitle();
    }

    /**
     * Gets the favicon for the current page. This is the favicon of the current
     * page until WebViewClient.onReceivedIcon is called.
     *
     * @return the favicon for the current page
     */
    public Bitmap getFavicon() {
        return mAwContents.getFavicon();
    }

    /**
     * Gets the progress for the current page.
     *
     * @return the progress for the current page between 0 and 100
     */
    public int getProgress() {
        // TODO
        return 0;
    }

    /**
     * Gets the height of the HTML content.
     *
     * @return the height of the HTML content
     */
//    @ViewDebug.ExportedProperty(category = "webview")
    public int getContentHeight() {
        return mAwContents.getContentHeightCss();
    }

    /**
     * Gets the width of the HTML content.
     *
     * @return the width of the HTML content
     * @hide
     */
//    @ViewDebug.ExportedProperty(category = "webview")
    public int getContentWidth() {
        return mAwContents.getContentWidthCss();
    }

    /**
     * Pauses all layout, parsing, and JavaScript timers for all WebViews. This
     * is a global requests, not restricted to just this WebView. This can be
     * useful if the application has been paused.
     */
    public void pauseTimers() {
        mAwContents.pauseTimers();
    }

    /**
     * Resumes all layout, parsing, and JavaScript timers for all WebViews.
     * This will resume dispatching all timers.
     */
    public void resumeTimers() {
        mAwContents.resumeTimers();
    }

    /**
     * Pauses any extra processing associated with this WebView and its
     * associated DOM, plugins, JavaScript etc. For example, if this WebView is
     * taken offscreen, this could be called to reduce unnecessary CPU or
     * network traffic. When this WebView is again "active", call onResume().
     * Note that this differs from pauseTimers(), which affects all WebViews.
     */
    public void onPause() {
        mAwContents.onPause();
    }

    /**
     * Resumes a WebView after a previous call to onPause().
     */
    public void onResume() {
        mAwContents.onResume();
    }

    /**
     * Gets whether this WebView is paused, meaning onPause() was called.
     * Calling onResume() sets the paused state back to false.
     *
     * @hide
     */
    public boolean isPaused() {
        return mAwContents.isPaused();
    }

    /**
     * Informs this WebView that memory is low so that it can free any available
     * memory.
     */
    public void freeMemory() {

    }

    /**
     * Clears the resource cache. Note that the cache is per-application, so
     * this will clear the cache for all WebViews used.
     *
     * @param includeDiskFiles if false, only the RAM cache is cleared
     */
    public void clearCache(boolean includeDiskFiles) {
        mAwContents.clearCache(includeDiskFiles);
    }

    /**
     * Removes the autocomplete popup from the currently focused form field, if
     * present. Note this only affects the display of the autocomplete popup,
     * it does not remove any saved form data from this WebView's store. To do
     * that, use {@link WebViewDatabase#clearFormData}.
     */
    public void clearFormData() {
        // TODO
    }

    /**
     * Tells this WebView to clear its internal back/forward list.
     */
    public void clearHistory() {
        mAwContents.clearHistory();
    }

    /**
     * Clears the SSL preferences table stored in response to proceeding with
     * SSL certificate errors.
     */
    public void clearSslPreferences() {
        mAwContents.clearSslPreferences();
    }

    /**
     * Gets the WebBackForwardList for this WebView. This contains the
     * back/forward list for use in querying each item in the history stack.
     * This is a copy of the private WebBackForwardList so it contains only a
     * snapshot of the current state. Multiple calls to this method may return
     * different objects. The object returned from this method will not be
     * updated to reflect any new state.
     */
    public WebBackForwardList copyBackForwardList() {
        NavigationHistory navHistory = mAwContents.getNavigationHistory();
        WebBackForwardListImpl backForwardList = new WebBackForwardListImpl(navHistory);

        return backForwardList.clone();
    }

    /**
     * Registers the listener to be notified as find-on-page operations
     * progress. This will replace the current listener.
     *
     * @param listener an implementation of {@link FindListener}
     */
    public void setFindListener(FindListener listener) {
        mAwContentsClient.setFindListener(listener);
    }

    /**
     * Highlights and scrolls to the next match found by
     * {@link #findAllAsync}, wrapping around page boundaries as necessary.
     * Notifies any registered {@link FindListener}. If {@link #findAllAsync(String)}
     * has not been called yet, or if {@link #clearMatches} has been called since the
     * last find operation, this function does nothing.
     *
     * @param forward the direction to search
     * @see #setFindListener
     */
    public void findNext(boolean forward) {
        mAwContents.findNext(forward);
    }

    /**
     * Finds all instances of find on the page and highlights them,
     * asynchronously. Notifies any registered {@link FindListener}.
     * Successive calls to this will cancel any pending searches.
     *
     * @param find the string to find.
     * @see #setFindListener
     */
    public void findAllAsync(String find) {
        mAwContents.findAllAsync(find);
    }

    /**
     * Starts an ActionMode for finding text in this WebView.  Only works if this
     * WebView is attached to the view system.
     *
     * @param text if non-null, will be the initial text to search for.
     *             Otherwise, the last String searched for in this WebView will
     *             be used to start.
     * @param showIme if true, show the IME, assuming the user will begin typing.
     *                If false and text is non-null, perform a find all.
     * @return true if the find dialog is shown, false otherwise
     */
    public boolean showFindDialog(String text, boolean showIme) {
        // TODO
        return false;
    }

    /**
     * Gets the first substring consisting of the address of a physical
     * location. Currently, only addresses in the United States are detected,
     * and consist of:
     * <ul>
     *   <li>a house number</li>
     *   <li>a street name</li>
     *   <li>a street type (Road, Circle, etc), either spelled out or
     *       abbreviated</li>
     *   <li>a city name</li>
     *   <li>a state or territory, either spelled out or two-letter abbr</li>
     *   <li>an optional 5 digit or 9 digit zip code</li>
     * </ul>
     * All names must be correctly capitalized, and the zip code, if present,
     * must be valid for the state. The street type must be a standard USPS
     * spelling or abbreviation. The state or territory must also be spelled
     * or abbreviated using USPS standards. The house number may not exceed
     * five digits.
     *
     * @param addr the string to search for addresses
     * @return the address, or if no address is found, null
     */
    public static String findAddress(String addr) {
        return null;
    }

    /**
     * Clears the highlighting surrounding text matches created by
     * {@link #findAllAsync}.
     */
    public void clearMatches() {
        mAwContents.clearMatches();
    }

    /**
     * Queries the document to see if it contains any image references. The
     * message object will be dispatched with arg1 being set to 1 if images
     * were found and 0 if the document does not reference any images.
     *
     * @param response the message that will be dispatched with the result
     */
    public void documentHasImages(Message response) {
        mAwContents.documentHasImages(response);
    }

    /**
     * Sets the WebViewClient that will receive various notifications and
     * requests. This will replace the current handler.
     *
     * @param client an implementation of WebViewClient
     */
    public void setWebViewClient(WebViewClient client) {
        mAwContentsClient.setWebViewClient(client);
    }

    /**
     * Registers the interface to be used when content can not be handled by
     * the rendering engine, and should be downloaded instead. This will replace
     * the current handler.
     *
     * @param listener an implementation of DownloadListener
     */
    public void setDownloadListener(DownloadListener listener) {
        mAwContentsClient.setDownloadListener(listener);
    }

    /**
     * Sets the chrome handler. This is an implementation of WebChromeClient for
     * use in handling JavaScript dialogs, favicons, titles, and the progress.
     * This will replace the current handler.
     *
     * @param client an implementation of WebChromeClient
     */
    public void setWebChromeClient(WebChromeClient client) {
        mAwContentsClient.setWebChromeClient(client);
    }

    /**
     * Injects the supplied Java object into this WebView. The object is
     * injected into the JavaScript context of the main frame, using the
     * supplied name. This allows the Java object's methods to be
     * accessed from JavaScript. For applications targeted to API
     * level {@link android.os.Build.VERSION_CODES#JELLY_BEAN_MR1}
     * and above, only public methods that are annotated with
     * {@link android.webkit.JavascriptInterface} can be accessed from JavaScript.
     * For applications targeted to API level {@link android.os.Build.VERSION_CODES#JELLY_BEAN} or below,
     * all public methods (including the inherited ones) can be accessed, see the
     * important security note below for implications.
     * <p> Note that injected objects will not
     * appear in JavaScript until the page is next (re)loaded. For example:
     * <pre>
     * class JsObject {
     *    {@literal @}JavascriptInterface
     *    public String toString() { return "injectedObject"; }
     * }
     * webView.addJavascriptInterface(new JsObject(), "injectedObject");
     * webView.loadData("<!DOCTYPE html><title></title>", "text/html", null);
     * webView.loadUrl("javascript:alert(injectedObject.toString())");</pre>
     * <p>
     * <strong>IMPORTANT:</strong>
     * <ul>
     * <li> This method can be used to allow JavaScript to control the host
     * application. This is a powerful feature, but also presents a security
     * risk for applications targeted to API level
     * {@link android.os.Build.VERSION_CODES#JELLY_BEAN} or below, because
     * JavaScript could use reflection to access an
     * injected object's public fields. Use of this method in a WebView
     * containing untrusted content could allow an attacker to manipulate the
     * host application in unintended ways, executing Java code with the
     * permissions of the host application. Use extreme care when using this
     * method in a WebView which could contain untrusted content.</li>
     * <li> JavaScript interacts with Java object on a private, background
     * thread of this WebView. Care is therefore required to maintain thread
     * safety.</li>
     * <li> The Java object's fields are not accessible.</li>
     * </ul>
     *
     * @param object the Java object to inject into this WebView's JavaScript
     *               context. Null values are ignored.
     * @param name the name used to expose the object in JavaScript
     */
    public void addJavascriptInterface(Object object, String name) {
        mAwContents.addPossiblyUnsafeJavascriptInterface(object, name,
                JavascriptInterface.class);
    }

    /**
     * Removes a previously injected Java object from this WebView. Note that
     * the removal will not be reflected in JavaScript until the page is next
     * (re)loaded. See {@link #addJavascriptInterface}.
     *
     * @param name the name used to expose the object in JavaScript
     */
    public void removeJavascriptInterface(String name) {
        mAwContents.removeJavascriptInterface(name);
    }

    /**
     * Gets the WebSettings object used to control the settings for this
     * WebView.
     *
     * @return a WebSettings object that can be used to control this WebView's
     *         settings
     */
    public WebSettings getSettings() {
        return new ChromeSettingsProxy(mAwContents);
    }

    public void flingScroll(int vx, int vy) {

    }

    /**
     * Performs zoom in in this WebView.
     *
     * @return true if zoom in succeeds, false if no zoom changes
     */
    public boolean zoomIn() {
        return mAwContents.zoomIn();
    }

    /**
     * Performs zoom out in this WebView.
     *
     * @return true if zoom out succeeds, false if no zoom changes
     */
    public boolean zoomOut() {
        return mAwContents.zoomOut();
    }

    ////Methods outside android.webkit.WebView.

    ////Forward a bunch of calls to the Chromium view.
    //// Lifted from chromium/src/android_webview/test/shell/src/org/chromium/android_webview/test/AwTestContainerView

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mAwContents.onConfigurationChanged(newConfig);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAwContents.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAwContents.onDetachedFromWindow();
    }

    @Override
    public void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        mAwContents.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return mAwContents.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mAwContents.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mAwContents.dispatchKeyEvent(event);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mAwContents.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        mAwContents.onSizeChanged(w, h, ow, oh);
    }

    @Override
    public void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        mAwContents.onContainerViewOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }

    @Override
    public void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mAwContents != null) {
            mAwContents.onContainerViewScrollChanged(l, t, oldl, oldt);
        }
    }

    @Override
    public void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        mAwContents.onVisibilityChanged(changedView, visibility);
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mAwContents.onWindowVisibilityChanged(visibility);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);
        return mAwContents.onTouchEvent(ev);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mAwContents.onDraw(canvas);
        super.onDraw(canvas);
    }

    /** Glue that passes calls from the Chromium view to its container (us). */
    private class ChromeInternalAcccessAdapter implements AwContents.InternalAccessDelegate {
      //// Lifted from chromium/src/android_webview/test/shell/src/org/chromium/android_webview/test/AwTestContainerView
      @Override
      public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return WebView.this.drawChild(canvas, child, drawingTime);
      }

      @Override
      public boolean super_onKeyUp(int keyCode, KeyEvent event) {
        return WebView.super.onKeyUp(keyCode, event);
      }

      @Override
      public boolean super_dispatchKeyEventPreIme(KeyEvent event) {
        return WebView.super.dispatchKeyEventPreIme(event);
      }

      @Override
      public boolean super_dispatchKeyEvent(KeyEvent event) {
        return WebView.super.dispatchKeyEvent(event);
      }

      @Override
      public boolean super_onGenericMotionEvent(MotionEvent event) {
        return WebView.super.onGenericMotionEvent(event);
      }

      @Override
      public void super_onConfigurationChanged(Configuration newConfig) {
          WebView.super.onConfigurationChanged(newConfig);
      }

      @Override
      public void super_scrollTo(int scrollX, int scrollY) {
          // We're intentionally not calling super.scrollTo here to make testing easier.
          WebView.this.scrollTo(scrollX, scrollY);
      }

      @Override
      public void overScrollBy(int deltaX, int deltaY,
              int scrollX, int scrollY,
              int scrollRangeX, int scrollRangeY,
              int maxOverScrollX, int maxOverScrollY,
              boolean isTouchEvent) {
          // We're intentionally not calling super.scrollTo here to make testing easier.
          WebView.this.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                   scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
      }

      @Override
      public void onScrollChanged(int lPix, int tPix, int oldlPix, int oldtPix) {
          WebView.this.onScrollChanged(lPix, tPix, oldlPix, oldtPix);
      }

      @Override
      public boolean awakenScrollBars() {
        return WebView.this.awakenScrollBars();
      }

      @Override
      public boolean super_awakenScrollBars(int startDelay, boolean invalidate) {
        return WebView.super.awakenScrollBars(startDelay, invalidate);
      }

      @Override
      public void setMeasuredDimension(int measuredWidth, int measuredHeight) {
          WebView.this.setMeasuredDimension(measuredWidth, measuredHeight);
      }

      @Override
      public int super_getScrollBarStyle() {
          return WebView.super.getScrollBarStyle();
      }

      @Override
      public boolean requestDrawGL(Canvas canvas) {
        if (canvas != null) {
          if (canvas.isHardwareAccelerated()) {
            // TODO(pwnall): figure out what AwContents wants from us, and do it;
            //               most likely something to do with
            //               AwContents.getAwDrawGLFunction()
            return false;
          } else {
            return false;
          }
        } else {
          if (WebView.this.isHardwareAccelerated()) {
            // TODO(pwnall): figure out what AwContents wants from us, and do it;
            //               most likely something to do with
            //               AwContents.getAwDrawGLFunction()
            return false;
          } else {
            return false;
          }
        }
      }
    }
}

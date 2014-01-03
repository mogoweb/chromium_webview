// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.Browser;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chromium.base.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.content.browser.ContentViewCore;

import java.net.URISyntaxException;

/**
 * Java side of Android implementation of the website settings UI.
 */
class WebsiteSettingsPopup implements OnClickListener {
    private static final String HELP_URL =
            "http://www.google.com/support/chrome/bin/answer.py?answer=95617";
    private final Context mContext;
    private final Dialog mDialog;
    private final LinearLayout mContainer;
    private final ContentViewCore mContentViewCore;
    private final int mPadding;
    private TextView mCertificateViewer, mMoreInfoLink;
    private String mLinkUrl;

    private WebsiteSettingsPopup(Context context, ContentViewCore contentViewCore,
            final int nativeWebsiteSettingsPopup) {
        mContext = context;
        mDialog = new Dialog(mContext);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                assert nativeWebsiteSettingsPopup != 0;
                nativeDestroy(nativeWebsiteSettingsPopup);
            }
        });
        mContainer = new LinearLayout(mContext);
        mContainer.setOrientation(LinearLayout.VERTICAL);
        mContentViewCore = contentViewCore;
        mPadding = (int) context.getResources().getDimension(R.dimen.certificate_viewer_padding);
        mContainer.setPadding(mPadding, 0, mPadding, 0);
    }

    /** Adds a section, which contains an icon, a headline, and a description. */
    @CalledByNative
    private void addSection(int enumeratedIconId, String headline, String description) {
        View section = LayoutInflater.from(mContext).inflate(R.layout.website_settings, null);
        ImageView i = (ImageView) section.findViewById(R.id.website_settings_icon);
        int drawableId = ResourceId.mapToDrawableId(enumeratedIconId);
        i.setImageResource(drawableId);

        TextView h = (TextView) section.findViewById(R.id.website_settings_headline);
        h.setText(headline);
        if (TextUtils.isEmpty(headline)) h.setVisibility(View.GONE);

        TextView d = (TextView) section.findViewById(R.id.website_settings_description);
        d.setText(description);
        if (TextUtils.isEmpty(description)) d.setVisibility(View.GONE);

        mContainer.addView(section);
    }

    /** Adds a horizontal dividing line to separate sections. */
    @CalledByNative
    private void addDivider() {
        View divider = new View(mContext);
        final int dividerHeight = (int) (2 * mContext.getResources().getDisplayMetrics().density);
        divider.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dividerHeight));
        divider.setBackgroundColor(Color.GRAY);
        mContainer.addView(divider);
    }

    @CalledByNative
    private void setCertificateViewer(String label) {
        assert mCertificateViewer == null;
        mCertificateViewer =  new TextView(mContext);
        mCertificateViewer.setText(Html.fromHtml("<a href='#'>" + label + "</a>"));
        mCertificateViewer.setOnClickListener(this);
        mCertificateViewer.setPadding(0, 0, 0, mPadding);
        mContainer.addView(mCertificateViewer);
    }

    @CalledByNative
    private void addMoreInfoLink(String linkText) {
        addUrl(linkText, HELP_URL);
    }

    /** Adds a section containing a description and a hyperlink. */
    private void addUrl(String label, String url) {
        mMoreInfoLink = new TextView(mContext);
        mLinkUrl = url;
        mMoreInfoLink.setText(Html.fromHtml("<a href='#'>" + label + "</a>"));
        mMoreInfoLink.setPadding(0, mPadding, 0, mPadding);
        mMoreInfoLink.setOnClickListener(this);
        mContainer.addView(mMoreInfoLink);
    }

    /** Displays the WebsiteSettingsPopup. */
    @CalledByNative
    private void show() {
        ScrollView scrollView = new ScrollView(mContext);
        scrollView.addView(mContainer);
        mDialog.addContentView(scrollView,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
        mDialog.show();
    }

    @Override
    public void onClick(View v) {
        mDialog.dismiss();
        if (mCertificateViewer == v) {
            byte[][] certChain = nativeGetCertificateChain(mContentViewCore);
            CertificateViewer.showCertificateChain(mContext, certChain);
        } else if (mMoreInfoLink == v) {
            try {
                Intent i = Intent.parseUri(mLinkUrl, Intent.URI_INTENT_SCHEME);
                i.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
                i.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());
                mContext.startActivity(i);
            } catch (URISyntaxException ex) {}
        }
    }

    @CalledByNative
    private static WebsiteSettingsPopup create(Context context, ContentViewCore contentViewCore,
            int nativeWebsiteSettingsPopup) {
        return new WebsiteSettingsPopup(context, contentViewCore, nativeWebsiteSettingsPopup);
    }

    private native void nativeDestroy(int nativeWebsiteSettingsPopupAndroid);
    private native byte[][] nativeGetCertificateChain(ContentViewCore contentViewCore);
}
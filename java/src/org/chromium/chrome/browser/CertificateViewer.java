// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.net.http.SslCertificate;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.chromium.chrome.R;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

/**
 * UI component for displaying certificate information.
 */
class CertificateViewer implements OnItemSelectedListener {
    private static final String X_509 = "X.509";
    private final Context mContext;
    private final ArrayList<LinearLayout> mViews;
    private final ArrayList<String> mTitles;
    private final int mPadding;
    private CertificateFactory mCertificateFactory;

    /**
     * Show a dialog with the provided certificate information.
     *
     * @param context The context this view should display in.
     * @param derData DER-encoded data representing a X509 certificate chain.
     */
    public static void showCertificateChain(Context context, byte[][] derData) {
        CertificateViewer viewer = new CertificateViewer(context);
        viewer.showCertificateChain(derData);
    }

    private CertificateViewer(Context context) {
        mContext = context;
        mViews = new ArrayList<LinearLayout>();
        mTitles = new ArrayList<String>();
        mPadding = (int) context.getResources().getDimension(
                R.dimen.certificate_viewer_padding) / 2;
    }

    // Show information about an array of DER-encoded data representing a X509 certificate chain.
    // A spinner will be displayed allowing the user to select which certificate to display.
    private void showCertificateChain(byte[][] derData) {
        for (int i = 0; i < derData.length; i++) {
            addCertificate(derData[i]);
        }
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(mContext,
                android.R.layout.simple_spinner_item,
                mTitles);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinner = new Spinner(mContext);
        spinner.setAdapter(arrayAdapter);
        spinner.setOnItemSelectedListener(this);

        LinearLayout container = new LinearLayout(mContext);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(spinner);

        for (int i = 0; i < mViews.size(); ++i) {
            LinearLayout certificateView = mViews.get(i);
            if (i != 0) {
                certificateView.setVisibility(LinearLayout.GONE);
            }
            container.addView(certificateView);
        }

        showDialogForView(container);
    }

    // Displays a dialog with scrolling for the given view.
    private void showDialogForView(View view) {
        Dialog dialog = new Dialog(mContext);
        dialog.setTitle(R.string.certtitle);
        ScrollView scrollView = new ScrollView(mContext);
        scrollView.addView(view);
        dialog.addContentView(scrollView,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
        dialog.show();
    }

    private void addCertificate(byte[] derData) {
        try {
            if (mCertificateFactory == null) {
                mCertificateFactory = CertificateFactory.getInstance(X_509);
            }
            Certificate cert = mCertificateFactory.generateCertificate(
                    new ByteArrayInputStream(derData));
            addCertificateDetails(cert, getDigest(derData, "SHA-256"), getDigest(derData, "SHA-1"));
        } catch (CertificateException e) {
            Log.e("CertViewer", "Error parsing certificate" + e.toString());
        }
    }

    private void addCertificateDetails(Certificate cert, byte[] sha256Digest, byte[] sha1Digest) {
        LinearLayout certificateView = new LinearLayout(mContext);
        mViews.add(certificateView);
        certificateView.setOrientation(LinearLayout.VERTICAL);

        X509Certificate x509 = (X509Certificate) cert;
        SslCertificate sslCert = new SslCertificate(x509);

        mTitles.add(sslCert.getIssuedTo().getCName());

        addSectionTitle(certificateView, nativeGetCertIssuedToText());
        addItem(certificateView, nativeGetCertInfoCommonNameText(),
                sslCert.getIssuedTo().getCName());
        addItem(certificateView, nativeGetCertInfoOrganizationText(),
                sslCert.getIssuedTo().getOName());
        addItem(certificateView, nativeGetCertInfoOrganizationUnitText(),
                sslCert.getIssuedTo().getUName());
        addItem(certificateView, nativeGetCertInfoSerialNumberText(),
                formatBytes(x509.getSerialNumber().toByteArray(), ':'));

        addSectionTitle(certificateView, nativeGetCertIssuedByText());
        addItem(certificateView, nativeGetCertInfoCommonNameText(),
                sslCert.getIssuedBy().getCName());
        addItem(certificateView, nativeGetCertInfoOrganizationText(),
                sslCert.getIssuedBy().getOName());
        addItem(certificateView, nativeGetCertInfoOrganizationUnitText(),
                sslCert.getIssuedBy().getUName());

        addSectionTitle(certificateView, nativeGetCertValidityText());
        java.text.DateFormat dateFormat = DateFormat.getDateFormat(mContext);
        addItem(certificateView, nativeGetCertIssuedOnText(),
                dateFormat.format(sslCert.getValidNotBeforeDate()));
        addItem(certificateView, nativeGetCertExpiresOnText(),
                dateFormat.format(sslCert.getValidNotAfterDate()));

        addSectionTitle(certificateView, nativeGetCertFingerprintsText());
        addItem(certificateView, nativeGetCertSHA256FingerprintText(),
                formatBytes(sha256Digest, ' '));
        addItem(certificateView, nativeGetCertSHA1FingerprintText(),
                formatBytes(sha1Digest, ' '));
    }

    private void addSectionTitle(LinearLayout certificateView, String label) {
        TextView title = addLabel(certificateView, label);
        title.setAllCaps(true);
    }

    private void addItem(LinearLayout certificateView, String label, String value) {
        if (value.isEmpty()) return;

        addLabel(certificateView, label);
        addValue(certificateView, value);
    }

    private TextView addLabel(LinearLayout certificateView, String label) {
        TextView t = new TextView(mContext);
        t.setPadding(mPadding, mPadding / 2, mPadding, 0);
        t.setText(label);
        t.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        certificateView.addView(t);
        return t;
    }

    private void addValue(LinearLayout certificateView, String value) {
        TextView t = new TextView(mContext);
        t.setText(value);
        t.setPadding(mPadding, 0, mPadding, mPadding / 2);
        certificateView.addView(t);
    }

    private static String formatBytes(byte[] bytes, char separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i != bytes.length - 1) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }

    private static byte[] getDigest(byte[] bytes, String algorithm) {
      try {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(bytes);
        return md.digest();
      } catch (java.security.NoSuchAlgorithmException e) {
        return null;
      }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        for (int i = 0; i < mViews.size(); ++i) {
            mViews.get(i).setVisibility(
                    i == position ? LinearLayout.VISIBLE : LinearLayout.GONE);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private static native String nativeGetCertIssuedToText();
    private static native String nativeGetCertInfoCommonNameText();
    private static native String nativeGetCertInfoOrganizationText();
    private static native String nativeGetCertInfoSerialNumberText();
    private static native String nativeGetCertInfoOrganizationUnitText();
    private static native String nativeGetCertIssuedByText();
    private static native String nativeGetCertValidityText();
    private static native String nativeGetCertIssuedOnText();
    private static native String nativeGetCertExpiresOnText();
    private static native String nativeGetCertFingerprintsText();
    private static native String nativeGetCertSHA256FingerprintText();
    private static native String nativeGetCertSHA1FingerprintText();
}

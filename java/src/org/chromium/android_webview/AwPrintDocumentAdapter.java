// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.webkit.ValueCallback;


/**
 * Adapter for printing Webview. This class implements the abstract
 * system class PrintDocumentAdapter and hides all printing details from
 * the developer.
 */
public class AwPrintDocumentAdapter extends PrintDocumentAdapter {

    private AwPdfExporter mPdfExporter;
    private PrintAttributes mAttributes;

    /**
     * Constructor.
     *
     * @param pdfExporter The PDF exporter to export the webview contents to a PDF file.
     */
    public AwPrintDocumentAdapter(AwPdfExporter pdfExporter) {
        mPdfExporter = pdfExporter;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
            CancellationSignal cancellationSignal, LayoutResultCallback callback,
            Bundle metadata) {
        mAttributes = newAttributes;
        // TODO(sgurun) pass a meaningful string once b/10705082 is resolved
        PrintDocumentInfo documentInfo = new PrintDocumentInfo
                .Builder("webview")
                .build();
        // TODO(sgurun) once componentization is done, do layout changes and
        // generate PDF here, set the page range information to documentinfo
        // and call onLayoutFinished with true/false depending on whether
        // layout actually changed.
        callback.onLayoutFinished(documentInfo, true);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
            CancellationSignal cancellationSignal, final WriteResultCallback callback) {
        mPdfExporter.exportToPdf(destination, mAttributes, new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean value) {
                if (value) {
                    callback.onWriteFinished(new PageRange[] { PageRange.ALL_PAGES });
                } else {
                    // TODO(sgurun) provide a localized error message
                    callback.onWriteFailed(null);
                }
            }
        }, cancellationSignal);
    }
}


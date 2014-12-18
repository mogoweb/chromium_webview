// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.android_webview;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.ValueCallback;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * Export the android webview as a PDF.
 * @TODO(sgurun) explain the ownership of this class and its native counterpart
 */
@JNINamespace("android_webview")
public class AwPdfExporter {

    private static final String TAG = "AwPdfExporter";
    private long mNativeAwPdfExporter;
    // TODO(sgurun) result callback should return an int/object indicating errors.
    // potential errors: invalid print parameters, already pending, IO error
    private ValueCallback<Boolean> mResultCallback;
    private PrintAttributes mAttributes;
    private ParcelFileDescriptor mFd;
    // Maintain a reference to the top level object (i.e. WebView) since in a common
    // use case (offscreen webview) application may expect the framework's print manager
    // to own the Webview (via PrintDocumentAdapter).
    // NOTE: it looks unused, but please do not remove this reference.
    private ViewGroup mContainerView;

    AwPdfExporter(ViewGroup containerView) {
        setContainerView(containerView);
    }

    public void setContainerView(ViewGroup containerView) {
        mContainerView = containerView;
    }

    public void exportToPdf(final ParcelFileDescriptor fd, PrintAttributes attributes,
            ValueCallback<Boolean> resultCallback, CancellationSignal cancellationSignal) {

        if (fd == null) {
            throw new IllegalArgumentException("fd cannot be null");
        }
        if (resultCallback == null) {
            throw new IllegalArgumentException("resultCallback cannot be null");
        }
        if (mResultCallback != null) {
            throw new IllegalStateException("printing is already pending");
        }
        if (attributes.getMediaSize() == null) {
            throw new  IllegalArgumentException("attributes must specify a media size");
        }
        if (attributes.getResolution() == null) {
            throw new IllegalArgumentException("attributes must specify print resolution");
        }
        if (attributes.getMinMargins() == null) {
            throw new IllegalArgumentException("attributes must specify margins");
        }
        if (mNativeAwPdfExporter == 0) {
            resultCallback.onReceiveValue(false);
            return;
        }
        mResultCallback = resultCallback;
        mAttributes = attributes;
        mFd = fd;
        nativeExportToPdf(mNativeAwPdfExporter, mFd.getFd(), cancellationSignal);
    }

    @CalledByNative
    private void setNativeAwPdfExporter(long nativePdfExporter) {
        mNativeAwPdfExporter = nativePdfExporter;
        // Handle the cornercase that Webview.Destroy is called before the native side
        // has a chance to complete the pdf exporting.
        if (nativePdfExporter == 0 && mResultCallback != null) {
            mResultCallback.onReceiveValue(false);
            mResultCallback = null;
        }
    }

    private static int getPrintDpi(PrintAttributes attributes) {
        // TODO(sgurun) android print attributes support horizontal and
        // vertical DPI. Chrome has only one DPI. Revisit this.
        int horizontalDpi = attributes.getResolution().getHorizontalDpi();
        int verticalDpi = attributes.getResolution().getVerticalDpi();
        if (horizontalDpi != verticalDpi) {
            Log.w(TAG, "Horizontal and vertical DPIs differ. Using horizontal DPI " +
                    " hDpi=" + horizontalDpi + " vDPI=" + verticalDpi);
        }
        return horizontalDpi;
    }

    @CalledByNative
    private void didExportPdf(boolean success) {
        mResultCallback.onReceiveValue(success);
        mResultCallback = null;
        mAttributes = null;
        // The caller should close the file.
        mFd = null;
    }

    @CalledByNative
    private int getPageWidth() {
        return mAttributes.getMediaSize().getWidthMils();
    }

    @CalledByNative
    private int getPageHeight() {
        return mAttributes.getMediaSize().getHeightMils();
    }

    @CalledByNative
    private int getDpi() {
        return getPrintDpi(mAttributes);
    }

    @CalledByNative
    private int getLeftMargin() {
        return mAttributes.getMinMargins().getLeftMils();
    }

    @CalledByNative
    private int getRightMargin() {
        return mAttributes.getMinMargins().getRightMils();
    }

    @CalledByNative
    private int getTopMargin() {
        return mAttributes.getMinMargins().getTopMils();
    }

    @CalledByNative
    private int getBottomMargin() {
        return mAttributes.getMinMargins().getBottomMils();
    }

    private native void nativeExportToPdf(long nativeAwPdfExporter, int fd,
            CancellationSignal cancellationSignal);
}

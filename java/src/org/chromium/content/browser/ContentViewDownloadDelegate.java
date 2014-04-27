// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

/**
 * Interface to be implemented by the embedder to handle file downloads.
 */
public interface ContentViewDownloadDelegate {
    /**
    * Notify the host application that a file should be downloaded. Replaces
    * onDownloadStart from DownloadListener.
    * @param downloadInfo Information about the requested download.
    */
    void requestHttpGetDownload(DownloadInfo downloadInfo);

    /**
     * Notify the host application that a download is started.
     * @param filename File name of the downloaded file.
     * @param mimeType Mime of the downloaded item.
     */
    void onDownloadStarted(String filename, String mimeType);

    /**
     * Notify the host application that a download has an extension indicating
     * a dangerous file type.
     * @param filename File name of the downloaded file.
     * @param downloadId The download id.
     */
    void onDangerousDownload(String filename, int downloadId);
}

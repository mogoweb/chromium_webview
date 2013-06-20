///*
// * Copyright (C) 2010 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.mogoweb.browser;
//
//import android.app.ProgressDialog;
//import android.app.WallpaperManager;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.graphics.Canvas;
//import android.graphics.drawable.Drawable;
//import android.util.Log;
//import android.view.MenuItem;
//import android.view.MenuItem.OnMenuItemClickListener;
//import java.io.BufferedInputStream;
//import java.io.ByteArrayInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.net.MalformedURLException;
//import java.net.URL;
//
///**
// * Handle setWallpaper requests
// *
// */
//public class WallpaperHandler extends Thread
//        implements OnMenuItemClickListener, DialogInterface.OnCancelListener {
//
//    private static final String LOGTAG = "WallpaperHandler";
//    // This should be large enough for BitmapFactory to decode the header so
//    // that we can mark and reset the input stream to avoid duplicate network i/o
//    private static final int BUFFER_SIZE = 128 * 1024;
//
//    private Context mContext;
//    private String  mUrl;
//    private ProgressDialog mWallpaperProgress;
//    private boolean mCanceled = false;
//
//    public WallpaperHandler(Context context, String url) {
//        mContext = context;
//        mUrl = url;
//    }
//
//    @Override
//    public void onCancel(DialogInterface dialog) {
//        mCanceled = true;
//    }
//
//    @Override
//    public boolean onMenuItemClick(MenuItem item) {
//        if (mUrl != null && getState() == State.NEW) {
//            // The user may have tried to set a image with a large file size as
//            // their background so it may take a few moments to perform the
//            // operation.
//            // Display a progress spinner while it is working.
//            mWallpaperProgress = new ProgressDialog(mContext);
//            mWallpaperProgress.setIndeterminate(true);
//            mWallpaperProgress.setMessage(mContext.getResources()
//                    .getText(R.string.progress_dialog_setting_wallpaper));
//            mWallpaperProgress.setCancelable(true);
//            mWallpaperProgress.setOnCancelListener(this);
//            mWallpaperProgress.show();
//            start();
//        }
//        return true;
//    }
//
//    @Override
//    public void run() {
//        WallpaperManager wm = WallpaperManager.getInstance(mContext);
//        Drawable oldWallpaper = wm.getDrawable();
//        InputStream inputstream = null;
//        try {
//            // TODO: This will cause the resource to be downloaded again, when
//            // we should in most cases be able to grab it from the cache. To fix
//            // this we should query WebCore to see if we can access a cached
//            // version and instead open an input stream on that. This pattern
//            // could also be used in the download manager where the same problem
//            // exists.
//            inputstream = openStream();
//            if (inputstream != null) {
//                if (!inputstream.markSupported()) {
//                    inputstream = new BufferedInputStream(inputstream, BUFFER_SIZE);
//                }
//                inputstream.mark(BUFFER_SIZE);
//                BitmapFactory.Options options = new BitmapFactory.Options();
//                options.inJustDecodeBounds = true;
//                // We give decodeStream a wrapped input stream so it doesn't
//                // mess with our mark (currently it sets a mark of 1024)
//                BitmapFactory.decodeStream(
//                        new BufferedInputStream(inputstream), null, options);
//                int maxWidth = wm.getDesiredMinimumWidth();
//                int maxHeight = wm.getDesiredMinimumHeight();
//                // Give maxWidth and maxHeight some leeway
//                maxWidth *= 1.25;
//                maxHeight *= 1.25;
//                int bmWidth = options.outWidth;
//                int bmHeight = options.outHeight;
//
//                int scale = 1;
//                while (bmWidth > maxWidth || bmHeight > maxHeight) {
//                    scale <<= 1;
//                    bmWidth >>= 1;
//                    bmHeight >>= 1;
//                }
//                options.inJustDecodeBounds = false;
//                options.inSampleSize = scale;
//                try {
//                    inputstream.reset();
//                } catch (IOException e) {
//                    // BitmapFactory read more than we could buffer
//                    // Re-open the stream
//                    inputstream.close();
//                    inputstream = openStream();
//                }
//                Bitmap scaledWallpaper = BitmapFactory.decodeStream(inputstream,
//                        null, options);
//                if (scaledWallpaper != null) {
//                    wm.setBitmap(scaledWallpaper);
//                } else {
//                    Log.e(LOGTAG, "Unable to set new wallpaper, " +
//                            "decodeStream returned null.");
//                }
//            }
//        } catch (IOException e) {
//            Log.e(LOGTAG, "Unable to set new wallpaper");
//            // Act as though the user canceled the operation so we try to
//            // restore the old wallpaper.
//            mCanceled = true;
//        } finally {
//            if (inputstream != null) {
//                try {
//                    inputstream.close();
//                } catch (IOException e) {
//                    // Ignore
//                }
//            }
//        }
//
//        if (mCanceled) {
//            // Restore the old wallpaper if the user cancelled whilst we were
//            // setting
//            // the new wallpaper.
//            int width = oldWallpaper.getIntrinsicWidth();
//            int height = oldWallpaper.getIntrinsicHeight();
//            Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
//            Canvas canvas = new Canvas(bm);
//            oldWallpaper.setBounds(0, 0, width, height);
//            oldWallpaper.draw(canvas);
//            canvas.setBitmap(null);
//            try {
//                wm.setBitmap(bm);
//            } catch (IOException e) {
//                Log.e(LOGTAG, "Unable to restore old wallpaper.");
//            }
//            mCanceled = false;
//        }
//
//        if (mWallpaperProgress.isShowing()) {
//            mWallpaperProgress.dismiss();
//        }
//    }
//
//    /**
//     * Opens the input stream for the URL that the class should
//     * use to set the wallpaper. Abstracts the difference between
//     * standard URLs and data URLs.
//     * @return An open InputStream for the data at the URL
//     * @throws IOException if there is an error opening the URL stream
//     * @throws MalformedURLException if the URL is malformed
//     */
//    private InputStream openStream() throws IOException, MalformedURLException {
//        InputStream inputStream = null;
//        if (DataUri.isDataUri(mUrl)) {
//            DataUri dataUri = new DataUri(mUrl);
//            inputStream = new ByteArrayInputStream(dataUri.getData());
//        } else {
//            URL url = new URL(mUrl);
//            inputStream = url.openStream();
//        }
//        return inputStream;
//    }
//}

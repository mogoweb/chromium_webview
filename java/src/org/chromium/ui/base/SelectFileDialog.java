// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.base;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import org.chromium.base.CalledByNative;
import org.chromium.base.ContentUriUtils;
import org.chromium.base.JNINamespace;
import org.chromium.ui.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A dialog that is triggered from a file input field that allows a user to select a file based on
 * a set of accepted file types. The path of the selected file is passed to the native dialog.
 */
@JNINamespace("ui")
class SelectFileDialog implements WindowAndroid.IntentCallback{
    private static final String IMAGE_TYPE = "image/";
    private static final String VIDEO_TYPE = "video/";
    private static final String AUDIO_TYPE = "audio/";
    private static final String ALL_IMAGE_TYPES = IMAGE_TYPE + "*";
    private static final String ALL_VIDEO_TYPES = VIDEO_TYPE + "*";
    private static final String ALL_AUDIO_TYPES = AUDIO_TYPE + "*";
    private static final String ANY_TYPES = "*/*";
    private static final String CAPTURE_IMAGE_DIRECTORY = "browser-photos";

    private final long mNativeSelectFileDialog;
    private List<String> mFileTypes;
    private boolean mCapture;
    private Uri mCameraOutputUri;

    private SelectFileDialog(long nativeSelectFileDialog) {
        mNativeSelectFileDialog = nativeSelectFileDialog;
    }

    /**
     * Creates and starts an intent based on the passed fileTypes and capture value.
     * @param fileTypes MIME types requested (i.e. "image/*")
     * @param capture The capture value as described in http://www.w3.org/TR/html-media-capture/
     * @param window The WindowAndroid that can show intents
     */
    @CalledByNative
    private void selectFile(String[] fileTypes, boolean capture, WindowAndroid window) {
        mFileTypes = new ArrayList<String>(Arrays.asList(fileTypes));
        mCapture = capture;

        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        mCameraOutputUri = Uri.fromFile(getFileForImageCapture());
        camera.putExtra(MediaStore.EXTRA_OUTPUT, mCameraOutputUri);
        Intent camcorder = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        Intent soundRecorder = new Intent(
                MediaStore.Audio.Media.RECORD_SOUND_ACTION);

        // Quick check - if the |capture| parameter is set and |fileTypes| has the appropriate MIME
        // type, we should just launch the appropriate intent. Otherwise build up a chooser based on
        // the accept type and then display that to the user.
        if (captureCamera()) {
            if (window.showIntent(camera, this, R.string.low_memory_error)) return;
        } else if (captureCamcorder()) {
            if (window.showIntent(camcorder, this, R.string.low_memory_error)) return;
        } else if (captureMicrophone()) {
            if (window.showIntent(soundRecorder, this, R.string.low_memory_error)) return;
        }

        Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        ArrayList<Intent> extraIntents = new ArrayList<Intent>();
        if (!noSpecificType()) {
            // Create a chooser based on the accept type that was specified in the webpage. Note
            // that if the web page specified multiple accept types, we will have built a generic
            // chooser above.
            if (shouldShowImageTypes()) {
                extraIntents.add(camera);
                getContentIntent.setType(ALL_IMAGE_TYPES);
            } else if (shouldShowVideoTypes()) {
                extraIntents.add(camcorder);
                getContentIntent.setType(ALL_VIDEO_TYPES);
            } else if (shouldShowAudioTypes()) {
                extraIntents.add(soundRecorder);
                getContentIntent.setType(ALL_AUDIO_TYPES);
            }
        }

        if (extraIntents.isEmpty()) {
            // We couldn't resolve an accept type, so fallback to a generic chooser.
            getContentIntent.setType(ANY_TYPES);
            extraIntents.add(camera);
            extraIntents.add(camcorder);
            extraIntents.add(soundRecorder);
        }

        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                extraIntents.toArray(new Intent[] { }));

        chooser.putExtra(Intent.EXTRA_INTENT, getContentIntent);

        if (!window.showIntent(chooser, this, R.string.low_memory_error)) {
            onFileNotSelected();
        }
    }

    /**
     * Get a file for the image capture in the CAPTURE_IMAGE_DIRECTORY directory.
     */
    private File getFileForImageCapture() {
        File externalDataDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM);
        File cameraDataDir = new File(externalDataDir.getAbsolutePath() +
                File.separator + CAPTURE_IMAGE_DIRECTORY);
        if (!cameraDataDir.exists() && !cameraDataDir.mkdirs()) {
            cameraDataDir = externalDataDir;
        }
        File photoFile = new File(cameraDataDir.getAbsolutePath() +
                File.separator + System.currentTimeMillis() + ".jpg");
        return photoFile;
    }

    /**
     * Callback method to handle the intent results and pass on the path to the native
     * SelectFileDialog.
     * @param window The window that has access to the application activity.
     * @param resultCode The result code whether the intent returned successfully.
     * @param contentResolver The content resolver used to extract the path of the selected file.
     * @param results The results of the requested intent.
     */
    @Override
    public void onIntentCompleted(WindowAndroid window, int resultCode,
            ContentResolver contentResolver, Intent results) {
        if (resultCode != Activity.RESULT_OK) {
            onFileNotSelected();
            return;
        }

        if (results == null) {
            // If we have a successful return but no data, then assume this is the camera returning
            // the photo that we requested.
            nativeOnFileSelected(mNativeSelectFileDialog, mCameraOutputUri.getPath(), "");

            // Broadcast to the media scanner that there's a new photo on the device so it will
            // show up right away in the gallery (rather than waiting until the next time the media
            // scanner runs).
            window.sendBroadcast(new Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mCameraOutputUri));
            return;
        }

        if (ContentResolver.SCHEME_FILE.equals(results.getData().getScheme())) {
            nativeOnFileSelected(mNativeSelectFileDialog,
                    results.getData().getSchemeSpecificPart(), "");
            return;
        }

        if (ContentResolver.SCHEME_CONTENT.equals(results.getScheme())) {
            GetDisplayNameTask task = new GetDisplayNameTask(contentResolver, false);
            task.execute(results.getData());
            return;
        }

        onFileNotSelected();
        window.showError(R.string.opening_file_error);
    }

    private void onFileNotSelected() {
        nativeOnFileNotSelected(mNativeSelectFileDialog);
    }

    private boolean noSpecificType() {
        // We use a single Intent to decide the type of the file chooser we display to the user,
        // which means we can only give it a single type. If there are multiple accept types
        // specified, we will fallback to a generic chooser (unless a capture parameter has been
        // specified, in which case we'll try to satisfy that first.
        return mFileTypes.size() != 1 || mFileTypes.contains(ANY_TYPES);
    }

    private boolean shouldShowTypes(String allTypes, String specificType) {
        if (noSpecificType() || mFileTypes.contains(allTypes)) return true;
        return acceptSpecificType(specificType);
    }

    private boolean shouldShowImageTypes() {
        return shouldShowTypes(ALL_IMAGE_TYPES, IMAGE_TYPE);
    }

    private boolean shouldShowVideoTypes() {
        return shouldShowTypes(ALL_VIDEO_TYPES, VIDEO_TYPE);
    }

    private boolean shouldShowAudioTypes() {
        return shouldShowTypes(ALL_AUDIO_TYPES, AUDIO_TYPE);
    }

    private boolean acceptsSpecificType(String type) {
        return mFileTypes.size() == 1 && TextUtils.equals(mFileTypes.get(0), type);
    }

    private boolean captureCamera() {
        return mCapture && acceptsSpecificType(ALL_IMAGE_TYPES);
    }

    private boolean captureCamcorder() {
        return mCapture && acceptsSpecificType(ALL_VIDEO_TYPES);
    }

    private boolean captureMicrophone() {
        return mCapture && acceptsSpecificType(ALL_AUDIO_TYPES);
    }

    private boolean acceptSpecificType(String accept) {
        for (String type : mFileTypes) {
            if (type.startsWith(accept)) {
                return true;
            }
        }
        return false;
    }

    private class GetDisplayNameTask extends AsyncTask<Uri, Void, String[]> {
        String[] mFilePaths;
        final ContentResolver mContentResolver;
        final boolean mIsMultiple;

        public GetDisplayNameTask(ContentResolver contentResolver, boolean isMultiple) {
            mContentResolver = contentResolver;
            mIsMultiple = isMultiple;
        }

        @Override
        protected String[] doInBackground(Uri...uris) {
            mFilePaths = new String[uris.length];
            String[] displayNames = new String[uris.length];
            for (int i = 0; i < uris.length; i++) {
                mFilePaths[i] = uris[i].toString();
                displayNames[i] = ContentUriUtils.getDisplayName(
                        uris[i], mContentResolver, MediaStore.MediaColumns.DISPLAY_NAME);
            }
            return displayNames;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (!mIsMultiple) {
                nativeOnFileSelected(mNativeSelectFileDialog, mFilePaths[0], result[0]);
            }
        }
    }

    @CalledByNative
    private static SelectFileDialog create(long nativeSelectFileDialog) {
        return new SelectFileDialog(nativeSelectFileDialog);
    }

    private native void nativeOnFileSelected(long nativeSelectFileDialogImpl,
            String filePath, String displayName);
    private native void nativeOnFileNotSelected(long nativeSelectFileDialogImpl);
}

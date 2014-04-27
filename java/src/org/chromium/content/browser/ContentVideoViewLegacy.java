// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.MediaController;

/**
 * Adds Android media controller to ContentVideoView.
 * The sole purpose of this class is to maintain legacy behavior while we test
 * the blink-based media controller.
 * https://code.google.com/p/chromium/issues/detail?id=331966
 */
public class ContentVideoViewLegacy extends ContentVideoView {
    private FullScreenMediaController mMediaController;
    private boolean mCanPause;
    private boolean mCanSeekBackward;
    private boolean mCanSeekForward;
    private int mCurrentBufferPercentage;
    private MediaControlsVisibilityListener mListener;

    /**
     * A listener for changes in the MediaController visibility.
     */
    public interface MediaControlsVisibilityListener {
        /**
         * Callback for when the visibility of the media controls changes.
         *
         * @param shown true if the media controls are shown to the user, false otherwise
         */
        public void onMediaControlsVisibilityChanged(boolean shown);
    }

    private static class FullScreenMediaController extends MediaController {

        final View mVideoView;
        final MediaControlsVisibilityListener mListener;

        /**
         * @param context The context.
         * @param video The full screen video container view.
         * @param listener A listener that listens to the visibility of media controllers.
         */
        public FullScreenMediaController(
                Context context,
                View video,
                MediaControlsVisibilityListener listener) {
            super(context);
            mVideoView = video;
            mListener = listener;
        }

        @Override
        public void show() {
            super.show();
            if (mListener != null) mListener.onMediaControlsVisibilityChanged(true);
            if (mVideoView != null) {
                mVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }

        @Override
        public void hide() {
            if (mVideoView != null) {
                mVideoView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
            if (mListener != null)  mListener.onMediaControlsVisibilityChanged(false);
            super.hide();
        }
    }

    ContentVideoViewLegacy(Context context, long nativeContentVideoView,
            ContentVideoViewClient client) {
        super(context, nativeContentVideoView, client);
        setBackgroundColor(Color.BLACK);
        mCurrentBufferPercentage = 0;
    }

    @Override
    protected void showContentVideoView() {
        SurfaceView surfaceView = getSurfaceView();
        surfaceView.setZOrderOnTop(true);
        surfaceView.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                boolean isKeyCodeSupported = (
                        keyCode != KeyEvent.KEYCODE_BACK &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                        keyCode != KeyEvent.KEYCODE_CALL &&
                        keyCode != KeyEvent.KEYCODE_MENU &&
                        keyCode != KeyEvent.KEYCODE_SEARCH &&
                        keyCode != KeyEvent.KEYCODE_ENDCALL);
                if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
                    if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                        if (isPlaying()) {
                            pause();
                            mMediaController.show();
                        } else {
                            start();
                            mMediaController.hide();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                        if (!isPlaying()) {
                            start();
                            mMediaController.hide();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                            || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                        if (isPlaying()) {
                            pause();
                            mMediaController.show();
                        }
                        return true;
                    } else {
                        toggleMediaControlsVisiblity();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_BACK &&
                        event.getAction() == KeyEvent.ACTION_UP) {
                    exitFullscreen(false);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SEARCH) {
                    return true;
                }
                return false;
            }
        });
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isInPlaybackState() && mMediaController != null &&
                        event.getAction() == MotionEvent.ACTION_DOWN) {
                    toggleMediaControlsVisiblity();
                }
                return true;
            }
        });
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.requestFocus();
        super.showContentVideoView();
    }

    @Override
    public void onMediaPlayerError(int errorType) {
        super.onMediaPlayerError(errorType);
        if (errorType == MEDIA_ERROR_INVALID_CODE) return;
        if (mMediaController != null) mMediaController.hide();
    }

    @Override
    protected void onBufferingUpdate(int percent) {
        super.onBufferingUpdate(percent);
        mCurrentBufferPercentage = percent;
    }

    @Override
    protected void onUpdateMediaMetadata(
            int videoWidth,
            int videoHeight,
            int duration,
            boolean canPause,
            boolean canSeekBack,
            boolean canSeekForward) {
        super.onUpdateMediaMetadata(videoWidth, videoHeight, duration,
                canPause, canSeekBack, canSeekForward);
        mCanPause = canPause;
        mCanSeekBackward = canSeekBack;
        mCanSeekForward = canSeekForward;

        if (mMediaController == null) return;

        mMediaController.setEnabled(true);
        // If paused , should show the controller forever.
        if (isPlaying()) {
            mMediaController.show();
        } else {
            mMediaController.show(0);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        super.surfaceChanged(holder, format, width, height);
        SurfaceView surfaceView = getSurfaceView();
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        if (isInPlaybackState() && mMediaController != null) {
            mMediaController.show();
        }
    }

    @Override
    protected void openVideo() {
        super.openVideo();

        mCurrentBufferPercentage = 0;

        if (mMediaController != null) return;
        mMediaController = new FullScreenMediaController(getContext(), this, mListener);
        mMediaController.setMediaPlayer(new MediaController.MediaPlayerControl() {
            @Override public boolean canPause() { return mCanPause; }
            @Override public boolean canSeekBackward() { return mCanSeekBackward; }
            @Override public boolean canSeekForward() { return mCanSeekForward; }
            @Override public int getAudioSessionId() { return 0; }
            @Override public int getBufferPercentage() { return mCurrentBufferPercentage; }

            @Override
            public int getCurrentPosition() {
                return ContentVideoViewLegacy.this.getCurrentPosition();
            }

            @Override
            public int getDuration() {
                return ContentVideoViewLegacy.this.getDuration();
            }

            @Override
            public boolean isPlaying() {
                return ContentVideoViewLegacy.this.isPlaying();
            }

            @Override
            public void pause() {
                ContentVideoViewLegacy.this.pause();
            }

            @Override
            public void seekTo(int pos) {
                ContentVideoViewLegacy.this.seekTo(pos);
            }

            @Override
            public void start() {
                ContentVideoViewLegacy.this.start();
            }
        });
        mMediaController.setAnchorView(getSurfaceView());
        mMediaController.setEnabled(false);
    }

    @Override
    protected void onCompletion() {
        super.onCompletion();
        if (mMediaController != null) {
            mMediaController.hide();
        }
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    @Override
    protected void destroyContentVideoView(boolean nativeViewDestroyed) {
        if (mMediaController != null) {
            mMediaController.setEnabled(false);
            mMediaController.hide();
            mMediaController = null;
        }
        super.destroyContentVideoView(nativeViewDestroyed);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    /**
     * Sets the MediaControlsVisibilityListener that wants to listen to visibility change events.
     *
     * @param listener the listener to send the events to.
     */
    public void setListener(MediaControlsVisibilityListener listener) {
        mListener = listener;
    }

}

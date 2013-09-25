// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.view.Surface;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

/**
 * A wrapper of the MediaCodec class to facilitate exception capturing and
 * audio rendering.
 */
@JNINamespace("media")
class MediaCodecBridge {
    private static final String TAG = "MediaCodecBridge";

    // Error code for MediaCodecBridge. Keep this value in sync with
    // MediaCodecStatus in media_codec_bridge.h.
    private static final int MEDIA_CODEC_OK = 0;
    private static final int MEDIA_CODEC_DEQUEUE_INPUT_AGAIN_LATER = 1;
    private static final int MEDIA_CODEC_DEQUEUE_OUTPUT_AGAIN_LATER = 2;
    private static final int MEDIA_CODEC_OUTPUT_BUFFERS_CHANGED = 3;
    private static final int MEDIA_CODEC_OUTPUT_FORMAT_CHANGED = 4;
    private static final int MEDIA_CODEC_INPUT_END_OF_STREAM = 5;
    private static final int MEDIA_CODEC_OUTPUT_END_OF_STREAM = 6;
    private static final int MEDIA_CODEC_NO_KEY = 7;
    private static final int MEDIA_CODEC_STOPPED = 8;
    private static final int MEDIA_CODEC_ERROR = 9;

    // After a flush(), dequeueOutputBuffer() can often produce empty presentation timestamps
    // for several frames. As a result, the player may find that the time does not increase
    // after decoding a frame. To detect this, we check whether the presentation timestamp from
    // dequeueOutputBuffer() is larger than input_timestamp - MAX_PRESENTATION_TIMESTAMP_SHIFT_US
    // after a flush. And we set the presentation timestamp from dequeueOutputBuffer() to be
    // non-decreasing for the remaining frames.
    private static final long MAX_PRESENTATION_TIMESTAMP_SHIFT_US = 100000;

    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;

    private MediaCodec mMediaCodec;
    private AudioTrack mAudioTrack;
    private boolean mFlushed;
    private long mLastPresentationTimeUs;

    private static class DequeueInputResult {
        private final int mStatus;
        private final int mIndex;

        private DequeueInputResult(int status, int index) {
            mStatus = status;
            mIndex = index;
        }

        @CalledByNative("DequeueInputResult")
        private int status() { return mStatus; }

        @CalledByNative("DequeueInputResult")
        private int index() { return mIndex; }
    }

    /**
     * This class represents supported android codec information.
     */
    private static class CodecInfo {
        private final String mCodecType;
        private final boolean mIsSecureDecoderSupported;

        private CodecInfo(String codecType, boolean isSecureDecoderSupported) {
            mCodecType = codecType;
            mIsSecureDecoderSupported = isSecureDecoderSupported;
        }

        @CalledByNative("CodecInfo")
        private String codecType() { return mCodecType; }

        @CalledByNative("CodecInfo")
        private boolean isSecureDecoderSupported() { return mIsSecureDecoderSupported; }
    }

    private static class DequeueOutputResult {
        private final int mStatus;
        private final int mIndex;
        private final int mFlags;
        private final int mOffset;
        private final long mPresentationTimeMicroseconds;
        private final int mNumBytes;

        private DequeueOutputResult(int status, int index, int flags, int offset,
                long presentationTimeMicroseconds, int numBytes) {
            mStatus = status;
            mIndex = index;
            mFlags = flags;
            mOffset = offset;
            mPresentationTimeMicroseconds = presentationTimeMicroseconds;
            mNumBytes = numBytes;
        }

        @CalledByNative("DequeueOutputResult")
        private int status() { return mStatus; }

        @CalledByNative("DequeueOutputResult")
        private int index() { return mIndex; }

        @CalledByNative("DequeueOutputResult")
        private int flags() { return mFlags; }

        @CalledByNative("DequeueOutputResult")
        private int offset() { return mOffset; }

        @CalledByNative("DequeueOutputResult")
        private long presentationTimeMicroseconds() { return mPresentationTimeMicroseconds; }

        @CalledByNative("DequeueOutputResult")
        private int numBytes() { return mNumBytes; }
    }

    /**
     * Get a list of supported android codec mimes.
     */
    @CalledByNative
    private static CodecInfo[] getCodecsInfo() {
        Map<String, CodecInfo> CodecInfoMap = new HashMap<String, CodecInfo>();
        int count = MediaCodecList.getCodecCount();
        for (int i = 0; i < count; ++i) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (info.isEncoder()) {
                continue;
            }

            String[] supportedTypes = info.getSupportedTypes();
            String codecString = info.getName();
            String secureCodecName = codecString + ".secure";
            boolean secureDecoderSupported = false;
            try {
                MediaCodec secureCodec = MediaCodec.createByCodecName(secureCodecName);
                secureDecoderSupported = true;
                secureCodec.release();
            } catch (Exception e) {
                Log.e(TAG, "Failed to create " + secureCodecName);
            }
            for (int j = 0; j < supportedTypes.length; ++j) {
                if (!CodecInfoMap.containsKey(supportedTypes[j]) || secureDecoderSupported) {
                    CodecInfoMap.put(supportedTypes[j],
                                     new CodecInfo(supportedTypes[j], secureDecoderSupported));
                }
            }
        }
        return CodecInfoMap.values().toArray(
            new CodecInfo[CodecInfoMap.size()]);
    }

    private static String getSecureDecoderNameForMime(String mime) {
        int count = MediaCodecList.getCodecCount();
        for (int i = 0; i < count; ++i) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (info.isEncoder()) {
                continue;
            }

            String[] supportedTypes = info.getSupportedTypes();
            for (int j = 0; j < supportedTypes.length; ++j) {
                if (supportedTypes[j].equalsIgnoreCase(mime)) {
                    return info.getName() + ".secure";
                }
            }
        }

        return null;
    }

    private MediaCodecBridge(MediaCodec mediaCodec) {
        assert(mediaCodec != null);
        mMediaCodec = mediaCodec;
        mLastPresentationTimeUs = 0;
        mFlushed = true;
    }

    @CalledByNative
    private static MediaCodecBridge create(String mime, boolean isSecure) {
        MediaCodec mediaCodec = null;
        try {
            // |isSecure| only applies to video decoders.
            if (mime.startsWith("video") && isSecure) {
                mediaCodec = MediaCodec.createByCodecName(getSecureDecoderNameForMime(mime));
            } else {
                mediaCodec = MediaCodec.createDecoderByType(mime);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create MediaCodec: " +  mime + ", isSecure: "
                    + isSecure + ", " + e.toString());
        }

        if (mediaCodec == null) {
            return null;
        }

        return new MediaCodecBridge(mediaCodec);
    }

    @CalledByNative
    private void release() {
        mMediaCodec.release();
        if (mAudioTrack != null) {
            mAudioTrack.release();
        }
    }

    @CalledByNative
    private void start() {
        mMediaCodec.start();
        mInputBuffers = mMediaCodec.getInputBuffers();
    }

    @CalledByNative
    private DequeueInputResult dequeueInputBuffer(long timeoutUs) {
        int status = MEDIA_CODEC_ERROR;
        int index = -1;
        try {
            int index_or_status = mMediaCodec.dequeueInputBuffer(timeoutUs);
            if (index_or_status >= 0) { // index!
                status = MEDIA_CODEC_OK;
                index = index_or_status;
            } else if (index_or_status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.e(TAG, "dequeueInputBuffer: MediaCodec.INFO_TRY_AGAIN_LATER");
                status = MEDIA_CODEC_DEQUEUE_INPUT_AGAIN_LATER;
            } else {
                assert(false);
            }
        } catch(Exception e) {
            Log.e(TAG, "Failed to dequeue input buffer: " + e.toString());
        }
        return new DequeueInputResult(status, index);
    }

    @CalledByNative
    private int flush() {
        try {
            mFlushed = true;
            if (mAudioTrack != null) {
                mAudioTrack.flush();
            }
            mMediaCodec.flush();
        } catch(IllegalStateException e) {
            Log.e(TAG, "Failed to flush MediaCodec " + e.toString());
            return MEDIA_CODEC_ERROR;
        }
        return MEDIA_CODEC_OK;
    }

    @CalledByNative
    private void stop() {
        mMediaCodec.stop();
        if (mAudioTrack != null) {
            mAudioTrack.pause();
        }
    }

    @CalledByNative
    private int getOutputHeight() {
        return mMediaCodec.getOutputFormat().getInteger(MediaFormat.KEY_HEIGHT);
    }

    @CalledByNative
    private int getOutputWidth() {
        return mMediaCodec.getOutputFormat().getInteger(MediaFormat.KEY_WIDTH);
    }

    @CalledByNative
    private ByteBuffer getInputBuffer(int index) {
        return mInputBuffers[index];
    }

    @CalledByNative
    private ByteBuffer getOutputBuffer(int index) {
        return mOutputBuffers[index];
    }

    @CalledByNative
    private int queueInputBuffer(
            int index, int offset, int size, long presentationTimeUs, int flags) {
        resetLastPresentationTimeIfNeeded(presentationTimeUs);
        try {
            mMediaCodec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
        } catch(Exception e) {
            Log.e(TAG, "Failed to queue input buffer: " + e.toString());
            return MEDIA_CODEC_ERROR;
        }
        return MEDIA_CODEC_OK;
    }

    @CalledByNative
    private int queueSecureInputBuffer(
            int index, int offset, byte[] iv, byte[] keyId, int[] numBytesOfClearData,
            int[] numBytesOfEncryptedData, int numSubSamples, long presentationTimeUs) {
        resetLastPresentationTimeIfNeeded(presentationTimeUs);
        try {
            MediaCodec.CryptoInfo cryptoInfo = new MediaCodec.CryptoInfo();
            cryptoInfo.set(numSubSamples, numBytesOfClearData, numBytesOfEncryptedData,
                    keyId, iv, MediaCodec.CRYPTO_MODE_AES_CTR);
            mMediaCodec.queueSecureInputBuffer(index, offset, cryptoInfo, presentationTimeUs, 0);
        } catch (MediaCodec.CryptoException e) {
            Log.e(TAG, "Failed to queue secure input buffer: " + e.toString());
            // TODO(xhwang): Replace hard coded value with constant/enum.
            if (e.getErrorCode() == 1) {
                Log.e(TAG, "No key available.");
                return MEDIA_CODEC_NO_KEY;
            }
            return MEDIA_CODEC_ERROR;
        } catch(IllegalStateException e) {
            Log.e(TAG, "Failed to queue secure input buffer: " + e.toString());
            return MEDIA_CODEC_ERROR;
        }
        return MEDIA_CODEC_OK;
    }

    @CalledByNative
    private void releaseOutputBuffer(int index, boolean render) {
        mMediaCodec.releaseOutputBuffer(index, render);
    }

    @CalledByNative
    private void getOutputBuffers() {
        mOutputBuffers = mMediaCodec.getOutputBuffers();
    }

    @CalledByNative
    private DequeueOutputResult dequeueOutputBuffer(long timeoutUs) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int status = MEDIA_CODEC_ERROR;
        int index = -1;
        try {
            int index_or_status = mMediaCodec.dequeueOutputBuffer(info, timeoutUs);
            if (info.presentationTimeUs < mLastPresentationTimeUs) {
                // TODO(qinmin): return a special code through DequeueOutputResult
                // to notify the native code the the frame has a wrong presentation
                // timestamp and should be skipped.
                info.presentationTimeUs = mLastPresentationTimeUs;
            }
            mLastPresentationTimeUs = info.presentationTimeUs;

            if (index_or_status >= 0) { // index!
                status = MEDIA_CODEC_OK;
                index = index_or_status;
            } else if (index_or_status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                status = MEDIA_CODEC_OUTPUT_BUFFERS_CHANGED;
            } else if (index_or_status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                status = MEDIA_CODEC_OUTPUT_FORMAT_CHANGED;
            } else if (index_or_status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                status = MEDIA_CODEC_DEQUEUE_OUTPUT_AGAIN_LATER;
            } else {
                assert(false);
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to dequeue output buffer: " + e.toString());
        }

        return new DequeueOutputResult(
                status, index, info.flags, info.offset, info.presentationTimeUs, info.size);
    }

    @CalledByNative
    private boolean configureVideo(MediaFormat format, Surface surface, MediaCrypto crypto,
            int flags) {
        try {
            mMediaCodec.configure(format, surface, crypto, flags);
            return true;
        } catch (IllegalStateException e) {
          Log.e(TAG, "Cannot configure the video codec " + e.toString());
        }
        return false;
    }

    @CalledByNative
    private static MediaFormat createAudioFormat(String mime, int SampleRate, int ChannelCount) {
        return MediaFormat.createAudioFormat(mime, SampleRate, ChannelCount);
    }

    @CalledByNative
    private static MediaFormat createVideoFormat(String mime, int width, int height) {
        return MediaFormat.createVideoFormat(mime, width, height);
    }

    @CalledByNative
    private static void setCodecSpecificData(MediaFormat format, int index, byte[] bytes) {
        String name = null;
        if (index == 0) {
            name = "csd-0";
        } else if (index == 1) {
            name = "csd-1";
        }
        if (name != null) {
            format.setByteBuffer(name, ByteBuffer.wrap(bytes));
        }
    }

    @CalledByNative
    private static void setFrameHasADTSHeader(MediaFormat format) {
        format.setInteger(MediaFormat.KEY_IS_ADTS, 1);
    }

    @CalledByNative
    private boolean configureAudio(MediaFormat format, MediaCrypto crypto, int flags,
            boolean playAudio) {
        try {
            mMediaCodec.configure(format, null, crypto, flags);
            if (playAudio) {
                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int channelConfig = (channelCount == 1) ? AudioFormat.CHANNEL_OUT_MONO :
                        AudioFormat.CHANNEL_OUT_STEREO;
                // Using 16bit PCM for output. Keep this value in sync with
                // kBytesPerAudioOutputSample in media_codec_bridge.cc.
                int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT);
                mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM);
            }
            return true;
        } catch (IllegalStateException e) {
            Log.e(TAG, "Cannot configure the audio codec " + e.toString());
        }
        return false;
    }

    @CalledByNative
    private void playOutputBuffer(byte[] buf) {
        if (mAudioTrack != null) {
            if (AudioTrack.PLAYSTATE_PLAYING != mAudioTrack.getPlayState()) {
                mAudioTrack.play();
            }
            int size = mAudioTrack.write(buf, 0, buf.length);
            if (buf.length != size) {
                Log.i(TAG, "Failed to send all data to audio output, expected size: " +
                        buf.length + ", actual size: " + size);
            }
        }
    }

    @CalledByNative
    private void setVolume(double volume) {
        if (mAudioTrack != null) {
            mAudioTrack.setStereoVolume((float) volume, (float) volume);
        }
    }

    private void resetLastPresentationTimeIfNeeded(long presentationTimeUs) {
        if (mFlushed) {
            mLastPresentationTimeUs =
                    Math.max(presentationTimeUs - MAX_PRESENTATION_TIMESTAMP_SHIFT_US, 0);
            mFlushed = false;
        }
    }
}

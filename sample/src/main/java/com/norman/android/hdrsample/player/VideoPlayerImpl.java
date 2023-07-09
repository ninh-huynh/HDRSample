package com.norman.android.hdrsample.player;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import com.norman.android.hdrsample.player.decode.VideoDecoder;
import com.norman.android.hdrsample.player.extract.VideoExtractor;
import com.norman.android.hdrsample.util.MediaFormatUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class VideoPlayerImpl extends DecodePlayer<VideoDecoder, VideoExtractor> implements VideoPlayer {

    private static final String VIDEO_PLAYER_NAME = "VideoPlayer";

    private static final String KEY_CROP_LEFT = "crop-left";
    private static final String KEY_CROP_RIGHT = "crop-right";
    private static final String KEY_CROP_TOP = "crop-top";
    private static final String KEY_CROP_BOTTOM = "crop-bottom";

    private final List<VideoSizeChangeListener> videoSizeChangedListeners = new CopyOnWriteArrayList<>();

    private int videoWidth;

    private int videoHeight;

    private final VideoOutput videoOutput;

    public VideoPlayerImpl(VideoOutput videoOutput) {
        this(VIDEO_PLAYER_NAME,videoOutput);
    }

    public VideoPlayerImpl(String threadName,VideoOutput videoOutput) {
        super(VideoDecoder.create(), VideoExtractor.create(), threadName);
        this.videoOutput = videoOutput;
    }


    @Override
    protected void onPlayPrepare() {
        videoOutput.prepare();
        super.onPlayPrepare();
    }


    @Override
    protected void onInputFormatPrepare(VideoExtractor extractor, VideoDecoder decoder, MediaFormat inputFormat) {
        MediaFormatUtil.setInteger(inputFormat, MediaFormat.KEY_COLOR_STANDARD, extractor.getColorStandard());
        MediaFormatUtil.setInteger(inputFormat, MediaFormat.KEY_COLOR_RANGE, extractor.getColorRange());
        MediaFormatUtil.setInteger(inputFormat, MediaFormat.KEY_COLOR_TRANSFER, extractor.getColorTransfer());
        MediaFormatUtil.setInteger(inputFormat, MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        MediaFormatUtil.setInteger(inputFormat, MediaFormat.KEY_WIDTH, extractor.getWidth());
        MediaFormatUtil.setInteger(inputFormat, MediaFormat.KEY_HEIGHT, extractor.getHeight());
        if (setVideoSize(extractor.getWidth(), extractor.getHeight())) {
            for (VideoSizeChangeListener videoSizeChangedListener : videoSizeChangedListeners) {
                videoSizeChangedListener.onVideoSizeChange(videoWidth, videoHeight);
            }
        }
        videoOutput.onDecoderPrepare(decoder, inputFormat);
    }


    @Override
    protected void onOutputFormatChanged(MediaFormat outputFormat) {
        int width = MediaFormatUtil.getInteger(outputFormat, MediaFormat.KEY_WIDTH);
        int height = MediaFormatUtil.getInteger(outputFormat, MediaFormat.KEY_HEIGHT);
        int cropLeft = MediaFormatUtil.getInteger(outputFormat, KEY_CROP_LEFT);
        int cropRight = MediaFormatUtil.getInteger(outputFormat, KEY_CROP_RIGHT);
        int cropTop = MediaFormatUtil.getInteger(outputFormat, KEY_CROP_TOP);
        int cropBottom = MediaFormatUtil.getInteger(outputFormat, KEY_CROP_BOTTOM);
        if (cropRight > 0 && cropBottom > 0) {
            width = cropRight - cropLeft + 1;
            height = cropBottom - cropTop + 1;
        }
        if (setVideoSize(width, height)) {
            for (VideoSizeChangeListener videoSizeChangedListener : videoSizeChangedListeners) {
                videoSizeChangedListener.onVideoSizeChange(videoWidth, videoHeight);
            }
        }
        videoOutput.onOutputFormatChanged(outputFormat);
    }

    @Override
    protected boolean onOutputBufferRender(float timeSecond, ByteBuffer buffer) {
        return videoOutput.onOutputBufferRender(timeSecond, buffer);
    }

    @Override
    protected void onOutputBufferRelease(float timeSecond, boolean render) {
        videoOutput.onOutputBufferRelease(timeSecond, render);
    }

    @Override
    protected void onPlayRelease() {
        super.onPlayRelease();
        videoOutput.release();
    }

    synchronized boolean setVideoSize(int width, int height) {
        int oldWidth = videoWidth;
        int oldHeight = videoHeight;
        videoWidth = width;
        videoHeight = height;
        return oldWidth != videoWidth || oldHeight != videoHeight;
    }

    @Override
    public VideoOutput getOutput() {
        return videoOutput;
    }

    @Override
    public void setOutputSurface(Surface surface) {
        videoOutput.setOutputSurface(surface);
    }

    @Override
    public synchronized int getWidth() {
        return videoWidth;
    }

    @Override
    public synchronized int getHeight() {
        return videoHeight;
    }

    @Override
    public void addSizeChangeListener(VideoSizeChangeListener changeListener) {
        if (videoSizeChangedListeners.contains(changeListener)) return;
        videoSizeChangedListeners.add(changeListener);
    }

    @Override
    public void removeSizeChangeListener(VideoSizeChangeListener changeListener) {
        if (!videoSizeChangedListeners.contains(changeListener)) return;
        videoSizeChangedListeners.remove(changeListener);
    }
}
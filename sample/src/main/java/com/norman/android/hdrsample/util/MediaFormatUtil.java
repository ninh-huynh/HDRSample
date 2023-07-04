package com.norman.android.hdrsample.util;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.text.TextUtils;

import java.nio.ByteBuffer;

public class MediaFormatUtil {

    private static final int HAL_PIXEL_FORMAT_YCbCr_420_P010 = 0x11F;
    private static final int HAL_PIXEL_FORMAT_YCbCr_420_P010_UBWC = 0x124;
    private static final int HAL_PIXEL_FORMAT_YCbCr_420_P010_VENUS = 0x7FA30C0A;

    private static final int HAL_PIXEL_FORMAT_YCbCr_420_TP10_UBWC = 0x7FA30C09;

    public static int getInteger(MediaFormat mediaFormat, String name) {
        return getInteger(mediaFormat, name, 0);
    }

    public static int getInteger(MediaFormat mediaFormat, String name, int defaultValue) {
        if (mediaFormat == null || TextUtils.isEmpty(name) || !mediaFormat.containsKey(name)) {
            return defaultValue;
        }
        return mediaFormat.getInteger(name);
    }

    public static long getLong(MediaFormat mediaFormat, String name) {
        return getLong(mediaFormat, name, 0);
    }

    public static long getLong(MediaFormat mediaFormat, String name, long defaultValue) {
        if (mediaFormat == null || TextUtils.isEmpty(name) || !mediaFormat.containsKey(name)) {
            return defaultValue;
        }
        return mediaFormat.getLong(name);
    }

    public static String getString(MediaFormat mediaFormat, String name) {
        return getString(mediaFormat, name, null);
    }

    public static String getString(MediaFormat mediaFormat, String name, String defaultValue) {
        if (mediaFormat == null || TextUtils.isEmpty(name) || !mediaFormat.containsKey(name)) {
            return defaultValue;
        }
        return mediaFormat.getString(name);
    }

    public static ByteBuffer getByteBuffer(MediaFormat mediaFormat, String name) {
        return getByteBuffer(mediaFormat, name, null);
    }

    public static ByteBuffer getByteBuffer(MediaFormat mediaFormat, String name, ByteBuffer defaultValue) {
        if (mediaFormat == null || TextUtils.isEmpty(name) || !mediaFormat.containsKey(name)) {
            return defaultValue;
        }
        return mediaFormat.getByteBuffer(name);
    }

    public static void setInteger(MediaFormat mediaFormat, String name, int value) {
        if (mediaFormat == null || TextUtils.isEmpty(name)) {
            return;
        }
        mediaFormat.setInteger(name, value);
    }


    public static void setLong(MediaFormat mediaFormat, String name, int value) {
        if (mediaFormat == null || TextUtils.isEmpty(name)) {
            return;
        }
        mediaFormat.setLong(name, value);
    }

    public static void setFloat(MediaFormat mediaFormat, String name, float value) {
        if (mediaFormat == null || TextUtils.isEmpty(name)) {
            return;
        }
        mediaFormat.setFloat(name, value);
    }


    public static void setString(MediaFormat mediaFormat, String name, String value) {
        if (mediaFormat == null || TextUtils.isEmpty(name) || value == null) {
            return;
        }
        mediaFormat.setString(name, value);
    }


    public static void setByteBuffer(MediaFormat mediaFormat, String name, ByteBuffer buffer) {
        if (mediaFormat == null || TextUtils.isEmpty(name)|| buffer == null) {
            return;
        }
        mediaFormat.setByteBuffer(name, buffer);
    }

    public static boolean isYuv420P10(MediaFormat mediaFormat) {
        if (mediaFormat == null || !mediaFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
            return false;
        }
        int colorFormat = getInteger(mediaFormat, MediaFormat.KEY_COLOR_FORMAT);
        return colorFormat == HAL_PIXEL_FORMAT_YCbCr_420_P010
                || colorFormat == HAL_PIXEL_FORMAT_YCbCr_420_P010_UBWC
                || colorFormat == HAL_PIXEL_FORMAT_YCbCr_420_P010_VENUS
                || colorFormat == HAL_PIXEL_FORMAT_YCbCr_420_TP10_UBWC
                || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
    }


    public static boolean isHdrProfile(MediaFormat mediaFormat) {
        if (mediaFormat == null || !mediaFormat.containsKey(MediaFormat.KEY_PROFILE)) {
            return false;
        }
        int profile = getInteger(mediaFormat, MediaFormat.KEY_PROFILE);
        return profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 ||
                profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 ||
                profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 ||
                profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus ||
                profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus;
    }
}
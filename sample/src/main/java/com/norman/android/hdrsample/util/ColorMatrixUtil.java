package com.norman.android.hdrsample.util;

import android.media.MediaFormat;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

public class ColorMatrixUtil {

    public static final int COLOR_RANGE_LIMITED = MediaFormat.COLOR_RANGE_LIMITED;
    public static final int COLOR_RANGE_FULL = MediaFormat.COLOR_RANGE_FULL;

    @IntDef({
            COLOR_RANGE_LIMITED,
            COLOR_RANGE_FULL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ColorRange {}

    private static final Map<Integer, ColorMatrix> YUV_TO_RGB_MATRIX_MAP = new HashMap<>();

    private static final float[] BT2020_8BIT_FULL_YUV_TO_RGB = {
            1.000000f, 1.000000f, 1.000000f, 0.000000f,
            -0.000000f, -0.164553f, 1.881400f, 0.000000f,
            1.474600f, -0.571353f, -0.000000f, 0.000000f,
            -0.740191f, 0.369396f, -0.944389f, 1.000000f
    };
    private static final float[] BT2020_8BIT_LIMITED_YUV_TO_RGB = {
            1.164384f, 1.164384f, 1.164384f, 0.000000f,
            -0.000000f, -0.187326f, 2.141772f, 0.000000f,
            1.678674f, -0.650424f, -0.000000f, 0.000000f,
            -0.915688f, 0.347458f, -1.148145f, 1.000000f
    };
    private static final float[] BT2020_10BIT_FULL_YUV_TO_RGB = {
            1.000000f, 1.000000f, 1.000000f, 0.000000f,
            -0.000000f, -0.164553f, 1.881400f, 0.000000f,
            1.474600f, -0.571353f, -0.000000f, 0.000000f,
            -0.738021f, 0.368313f, -0.941620f, 1.000000f
    };

    private static final float[] BT2020_10BIT_LIMITED_YUV_TO_RGB = {
            1.167808f, 1.167808f, 1.167808f, 0.000000f,
            -0.000000f, -0.187877f, 2.148072f, 0.000000f,
            1.683611f, -0.652337f, -0.000000f, 0.000000f,
            -0.915688f, 0.347458f, -1.148145f, 1.000000f
    };

    private static final float[] BT2020_12BIT_FULL_YUV_TO_RGB = {
            1.000000f, 1.000000f, 1.000000f, 0.000000f,
            -0.000000f, -0.164553f, 1.881400f, 0.000000f,
            1.474600f, -0.571353f, -0.000000f, 0.000000f,
            -0.737480f, 0.368043f, -0.940930f, 1.000000f
    };

    private static final float[] BT2020_12BIT_LIMITED_YUV_TO_RGB = {
            1.168664f, 1.168664f, 1.168664f, 0.000000f,
            -0.000000f, -0.188015f, 2.149647f, 0.000000f,
            1.684846f, -0.652816f, -0.000000f, 0.000000f,
            -0.915688f, 0.347458f, -1.148145f, 1.000000f
    };

    static {
        YUV_TO_RGB_MATRIX_MAP.put(8, new ColorMatrix(BT2020_8BIT_FULL_YUV_TO_RGB, BT2020_8BIT_LIMITED_YUV_TO_RGB));
        YUV_TO_RGB_MATRIX_MAP.put(10, new ColorMatrix(BT2020_10BIT_FULL_YUV_TO_RGB, BT2020_10BIT_LIMITED_YUV_TO_RGB));
        YUV_TO_RGB_MATRIX_MAP.put(12, new ColorMatrix(BT2020_12BIT_FULL_YUV_TO_RGB, BT2020_12BIT_LIMITED_YUV_TO_RGB));
    }


    public static float[] getYuvToRgbMatrix(int bitDepth,@ColorRange int colorRange){
        ColorMatrix colorMatrix = YUV_TO_RGB_MATRIX_MAP.get(bitDepth);
        if(colorMatrix == null){
            throw new NullPointerException("not support bitDepth "+bitDepth+" colorRange"+colorRange);
        }
        return colorRange == MediaFormat.COLOR_RANGE_FULL ?
                colorMatrix.fullMatrix :
                colorMatrix.limitMatrix;
    }


   static class ColorMatrix {
        float[] fullMatrix;
        float[] limitMatrix;

       public ColorMatrix(float[] fullMatrix, float[] limitMatrix) {
           this.fullMatrix = fullMatrix;
           this.limitMatrix = limitMatrix;
       }
   }
}

package com.norman.android.hdrsample.player.decode;

import android.view.Surface;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface VideoDecoder extends Decoder {

    String KEY_YUV420_TYPE = "yuv420Type";
    int BUFFER_MODE = 1;
    int SURFACE_MODE = 2;
    @IntDef({BUFFER_MODE, SURFACE_MODE})
    @Retention(RetentionPolicy.SOURCE)
    @interface OutputMode {
    }

    static VideoDecoder create() {
        return new VideoDecoderImpl();
    }

    void setOutputSurface(Surface surface);


    void setOutputMode(@OutputMode int outputMode);

    boolean isSupportYUV420P010BufferMode();

    boolean isSupportColorFormat(int colorFormat);

}

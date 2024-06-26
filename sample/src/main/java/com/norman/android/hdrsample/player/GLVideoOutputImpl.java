package com.norman.android.hdrsample.player;

import android.graphics.Rect;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import com.norman.android.hdrsample.opengl.GLEnvColorSpace;
import com.norman.android.hdrsample.opengl.GLEnvConfig;
import com.norman.android.hdrsample.opengl.GLEnvConfigSimpleChooser;
import com.norman.android.hdrsample.opengl.GLEnvContext;
import com.norman.android.hdrsample.opengl.GLEnvContextManager;
import com.norman.android.hdrsample.opengl.GLEnvDisplay;
import com.norman.android.hdrsample.opengl.GLEnvWindowSurface;
import com.norman.android.hdrsample.opengl.GLTextureSurface;
import com.norman.android.hdrsample.player.color.ColorRange;
import com.norman.android.hdrsample.player.color.ColorSpace;
import com.norman.android.hdrsample.player.decode.VideoDecoder;
import com.norman.android.hdrsample.util.GLESUtil;
import com.norman.android.hdrsample.util.MediaFormatUtil;
import com.norman.android.hdrsample.util.TimeUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GLVideoOutput的具体实现，先通过外部纹理(OES或Y2Y)或YUV420 Buffer转成2D纹理，然后再用frontTarget和backTarget对纹理
 * 作Transform输出一个最终纹理，然后把纹理上屏， 如果最终纹理是PQ或者HLG传递函数，上屏的Surface要配置对应的色域
 * 注意TextureView在Android13以下配置对应的色域是无效的，只有SurfaceView或者Android以上才有效，这个时候可以转成SDR解决
 */
class GLVideoOutputImpl extends GLVideoOutput {

    /**
     * EGLContext管理器
     */
    private GLEnvContextManager envContextManager;
    /**
     *
     */
    private GLEnvContext envContext;

    /**
     * 如果是Surface模式，视频就先输出到videoSurface的纹理上
     */
    private GLTextureSurface videoSurface;

    private final OutputSurface outputSurface = new OutputSurface();//对最终渲染的Surface对应的GLWindowSurface的封装

    /**
     * OES纹理渲染
     */
    private final GLTextureRenderer externalTextureRenderer = new GLTextureOESRenderer();
    /**
     * Y2Y纹理渲染
     */
    private final GLTextureY2YRenderer y2yExtTextureRenderer = new GLTextureY2YRenderer();

    /**
     * 2D纹理渲染
     */
    private final GLTexture2DRenderer texture2DRenderer = new GLTexture2DRenderer();

    /**
     * 如果是Buffer模式，就把Buffer根据YUV420的四种格式对应转换成纹理
     */
    private final GLYUV420Renderer bufferYUV420Renderer = new GLYUV420Renderer();

    /**
     * 如果是Buffer模式，Buffer转成后的纹理
     */
    private final GLRenderTextureTarget yuv420TextureTarget = new GLRenderTextureTarget();

    /**
     * 纹理处理转换器
     */
    private final List<GLVideoTransform> transformList = new ArrayList<>();


    /**
     * 渲染到屏幕上
     */
    private final GLRenderScreenTarget screenTarget = new GLRenderScreenTarget();


    /**
     * Transform转换时用frontTarget和backTarget交替做为中转
     */
    private GLRenderTextureTarget frontTarget = new GLRenderTextureTarget();

    private GLRenderTextureTarget backTarget = new GLRenderTextureTarget();

    /**
     * buffer转纹理模式
     */
    private boolean bufferMode;
    /**
     * true表示Y2Y转纹理，不然就是OES转纹理
     */

    private boolean textureY2YMode;
    /**
     * 根据profile判断是10位
     */
    private boolean profile10Bit;

    private @ColorSpace int colorSpace;

    private int maxContentLuminance;
    private int maxFrameAverageLuminance;
    private int maxMasteringLuminance;

    private @ColorRange int colorRange;



    private  @TextureSource int textureSource = TextureSource.AUTO;

    private  @HdrBitDepth int hdrDisplayBitDepth = HdrBitDepth.BIT_DEPTH_10;


    public GLVideoOutputImpl() {

    }

    @Override
    protected void onOutputStop() {
        externalTextureRenderer.destroy();
        y2yExtTextureRenderer.destroy();
        texture2DRenderer.destroy();
        bufferYUV420Renderer.destroy();
        yuv420TextureTarget.destroy();
        screenTarget.destroy();
        frontTarget.destroy();
        backTarget.destroy();
        for (GLVideoTransform videoTransform : transformList) {
            videoTransform.destroy();
        }
        outputSurface.release();
        envContextManager.detach();
        if (videoSurface != null) {
            videoSurface.release();
        }
    }

    @Override
    protected void onOutputSurfaceChange(Surface surface) {
        outputSurface.setSurface(surface);
    }

    @Override
    protected void onOutputPrepare(MediaFormat inputFormat) {

        profile10Bit = MediaFormatUtil.is10BitProfile(inputFormat);
        GLEnvDisplay glEnvDisplay = GLEnvDisplay.createDisplay();

        // 8位
        GLEnvConfig configRGBA8Bit = glEnvDisplay.chooseConfig(new GLEnvConfigSimpleChooser.Builder()
                .build());

        // 10位，注意alpha是2位，视频不需要alpha够用了，其他需要alpha的情况下就不够用了
        GLEnvConfig configRGBA1010102Bit = glEnvDisplay.chooseConfig(new GLEnvConfigSimpleChooser.Builder()
                .setRedSize(10)
                .setGreenSize(10)
                .setBlueSize(10)
                .setAlphaSize(2)
                .build());

        //  16位
        GLEnvConfig configRGBA16Bit = glEnvDisplay.chooseConfig(new GLEnvConfigSimpleChooser.Builder()
                .setRedSize(16)
                .setGreenSize(16)
                .setBlueSize(16)
                .setAlphaSize(16)
                .build());

        GLEnvConfig envConfig = configRGBA8Bit;
        if (profile10Bit) {
            if (hdrDisplayBitDepth == HdrBitDepth.BIT_DEPTH_8){
                envConfig = configRGBA8Bit;
            }else if (hdrDisplayBitDepth == HdrBitDepth.BIT_DEPTH_16 ||
                    Build.MODEL.equals("MIX 2S")) {  //MIX 2S手机10位+PQ视频+SurfaceView没有HDR效果需要改成16位
                envConfig = configRGBA16Bit;
            } else if (hdrDisplayBitDepth == HdrBitDepth.BIT_DEPTH_10) {
                envConfig = configRGBA1010102Bit;
            }
            if (envConfig == null) {
                envConfig = configRGBA8Bit;
            }
        }
        envContextManager = GLEnvContextManager.create(glEnvDisplay, envConfig);
        envContextManager.attach();
        envContext = envContextManager.getEnvContext();
        if (textureSource == TextureSource.AUTO) {
            // 支持10位YUV420Buffer就用Buffer模式，不然就用外部纹理模式
            bufferMode = profile10Bit &&
                    videoDecoder.isSupport10BitYUV420BufferMode();
        } else {
            bufferMode = textureSource == TextureSource.BUFFER;
        }
        if (bufferMode) {//buffer解码
            videoDecoder.setOutputSurface(null);
            videoDecoder.setOutputMode(VideoDecoder.OutputMode.BUFFER_MODE);
        } else {//解码到Surface
            videoDecoder.setOutputMode(VideoDecoder.OutputMode.SURFACE_MODE);
            videoSurface = new GLTextureSurface(GLESUtil.createExternalTextureId());
            videoDecoder.setOutputSurface(videoSurface);// 视频解码到videoSurface的纹理上
            externalTextureRenderer.setTextureId(videoSurface.getTextureId());
            y2yExtTextureRenderer.setTextureId(videoSurface.getTextureId());
        }
    }

    @Override
    public synchronized void setTextureSource(@TextureSource int textureSource) {
        if (isPlayerPrepared()){
            throw new IllegalStateException("setTextureSource must before prepare");
        }
        this.textureSource = textureSource;
    }


    @Override
    public synchronized @TextureSource int getTextureSource() {
        return textureSource;
    }

    @Override
    public synchronized void setHdrBitDepth(@HdrBitDepth int hdrDisplayBitDepth) {
        if (isPlayerPrepared()){
            throw new IllegalStateException("setHdrDisplayBitDepth must before prepare");
        }
        this.hdrDisplayBitDepth = hdrDisplayBitDepth;
    }

    @Override
    public synchronized @HdrBitDepth int getHdrDisplayBitDepth() {
        return hdrDisplayBitDepth;
    }

    /**
     * 添加GLVideoTransform
     *
     * @param videoTransform
     */
    @Override
    public synchronized void addVideoTransform(GLVideoTransform videoTransform) {
        transformList.add(videoTransform);
    }

    @Override
    protected void onOutputFormatChanged(MediaFormat outputFormat) {
        super.onOutputFormatChanged(outputFormat);
        colorRange = MediaFormatUtil.getColorRange(outputFormat);
        colorSpace = MediaFormatUtil.getColorSpace(outputFormat);
        //MediaExtractor不兼容KEY_HDR10_PLUS_INFO，不论HDR10还是HDR10+出来的都是KEY_HDR_STATIC_INFO，后续看看怎么解决
        ByteBuffer hdrStaticInfo = MediaFormatUtil.getByteBuffer(outputFormat, MediaFormat.KEY_HDR_STATIC_INFO);
        if (hdrStaticInfo != null) {
            hdrStaticInfo.clear();
            hdrStaticInfo.position(1);
            hdrStaticInfo.limit(hdrStaticInfo.capacity());
            hdrStaticInfo.order(ByteOrder.LITTLE_ENDIAN);
            ShortBuffer shortBuffer = hdrStaticInfo.asShortBuffer();
            int primaryRChromaticityX = shortBuffer.get(0);
            int primaryRChromaticityY = shortBuffer.get(1);
            int primaryGChromaticityX = shortBuffer.get(2);
            int primaryGChromaticityY = shortBuffer.get(3);
            int primaryBChromaticityX = shortBuffer.get(4);
            int primaryBChromaticityY = shortBuffer.get(5);
            int whitePointChromaticityX = shortBuffer.get(6);
            int whitePointChromaticityY = shortBuffer.get(7);
            maxMasteringLuminance = shortBuffer.get(8);
            int minMasteringLuminance = shortBuffer.get(9);
            maxContentLuminance = shortBuffer.get(10);
            maxFrameAverageLuminance = shortBuffer.get(11);
        } else {
            maxMasteringLuminance = 0;
            maxContentLuminance = 0;
            maxFrameAverageLuminance = 0;
        }
        if (bufferMode) {//用buffer转纹理
            int strideWidth = MediaFormatUtil.getInteger(outputFormat, MediaFormat.KEY_STRIDE);
            int sliceHeight = MediaFormatUtil.getInteger(outputFormat, MediaFormat.KEY_SLICE_HEIGHT);
            int yuv420Type = MediaFormatUtil.getYUV420Type(outputFormat);
            int bitDepth = strideWidth / videoWidth == 2 ? 10 : 8;// strideWidth表示字节宽度，除以宽就是表示几个字节，10位其实是16位(2个字节)存储
            bufferYUV420Renderer.setBufferFormat(strideWidth, sliceHeight, bitDepth, new Rect(cropLeft, cropTop, cropRight, cropBottom), yuv420Type);
        } else {//用扩展纹理转2D纹理
            if (textureSource == TextureSource.AUTO ||
                    textureSource == TextureSource.EXT) {
                // HDR且支持Y2Y才有必要用Y2Y处理
                textureY2YMode = colorSpace != ColorSpace.VIDEO_SDR &&
                        GLTextureY2YRenderer.isSupportY2YEXT();
            } else {
                textureY2YMode = textureSource == TextureSource.Y2Y;
            }
            if (textureY2YMode) {
                y2yExtTextureRenderer.setBitDepth(profile10Bit ? 10 : 8);
                y2yExtTextureRenderer.setColorRange(colorRange);
            }
        }
    }

    @Override
    protected void onOutputBufferAvailable(ByteBuffer outputBuffer, long presentationTimeUs) {
        super.onOutputBufferAvailable(outputBuffer, presentationTimeUs);
        if (bufferMode) {//输入buffer
            bufferYUV420Renderer.updateBuffer(outputBuffer);
        }
    }

    @Override
    protected synchronized boolean onOutputBufferRender(long presentationTimeUs) {
        if (!outputSurface.isValid()) {
            return false;
        }
        GLTextureRenderer textureRenderer;
        if (bufferMode) {
            // buffer通过bufferYUV420Renderer转成2D纹理，2D纹理再去后续处理就能重采样，虽然消耗了一点性能但是方便
            // 注意bufferYUV420Renderer是通过texelFetch获取量化数据的不能重采样，要确保yuv420的图像大小和yuv420TextureTarget图像大小一样
            textureRenderer = texture2DRenderer;
            yuv420TextureTarget.setRenderSize(videoWidth, videoHeight);
            bufferYUV420Renderer.renderToTarget(yuv420TextureTarget);
            textureRenderer.setTextureId(yuv420TextureTarget.textureId);
        } else {//两种扩展纹理 OES或Y2Y
            textureRenderer = textureY2YMode ? y2yExtTextureRenderer : externalTextureRenderer;
            videoSurface.updateTexImage();
            videoSurface.getTransformMatrix(textureRenderer.getTextureMatrix());//纹理矩阵能解决绿边问题
        }

        GLTextureRenderer screenRenderer;
        int finalColorSpace = colorSpace;
        if (transformList.isEmpty()) {//没有transform直接输出到screen
            screenRenderer = textureRenderer;
        } else {
            // 前面得到的纹理输出到frontTarget上
            int targetBitDepth = 8;
            if (profile10Bit){
                if (hdrDisplayBitDepth == HdrBitDepth.BIT_DEPTH_8){
                    targetBitDepth =  8;
                }else if (hdrDisplayBitDepth == HdrBitDepth.BIT_DEPTH_10){
                    targetBitDepth =  10;
                }else if (hdrDisplayBitDepth == HdrBitDepth.BIT_DEPTH_16){
                    targetBitDepth =  16;
                }
            }
            frontTarget.setBitDepth(targetBitDepth);
            backTarget.setBitDepth(targetBitDepth);
            frontTarget.setRenderSize(videoWidth, videoHeight);
            backTarget.setRenderSize(videoWidth, videoHeight);

            // 标记frontTarget的属性，方便后续处理
            frontTarget.setColorSpace(colorSpace);
            frontTarget.setMaxContentLuminance(maxContentLuminance);
            frontTarget.setMaxFrameAverageLuminance(maxFrameAverageLuminance);
            frontTarget.setMaxMasteringLuminance(maxMasteringLuminance);
            //把前面的数据渲染到新的纹理上面
            textureRenderer.renderToTarget(frontTarget);

            //用frontTarget和backTarget做中转做Transform的处理
            for (GLVideoTransform videoTransform : transformList) {
                videoTransform.renderToTarget(frontTarget, backTarget);
                if (videoTransform.renderSuccess) {//如果绘制成功了，才中转纹理
                    GLRenderTextureTarget temp = frontTarget;
                    frontTarget = backTarget;
                    backTarget = temp;
                }
            }
            // 获得最终纹理
            texture2DRenderer.setTextureId(frontTarget.textureId);
            finalColorSpace = frontTarget.colorSpace;//
            screenRenderer = texture2DRenderer;
        }
        GLEnvWindowSurface windowSurface = outputSurface.getWindowSurface(finalColorSpace);
        if (windowSurface == null) {
            return false;
        }
        // 把最终的纹理上屏
        envContext.makeCurrent(windowSurface);
        screenTarget.setRenderSize(windowSurface.getWidth(), windowSurface.getHeight());
        screenTarget.clearColor();
        screenRenderer.renderToTarget(screenTarget);
        windowSurface.setPresentationTime(TimeUtil.microToNano(presentationTimeUs));
        windowSurface.swapBuffers();
        return true;
    }


    class OutputSurface {
        private GLEnvWindowSurface windowSurface;

        private Surface surface;

        private int lastColorSpace;


        public  void setSurface(Surface surface) {
            synchronized (GLVideoOutputImpl.this){
                this.surface = surface;
                if (surface == null || !surface.isValid()) {
                    release();
                }
            }

        }


        public void release() {
            synchronized (GLVideoOutputImpl.this){
                if (windowSurface == null) {
                    return;
                }
                windowSurface.release();
                windowSurface = null;
            }
        }

        public  boolean isValid() {
            synchronized (GLVideoOutputImpl.this){
                return surface != null && surface.isValid();
            }
        }

        public  GLEnvWindowSurface getWindowSurface(@ColorSpace int requestColorSpace) {
            synchronized (GLVideoOutputImpl.this){
                if (surface == null) {
                    release();
                    return null;
                }
                if (windowSurface == null ||
                        surface != windowSurface.getSurface() ||
                        this.lastColorSpace != requestColorSpace) {//surface不同或者色域不同就要重新创建WindowSurface
                    release();
                    GLEnvDisplay envDisplay = envContext.getEnvDisplay();
                    GLEnvWindowSurface.Builder builder = new GLEnvWindowSurface.Builder(envContext, surface);
                    if (isSupportHDR(surface)) {//判断Surface是否支持HDR
                        if (requestColorSpace == ColorSpace.VIDEO_BT2020_PQ) {
                            if (envDisplay.isSupportBT2020PQ()) {
                                builder.setColorSpace(GLEnvColorSpace.BT2020_PQ);
                            }
                        } else if (requestColorSpace == ColorSpace.VIDEO_BT2020_HLG) {
                            if (envDisplay.isSupportBT2020HLG()) {
                                builder.setColorSpace(GLEnvColorSpace.BT2020_HLG);
                            }
                        } else if (requestColorSpace == ColorSpace.VIDEO_BT2020_LINEAR) {
                            if (envDisplay.isSupportBT2020Linear()) {
                                builder.setColorSpace(GLEnvColorSpace.BT2020_LINEAR);
                            }
                        }
                    }
                    windowSurface = builder.build();
                    this.lastColorSpace = requestColorSpace;
                }
                if (!windowSurface.isValid()) {
                    release();
                }
                return windowSurface;
            }
        }

        /**
         * 如果是SurfaceView的Surface(通过判断toString是否包含字符串null)或者版本13以上Surface就支持HDR
         *
         * @param surface
         * @return
         */
        private boolean isSupportHDR(Surface surface) {
            if (surface == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return true;
            }
            String str = surface.toString();
            String pattern = "Surface\\(name=([^)]+)\\)";
            Pattern regexPattern = Pattern.compile(pattern);
            Matcher matcher = regexPattern.matcher(str);
            if (!matcher.find()) {
                return false;
            }
            String extractedValue = matcher.group(1);
            return extractedValue.equals("null");
        }
    }
}

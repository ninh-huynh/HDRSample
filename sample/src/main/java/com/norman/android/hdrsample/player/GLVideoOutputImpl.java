package com.norman.android.hdrsample.player;

import android.graphics.Rect;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import com.norman.android.hdrsample.opengl.GLEnvConfigSimpleChooser;
import com.norman.android.hdrsample.opengl.GLEnvContext;
import com.norman.android.hdrsample.opengl.GLEnvContextManager;
import com.norman.android.hdrsample.opengl.GLEnvDisplay;
import com.norman.android.hdrsample.opengl.GLEnvSurface;
import com.norman.android.hdrsample.opengl.GLEnvWindowSurface;
import com.norman.android.hdrsample.opengl.GLTextureSurface;
import com.norman.android.hdrsample.player.decode.VideoDecoder;
import com.norman.android.hdrsample.player.extract.VideoExtractor;
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
 * GLVideoOutput的具体实现
 */
class GLVideoOutputImpl extends GLVideoOutput {

    private GLEnvContextManager envContextManager;
    private GLEnvContext envContext;

    private GLTextureSurface videoSurface;//如果是Surface模式，视频就先输出到videoSurface的纹理上

    private final PlayerSurface playerSurface = new PlayerSurface();//对最终渲染的Surface对应的GLWindowSurface的封装

    /**
     * EXTERNAL_OES纹理渲染
     */
    private final GLTextureRenderer externalTextureRenderer = new GLTextureRenderer(GLTextureRenderer.TYPE_TEXTURE_EXTERNAL_OES);
    /**
     * Y2YExtension纹理渲染
     */
    private final GLY2YExtensionRenderer y2yExtTextureRenderer = new GLY2YExtensionRenderer();

    /**
     * 2D纹理渲染
     */
    private final GLTextureRenderer texture2DRenderer = new GLTextureRenderer(GLTextureRenderer.TYPE_TEXTURE_2D);

    /**
     * 如果是Buffer模式，就把Buffer根据YUV420的四种格式对应转换成纹理
     */
    private final GLYUV420Renderer bufferYUV420Renderer = new GLYUV420Renderer();

    /**
     * 如果是Buffer模式，Buffer转成纹理的对象
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

    private @VideoExtractor.ColorRange int colorRange;

    private VideoView videoView;

    @TextureSourceType
    private final int textureSourceType;

    @HdrDisplayBitDepth
    private final   int hdrDisplayBitDepth;

    private final VideoView.SurfaceSubscriber surfaceSubscriber = new VideoView.SurfaceSubscriber() {
        @Override
        public void onSurfaceAvailable(Surface surface, int width, int height) {

            playerSurface.setOutputSurface(surface);

        }

        @Override
        public void onSurfaceRedraw() {
            waitNextFrame(DEFAULT_WAIT_TIME_SECOND);//等待下一帧数据准备好，防止第一次黑屏
        }

        @Override
        public void onSurfaceDestroy() {
            playerSurface.setOutputSurface(null);
        }
    };

    private final OutputSizeSubscriber outputSizeSubscriber = new OutputSizeSubscriber() {
        @Override
        public void onOutputSizeChange(int width, int height) {
            videoView.setAspectRatio(width * 1.0f / height);//修改View比例和视频一样
        }
    };

    public GLVideoOutputImpl() {
        this(TEXTURE_SOURCE_TYPE_AUTO);
    }

    public GLVideoOutputImpl(@TextureSourceType int textureSourceType) {
        this(textureSourceType,HDR_DISPLAY_BIT_DEPTH_10);
    }

    public GLVideoOutputImpl(@TextureSourceType int textureSourceType,@HdrDisplayBitDepth int hdrDisplayBitDepth) {
        this.textureSourceType = textureSourceType;
        this.hdrDisplayBitDepth =hdrDisplayBitDepth;
    }

    @Override
    protected void onOutputStop() {
        playerSurface.release();
        envContextManager.detach();
        if (videoSurface != null) {
            videoSurface.release();
        }
    }

    @Override
    public synchronized void setOutputSurface(Surface surface) {
        setOutputVideoView(null);//VideoView和Surface同时只能设置一个
        this.playerSurface.setOutputSurface(surface);
    }

    @Override
    public synchronized void setOutputVideoView(VideoView view) {
        if (videoView == view) {
            return;
        }
        VideoView oldView = videoView;
        if (oldView != null) {
            oldView.unsubscribe(surfaceSubscriber);
            unsubscribe(outputSizeSubscriber);
        }
        videoView = view;
        if (videoView != null) {
            subscribe(outputSizeSubscriber);
            videoView.subscribe(surfaceSubscriber);
        }
    }

    @Override
    protected void onOutputPrepare(MediaFormat inputFormat) {
        profile10Bit = MediaFormatUtil.is10BitProfile(inputFormat);
        GLEnvDisplay glEnvDisplay = GLEnvDisplay.createDisplay();
        GLEnvConfigSimpleChooser.Builder configBuilder = new GLEnvConfigSimpleChooser.Builder();
        if (profile10Bit) {
            if (hdrDisplayBitDepth == HDR_DISPLAY_BIT_DEPTH_10){
                configBuilder.setRedSize(10);
                configBuilder.setGreenSize(10);
                configBuilder.setBlueSize(10);
                configBuilder.setAlphaSize(2);
            }else if (hdrDisplayBitDepth == HDR_DISPLAY_BIT_DEPTH_16){
                configBuilder.setRedSize(16);
                configBuilder.setGreenSize(16);
                configBuilder.setBlueSize(16);
                configBuilder.setAlphaSize(16);
            }
            if (!glEnvDisplay.supportConfig(configBuilder.build())){
                configBuilder.setRedSize(8);
                configBuilder.setGreenSize(8);
                configBuilder.setBlueSize(8);
                configBuilder.setAlphaSize(8);
            }
        }
        envContextManager = GLEnvContextManager.create(glEnvDisplay,configBuilder.build());
        envContextManager.attach();
        envContext = envContextManager.getEnvContext();
        if (textureSourceType == TEXTURE_SOURCE_TYPE_AUTO){// 如果是自动判断用哪种方式，支持10位YUV420Buffer就用Buffer模式，不然就用外部纹理模式
            // 支持10位YUV420 Buffer
            bufferMode = profile10Bit &&
                    videoDecoder.isSupport10BitYUV420BufferMode();
        }else {
            bufferMode = textureSourceType == TEXTURE_SOURCE_TYPE_BUFFER;
        }
        if (bufferMode) {
            videoDecoder.setOutputMode(VideoDecoder.BUFFER_MODE);
        } else {
            videoDecoder.setOutputMode(VideoDecoder.SURFACE_MODE);
            videoSurface = new GLTextureSurface(GLESUtil.createExternalTextureId());
            videoDecoder.setOutputSurface(videoSurface);// 视频解码到videoSurface的纹理上
            externalTextureRenderer.setTextureId(videoSurface.getTextureId());
            y2yExtTextureRenderer.setTextureId(videoSurface.getTextureId());
        }
    }

    /**
     * 添加GLVideoTransform
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
        colorSpace = MediaFormatUtil.getInteger(outputFormat,KEY_COLOR_SPACE,COLOR_SPACE_SDR);
        //MediaExtractor不兼容KEY_HDR10_PLUS_INFO，不论HDR10还是HDR10+出来的都是KEY_HDR_STATIC_INFO，后续看看怎么解决
        ByteBuffer hdrStaticInfo = MediaFormatUtil.getByteBuffer(outputFormat, MediaFormat.KEY_HDR_STATIC_INFO);
        if (hdrStaticInfo != null) {
            hdrStaticInfo.clear();
            hdrStaticInfo.position(1);
            hdrStaticInfo.limit(hdrStaticInfo.capacity());
            hdrStaticInfo.order(ByteOrder.LITTLE_ENDIAN);
            ShortBuffer shortBuffer = hdrStaticInfo.asShortBuffer();
            int primaryRChromaticityX = shortBuffer.get(0);
            int primaryRChromaticityY = shortBuffer.get(1);;
            int primaryGChromaticityX = shortBuffer.get(2);;
            int primaryGChromaticityY = shortBuffer.get(3);;
            int primaryBChromaticityX = shortBuffer.get(4);;
            int primaryBChromaticityY = shortBuffer.get(5);;
            int whitePointChromaticityX = shortBuffer.get(6);;
            int whitePointChromaticityY = shortBuffer.get(7);;
            int maxMasteringLuminance = shortBuffer.get(8);;
            int minMasteringLuminance = shortBuffer.get(9);;
            int maxContentLuminance = shortBuffer.get(10);;
            int maxFrameAverageLuminance = shortBuffer.get(11);
            this.maxContentLuminance =   maxFrameAverageLuminance <=0? 1000: maxFrameAverageLuminance;//todo 应该用哪个 maxMasteringLuminance maxContentLuminance maxFrameAverageLuminance
        }else{
            maxContentLuminance = 0;
        }
        if (bufferMode) {//用buffer转纹理
            int strideWidth = MediaFormatUtil.getInteger(outputFormat, MediaFormat.KEY_STRIDE);
            int sliceHeight = MediaFormatUtil.getInteger(outputFormat, MediaFormat.KEY_SLICE_HEIGHT);
            int yuv420Type = MediaFormatUtil.getInteger(outputFormat, VideoDecoder.KEY_YUV420_TYPE);
            int bitDepth = strideWidth / videoWidth == 2 ? 10 : 8;// strideWidth表示字节宽度，除以宽就是表示几个字节，10位其实是16位存储，也就是2个字节其实是10bit
            bufferYUV420Renderer.setBufferFormat(strideWidth, sliceHeight, bitDepth, new Rect(cropLeft, cropTop, cropRight, cropBottom), yuv420Type);
        } else {//用扩展纹理转2D纹理
            if (textureSourceType == TEXTURE_SOURCE_TYPE_AUTO ||
                    textureSourceType == TEXTURE_SOURCE_TYPE_EXT){
                // HDR且支持Y2Y才有必要用Y2Y处理
                textureY2YMode = colorSpace !=COLOR_SPACE_SDR &&
                        GLY2YExtensionRenderer.isSupportY2YEXT();
            }else {
                textureY2YMode = textureSourceType == TEXTURE_SOURCE_TYPE_Y2Y;
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
        if (!playerSurface.isValid()) {
            return false;
        }
        GLTextureRenderer textureRenderer;
        if (bufferMode) {
            // buffer通过bufferYUV420Renderer转成yuv420TextureTarget的纹理，然后textureRenderer在处理这个纹理
            // 注意bufferYUV420Renderer是通过texelFetch获取量化数据的不能重采样，要确保yuv420的图像大小和yuv420TextureTarget图像大小一样
            // 先输出到纹理上，纹理再去后续处理就能重采样，虽然消耗了一点性能但是方便
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
            int targetBitDepth = profile10Bit ? (hdrDisplayBitDepth == HDR_DISPLAY_BIT_DEPTH_16 ?16:10):8;
            frontTarget.setBitDepth(targetBitDepth);
            backTarget.setBitDepth(targetBitDepth);
            frontTarget.setRenderSize(videoWidth, videoHeight);
            backTarget.setRenderSize(videoWidth, videoHeight);
            textureRenderer.renderToTarget(frontTarget);

            // 标记frontTarget的属性，方便后续处理
            frontTarget.setColorSpace(colorSpace);
            frontTarget.setMaxContentLuminance(maxContentLuminance);

            //用frontTarget和backTarget做中转做Transform的处理
            for (GLVideoTransform videoTransform : transformList) {
                videoTransform.renderToTarget(frontTarget, backTarget);
                if (videoTransform.transformSuccess) {//如果标记不处理，就不用中转，这样才一次纹理绘制
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
        GLEnvWindowSurface windowSurface = playerSurface.getWindowSurface(finalColorSpace);//
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

    class PlayerSurface {
        private GLEnvWindowSurface windowSurface;

        private Surface outputSurface;

        private int colorSpace;

        public synchronized void setOutputSurface(Surface surface) {
            outputSurface = surface;
            if (surface == null || !surface.isValid()) {
                release();
            }
        }


        public synchronized void release() {
            if (windowSurface == null) {
                return;
            }
            windowSurface.release();
            windowSurface = null;
        }

        public synchronized boolean isValid() {
            return outputSurface != null && outputSurface.isValid();
        }

        public synchronized GLEnvWindowSurface getWindowSurface(@ColorSpace int  requestColorSpace) {
            if (outputSurface == null) {
                release();
                return null;
            }
            if (windowSurface == null ||
                    outputSurface != windowSurface.getSurface() ||
                    this.colorSpace != requestColorSpace) {//surface不同或者色域不同就要重新创建WindowSurface
                release();
                GLEnvDisplay envDisplay = envContext.getEnvDisplay();
                GLEnvWindowSurface.Builder builder = new GLEnvWindowSurface.Builder(envContext, outputSurface);
                if (isSupportHDR(outputSurface)) {//判断Surface是否支持HDR
                    if (requestColorSpace == COLOR_SPACE_BT2020_PQ){
                        if (envDisplay.isSupportBT2020PQ()){
                            builder.setColorSpace(GLEnvSurface.EGL_COLOR_SPACE_BT2020_PQ);
                        }
                    }else if (requestColorSpace == COLOR_SPACE_BT2020_HLG){
                        if (envDisplay.isSupportBT2020HLG()){
                            builder.setColorSpace(GLEnvSurface.EGL_COLOR_SPACE_BT2020_HLG);
                        }
                    }
                }
                windowSurface = builder.build();
                this.colorSpace = requestColorSpace;
            }
            if (!windowSurface.isValid()) {
                release();
            }
            return windowSurface;
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
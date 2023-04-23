package com.jonanorman.android.hdrsample.player;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.jonanorman.android.hdrsample.player.opengl.env.GLEnvDisplay;
import com.jonanorman.android.hdrsample.util.GLESUtil;
import com.jonanorman.android.hdrsample.util.Matrix4;

import java.nio.FloatBuffer;

public class AndroidTexturePlayerRenderer {

    private static final int VERTEX_LENGTH = 2;
    private static final String EXTENSION_YUV_TARGET = "GL_EXT_YUV_target";


    private static final float POSITION_COORDINATES[] = {
            -1.0f, -1.0f,//left bottom
            1.0f, -1.0f,//right bottom
            -1.0f, 1.0f,//left top
            1.0f, 1.0f,//right top
    };


    private static final float TEXTURE_COORDINATES[] = {
            0.0f, 0.0f,//left bottom
            1.0f, 0.0f,//right bottom
            0.0f, 1.0f,//left top
            1.0f, 1.0f,//right  top
    };


    private final static String OES_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying highp vec2 textureCoordinate;\n" +
                    "uniform samplerExternalOES inputImageTexture;\n" +
                    "\n" +
                    "void main()\n" +
                    "{\n" +
                    "    vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);\n" +
                    "    mat3 tonemap = mat3(1.6605, -0.1246, -0.0182,\n" +
                    "    -0.5876,  1.1329, -0.1006,\n" +
                    "   -0.0728, -0.0083,  1.1187);\n" +
                    "   vec3 tonemapColor = tonemap * textureColor.rgb;\n" +
                    "    gl_FragColor = vec4(tonemapColor.rgb,textureColor.a);\n" +
                    "}";

    private static final String OES_VERTEX_SHADER = "precision mediump float;\n" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "uniform mat4 textureMatrix;\n" +
            "\n" +
            "varying vec2 textureCoordinate;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = (textureMatrix*inputTextureCoordinate).xy;\n" +
            "}";


    private static final String EXT_2DY2Y_VERTEX_SHADER = "#version 300 es\n" +
            "in vec4 position;\n" +
            "in vec4 inputTextureCoordinate;\n" +
            "uniform mat4 textureMatrix;\n" +
            "out vec2 textureCoordinate;\n" +
            "void main() {\n" +
            "    gl_Position =position;\n" +
            "    textureCoordinate =(textureMatrix*inputTextureCoordinate).xy;\n" +
            "}";

    private static final String EXT_2DY2Y_FRAGMENT_SHADER = "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "#extension GL_EXT_YUV_target : require\n" +
            "precision highp float;\n" +
            "\n" +
            "uniform __samplerExternal2DY2YEXT inputImageTexture;\n" +
            "\n" +
            "in  vec2 textureCoordinate;\n" +
            "out vec4 outColor;\n" +
            "\n" +
            "vec4 yuv_to_rgb(vec4 yuv){\n" +
            "    mat4 colorMat = mat4(\n" +
            "    1.167808, 1.167808, 1.167808, 0.0,\n" +
            "    0.0, -0.187877, 2.148072, 0.0,\n" +
            "    1.683611, -0.652337, 0.000000, 0.0,\n" +
            "    -0.915688, 0.347458, -1.148145, 1.0\n" +
            "    );\n" +
            "    return colorMat* yuv;\n" +
            "}\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    vec4 yuv  = texture(inputImageTexture, textureCoordinate);\n" +
            "    vec4 rgb = yuv_to_rgb(yuv);\n" +
            "    mat3 tonemap = mat3(1.6605, -0.1246, -0.0182,\n" +
            "    -0.5876,  1.1329, -0.1006,\n" +
            "   -0.0728, -0.0083,  1.1187);\n" +
            "   vec3 tonemapColor = tonemap * rgb.rgb;\n" +
            "    outColor = vec4(tonemapColor.rgb,rgb.a);\n" +
            "}\n" +
            "\n";



    private FloatBuffer textureCoordinateBuffer;
    private FloatBuffer positionCoordinateBuffer;

    private int positionCoordinateAttribute;
    private int textureCoordinateAttribute;
    private int textureUnitUniform;
    private int textureMatrixUniform;

    private int programId;

    private int textureId;

    private boolean hasLocation;

    private int width;
    private int height;

    private boolean release;

    private Matrix4 textureMatrix = new Matrix4();

    public AndroidTexturePlayerRenderer() {
        positionCoordinateBuffer = GLESUtil.createDirectFloatBuffer(POSITION_COORDINATES);
        textureCoordinateBuffer = GLESUtil.createDirectFloatBuffer(TEXTURE_COORDINATES);
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        if (extensions != null && extensions.contains(EXTENSION_YUV_TARGET)){
            this.programId = GLESUtil.createProgramId(EXT_2DY2Y_VERTEX_SHADER, EXT_2DY2Y_FRAGMENT_SHADER);
        }else {
            this.programId = GLESUtil.createProgramId(OES_VERTEX_SHADER, OES_FRAGMENT_SHADER);
        }

    }

    public void setSurfaceSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setTextureId(int textureId) {
        this.textureId = textureId;
    }


    public Matrix4 getTextureMatrix() {
        return textureMatrix;
    }

    public void render() {
        if (release) {
            return;
        }
        positionCoordinateBuffer.clear();
        textureCoordinateBuffer.clear();
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(programId);
        GLES20.glViewport(0, 0, width, height);
        if (!hasLocation) {
            hasLocation = true;
            positionCoordinateAttribute = GLES20.glGetAttribLocation(programId, "position");
            textureCoordinateAttribute = GLES20.glGetAttribLocation(programId, "inputTextureCoordinate");
            textureUnitUniform = GLES20.glGetUniformLocation(programId, "inputImageTexture");
            textureMatrixUniform = GLES20.glGetUniformLocation(programId, "textureMatrix");

        }
        GLES20.glEnableVertexAttribArray(positionCoordinateAttribute);
        GLES20.glVertexAttribPointer(positionCoordinateAttribute, VERTEX_LENGTH, GLES20.GL_FLOAT, false, 0, positionCoordinateBuffer);
        if (textureCoordinateAttribute >= 0) {
            GLES20.glEnableVertexAttribArray(textureCoordinateAttribute);
            GLES20.glVertexAttribPointer(textureCoordinateAttribute, VERTEX_LENGTH, GLES20.GL_FLOAT, false, 0, textureCoordinateBuffer);
        }
        if (textureUnitUniform >= 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(textureUnitUniform, 0);
        }
        GLES20.glUniformMatrix4fv(textureMatrixUniform, 1, false, textureMatrix.get(), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(positionCoordinateAttribute);
        if (textureCoordinateAttribute >= 0) {
            GLES20.glDisableVertexAttribArray(textureCoordinateAttribute);
        }
        GLES20.glUseProgram(0);
        GLES20.glDisable(GLES20.GL_BLEND);
        if (textureUnitUniform >= 0) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }
    }


    public void release() {
        if (release) {
            return;
        }
        release = true;
        GLESUtil.delProgramId(programId);
    }

}

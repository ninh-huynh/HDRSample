package com.norman.android.hdrsample;

import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.norman.android.hdrsample.player.GLVideoOutput;
import com.norman.android.hdrsample.player.VideoPlayer;
import com.norman.android.hdrsample.player.VideoView;
import com.norman.android.hdrsample.player.source.AssetFileSource;
import com.norman.android.hdrsample.transform.CubeLutVideoTransform;
import com.norman.android.hdrsample.transform.HDRToSDRVideoTransform;
import com.norman.android.hdrsample.transform.shader.chromacorrect.ChromaCorrection;
import com.norman.android.hdrsample.transform.shader.gamma.GammaOETF;
import com.norman.android.hdrsample.transform.shader.gamutmap.GamutMap;
import com.norman.android.hdrsample.transform.shader.tonemap.ToneMap;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HDRPlayActivity extends AppCompatActivity implements View.OnClickListener,HdrToSdrShaderDialog.OnShaderSelectListener {
    VideoPlayer videoPlayer;
    VideoView videoView;
    CubeLutVideoTransform videoTransform;

    AlertDialog cubeLutDialog;

    boolean loadLutSuccess;

    List<String> lutPathList = new ArrayList<>();

    List<String> lutNameList = new ArrayList<>();

    int selectLutPosition;
    HDRToSDRVideoTransform hdrToSDRVideoTransform;

    HdrToSdrShaderDialog hdrToSdrShaderDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_hdr_player);
        hdrToSdrShaderDialog = new HdrToSdrShaderDialog(this);
        hdrToSdrShaderDialog.setOnShaderSelectListener(this);
        videoView = findViewById(R.id.VideoPlayerView);
        GLVideoOutput videoOutput = GLVideoOutput.create(GLVideoOutput.TEXTURE_SOURCE_TYPE_BUFFER);
        videoPlayer = VideoPlayer.create(videoOutput);
//        1.mp4
//        2.mp4  //
//        3.mp4
//        4.mp4
//        5.mp4
//        6.mp4
//        7.mp4
//        bear-1280x720-hevc-10bit-hdr10.mp4
//        google-bag-to-sky-hlg-hdr.mp4
//        hdr10-video-with-sdr-container.mp4
//        hlg-1080p.mp4
//        video_1280x720_hevc_hdr10_static_3mbps.mp4
//        video_h265_hdr10plus.mp4

        videoPlayer.setSource(AssetFileSource.create("video/1.mp4"));
        videoTransform = new CubeLutVideoTransform();
        hdrToSDRVideoTransform = new HDRToSDRVideoTransform();
        videoOutput.addVideoTransform(videoTransform);
        videoOutput.addVideoTransform(hdrToSDRVideoTransform);
        videoOutput.setOutputVideoView(videoView);

        findViewById(R.id.ButtonCubeLut).setOnClickListener(this);
        findViewById(R.id.ButtonHdrToSdr).setOnClickListener(this);



    }


    @Override
    protected void onResume() {
        super.onResume();
        videoPlayer.play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        videoPlayer.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoPlayer.release();
    }

    private void showCubeLutDialog() {
        if (cubeLutDialog != null) {
            if (!cubeLutDialog.isShowing()) {
                cubeLutDialog.show();
            }
            return;
        }
        loadLutList();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("3D CUBE LUT")
                //.setMessage("You can buy our products without registration too. Enjoy the shopping")
                .setSingleChoiceItems(lutNameList.toArray(new String[0]), selectLutPosition, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectLutPosition = which;
                        String strName = lutPathList.get(which);
                        videoTransform.setCubeLutForAsset(strName);
                        dialog.dismiss();
                        cubeLutDialog = null;
                    }
                });
        cubeLutDialog = builder.show();


    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.ButtonCubeLut) {
            showCubeLutDialog();
        }else if (id == R.id.ButtonHdrToSdr){
            hdrToSdrShaderDialog.show();
        }
    }


    private void loadLutList() {
        if (loadLutSuccess) {
            return;
        }
        loadLutSuccess = true;
        AssetManager assetManager = getResources().getAssets();
        LinkedList<String> pathList = new LinkedList<>();
        pathList.add("lut/pq2sdr");
        List<String> fileList = new ArrayList<>();
        while (!pathList.isEmpty()) {
            String path = pathList.poll();
            String[] names = null;
            try {
                names = assetManager.list(path);
            } catch (Exception ignore) {
                continue;
            }
            if (names.length == 0) {
                fileList.add(path);
            } else {
                for (String name : names) {
                    pathList.add(path + "/" + name);
                }
            }
        }

        List<String> nameList = new ArrayList<>();
        for (String path : fileList) {
            String fileName = new File(path).getName();
            int pos = fileName.lastIndexOf(".");
            if (pos > 0) {
                fileName = fileName.substring(0, pos);
            }
            nameList.add(fileName);
        }
        fileList.add(0, null);
        nameList.add(0, "无");
        lutNameList = nameList;
        lutPathList = fileList;
    }

    @Override
    public void onShaderSelect(ChromaCorrection chromaCorrection, ToneMap toneMap, GamutMap gamutMap, GammaOETF gammaOETF) {
        hdrToSDRVideoTransform.setChromaCorrection(chromaCorrection);
        hdrToSDRVideoTransform.setToneMap(toneMap);
        hdrToSDRVideoTransform.setGamutMap(gamutMap);
        hdrToSDRVideoTransform.setGammaOETF(gammaOETF);
    }
}
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
import com.norman.android.hdrsample.player.source.AssetFileSource;
import com.norman.android.hdrsample.player.view.VideoPlayerView;
import com.norman.android.hdrsample.todo.CubeLutVideoTransform;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HDRPlayActivity extends AppCompatActivity  implements View.OnClickListener {
    VideoPlayer videoPlayer;
    VideoPlayerView surfaceView;
    CubeLutVideoTransform videoTransform;

    AlertDialog cubeLutDialog;

    boolean loadLutSuccess;

    List<String>  lutPathList = new ArrayList<>();

    List<String> lutNameList = new ArrayList<>();

    int selectLutPosition;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_hdr_player);
        surfaceView = findViewById(R.id.VideoPlayerView);
        GLVideoOutput videoOutput = GLVideoOutput.create();
        videoPlayer = VideoPlayer.create(videoOutput);
        videoPlayer.setSource(AssetFileSource.create("video/1.mp4"));
        videoTransform = new CubeLutVideoTransform();
        videoOutput.addVideoTransform(videoTransform);
        surfaceView.setVideoPlayer(videoPlayer);
        findViewById(R.id.ButtonCubeLut).setOnClickListener(this);
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

    private void showCubeLutDialog(){
        if (cubeLutDialog != null){
            if (!cubeLutDialog.isShowing()) {
                cubeLutDialog.show();
            }
            return;
        }
        loadLutList();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Buy Now")
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
        cubeLutDialog =   builder.show();


    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.ButtonCubeLut){
            showCubeLutDialog();
        }
    }


    private void loadLutList(){
        if (loadLutSuccess){
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
                names   = assetManager.list(path);
            }catch (Exception ignore){
                continue;
            }
            if (names.length == 0){
                fileList.add(path);
            }else{
                for (String name : names) {
                    pathList.add(path+"/"+name);
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
        fileList.add(0,null);
        nameList.add(0,"无");
        lutNameList = nameList;
        lutPathList = fileList;


    }
}
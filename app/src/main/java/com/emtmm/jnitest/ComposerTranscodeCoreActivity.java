// Copyright (c) 2014, Intel Corporation
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
// 1. Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
// 3. Neither the name of the copyright holder nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.emtmm.jnitest;

import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;

import com.emtmm.jnitest.controls.TranscodeSurfaceView;
import com.intel.inde.mp.AudioFormat;
import com.intel.inde.mp.IProgressListener;
import com.intel.inde.mp.MediaComposer;
import com.intel.inde.mp.MediaFileInfo;
import com.intel.inde.mp.VideoFormat;
import com.intel.inde.mp.android.AndroidMediaObjectFactory;
import com.intel.inde.mp.android.AudioFormatAndroid;
import com.intel.inde.mp.android.VideoFormatAndroid;
import com.intel.inde.mp.domain.ISurfaceWrapper;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ComposerTranscodeCoreActivity extends ActivityWithTimeline implements SurfaceHolder.Callback {

    protected String srcMediaName1 = null;
    protected String srcMediaName2 = null;
    protected String dstMediaPath = null;
    protected com.intel.inde.mp.Uri mediaUri1;
    protected com.intel.inde.mp.Uri mediaUri2;

    protected MediaFileInfo mediaFileInfo = null;

    protected long duration = 0;

    protected ProgressBar progressBar;

    ///////////////////////////////////////////////////////////////////////////

    protected AudioFormat audioFormat = null;
    protected VideoFormat videoFormat = null;

    // Transcode parameters

    // Video
    protected int videoWidthOut = 640;
    protected int videoHeightOut = 480;

    protected int videoWidthIn = 640;
    protected int videoHeightIn = 480;

    protected final String videoMimeType = "video/avc";
    protected int videoBitRateInKBytes = 5000;
    protected final int videoFrameRate = 30;

    protected final int videoIFrameInterval = 1;
    // Audio
    protected final String audioMimeType = "audio/mp4a-latm";
    protected final int audioSampleRate = 48000;
    protected final int audioChannelCount = 2;

    protected final int audioBitRate = 96 * 1024;

    ///////////////////////////////////////////////////////////////////////////

    // Media Composer parameters and logic

    protected MediaComposer mediaComposer;

    private boolean isStopped = false;

    public IProgressListener progressListener = new IProgressListener() {
        @Override
        public void onMediaStart() {

            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setProgress(0);
                        updateUI(true);
                    }
                });
            } catch (Exception e) {
            }
        }

        @Override
        public void onMediaProgress(float progress) {

            final float mediaProgress = progress;

            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setProgress((int) (progressBar.getMax() * mediaProgress));
                    }
                });
            } catch (Exception e) {
            }
        }

        @Override
        public void onMediaDone() {
            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isStopped) {
                            return;
                        }
                        updateUI(false);
                        reportTranscodeDone();
                    }
                });
            } catch (Exception e) {
            }
        }

        @Override
        public void onMediaPause() {
        }

        @Override
        public void onMediaStop() {
        }

        @Override
        public void onError(Exception exception) {
            try {
                final Exception e = exception;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateUI(false);
                        String message = (e.getMessage() != null) ? e.getMessage() : e.toString();
                        showMessageBox("Transcoding failed." + "\n" + message, null);
                    }
                });
            } catch (Exception e) {
            }
        }
    };
    protected AndroidMediaObjectFactory factory;

    /////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.composer_transcode_core_activity);


        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setMax(100);

        ////
        TranscodeSurfaceView transcodeSurfaceView = (TranscodeSurfaceView) findViewById(R.id.transcodeSurfaceView);
        transcodeSurfaceView.getHolder().addCallback(this);
        ////


        getActivityInputs();

        getFileInfo();
        setupUI();
        printFileInfo();

        transcodeSurfaceView.setImageSize(videoWidthOut, videoHeightOut);

        updateUI(false);
        startTranscode();
    }

    @Override
    protected void onDestroy() {
        if (mediaComposer != null) {
            mediaComposer.stop();
            isStopped = true;
        }
        super.onDestroy();
    }

    protected void setupUI() {
//        buttonStart.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                startTranscode();
//            }
//        });
//
//        buttonStop.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                stopTranscode();
//            }
//        });
    }

    ///////////////////////////////////////////////////////////////////////////

    protected void getActivityInputs() {

        Bundle b = getIntent().getExtras();
        srcMediaName1 = b.getString("srcMediaName1");
        dstMediaPath = b.getString("dstMediaPath");
        mediaUri1 = new com.intel.inde.mp.Uri(b.getString("srcUri1"));
    }

    /////////////////////////////////////////////////////////////////////////

    protected void getFileInfo() {
        try {
            mediaFileInfo = new MediaFileInfo(new AndroidMediaObjectFactory(getApplicationContext()));
            mediaFileInfo.setUri(mediaUri1);

            duration = mediaFileInfo.getDurationInMicroSec();

            audioFormat = (AudioFormat) mediaFileInfo.getAudioFormat();
            if (audioFormat == null) {
                showMessageBox("Audio format info unavailable", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                });
            }

            videoFormat = (VideoFormat) mediaFileInfo.getVideoFormat();
            if (videoFormat == null) {
                showMessageBox("Video format info unavailable", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                });
            } else {
                videoWidthIn = videoFormat.getVideoFrameSize().width();
                videoHeightIn = videoFormat.getVideoFrameSize().height();
            }
        } catch (Exception e) {
            String message = (e.getMessage() != null) ? e.getMessage() : e.toString();

            showMessageBox(message, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                }
            });
        }
    }

    protected void displayVideoFrame(SurfaceHolder holder) {

        if (videoFormat != null) {
            try {
                ISurfaceWrapper surface = AndroidMediaObjectFactory.Converter.convert(holder.getSurface());
                mediaFileInfo.setOutputSurface(surface);

                ByteBuffer buffer = ByteBuffer.allocate(1);
                mediaFileInfo.getFrameAtPosition(100, buffer);

            } catch (Exception e) {
                String message = (e.getMessage() != null) ? e.getMessage() : e.toString();

                showMessageBox(message, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                });
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        displayVideoFrame(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    ///////////////////////////////////////////////////////////////////////////

    protected void configureVideoEncoder(MediaComposer mediaComposer, int width, int height) {

        VideoFormatAndroid videoFormat = new VideoFormatAndroid(videoMimeType, width, height);

        videoFormat.setVideoBitRateInKBytes(videoBitRateInKBytes);
        videoFormat.setVideoFrameRate(videoFrameRate);
        videoFormat.setVideoIFrameInterval(videoIFrameInterval);

        mediaComposer.setTargetVideoFormat(videoFormat);
    }

    protected void configureAudioEncoder(MediaComposer mediaComposer) {

        AudioFormatAndroid audioFormat = new AudioFormatAndroid(audioMimeType, audioSampleRate, audioChannelCount);

        audioFormat.setAudioBitrateInBytes(audioBitRate);
        audioFormat.setAudioProfile(MediaCodecInfo.CodecProfileLevel.AACObjectLC);

        mediaComposer.setTargetAudioFormat(audioFormat);
    }

    protected void setTranscodeParameters(MediaComposer mediaComposer) throws IOException {

        mediaComposer.addSourceFile(mediaUri1);
        mediaComposer.setTargetFile(dstMediaPath);

        configureVideoEncoder(mediaComposer, videoWidthOut, videoHeightOut);
        configureAudioEncoder(mediaComposer);
    }

    protected void transcode() throws Exception {

        factory = new AndroidMediaObjectFactory(getApplicationContext());
        mediaComposer = new MediaComposer(factory, progressListener);
        setTranscodeParameters(mediaComposer);
        mediaComposer.start();
    }

    public void startTranscode() {

        try {
            transcode();

        } catch (Exception e) {

            String message = (e.getMessage() != null) ? e.getMessage() : e.toString();

            showMessageBox(message, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                }
            });
        }
    }

    public void stopTranscode() {
        mediaComposer.stop();
    }

    private void reportTranscodeDone() {

        String message = "Transcoding finished.";

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                progressBar.setVisibility(View.INVISIBLE);

                OnClickListener l = new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        playResult();
                    }
                };

            }
        };
        showMessageBox(message, listener);
    }

    private void playResult() {

        String videoUrl = "file:///" + dstMediaPath;

        Intent intent = new Intent(android.content.Intent.ACTION_VIEW);

        Uri data = Uri.parse(videoUrl);
        intent.setDataAndType(data, "video/mp4");
        startActivity(intent);
    }

    private void updateUI(boolean inProgress) {
        if (inProgress) {
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    ////////////////////////////////////////////////////////////////////////////    

    protected void printPaths() {
//        pathInfo.setText(String.format("srcMediaFileName = %s\ndstMediaPath = %s\n", srcMediaName1, dstMediaPath));
    }

    protected void getDstDuration() {
    }

    protected void printDuration() {
        getDstDuration();
//        durationInfo.setText(String.format("Duration = %d sec", TimeUnit.MICROSECONDS.toSeconds(duration)));
    }

    protected void printEffectDetails() {
    }

    protected void printFileInfo() {
        printPaths();
        printDuration();
        printEffectDetails();
    }
}



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

import android.content.Intent;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;

import com.emtmm.jnitest.controls.TimelineItem;
import com.intel.inde.mp.AudioFormat;
import com.intel.inde.mp.IProgressListener;
import com.intel.inde.mp.MediaComposer;
import com.intel.inde.mp.MediaFileInfo;
import com.intel.inde.mp.VideoFormat;
import com.intel.inde.mp.android.AndroidMediaObjectFactory;


public class ComposerCutActivity extends ActivityWithTimeline implements View.OnClickListener {
    TimelineItem mItem;

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

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.composer_cut_activity);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setMax(100);

        getActivityInputs();

        getFileInfo();
        setupUI();

        updateUI(false);
        startTranscode();

        init();
    }

    private void init() {
        mItem = (TimelineItem) findViewById(R.id.timelineItem);
        mItem.setEventsListener(this);
        mItem.enableSegmentPicker(true);

        ((Button) findViewById(R.id.action)).setOnClickListener(this);

        mItem.onOpen();
    }

    public void action() {
        String mediaFileName = mItem.getMediaFileName();

        if (mediaFileName == null) {
            showToast("Please select a valid video file first.");

            return;
        }

        mItem.stopVideoView();

        int segmentFrom = mItem.getSegmentFrom();
        int segmentTo = mItem.getSegmentTo();

        Intent intent = new Intent();
        intent.setClass(this, ComposerCutCoreActivity.class);

        Bundle b = new Bundle();
        b.putString("srcMediaName1", mItem.getMediaFileName());
        intent.putExtras(b);
        b.putString("dstMediaPath", mItem.genDstPath(mItem.getMediaFileName(), "segment"));
        intent.putExtras(b);
        b.putLong("segmentFrom", segmentFrom);
        intent.putExtras(b);
        b.putLong("segmentTo", segmentTo);
        intent.putExtras(b);
        b.putString("srcUri1", mItem.getUri().getString());
        intent.putExtras(b);

        startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        switch (id) {
            case R.id.action: {
                action();
            }
            break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mItem != null) {
            mItem.updateView();
        }
    }

    @Override
    protected void onDestroy() {
        if (mediaComposer != null) {
            mediaComposer.stop();
            isStopped = true;
        }
        super.onDestroy();
    }

}

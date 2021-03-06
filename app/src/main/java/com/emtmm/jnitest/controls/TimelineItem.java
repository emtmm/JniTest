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

package com.emtmm.jnitest.controls;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.VideoView;

import com.emtmm.jnitest.Format;
import com.emtmm.jnitest.R;
import com.intel.inde.mp.MediaFileInfo;
import com.intel.inde.mp.android.AndroidMediaObjectFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class TimelineItem extends RelativeLayout implements RangeSelector.RangeSelectorEvents {

    private static final String DEFAULT_MEDIA_PACK_FOLDER = "UssembleVideos";
    private TextView mStartTime;
    private TextView mEndTime;

    public void onOpen() {
        if (mEvents != null) {
            mEvents.onOpen(this);
        }
    }

    public interface TimelineItemEvents {

        public void onOpen(TimelineItem item);
        public void onDelete(TimelineItem item);

    }
    private TimelineItemEvents mEvents;

    private Context mContext;

    private String mediaFileName = null;

    private VideoView mVideoView;
    private RangeSelector mSegmentSelector;

    private MediaFileInfo mMediaInfo;

    private long mVideoDuration;
    private long mVideoPosition;

    private boolean mEnableSegmentPicker;

    public TimelineItem(Context context) {
        super(context);

        mContext = context;

        init(null, 0);
    }

    public TimelineItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;

        init(attrs, 0);
    }

    public TimelineItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;

        init(attrs, defStyle);
    }

    public void setEventsListener(TimelineItemEvents listener) {
        mEvents = listener;
    }

    public void enableSegmentPicker(boolean enable) {
        mEnableSegmentPicker = enable;

        int visibility = enable ? View.VISIBLE : View.INVISIBLE;

        if (mMediaInfo.getUri() != null) {
            mSegmentSelector.setVisibility(visibility);
        }
    }

    /*
    Get segment starting position in milliseconds
     */
    public int getSegmentFrom() {
        return percentToPosition(mSegmentSelector.getStartPosition());
    }

    /*
    Get segment ending position in milliseconds
     */
    public int getSegmentTo() {
        return percentToPosition(mSegmentSelector.getEndPosition());
    }

    public String getMediaFileName() {
        return mediaFileName;
    }

    public com.intel.inde.mp.Uri getUri() {
        return mMediaInfo.getUri();
    }

    public long getMediaFileDurationInSec() { return TimeUnit.SECONDS.convert(mVideoDuration, TimeUnit.MICROSECONDS); }

    public void setMediaFileName(String name) {
        mediaFileName = name;
    }

    public void setMediaUri(com.intel.inde.mp.Uri uri) {
        int visibility = (uri == null) ? View.INVISIBLE : View.VISIBLE;

        if (mEnableSegmentPicker) {
            mSegmentSelector.setVisibility(visibility);
        }

        mVideoView.setVisibility(visibility);

        if (uri == null) {
            mediaFileName = null;

            mVideoDuration = 0;
            mVideoPosition = 0;

            postInvalidate();

            return;
        }

        try {
            mMediaInfo.setUri(uri);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unsupported media file format");
        }

        mVideoDuration = mMediaInfo.getDurationInMicroSec();
        mVideoPosition = (mVideoDuration / 2);

        mVideoView.setVideoURI(Uri.parse(uri.getString()));

        int duration = (int) Math.round((mVideoDuration / 1000000));


        mSegmentSelector.setStartPosition(0);
        mSegmentSelector.setEndPosition(duration > 30 ? ((30 * 100) + RangeSelector.HandleSize) / duration : 100);

        mSegmentSelector.setTime(duration);

        setStartTimeView();
        setEndTimeView();

        showPreview(10);
    }
    
    public String genDstPath(String srcName, String effect)
    {
        String substring = srcName.substring(0, srcName.lastIndexOf('.'));
        File outputFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath(), DEFAULT_MEDIA_PACK_FOLDER);

        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        return ( outputFolder.getPath() + "/" + substring + "_" + effect + ".mp4");
    }

    public String genDstPath(String srcPath1, String srcPath2, String effect)
    {
        String extension = srcPath1.substring(srcPath1.lastIndexOf('.') + 1);
                
        String srcFileName2 = srcPath2.substring(srcPath1.lastIndexOf('/') + 1);
        String srcFileName2Base = srcFileName2.substring(0, srcFileName2.lastIndexOf('.'));
        
        String dstPath = srcPath1.replace( "." + extension, "_" + srcFileName2Base + "_" + effect + ".mp4");
        
        return dstPath;
    }      

    public String getVideoEffectName(int index) {
    	
    	String baseName = "video_effect_";

        switch (index){
            case 0:  return baseName + "sepia";
            case 1:  return baseName + "grayscale";
            case 2:  return baseName + "inverse";
            case 3:  return baseName + "text_overlay";
            default: return baseName + "unknown";
        }    	
    }
    
    private void init(AttributeSet attrs, int defStyle) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        inflater.inflate(R.layout.timeline_item, this, true);

        mVideoView = (VideoView) findViewById(R.id.thumbinail);

        mSegmentSelector = (RangeSelector) findViewById(R.id.segment);
        mSegmentSelector.setEventsListener(this);

        mStartTime = (TextView) findViewById(R.id.time_start);
        mEndTime = (TextView) findViewById(R.id.time_end);

        mMediaInfo = new MediaFileInfo(new AndroidMediaObjectFactory(mContext));

        mEnableSegmentPicker = true;

        setMediaUri(null);
    }

    public void stopVideoView() {
        mVideoView.stopPlayback();
    }


    @Override
    public void onStartPositionChanged(int position) {
        setStartTimeView();
        showPreview(position);
    }

    @Override
    public void onEndPositionChanged(int position) {
        setEndTimeView();
        showPreview(position);
    }

    private void setStartTimeView() {
        String duration = Format.duration(getSegmentFrom()/1000);
        mStartTime.setText(duration);
    }

    private void setEndTimeView() {
        String duration = Format.duration(getSegmentTo()/1000);
        mEndTime.setText(duration);
    }

    public void updateView() {
        if (mVideoView != null && mMediaInfo.getUri() != null) {
            int position = (int) mVideoPosition;
            mVideoView.seekTo(position);
        }
    }

    private void showPreview(int position) {
        if (mMediaInfo.getUri() == null || mMediaInfo.getUri().getString().isEmpty()) {
            return;
        }

        int seekTo = percentToPosition(position);

        mVideoPosition = seekTo / 1000;

        mVideoView.seekTo((int) mVideoPosition);
    }

    private int percentToPosition(int percent) {

        return (int) (mVideoDuration * percent / 100);
    }

}

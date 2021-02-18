package com.iflytek.mictest;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class AudioPlayer {
    private String TAG = AudioPlayer.class.getSimpleName();
    private AudioTrack mAudioTrack;

    public AudioPlayer() {
        int mAudioMinBufSize = AudioTrack.getMinBufferSize(48000,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 48000,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, mAudioMinBufSize,
                AudioTrack.MODE_STREAM);
    }

    public void play() {
        mAudioTrack.play();
    }

    public void writeAudio(byte[] buf, int offset, int len) {
        Log.d(TAG, "writeAudio:" + len);
        mAudioTrack.write(buf, offset, len);
    }

    public void stop() {
        mAudioTrack.stop();
    }
}

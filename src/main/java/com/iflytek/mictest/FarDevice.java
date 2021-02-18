package com.iflytek.mictest;

import android.content.Context;
import android.util.Log;

public class FarDevice {
    private String TAG = FarDevice.class.getSimpleName();
    private boolean mIsStartRecord = false;
    private IRecordListener mIRecordListener;

    public FarDevice(Context context) {
        LocalTransfer.getInstance().setOnOnConnectedListener(new LocalTransfer.OnConnectedListener() {
            @Override
            public void onConnected() {
                LocalTransfer.getInstance().setAuthSuccess();
            }

            @Override
            public void onAudio(byte[] data, int len) {
                if (mIsStartRecord) {
                    mIRecordListener.onData(data, len);
                }
            }
        });
        LocalTransfer.getInstance().start();
    }

    public void startRecord(IRecordListener iRecordListener) {
        Log.e(TAG, "startRecord");
        mIRecordListener = iRecordListener;
        mIsStartRecord = true;
    }

    public void stopRecord() {
        mIsStartRecord = false;
    }

    public interface IRecordListener {
        void onData(byte[] buf, int len);
    }
}

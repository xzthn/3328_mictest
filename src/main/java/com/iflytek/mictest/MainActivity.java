package com.iflytek.mictest;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.mic.MIC;
import com.iflytek.mictest.FarDevice.IRecordListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends Activity implements OnClickListener {
    private String TAG = MainActivity.class.getSimpleName();
    private final int START_PLAY_AUDIO = 0x1001;
    private final int START_RECORD = 0x1002;
    private final int STOP_RECORD = 0x1003;
    private final int CHECK_START = 0x1004;
    private final int CHECK_FINISH = 0x1005;
    private final int START_AUTHEN = 0x1006;
    private final String RECORD_PATH = Environment
            .getExternalStorageDirectory().getAbsolutePath()
            + File.separator
            + "record.pcm";
    private AudioPlayer mAudioPlayer;
    private FarDevice mFarDevice;
    private Context mContext;
    private Handler mMaiHandler;
    private StringBuilder mContentBuf;
    private TextView mContentTV;
    private TextView mResult1Tv;
    private TextView mResult2Tv;
    private TextView mResult3Tv;
    private TextView mResult4Tv;
    private TextView mResultRef1Tv;
    private TextView mResultRef2Tv;
    private TextView mEnergeTv;
    private TextView mStateVol;
    private boolean isInTest;
    private String version = "";
    private int Mic1Ret;
    private int Mic2Ret;
    private int Mic3Ret;
    private int Mic4Ret;
    private int Ref1Ret;
    private int Ref2Ret;

    private static final int MAX_VOL = (1 << 15) - 1;
    private final HashMap<Integer, List<Float>> channelPowerData = new HashMap<Integer, List<Float>>();
    private boolean isStartRecord = false;

    // 播音线程
    class PlayerThread extends Thread {
        @Override
        public void run() {
            InputStream in;
            try {
                in = mContext.getAssets().open("recordtest.pcm");
                byte[] buf = new byte[1024];
                mAudioPlayer.play();
                int len = 0;
                while ((len = in.read(buf)) != -1) {
                    mAudioPlayer.writeAudio(buf, 0, len);
                }
                mAudioPlayer.stop();
                mMaiHandler.sendEmptyMessage(STOP_RECORD);
                refreshContent("测试音频播放结束.\n");
            } catch (IOException e) {
                refreshContent("播放测试音频失败.\n");
                mMaiHandler.sendEmptyMessage(STOP_RECORD);
            }
        }
    }

    // 录音线程
    FileOutputStream mRecordFos = null;

    class RecordThread extends Thread {
        @Override
        public void run() {
            // String usbVersion = mFarDevice.getSerialNumber();
            // refreshContent("USB固件版本号:" + usbVersion + ".\n");
            File file = new File(RECORD_PATH);
            if (file.exists()) { file.delete(); }
            try {
                mRecordFos = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            if (mRecordFos == null) {
                refreshContent("创建录音文件失败.\n");
                return;
            }
            refreshContent("创建录音文件" + RECORD_PATH + ".\n" + "开始录音.\n");

            channelPowerData.get(1).clear();
            channelPowerData.get(2).clear();
            channelPowerData.get(3).clear();
            channelPowerData.get(5).clear();
            channelPowerData.get(6).clear();
            channelPowerData.get(7).clear();

            mFarDevice.startRecord(new IRecordListener() {

                @Override
                public void onData(byte[] buf, int len) {
                    try {
                        MyLog.hex_dump(TAG, buf, 0, len);
                        int sampleNum = len / 4;
                        float[] dbCompute = {0, 0, 0, 0, 0, 0, 0, 0};
                        for (int i = 0; i < sampleNum; i++) {
                            int start = 4 * i;
                            int channel = (buf[start + 1] & 0x0F) - 1;
                            if (channel > 7) {
                                Log.e(TAG, "error channel---" + channel);
                                continue;
                            }
                            int vol = ((short) buf[start + 3] << 8 | buf[start + 2]);
                            dbCompute[channel] = vol > dbCompute[channel] ? vol
                                    : -vol > dbCompute[channel] ? -vol
                                    : dbCompute[channel];
                        }
                        LogUtil.w(TAG, "dbCompute[0] " + dbCompute[0] + "dbCompute[1] " + dbCompute[1] + "dbCompute[2] " + dbCompute[2]
                                + "dbCompute[4] " + dbCompute[4] + "dbCompute[5] " + dbCompute[5] + "dbCompute[6] " + dbCompute[6]);

                        if (dbCompute[0] > 0) {
                            channelPowerData.get(1).add(dbCompute[0]);
                        }
                        if (dbCompute[1] > 0) {
                            channelPowerData.get(2).add(dbCompute[1]);
                        }
                        if (dbCompute[2] > 0) {
                            channelPowerData.get(3).add(dbCompute[2]);
                        }
                        if (dbCompute[4] > 0) {
                            channelPowerData.get(5).add(dbCompute[4]);
                        }
                        if (dbCompute[5] > 0) {
                            channelPowerData.get(6).add(dbCompute[5]);
                        }
                        if (dbCompute[6] > 0) {
                            channelPowerData.get(7).add(dbCompute[6]);
                        }
                        // 6 to 8
                        byte[] buf8 = new byte[len / 6 * 8];
                        int index = 0;
                        for (int i = 0; i < len; i += 24) {
                            // channel 1
                            buf8[index] = buf[i];
                            buf8[index + 1] = buf[i + 1];
                            buf8[index + 2] = buf[i + 2];
                            buf8[index + 3] = buf[i + 3];
                            // channel 2
                            buf8[index + 4] = buf[i + 4];
                            buf8[index + 5] = buf[i + 5];
                            buf8[index + 6] = buf[i + 6];
                            buf8[index + 7] = buf[i + 7];
                            // channel3
                            buf8[index + 8] = buf[i + 16];
                            buf8[index + 9] = buf[i + 17];
                            buf8[index + 10] = buf[i + 18];
                            buf8[index + 11] = buf[i + 19];
                            // channel 4
                            buf8[index + 12] = 0x0;
                            buf8[index + 13] = 0x4;
                            buf8[index + 14] = 0x0;
                            buf8[index + 15] = 0x0;
                            // channel 5
                            buf8[index + 16] = buf[i + 8];
                            buf8[index + 17] = buf[i + 9];
                            buf8[index + 18] = buf[i + 10];
                            buf8[index + 19] = buf[i + 11];
                            // channel 6
                            buf8[index + 20] = buf[i + 12];
                            buf8[index + 21] = buf[i + 13];
                            buf8[index + 22] = buf[i + 14];
                            buf8[index + 23] = buf[i + 15];
                            // channel 7
                            buf8[index + 24] = buf[i + 20];
                            buf8[index + 25] = buf[i + 21];
                            buf8[index + 26] = buf[i + 22];
                            buf8[index + 27] = buf[i + 23];
                            // channel 8
                            buf8[index + 28] = 0x0;
                            buf8[index + 29] = 0x8;
                            buf8[index + 30] = 0x0;
                            buf8[index + 31] = 0x0;
                            index += 32;
                        }
                        mRecordFos.write(buf8);
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            mRecordFos.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }

                }
            });
        }
    }

    // 一致性检查线程
    class CheckThread extends Thread {
        @Override
        public void run() {
            File file = new File(RECORD_PATH);
            try {
                FileInputStream fis = new FileInputStream(file);
                int len = fis.available();
                if (len <= 0) {
                    refreshContent("待检测的音频文为空.\n");
                    return;
                }
                byte[] buf = new byte[len];
                fis.read(buf);
                // 检查第一帧音频是否是序号3
                int channel = buf[1] & 0x0F;
                if (channel != 3) {//todo
                    refreshContent("待检测的音频第一帧channel号不是3.\n");
                    return;
                }
                int[] ibuffer = bytesToInt(buf, len);
                int ret[] = MIC.checkAgreement(ibuffer, ibuffer.length - 1);
                Log.d(TAG, "check len:" + ibuffer.length);
                if (ret == null) {
                    Log.d(TAG, "check res is null.");
                    return;
                }
                for (int i = 0; i < 8; i++) {
                    Log.d(TAG, "ret[" + i + "]:" + ret[i]);
                }
                // 6,2,5,1 => 1,2,3,4
                Mic1Ret = ret[5];
                Mic2Ret = ret[1];
                Mic3Ret = ret[4];
                Mic4Ret = ret[0];
                Ref1Ret = ret[2];
                Ref2Ret = ret[6];
                //
                mMaiHandler.removeCallbacks(updateVolState);
                mMaiHandler.postDelayed(updateResultState, 1000);

                //
                mMaiHandler.sendEmptyMessage(CHECK_FINISH);
            } catch (FileNotFoundException e) {
                refreshContent("读取录音文件失败.\n");
            } catch (IOException e) {
                refreshContent("读取录音文件失败.\n");
            }
        }
    }

//	private IAuthenListener authenListener = new IAuthenListener() {
//		@Override
//		public void authenResult(boolean isPass) {
//			if (isPass) {
//				refreshContent("鉴权通过.\n");
//				mMaiHandler.sendEmptyMessage(START_PLAY_AUDIO);
//			} else {
//				refreshContent("鉴权失败.\n");
//			}
//		}
//	};

    // USB鉴权线程
//	class AuthenThread extends Thread {
//		@Override
//		public void run() {
//			mFarDevice.startAuthen(new IAuthenListener() {
//				@Override
//				public void authenResult(boolean isPass) {
//					if (isPass) {
//						refreshContent("鉴权通过.\n");
//						mMaiHandler.sendEmptyMessage(START_PLAY_AUDIO);
//					} else {
//						refreshContent("鉴权失败.\n");
//					}
//				}
//			});
//		}
//	}

    private VolComputeResult computeDB(List<Float> data, int channelId) {
        VolComputeResult result = new VolComputeResult(channelId);
        List<Float> temp = new ArrayList<Float>();
        temp.addAll(data);
        int num = temp.size();
        if (num < 3) {
            return result;
        }

        float maxValue = 0.0f;
        float minValue = temp.size() > 0 ? temp.get(0) : 0;
        float sum = 0;

        Iterator<Float> it = temp.iterator();
        while (it.hasNext()) {
            float one = it.next();
            maxValue = Math.max(maxValue, one);
            minValue = Math.min(minValue, one);
            sum += one;
        }
        float average = (sum - maxValue - minValue) / (num - 2);
        float db = (float) (10 * Math.log10(average / MAX_VOL));
        result.average = average;
        result.db = db;
        result.max = maxValue;
        result.min = minValue;

        float variance = 0;
        Iterator<Float> it2 = temp.iterator();
        while (it2.hasNext()) {
            float one = it2.next();
            variance = (one - average) * (one - average);
        }
        variance = variance - (maxValue - average) * (maxValue - average)
                - (minValue - average) * (minValue * average);

        variance = variance / (num - 2);
        result.sqrtVariance = (float) Math.sqrt(variance);
        Log.d(TAG, "max:" + maxValue + "  min:" + minValue + "   avg:"
                + average + "  sqrt:" + result.sqrtVariance + "   db:" + db);
        return result;
    }

    private static class VolComputeResult {
        VolComputeResult(int channel) {
            this.channel = channel;
        }

        final int channel;
        float max = 0;
        float min = 0;
        float average = 0;
        float db = 0;
        float sqrtVariance;// 平方差
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        channelPowerData.put(1, new ArrayList<Float>());
        channelPowerData.put(2, new ArrayList<Float>());
        channelPowerData.put(3, new ArrayList<Float>());
        channelPowerData.put(5, new ArrayList<Float>());
        channelPowerData.put(6, new ArrayList<Float>());
        channelPowerData.put(7, new ArrayList<Float>());

        mContext = MainActivity.this;
        PackageManager packageManager = getPackageManager();
        PackageInfo packInfo;
        try {
            packInfo = packageManager.getPackageInfo(getPackageName(), 0);
            version = packInfo.versionName;
        } catch (NameNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        mContentBuf = new StringBuilder();
        mMaiHandler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case START_PLAY_AUDIO:
                        refreshContent("开始播放测试音频.\n");
                        PlayerThread playerThread = new PlayerThread();
                        playerThread.start();
                        // 先播后录
                        sendEmptyMessageDelayed(START_RECORD, 1000);
                        break;
                    case START_RECORD:
                        RecordThread recordThread = new RecordThread();
                        recordThread.start();

                        isStartRecord = true;
                        mMaiHandler.postDelayed(updateVolState, 1000);
                        break;
                    case STOP_RECORD:
                        refreshContent("录音结束.\n");
                        // 停止录音
                        mFarDevice.stopRecord();
                        try {
                            mRecordFos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        sendEmptyMessage(CHECK_START);
                        isStartRecord = false;
                        break;
                    case CHECK_START:
                        refreshContent("开始音频一致性检查.\n");
                        CheckThread checkThread = new CheckThread();
                        checkThread.start();
                        break;
                    case CHECK_FINISH:
                        isInTest = false;
                        refreshContent("一致性检查完成.\n");
                        break;
//				case START_AUTHEN:
//					refreshContent("开始鉴权测试.\n");
//					AuthenThread authenThread = new AuthenThread();
//					authenThread.start();
//					break;
                    default:
                        break;
                }
            }

            ;
        };
        initViews();
        Log.e(TAG, "version=" + version);
//		refreshContent("当前版本号为:" + version + "\n");
        mAudioPlayer = new AudioPlayer();
        // 初始化USB录音
        mFarDevice = new FarDevice(mContext);
        int ret = MIC.init(1, 400);// 400HZ or 800HZ
        Log.d(TAG, "MIC.init ret:" + ret);
        String version = MIC.getVersion();
        Log.d(TAG, "MIC version:" + version);
    }

    private void initViews() {
        mContentTV = (TextView) findViewById(R.id.content_tv);
        mResult1Tv = (TextView) findViewById(R.id.result1_tv);
        mResult2Tv = (TextView) findViewById(R.id.result2_tv);
        mResult3Tv = (TextView) findViewById(R.id.result3_tv);
        mResult4Tv = (TextView) findViewById(R.id.result4_tv);
        mResultRef1Tv = (TextView) findViewById(R.id.resultref1_tv);
        mResultRef2Tv = (TextView) findViewById(R.id.resultref2_tv);
        mEnergeTv = (TextView) findViewById(R.id.energe_tv);
        mStateVol = (TextView) findViewById(R.id.state_vol);
        findViewById(R.id.start_test_btn).setOnClickListener(this);
        findViewById(R.id.exit_btn).setOnClickListener(this);
    }

    public static String getVerName(Context context) {
        String verName = "";
        try {
            verName = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return verName;
    }

    private static final DecimalFormat mDecimalFormat = new DecimalFormat(
            "####0.00");

    private Runnable updateResultState = new Runnable() {

        @Override
        public void run() {
            VolComputeResult channel1Res = computeDB(channelPowerData.get(1), 1);
            VolComputeResult channel2Res = computeDB(channelPowerData.get(2), 2);
            VolComputeResult channel3Res = computeDB(channelPowerData.get(3), 3);
            VolComputeResult channel5Res = computeDB(channelPowerData.get(5), 5);
            VolComputeResult channel6Res = computeDB(channelPowerData.get(6), 6);
            VolComputeResult channel7Res = computeDB(channelPowerData.get(7), 7);

            float minDb = Math.min(channel1Res.db, channel2Res.db);
            minDb = Math.min(minDb, channel5Res.db);
            minDb = Math.min(minDb, channel6Res.db);

            float maxDb = Math.max(channel1Res.db, channel2Res.db);
            maxDb = Math.max(maxDb, channel5Res.db);
            maxDb = Math.max(maxDb, channel6Res.db);
            // maxDb = Math.max(maxDb, channel3Res.db);
            // maxDb = Math.max(maxDb, channel7Res.db);

            float maxRefDb = Math.max(channel3Res.db, channel7Res.db);
            float minRefDb = Math.min(channel3Res.db, channel7Res.db);

            StringBuilder sb = new StringBuilder();
            sb.append("麦克风序号:1").append("\r\n").append("平均能量: ")
                    .append(mDecimalFormat.format(channel6Res.average))
                    .append("\t\t").append("相对分贝值: ")
                    .append(mDecimalFormat.format(channel6Res.db))
                    .append(" db").append("\r\n");
            sb.append("麦克风序号:2").append("\r\n").append("平均能量: ")
                    .append(mDecimalFormat.format(channel2Res.average))
                    .append("\t\t").append("相对分贝值: ")
                    .append(mDecimalFormat.format(channel2Res.db))
                    .append(" db").append("\r\n");
            sb.append("麦克风序号:3").append("\r\n").append("平均能量: ")
                    .append(mDecimalFormat.format(channel5Res.average))
                    .append("\t\t").append("相对分贝值: ")
                    .append(mDecimalFormat.format(channel5Res.db))
                    .append(" db").append("\r\n");
            sb.append("麦克风序号:4").append("\r\n").append("平均能量: ")
                    .append(mDecimalFormat.format(channel1Res.average))
                    .append("\t\t").append("相对分贝值: ")
                    .append(mDecimalFormat.format(channel1Res.db))
                    .append(" db").append("\r\n");
            sb.append(" : 1").append("\r\n").append("平均能量: ")
                    .append(mDecimalFormat.format(channel3Res.average))
                    .append("\t\t").append("相对分贝值: ")
                    .append(mDecimalFormat.format(channel3Res.db))
                    .append(" db").append("\r\n");
            sb.append("参考信号: 2").append("\r\n").append("平均能量: ")
                    .append(mDecimalFormat.format(channel7Res.average))
                    .append("\t\t").append("相对分贝值: ")
                    .append(mDecimalFormat.format(channel7Res.db))
                    .append(" db").append("\r\n\r\n");

            sb.append("麦克风最大分贝差值:  ")
                    .append(mDecimalFormat.format(maxDb - minDb))
                    .append("\r\n\r\n");
            sb.append("参考信号最大分贝差值:  ")
                    .append(mDecimalFormat.format(maxRefDb - minRefDb))
                    .append("\r\n\r\n");

            sb.append("相对分贝 : 相对于声音最大能量值的分贝数。");
            mStateVol.setText(sb.toString());
            if (Math.abs(maxDb - minDb) > 3
                    || Math.abs(maxRefDb - minRefDb) > 1) {
                // Mic1
                boolean isDbPass = true;
                if (channel6Res.db != maxDb && maxDb - channel6Res.db > 3) {
                    isDbPass = false;
                } else {
                    isDbPass = true;
                }
                if (Mic1Ret == 0 && isDbPass) {
                    showResult(mResult1Tv, "1号麦克风测试通过.", Color.GREEN);
                } else if (Mic1Ret != 0 && isDbPass) {
                    showResult(mResult1Tv, "录音数据异常,1号麦克风测试未通过.", Color.RED);
                } else if (Mic1Ret == 0 && !isDbPass) {
                    showResult(mResult1Tv, "录音能量异常,1号麦克风测试未通过.", Color.RED);
                } else if (Mic1Ret != 0 && !isDbPass) {
                    showResult(mResult1Tv, "录音数据和录音能量均异常,1号麦克风测试未通过.",
                            Color.RED);
                }
                // Mic2
                isDbPass = true;
                if (channel2Res.db != maxDb && maxDb - channel2Res.db > 3) {
                    isDbPass = false;
                } else {
                    isDbPass = true;
                }
                if (Mic2Ret == 0 && isDbPass) {
                    showResult(mResult2Tv, "2号麦克风测试通过.", Color.GREEN);
                } else if (Mic2Ret != 0 && isDbPass) {
                    showResult(mResult2Tv, "录音数据异常,2号麦克风测试未通过.", Color.RED);
                } else if (Mic2Ret == 0 && !isDbPass) {
                    showResult(mResult2Tv, "录音能量异常,2号麦克风测试未通过.", Color.RED);
                } else if (Mic2Ret != 0 && !isDbPass) {
                    showResult(mResult2Tv, "录音数据和录音能量均异常,2号麦克风测试未通过.",
                            Color.RED);
                }
                // Mic3
                isDbPass = true;
                if (channel5Res.db != maxDb && maxDb - channel5Res.db > 3) {
                    isDbPass = false;
                } else {
                    isDbPass = true;
                }
                if (Mic3Ret == 0 && isDbPass) {
                    showResult(mResult3Tv, "3号麦克风测试通过.", Color.GREEN);
                } else if (Mic3Ret != 0 && isDbPass) {
                    showResult(mResult3Tv, "录音数据异常,3号麦克风测试未通过.", Color.RED);
                } else if (Mic3Ret == 0 && !isDbPass) {
                    showResult(mResult3Tv, "录音能量异常,3号麦克风测试未通过.", Color.RED);
                } else if (Mic3Ret != 0 && !isDbPass) {
                    showResult(mResult3Tv, "录音数据和录音能量均异常,3号麦克风测试未通过.",
                            Color.RED);
                }
                // Mic4
                isDbPass = true;
                if (channel1Res.db != maxDb && maxDb - channel1Res.db > 3) {
                    isDbPass = false;
                } else {
                    isDbPass = true;
                }
                if (Mic4Ret == 0 && isDbPass) {
                    showResult(mResult4Tv, "4号麦克风测试通过.", Color.GREEN);
                } else if (Mic4Ret != 0 && isDbPass) {
                    showResult(mResult4Tv, "录音数据异常,4号麦克风测试未通过.", Color.RED);
                } else if (Mic4Ret == 0 && !isDbPass) {
                    showResult(mResult4Tv, "录音能量异常,4号麦克风测试未通过.", Color.RED);
                } else if (Mic4Ret != 0 && !isDbPass) {
                    showResult(mResult4Tv, "录音数据和录音能量均异常,4号麦克风测试未通过.",
                            Color.RED);
                }

                // Ref1
                isDbPass = true;
                if (channel3Res.db != maxRefDb && maxRefDb - channel3Res.db > 1) {
                    isDbPass = false;
                } else {
                    isDbPass = true;
                }
                if (Ref1Ret == 0 && isDbPass) {
                    showResult(mResultRef1Tv, "参考信号1测试通过.", Color.GREEN);
                } else if (Ref1Ret != 0 && isDbPass) {
                    showResult(mResultRef1Tv, "录音数据异常,参考信号1测试未通过.", Color.RED);
                } else if (Ref1Ret == 0 && !isDbPass) {
                    showResult(mResultRef1Tv, "录音能量异常,参考信号1测试未通过.", Color.RED);
                } else if (Ref1Ret != 0 && !isDbPass) {
                    showResult(mResultRef1Tv, "录音数据和录音能量均异常,参考信号1测试未通过.",
                            Color.RED);
                }

                // Ref2
                isDbPass = true;
                if (channel7Res.db != maxRefDb && maxRefDb - channel7Res.db > 1) {
                    isDbPass = false;
                } else {
                    isDbPass = true;
                }
                if (Ref2Ret == 0 && isDbPass) {
                    showResult(mResultRef2Tv, "参考信号2测试通过.", Color.GREEN);
                } else if (Ref2Ret != 0 && isDbPass) {
                    showResult(mResultRef2Tv, "录音数据异常,参考信号2测试未通过.", Color.RED);
                } else if (Ref2Ret == 0 && !isDbPass) {
                    showResult(mResultRef2Tv, "录音能量异常,参考信号2测试未通过.", Color.RED);
                } else if (Ref2Ret != 0 && !isDbPass) {
                    showResult(mResultRef2Tv, "录音数据和录音能量均异常,参考信号2测试未通过.",
                            Color.RED);
                }
            } else {
                if (Mic1Ret == 0) {
                    showResult(mResult1Tv, "1号麦克风测试通过.", Color.GREEN);
                } else {
                    showResult(mResult1Tv, "录音数据异常,1号麦克风测试未通过.", Color.RED);
                }
                if (Mic2Ret == 0) {
                    showResult(mResult2Tv, "2号麦克风测试通过.", Color.GREEN);
                } else {
                    showResult(mResult2Tv, "录音数据异常,2号麦克风测试未通过.", Color.RED);
                }
                if (Mic3Ret == 0) {
                    showResult(mResult3Tv, "3号麦克风测试通过.", Color.GREEN);
                } else {
                    showResult(mResult3Tv, "录音数据异常,3号麦克风测试未通过.", Color.RED);
                }
                if (Mic4Ret == 0) {
                    showResult(mResult4Tv, "4号麦克风测试通过.", Color.GREEN);
                } else {
                    showResult(mResult4Tv, "录音数据异常,4号麦克风测试未通过.", Color.RED);
                }
                if (Ref1Ret == 0) {
                    showResult(mResultRef1Tv, "参考信号1测试通过.", Color.GREEN);
                } else {
                    showResult(mResultRef1Tv, "录音数据异常,参考信号1测试未通过.", Color.RED);
                }
                if (Ref2Ret == 0) {
                    showResult(mResultRef2Tv, "参考信号2测试通过.", Color.GREEN);
                } else {
                    showResult(mResultRef2Tv, "录音数据异常,参考信号2测试未通过.", Color.RED);
                }
            }

        }
    };

    private Runnable updateVolState = new Runnable() {

        @Override
        public void run() {

            float currentPower1 = 0;
            float currentPower2 = 0;
            float currentPower3 = 0;
            float currentPower5 = 0;
            float currentPower6 = 0;
            float currentPower7 = 0;

            List<Float> channelData = channelPowerData.get(1);
            if (channelData.size() > 0) {
                currentPower1 = channelData.get(channelData.size() - 1);
            }
            channelData = channelPowerData.get(2);
            if (channelData.size() > 0) {
                currentPower2 = channelData.get(channelData.size() - 1);
            }
            channelData = channelPowerData.get(3);
            if (channelData.size() > 0) {
                currentPower3 = channelData.get(channelData.size() - 1);
            }
            channelData = channelPowerData.get(5);
            if (channelData.size() > 0) {
                currentPower5 = channelData.get(channelData.size() - 1);
            }
            channelData = channelPowerData.get(6);
            if (channelData.size() > 0) {
                currentPower6 = channelData.get(channelData.size() - 1);
            }
            channelData = channelPowerData.get(7);
            if (channelData.size() > 0) {
                currentPower7 = channelData.get(channelData.size() - 1);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("麦克风序号: 1").append("\t").append("当前能量: ")
                    .append(currentPower6).append("\r\n\r\n");
            sb.append("麦克风序号: 2").append("\t").append("当前能量: ")
                    .append(currentPower2).append("\r\n\r\n");
            sb.append("麦克风序号: 3").append("\t").append("当前能量: ")
                    .append(currentPower5).append("\r\n\r\n");
            sb.append("麦克风序号: 4").append("\t").append("当前能量: ")
                    .append(currentPower1).append("\r\n\r\n");
            sb.append("参考信号: 1").append("\t").append("当前能量: ")
                    .append(currentPower3).append("\r\n\r\n");
            sb.append("参考信号: 2").append("\t").append("当前能量: ")
                    .append(currentPower7).append("\r\n\r\n");
            sb.append("最大能量:" + MAX_VOL);
            mStateVol.setText(sb.toString());

            if (isStartRecord) {
                mMaiHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start_test_btn:
                Mic1Ret = 0;
                Mic2Ret = 0;
                Mic3Ret = 0;
                Mic4Ret = 0;
                Ref1Ret = 0;
                Ref2Ret = 0;

                if (isInTest) {
                    Toast.makeText(mContext, "正在测试中.", Toast.LENGTH_SHORT).show();
                } else {
                    clearContent();
                    mResult1Tv.setText("");
                    mResult2Tv.setText("");
                    mResult3Tv.setText("");
                    mResult4Tv.setText("");
                    mResultRef1Tv.setText("");
                    mResultRef2Tv.setText("");
                    mEnergeTv.setText("");
                    isInTest = true;
//				mMaiHandler.sendEmptyMessage(START_AUTHEN);
                    mMaiHandler.sendEmptyMessage(START_PLAY_AUDIO);
                }
                break;
            case R.id.exit_btn:
                mFarDevice.stopRecord();
                finish();
                break;
            default:
                break;
        }
    }

    private void refreshContent(String appendContent) {
        mContentBuf.append(appendContent);
        mMaiHandler.post(new Runnable() {

            @Override
            public void run() {
                mContentTV.setText(mContentBuf.toString());
            }
        });
    }

    private void clearContent() {
        mContentBuf.delete(0, mContentBuf.length());
        mContentTV.setText(mContentBuf.toString());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { return true; }
        return super.onKeyDown(keyCode, event);
    }

    private int[] bytesToInt(byte[] src, int length) {
        int value = 0, offset = 0, i = 0;
        int[] ret = new int[length / 4 + 1];
        while (offset < length && length - offset >= 4) {
            value = (int) ((src[offset] & 0xFF)
                    | ((src[offset + 1] & 0xFF) << 8)
                    | ((src[offset + 2] & 0xFF) << 16) | ((src[offset + 3] & 0xFF) << 24));
            offset += 4;
            ret[i] = value;
            i++;
        }
        return ret;
    }

    private void showResult(TextView tv, String content, int color) {
        tv.setTextColor(color);
        tv.setText(content);
    }

    public static int GET_USB_MANAGER_FAILED = 0x1001;
    public static int GET_USB_MANAGER_OK = 0x1002;
    public static int GET_USB_DEVICE_FAILED = 0X1003;

}

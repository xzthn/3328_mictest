package com.iflytek.mictest;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class LocalTransfer {
    private static final String SCOKETNAME = "PLAYBACKTRANSFER1";
    private static final String TAG = "LocalTransfer";
    private static LocalTransfer instance = null;
    private boolean isAuth = false;
    private boolean running = false;

    private ClientSocketThread mClientSocketThread;
    private Handler mHandler = null;
    private int errorCounter = 0;
    private boolean isConnected;
    private static final boolean DEBUG = false;
    private OnConnectedListener listener;

    public static LocalTransfer getInstance() {
        if (instance == null) {
            instance = new LocalTransfer();
        }
        return instance;
    }

    private LocalTransfer() {
    }

    public void start() {
        if (!running) {
            Log.d(TAG, "LocalTransfer start");
            running = true;
            mClientSocketThread = new ClientSocketThread();
            mClientSocketThread.start();
        }
    }

    void setAuthSuccess() {
        if (!isAuth) {
            Log.d(TAG, "LocalTransfer setAuthSuccess");
            isAuth = true;
            mClientSocketThread.setAuthSuccess();
        }
    }

    void stop() {
        if (running) {
            Log.d(TAG, "LocalTransfer stop");
            running = false;
            isAuth = false;
        }
    }


    private class ClientSocketThread extends Thread {
        private LocalSocket clientSocket = null;
        private InteractClientSocketThread interactThread = null;

        void setAuthSuccess() {
            while (mHandler == null) {
                try {
                    sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            mHandler.sendEmptyMessage(4);
        }

        @Override
        public void run() {
            while (running) {
                try {
                    clientSocket = new LocalSocket();
                    clientSocket.connect(new LocalSocketAddress(SCOKETNAME));
                } catch (IOException e) {
                    if (errorCounter++ < 5) {
                        Log.e(TAG, errorCounter + "connect to server failed:" + e.getMessage());
                    }

                    try {
                        clientSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        Log.e(TAG, " clientSocket.close() failed: " + e1.getMessage());
                    }

                    if (errorCounter >= 1024) {
                        errorCounter = 0;
                    }
                    try {
                        sleep(2000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    continue;
                }
                Log.d(TAG, "connected to media server");

                interactThread = new InteractClientSocketThread(clientSocket);
                interactThread.start();

                try {
                    interactThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (clientSocket != null) {
                    try {
                        clientSocket.close();
                        clientSocket = null;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (clientSocket == null) {
                    Log.e(TAG, "clientSocket is null");
                }
            }
        }
    }

    private class InteractClientSocketThread extends Thread {
        private LocalSocket interactClientSocket;

//        private OutputStream os_origin = null;
//        private OutputStream os_mic = null;
//        private OutputStream os_ref = null;
        private HandlerThread handlerthread = null;

        private void initDataThread() {
            handlerthread = new HandlerThread("data_thread");
            handlerthread.start();
            mHandler = new Handler(handlerthread.getLooper()) {
                byte[] header = null;
                byte[] dataPkg = null;

                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case 4://auth success
                            Log.e(TAG, "auth success");
                            mHandler.removeMessages(4);
                            String s = "success:";
                            dataPkg = new byte[1024];
                            header = s.getBytes();
                            System.arraycopy(header, 0, dataPkg, 0, header.length);
                            try {
                                interactClientSocket.getOutputStream().write(dataPkg);
                                interactClientSocket.getOutputStream().flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                }
            };
            isConnected = true;
            listener.onConnected();
        }

        public InteractClientSocketThread(LocalSocket interactClientSocket) {
            this.interactClientSocket = interactClientSocket;
        }

        private int readBuffer(final InputStream inputStream, final byte[] buffer, final int reqLen) {
            int offset = 0;
            int byteLeft = reqLen;
            try {
                int readLen;
                while (byteLeft > 0 && (readLen = inputStream.read(buffer, offset, byteLeft)) > 0) {
                    offset += readLen;
                    byteLeft -= readLen;
                }
            } catch (IOException ex) {
                Log.e(TAG, "error on read buffer from input stream.", ex);
            }
            return offset;
        }

        @Override
        public void run() {
            Log.d(TAG, "InteractClientSocketThread start");
            initDataThread();
//            if (DEBUG) {
//                try {
//                    if (os_origin != null) {
//                        os_origin.close();
//                    }
//                    if (os_mic != null) {
//                        os_mic.close();
//                    }
//                    if (os_ref != null) {
//                        os_ref.close();
//                    }
//
//                    File pcmfile = new File("/sdcard/audio.pcm");
//                    if (!pcmfile.exists()) {
//                        boolean createSuccess = pcmfile.createNewFile();
//                        Log.e(TAG, "createSuccess: " + createSuccess);
//                    }
//                    os_origin = new FileOutputStream(pcmfile);
//
//                    File micFile = new File("/sdcard/audio_mic.pcm");
//                    File refFile = new File("/sdcard/audio_ref.pcm");
//                    if (!micFile.exists()) {
//                        micFile.createNewFile();
//                    }
//                    os_mic = new FileOutputStream(micFile);
//
//                    if (!refFile.exists()) {
//                        refFile.createNewFile();
//                    }
//                    os_ref = new FileOutputStream(refFile);
//
//                } catch (Exception e1) {
//                    e1.printStackTrace();
//                }
//
//            }
            InputStream inputStream = null;
            try {
                inputStream = interactClientSocket.getInputStream();
                int originSize = 3840;
                byte[] buf_origin = new byte[originSize];

                while (running && readBuffer(inputStream, buf_origin, originSize) == originSize) {
                    listener.onAudio(buf_origin.clone(), originSize);
//                        if (DEBUG) {
//                            os_origin.write(buf_origin);
//                            os_ref.write(buf_ref);
//                            os_mic.write(buf_mic);
//                        }
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "resolve data error !");
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            Log.d(TAG, "InteractClientSocketThread exit");
            handlerthread.quit();
//            if (DEBUG) {
//                try {
//                    if (os_origin != null) {
//                        os_origin.close();
//                        os_origin = null;
//                    }
//
//                    if (os_mic != null) {
//                        os_mic.close();
//                        os_mic = null;
//                    }
//
//                    if (os_ref != null) {
//                        os_ref.close();
//                        os_ref = null;
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
        }
    }

    public LocalTransfer setOnOnConnectedListener(OnConnectedListener lsn) {
        listener = lsn;
        return this;
    }

    public interface OnConnectedListener {
        void onConnected();
        void onAudio(byte[] data, int len);
    }
}

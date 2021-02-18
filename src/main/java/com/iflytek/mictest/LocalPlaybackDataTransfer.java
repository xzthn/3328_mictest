package com.iflytek.mictest;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class LocalPlaybackDataTransfer {
    private static final String SCOKETNAME = "PLAYBACKTRANSFER";
    private static final String TAG = "LocalPlaybackDataTransfer";
    private ClientSocketThread mClientSocketThread;
    // private LocalSocket currentClientSocket;
    private OnDataReadListener listener;
    private static final boolean DEBUG = false;
    private long start_time, cur_time;
    private static final String debugdatapth = "/mnt/usb/9C44-66FE/cae_record/";
    private static final String debugdatapth2 = "/mnt/usb/9C44-66FE/";
    private OutputStream os = null;
    private boolean running = false;
    private boolean connected = false;
    private static final int PACKETSIZE = 4800;
    private static LocalPlaybackDataTransfer instance = null;

    public static LocalPlaybackDataTransfer getInstance() {
        if (instance == null)
            instance = new LocalPlaybackDataTransfer();
        return instance;
    }

    private LocalPlaybackDataTransfer() {
        mClientSocketThread = new ClientSocketThread();
        start();
    }

    public boolean isConnected() {
        return connected;
    }

    synchronized public void start() {
        if (!running) {
            running = true;
            mClientSocketThread.start();
        }
    }

    // private boolean interactThreadRunning = true;

    /* 内部类begin */
    private class ClientSocketThread extends Thread {
        private LocalSocket clientSocket = null;

        // private InteractThread interactThread = null;

        @Override
        public void run() {
            Log.d(TAG, "ClientSocketThread start");
            if (DEBUG) {
                File pcmfile2 = new File(debugdatapth2);
                File pcmfile3 = new File(debugdatapth);
                File pcmfile = null;
                if (pcmfile2.exists()) {
                    pcmfile = new File(debugdatapth + "socketback.pcm");
                    try {
                        if (pcmfile.exists()) {
                            pcmfile.delete();
                        }
                        pcmfile.createNewFile();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    try {
                        os = new FileOutputStream(pcmfile);
                    } catch (FileNotFoundException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }

            }
            while (true) {
                try {
                    clientSocket = new LocalSocket();
                    clientSocket.connect(new LocalSocketAddress(SCOKETNAME));
                } catch (IOException e) {
                    Log.d(TAG, "connect to server failed");
                    try {
                        sleep(2000);
                    } catch (InterruptedException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    continue;
                }
                connected = true;
                Log.d(TAG, "connected to mediaserver");
                InputStream inputStream = null;
                // InputStreamReader inputStreamReader = null;
                try {
                    inputStream = clientSocket.getInputStream();
                    // inputStreamReader = new InputStreamReader(
                    // inputStream);
                    byte[] buf = new byte[PACKETSIZE];
                    int readBytes = -1;
                    if (DEBUG) {
                        start_time = System.currentTimeMillis();
                    }
                    while ((readBytes = inputStream.read(buf)) != -1) {
                        if (readBytes > 0) {
                            // Log.d(TAG, "readed " + readBytes
                            // + " bytes data");
                            if (DEBUG) {
                                cur_time = System.currentTimeMillis();
                                if (cur_time - start_time < 1000 * 60 * 5) {
                                    try {
                                        if (os != null)
                                            os.write(buf, 0, readBytes);
                                    } catch (IOException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }
                            }
                            if (listener != null)
                                listener.OnDataRead(buf, readBytes);
                        }
                        sleep(1);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "resolve data error !");
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (DEBUG) {
                    try {
                        if (os != null) {
                            os.close();
                            os = null;
                        }
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                connected = false;
                if (clientSocket != null) {
                    try {
                        clientSocket.close();
                        clientSocket = null;
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public void setOnDataReadListener(OnDataReadListener lsn) {
        listener = lsn;
    }

    public interface OnDataReadListener {
        public void OnDataRead(byte[] data, int len);
    }
}

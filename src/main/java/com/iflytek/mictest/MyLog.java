package com.iflytek.mictest;

import android.util.Log;

public class MyLog {
    private final static String HEX_NUMS = "0123456789ABCDEF";
    public static final String TAG = "ZCTEST";
    public static void hex_dump(String tag, byte[] bytes, int offset, int len) {
        if (bytes == null) {
            Log.e(TAG, "hex_dump null byte array!");
            return;
        }

        StringBuilder tip = new StringBuilder();
        tip.append(String.format("[%d bytes]:", len));
        for (int i = 0; i < len; i++) {
            tip.append("0x");
            tip.append(HEX_NUMS.charAt((bytes[offset + i] >> 4) & 0xF));
            tip.append(HEX_NUMS.charAt(bytes[offset + i] & 0xF));
            tip.append(" ");
        }
        Log.d(tag, tip.toString());
    }
}

package tv.yuyin.utility;

public class Crc16 {
    static {
        System.loadLibrary("Crc16");
    }

    private native byte[] crc16(byte[] bytes, int position, int length);

    public byte[] calc(byte[] bytes, int position, int length) {
        return crc16(bytes, position, length);
    }
}

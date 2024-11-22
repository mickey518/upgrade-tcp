package lab.dragon.util;

public class ByteUtils {

    public static int bytes2IntBigEndian(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < bytes.length; i++) {
            result |= (bytes[i] & 0xFF) << (8 * (bytes.length - 1 - i));
        }
        return result;
    }

    public static int bytes2IntLittleEndian(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < bytes.length; i++) {
            result |= (bytes[i] & 0xFF) << (8 * i);
        }
        return result;
    }

    public static float bytes2Float(byte[] bytes) {
        int accum = 0;
        accum = accum | (bytes[0] & 0xff) << 0;
        accum = accum | (bytes[1] & 0xff) << 8;
        accum = accum | (bytes[2] & 0xff) << 16;
        accum = accum | (bytes[3] & 0xff) << 24;
        return Float.intBitsToFloat(accum);
    }

    public static boolean isSixthBitOne(int number) {
        int mask = 1 << 5; // 第6位的掩码（00100000 或者十进制32）
        return (number & mask) != 0;
    }
}

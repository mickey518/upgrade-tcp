package lab.dragon.util;

public class ByteUtils {

    public static float bytes2Float(byte[] bytes) {
        int accum = 0;
        accum = accum | (bytes[0] & 0xff) << 0;
        accum = accum | (bytes[1] & 0xff) << 8;
        accum = accum | (bytes[2] & 0xff) << 16;
        accum = accum | (bytes[3] & 0xff) << 24;
        return Float.intBitsToFloat(accum);
    }
}

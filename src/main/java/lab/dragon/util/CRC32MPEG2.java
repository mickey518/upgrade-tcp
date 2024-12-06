package lab.dragon.util;

import io.netty.buffer.ByteBufUtil;

import javax.xml.bind.DatatypeConverter;

public class CRC32MPEG2 {
    private static final int POLYNOMIAL = 0x04C11DB7;
    private static final int INITIAL_CRC = 0xFFFFFFFF;

    private static final int[] CRC_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 24;
            for (int j = 8; j > 0; j--) {
                if ((crc & 0x80000000) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc <<= 1;
                }
            }
            CRC_TABLE[i] = crc & 0xFFFFFFFF;
        }
    }

    private static int reverseByte(int b) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 1) | (b & 1);
            b >>= 1;
        }
        return result;
    }

    public static int computeCRC32MPEG2(byte[] data) {
        int crc = INITIAL_CRC;
        for (byte b : data) {
            crc = (crc << 8) ^ CRC_TABLE[((crc >> 24) ^ b) & 0xFF];
        }
        return crc & 0xFFFFFFFF;
    }

    public static void main(String[] args) {
        String hexInput = "FFF364C40000000348000000004C414D455555554C414D45332E313030555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555";
        byte[] inputBytes = javax.xml.bind.DatatypeConverter.parseHexBinary(hexInput);

        int crc32Value = computeCRC32MPEG2(inputBytes);
        System.out.println("Final CRC32 Checksum: " + crc32Value);
        System.out.printf("Manual CRC32: 0x%08X\n", crc32Value); // 结果应该为 0x4803A057
    }
}
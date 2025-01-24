package lab.dragon.power.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class CRC32MPEG2Old {
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

    public static int computeCRC32MPEG2LittleEndian(byte[] data) {
        // 将待计算的数据流长度规整到4的整数倍长度
        byte[] buffer = new byte[data.length % 4 == 0 ? data.length : (((short) data.length / 4) + 1) * 4];

        // 数据内容补 0
        Arrays.fill(buffer, data.length, buffer.length, (byte) 0x00);
        System.arraycopy(data, 0, buffer, 0, data.length);

        int crc = INITIAL_CRC;
        for (int i = 0; i < buffer.length; i += 4) {
            for (int j = Math.min(i + 3, buffer.length - 1); j >= i; j--) {
                crc = (crc << 8) ^ CRC_TABLE[((crc >> 24) ^ buffer[j]) & 0xFF];
            }
        }
        return crc & 0xFFFFFFFF;
    }

    public static void main(String[] args) throws IOException {
        byte[] testData = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};

        Files.write(Paths.get("demo.bin"), testData, StandardOpenOption.CREATE);

        int bigEndianCRC = computeCRC32MPEG2(testData);
        System.out.printf("CRC32 MPEG-2 (Big-Endian): 0x%08X\n", bigEndianCRC);

        int littleEndianCRC = computeCRC32MPEG2LittleEndian(testData);
        System.out.printf("CRC32 MPEG-2 (Little-Endian): 0x%08X\n", littleEndianCRC);
    }
}
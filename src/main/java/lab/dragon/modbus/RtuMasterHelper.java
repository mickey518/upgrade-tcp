package lab.dragon.modbus;

import com.fazecast.jSerialComm.SerialPort;
import io.netty.buffer.ByteBufUtil;
import lab.dragon.api.ConnectionWebSocket;
import lab.dragon.common.gson.GsonUtils;
import lab.dragon.common.util.ByteUtils;
import lab.dragon.config.SerialPortConfig;
import lab.dragon.entity.WsConnectMessage;
import lab.dragon.entity.WsConnectMessageEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;


/**
 * 对接主控板
 *
 * @author mickey.wang
 */
public class RtuMasterHelper {
    private static final Logger log = LoggerFactory.getLogger(RtuMasterHelper.class);
    private final SerialPort serialPort;
    private final byte[] idTemp = new byte[2];

    private WriteSpdThread writeSpdThread = null;

    private RtuMasterHelper(String port) {
        SerialPortConfig serialPortConfig = new SerialPortConfig(port);

        this.serialPort = SerialPort.getCommPort(port);
        this.serialPort.setBaudRate(serialPortConfig.getBaudRate());
        this.serialPort.setNumDataBits(serialPortConfig.getDataBits());
        this.serialPort.setNumStopBits(serialPortConfig.getStopBits());
        this.serialPort.setParity(serialPortConfig.getParity());

        if (!this.serialPort.openPort()) {
            log.error("[主控板]{}打开端口失败", port);
        } else {
            log.info("[主控板]{}打开端口成功", port);
        }
    }

    public static RtuMasterHelper createMaster(String port) {
        return new RtuMasterHelper(port);
    }

    public void listen() {
        if (!this.serialPort.isOpen()) {
            throw new RuntimeException("serial port is not open.");
        }

        /*
         0  0xAA    帧头              AA
         1  0x13    长度              13
         2  0x     校验和              00
         3  0x00    error             00
         4  0x00    id-1              00
         5  0x00    id-2              00
         6  0x00    bms报错1           00
         7  0x00    bms报错2           00
         8  0xE8    电流低位       E8
         9  0x03    电流高位       03
         10 0xE8    霍尔速度低位        e8
         11 0x03    霍尔速度高位        03
         12 0x16    驱动器温度          16
         13 0x16    电机温度           16
         14 0xDC    电池电压低位        DC
         15 0x00    电池电压高位        00
         16 0x16    电池容量           50
         17 0x16    --无              08
         18 0x16    电池温度1          16
         19 0x16    电池温度2          22
         20 0x55    帧尾              55
         */

        log.info("[主控板]开始接收数据...");
        // 缓冲区初始化
        ByteBuffer byteBuffer = ByteBuffer.allocate(4096);

        // 持续监听数据
        while (this.serialPort.isOpen()) {
            try {
                // 从串口输入流读取数据
                byte[] tmpBytes = new byte[1024];
                int bytesRead = serialPort.readBytes(tmpBytes, tmpBytes.length);
                if (bytesRead < 0) {
                    continue;
                }

                // 截取有效数据
                byte[] read = new byte[bytesRead];
                System.arraycopy(tmpBytes, 0, read, 0, bytesRead);
//                log.info("串口缓冲数据：{}", ByteUtils.toHexPrettyString(tmpBytes));

                // 写入缓冲区
                if (byteBuffer.remaining() < bytesRead) {
                    log.warn("Buffer overflow risk. Compacting buffer.");
                    // 压缩缓冲区
                    byteBuffer.compact();
                    if (byteBuffer.remaining() < bytesRead) {
                        throw new IllegalStateException("Insufficient buffer capacity even after compacting.");
                    }
                }
                byteBuffer.put(read);

                // 检查缓冲区数据是否足够长
                if (byteBuffer.position() <= 5) continue;

                // 读取数据帧
                byteBuffer.flip(); // 切换到读取模式

                // 从AA 或 A5帧头开始读取
                while (byteBuffer.hasRemaining()) {
                    byte header = byteBuffer.get();
                    if (header == (byte) 0xAA || header == (byte) 0xA5) {
                        byteBuffer.position(byteBuffer.position() - 1);
                        break;
                    }
                }

                int len = byteBuffer.get(1) & 0xFF;

                // 检查是否存在完整帧
                if (byteBuffer.limit() < len) {
                    // 返回写模式，等待更多数据
                    byteBuffer.compact();
                    continue;
                }

                // 提取完整帧
                byte[] buf2 = new byte[len];
                byteBuffer.get(buf2, 0, len);

                // 处理完帧后，调整缓冲区状态，清理已读取数据，准备接收新数据
                byteBuffer.compact();

                // 处理接收到的数据
                String tmp = String.format("[主控板]接收到数据[%s]: %s", len, ByteUtils.toHexPrettyString(buf2));
                log.info(tmp);
                switch (buf2[0]) {
                    case (byte) 0xAA:
                        decodeMsgAA(buf2);
                        break;
                    case (byte) 0xA5:
                        decodeMsgA5(buf2);
                        break;
                }

                Thread.sleep(100);
            } catch (Exception e) {
                log.error("[主控板] 读取数据错误，错误消息： {}", e.getMessage(), e);
                break;
            }
        }
    }

    private void decodeMsgA5(byte[] buffer) {
        if (!checkSum(buffer)) {
            log.error("校验和错误，跳过");
            return;
        }
    }

    private void decodeMsgAA(byte[] buffer) {
        if (!checkSum(buffer)) {
            log.error("校验和错误，跳过");
            return;
        }

        if (buffer[1] < 0x15) {
            log.error("AA数据帧长度错误，跳过 【{}】", ByteUtils.toHexPrettyString(buffer));
            return;
        }

        int index = 0;
        // 帧头 0 0xAA
        byte head = buffer[index];
        index++;
        // 长度 1
        byte length = buffer[index];
        index++;
        // 校验和 2
        byte sum = buffer[index];
        index++;
        // 错误 3
        byte error = buffer[index];
        index++;
        // 4
        idTemp[0] = buffer[index];
        index++;
        // 5
        idTemp[1] = buffer[index];
        index++;
        // 6
        byte bmsError1 = buffer[index];
        index++;
        // 7
        byte bmsError2 = buffer[index];
        index++;
        // 8 bms电流低位
        byte currentIOLow = buffer[index];
        index++;
        // 9 bms电流高位
        byte currentIOHigh = buffer[index];
        index++;
        // 10 霍尔速度低位
        byte hallSpdLow = buffer[index];
        index++;
        // 11 霍尔速度高位
        byte hallSpdHigh = buffer[index];
        index++;
        // 12 驱动温度
        byte drvTemp = buffer[index];
        index++;
        // 13 电机温度
        byte motTemp = buffer[index];
        index++;
        // 14 电池电压低位
        byte battVoltageLow = buffer[index];
        index++;
        // 15 电池电压高位
        byte battVoltageHigh = buffer[index];
        index++;
        // 16 电池容量
        byte battVolumn = buffer[index];
        index++;
        // 17
        index++;
        // 18 电池温度1
        byte battTemp1 = buffer[index];
        index++;
        // 19 电池温度2
        byte battTemp2 = buffer[index];
        index++;
        // 20 帧尾
        byte feater = buffer[index];
        index++;

        // 编码器速度
        int currentIO = ByteUtils.bytes2ShortBigEndian(new byte[]{currentIOHigh, currentIOLow});
        // 霍尔速度
        int hallSpd = ByteUtils.bytes2ShortBigEndian(new byte[]{hallSpdHigh, hallSpdLow});
        // 电池电压
        int battVoltage = ByteUtils.bytes2IntBigEndian(new byte[]{battVoltageHigh, battVoltageLow});
        Map<String, Object> result = new HashMap<>(2);
        result.put("error", error);
        result.put("bmsError1", bmsError1);
        result.put("bmsError2", bmsError2);
        // 霍尔速度
        result.put("hallSpd", hallSpd);
        // 驱动温度
        result.put("drvTemp", drvTemp);
        // 电机温度
        result.put("motTemp", motTemp);
        // 电池电压
        result.put("battVoltage", battVoltage / 100.00f);
        // 电池容量
        result.put("battVolumn", battVolumn);
        // 电流
        result.put("currentIO", currentIO / 100.00f);
        // 电池温度1
        result.put("battTemp1", battTemp1);
        // 电池温度2
        result.put("battTemp2", battTemp2);

        ConnectionWebSocket.SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.resultBatt).json(GsonUtils.toJson(result)).build()));

        if (writeSpdThread == null) {
            writeSpdThread = new WriteSpdThread(this, idTemp);
            writeSpdThread.start();
        }
    }

    public void writeSpd(Integer spd) throws IOException {
        writeSpdThread.spd.set(spd);
    }

    public void writeZeroSpd() throws IOException {
        byte[] bytes = DatatypeConverter.parseHexBinary("AA0B000071c00000000055");
        bytes[4] = idTemp[0];
        bytes[5] = idTemp[1];
        writeCommand(bytes);
    }

    /**
     * 下发指令，切换至调试模式
     *
     * @throws IOException
     */
    public void writeMode(int mode) throws IOException {
        /*
        帧头  0  ｜长度 1  ｜校验和 2｜模式3 |id 低 4 | id 高 5 |帧尾6
        --------------------------------------------------------------
        0xA5    ｜0x05   ｜0x00   ｜0x00   ｜0x71   ｜0xC0   | 0x55
         */
        byte[] bytes = new byte[7];

        bytes[0] = (byte) 0xAA;
        bytes[bytes.length - 1] = (byte) 0x55;
        bytes[1] = (byte) bytes.length;
        bytes[2] = 0;
        bytes[3] = (byte) mode;
        bytes[4] = idTemp[0];   //
        bytes[5] = idTemp[1];   //

        writeCommand(bytes);
    }

    public void writeCommand(byte[] bytes) throws IOException {
        bytes[2] = (byte) ((byte) 0xFF & ByteUtils.sum(bytes));

        String format = String.format("[主控板] 下发命令 [%s]", ByteUtils.toHexPrettyString(bytes));
        log.info(format);
        OutputStream outputStream = this.serialPort.getOutputStream();
        outputStream.write(bytes);
        outputStream.flush();
    }

    private boolean checkSum(byte[] buffer) {
        byte[] bytes = new byte[buffer.length];
        System.arraycopy(buffer, 0, bytes, 0, bytes.length);
        bytes[2] = 0;
        long summed = ByteUtils.sum(bytes);
        return buffer[2] == (byte) ((byte) 0xFF & summed);
    }

    /**
     * 线程关闭释放流程
     */
    public void close() {
        this.writeSpdThread = null;
    }
}

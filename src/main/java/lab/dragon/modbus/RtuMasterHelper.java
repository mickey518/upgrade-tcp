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

import java.io.IOException;
import java.io.OutputStream;
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
        }
        log.info("[主控板]{}打开端口", port);
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

        // 设置一个缓冲区来接收数据
        byte[] bytes = new byte[128];
        byte[] buffer;
        int bytesRead;

        log.info("[主控板]开始接收数据...");

        // 持续监听数据
        while (true) {
            try {
                // 从串口输入流读取数据
                bytesRead = serialPort.readBytes(bytes, 21);
                if (bytesRead < 5) {
                    Thread.sleep(50);
                    continue;
                }
                // 串口有接收到数据
                buffer = new byte[bytesRead];
                System.arraycopy(bytes, 0, buffer, 0, bytesRead);
                if (buffer[1] != bytesRead) {
                    continue;
                }
                // 处理接收到的数据
                log.info("[主控板]接收到数据: {}", ByteBufUtil.hexDump(buffer));
                switch (buffer[0]) {
                    case (byte) 0xAA:
                        decodeMsgAA(buffer);
                        break;
                    case (byte) 0xA5:
                        decodeMsgA5(buffer);
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

    /**
     * 下发指令，切换至调试模式
     *
     * @throws IOException
     */
    public void writeMode(int mode) throws IOException {
        /*
        0     1     2     3    4
        帧头  长度  检验和 模式  帧尾
         */
        byte[] bytes = new byte[5];

        bytes[0] = (byte) 0xAA;
        bytes[bytes.length - 1] = (byte) 0x55;
        bytes[1] = (byte) bytes.length;
        bytes[2] = 0;
        bytes[3] = (byte) mode;

        writeCommand(bytes);
    }

    public void writeCommand(byte[] bytes) throws IOException {
        bytes[2] = (byte) ((byte) 0xFF & ByteUtils.sum(bytes));

        log.info("[主控板][{}] 下发命令 [{}]", this.serialPort.getSystemPortName(), ByteUtils.toHexPrettyString(bytes));
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

package lab.dragon.modbus;

import com.fazecast.jSerialComm.SerialPort;
import io.netty.buffer.ByteBufUtil;
import lab.dragon.api.ConnectionWebSocket;
import lab.dragon.common.gson.GsonUtils;
import lab.dragon.config.SerialPortConfig;
import lab.dragon.entity.WsConnectMessage;
import lab.dragon.common.util.ByteUtils;
import lab.dragon.entity.WsConnectMessageEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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

    private RtuMasterHelper (String port) {
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
         0  0xAA    帧头
         1  0x13    长度
         2  0x     校验和
         3  0x00    error
         4  0x00    bms报错1
         5  0x00    bms报错2
         6  0xE8    编码器速度低位
         7  0x03    编码器速度高位
         8  0xE8    霍尔速度低位
         9  0x03    霍尔速度高位
         10 0x16    驱动器温度
         11 0x16    电机温度
         12 0x16    电池电压低位
         13 0x16    电池电压高位
         14 0x16    电池容量
         15 0x16    电流
         16 0x16    电池温度1
         17 0x16    电池温度2
         18 0x55    帧尾
         */

        // 监听接收的数据 todo 这里是测试数据，是要删除掉的额
        byte[] buffer = new byte[] {(byte) 0xAA, (byte) 0x13, (byte) 0x9D, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0xE8, (byte) 0x03,
                (byte) 0xE8, (byte) 0x08, (byte) 0x16, (byte) 0x16,
                (byte) 0x16, (byte) 0x16, (byte) 0x16, (byte) 0x16,
                (byte) 0x16, (byte) 0x16, (byte) 0x55
        };
        //  todo 这里是测试数据，是要删除掉的额
        int bytesRead = 19;

        log.info("[主控板]开始接收数据...");

        // 持续监听数据
        while (true) {
            try {
                // 从串口输入流读取数据
                // 获取输入流  todo 这里是测试数据，是要放开下面两行的
//                InputStream inputStream = serialPort.getInputStream();
//                bytesRead = inputStream.read(buffer);

                if (bytesRead > 0) {
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

                }
                //  todo 这里是测试数据，是要删除掉的额
                Thread.sleep(500);
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

        // 帧头 0xAA
        byte head = buffer[0];
        // 长度
        byte length = buffer[1];
        // 校验和
        byte sum = buffer[2];
        // 错误
        byte error = buffer[3];
        byte bmsError1 = buffer[4];
        byte bmsError2 = buffer[5];
        // 编码器速度低位
        byte spdLow = buffer[6];
        // 编码器速度高位
        byte spdHigh = buffer[7];
        // 霍尔速度低位
        byte hallSpdLow = buffer[8];
        // 霍尔速度高位
        byte hallSpdHigh = buffer[9];
        // 驱动温度
        byte drvTemp = buffer[10];
        // 电机温度
        byte motTemp = buffer[11];
        // 电池电压低位
        byte battVoltageLow = buffer[12];
        // 电池电压高位
        byte battVoltageHigh = buffer[13];
        // 电池容量
        byte battVolumn = buffer[14];
        // 电流
        byte currentIO = buffer[15];
        // 电池温度1
        byte battTemp1 = buffer[16];
        // 电池温度2
        byte battTemp2 = buffer[17];
        // 帧尾
        byte feater = buffer[18];

        // 编码器速度
        int spd = ByteUtils.bytes2IntBigEndian(new byte[]{spdHigh, spdLow});
        // 霍尔速度
        int hallSpd = ByteUtils.bytes2IntBigEndian(new byte[]{hallSpdHigh, hallSpdLow});
        // 电池电压
        int battVoltage = ByteUtils.bytes2IntBigEndian(new byte[] {battVoltageHigh, battVoltageLow});
        Map<String, Object> result = new HashMap<>(2);
        result.put("error", error);
        result.put("bmsError1", bmsError1);
        result.put("bmsError2", bmsError2);
        // 编码器速度
        result.put("spd", spd);
        // 霍尔速度
        result.put("hallSpd", hallSpd);
        // 驱动温度
        result.put("drvTemp", drvTemp);
        // 电机温度
        result.put("motTemp", motTemp);
        // 电池电压
        result.put("battVoltage", battVoltage);
        // 电池容量
        result.put("battVolumn", battVolumn);
        // 电流
        result.put("currentIO", currentIO);
        // 电池温度1
        result.put("battTemp1", battTemp1);
        // 电池温度2
        result.put("battTemp2", battTemp2);

        ConnectionWebSocket.SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.resultBatt).json(GsonUtils.toJson(result)).build()));
    }

    public void writeSpd(Integer spd) throws IOException {
        /*
        0     1     2     3     4           5           6           7       8
        帧头  长度  检验和 模式  速度低位    速度高位    母线电压低位  母线电压高位  帧尾
         */
        byte[] bytes = new byte[9];

        bytes[0] = (byte) 0xAA;
        bytes[bytes.length - 1] = (byte) 0x55;
        bytes[1] = (byte) bytes.length;
        bytes[2] = 0;
        bytes[3] = 0;
        byte[] bytesLittleEndian = ByteUtils.short2BytesLittleEndian(spd.shortValue());
        System.arraycopy(bytesLittleEndian, 0, bytes, 4,2);
        bytes[6] = 0;
        bytes[7] = 0;

        writeCommand(bytes);
    }

    /**
     * 下发指令，切换至调试模式
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

    private void writeCommand(byte[] bytes) throws IOException {
        bytes[2] = (byte) ((byte)0xFF & ByteUtils.sum(bytes));

        log.info("[主控板][{}] 下发命令 [{}]", this.serialPort.getSystemPortName(), ByteUtils.hexString(bytes));
        OutputStream outputStream = this.serialPort.getOutputStream();
        outputStream.write(bytes);
        outputStream.flush();
    }

    private boolean checkSum(byte[] buffer) {
        byte[] bytes = new byte[buffer.length];
        System.arraycopy(buffer, 0, bytes, 0, bytes.length);
        bytes[2] = 0;
        long summed = ByteUtils.sum(bytes);
        return buffer[2] == (byte) ((byte)0xFF & summed);
    }
}

package lab.dragon.modbus;

import com.fazecast.jSerialComm.SerialPort;
import lab.dragon.api.AdvancedWebSocket;
import lab.dragon.api.ConnectionWebSocket;
import lab.dragon.common.gson.GsonUtils;
import lab.dragon.common.util.ByteUtils;
import lab.dragon.common.util.DateTimeUtils;
import lab.dragon.config.SerialPortConfig;
import lab.dragon.entity.WsConnectMessage;
import lab.dragon.entity.WsConnectMessageEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * 对接主控板
 *
 * @author mickey.wang
 */
public class RtuMasterHelper {
    private static final Logger log = LoggerFactory.getLogger(RtuMasterHelper.class);
    public final AtomicBoolean isContinued = new AtomicBoolean(false);
    private final SerialPort serialPort;
    private final byte[] idTemp = new byte[]{0, 0};
    int chunkSize = 128; // 128
    long currented = System.currentTimeMillis();
    private WriteSpdThread writeSpdThread = null;

    private RtuMasterHelper(String port) {
        SerialPortConfig serialPortConfig = new SerialPortConfig(port);

        this.serialPort = SerialPort.getCommPort(port);
        this.serialPort.setBaudRate(serialPortConfig.getBaudRate());
        this.serialPort.setNumDataBits(serialPortConfig.getDataBits());
        this.serialPort.setNumStopBits(serialPortConfig.getStopBits());
        this.serialPort.setParity(serialPortConfig.getParity());

        String tmp;
        if (!this.serialPort.openPort()) {
            tmp = String.format("[主控板]%s打开端口失败", port);
            log.error(tmp);
        } else {
            tmp = String.format("[主控板]%s打开端口成功", port);
            log.info("[主控板]{}打开端口成功", port);
        }
        AdvancedWebSocket.SEND_MESSAGE_QUEUE.add(tmp);

        byte[] bytes = new byte[0];
        try {
            bytes = Files.readAllBytes(Paths.get("driver-ids"));
            if (bytes.length >= 2) {
                idTemp[0] = bytes[0];
                idTemp[1] = bytes[1];
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static RtuMasterHelper createMaster(String port) {
        return new RtuMasterHelper(port);
    }

    public SerialPort getSerialPort() {
        return this.serialPort;
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
        // 缓冲区初始化（根据最大数据帧长度分配）
        ByteBuffer byteBuffer = ByteBuffer.allocate(4096);

        // 持续监听数据
        while (this.serialPort.isOpen()) {
            try {
                // 从串口读取数据
                byte[] tmpBytes = new byte[1024];
                int bytesRead = serialPort.readBytes(tmpBytes, tmpBytes.length);
                if (bytesRead <= 0) continue;

                // 截取有效数据
                byte[] read = new byte[bytesRead];
                System.arraycopy(tmpBytes, 0, read, 0, bytesRead);
                log.info("串口缓冲数据: {}", ByteUtils.toHexPrettyString(read));

                // 写入缓冲区
                if (byteBuffer.remaining() < bytesRead) {
                    log.warn("Buffer overflow risk. Compacting buffer.");
                    byteBuffer.compact(); // 压缩缓冲区
                    if (byteBuffer.remaining() < bytesRead) {
                        throw new IllegalStateException("Insufficient buffer capacity even after compacting.");
                    }
                }
                byteBuffer.put(read);

                // 检查缓冲区数据是否足够长
                if (byteBuffer.position() <= 5) continue; // 数据帧最小长度不足

                // 读取数据帧长度字段
                byteBuffer.flip(); // 切换到读取模式

                while (byteBuffer.hasRemaining()) {
                    byte header = byteBuffer.get();
                    if (header == (byte) 0xAA || header == (byte) 0xA5) {
                        byteBuffer.position(byteBuffer.position() - 1);
                        break;
                    } else {
                        System.out.println("无效字节：" + String.format("0x%02X", header));
                    }
                }

                int len = byteBuffer.get(1) & 0xFF; // 确保 len 是无符号值

                // 检查是否存在完整帧
                if (byteBuffer.limit() < len) {
                    byteBuffer.compact(); // 返回写模式，等待更多数据
                    continue;
                }
                // 提取完整帧数据
                byte[] buf2 = new byte[len];
                byteBuffer.get(buf2, 0, len);

                // 处理完帧后，调整缓冲区状态
                byteBuffer.compact(); // 清理已读取数据，准备接收新数据

                // 处理接收到的数据
                String tmp = String.format("[主控板]接收到数据[%s]: %s", len, ByteUtils.toHexPrettyString(buf2));
                log.info(tmp);
                AdvancedWebSocket.SEND_MESSAGE_QUEUE.add(tmp);
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
            }
        }
    }

    private void decodeMsgA5(byte[] buffer) {
        if (!checkSum(buffer)) {
            log.error("校验和错误，跳过");
            isContinued.set(false);
            return;
        }
        /*
        帧头  0  ｜长度 1  ｜校验和 2｜返回码3 |id 低 4 | id 高 5| 数据 6|帧尾7
        --------------------------------------------------------------
        0xA5    ｜0x05   ｜0x00   ｜0x00   ｜0x71   ｜0xC0   |       | 0x55
         */
        byte code = buffer[3];
        if (code == 0) {
            // 接收到A5回复，可以继续发包
            isContinued.set(true);
            currented = System.currentTimeMillis();
        } else if (code == 1) {
            isContinued.set(false);
            log.error("CRC错误");
        } else if (code == 2) {
            isContinued.set(false);
            log.error("校验错误");
        }
        if (buffer[1] != (byte) 0x09 && buffer[1] != (byte) 0x29) {
            return;
        }
        byte[] tmpBuffer;
        String folder = "parameters";
        String fileNamePrefix;
        switch (buffer[1]) {
            case (byte) 0x29: {
                // 返回的数据是驱动板参数,驱动板参数这里本来是32个字节，现在要增加2个字节的 FFFF
                tmpBuffer = new byte[buffer.length - 7]; //  + 2
                System.arraycopy(buffer, 6, tmpBuffer, 0, tmpBuffer.length);
                tmpBuffer[tmpBuffer.length - 2] = (byte) 0xFF;
                tmpBuffer[tmpBuffer.length - 1] = (byte) 0xFF;
                fileNamePrefix = "驱动板参数-";
                break;
            } case (byte) 0x09: {
                tmpBuffer = new byte[buffer.length - 7];
                System.arraycopy(buffer, 6, tmpBuffer, 0, tmpBuffer.length);
                fileNamePrefix = "主控板参数-";
                break;
            } default:
                tmpBuffer = new byte[0];
                fileNamePrefix = "参数-";
                break;
        }
        try {
            Path fold = Paths.get(folder);
            if (Files.notExists(fold)) {
                Files.createDirectory(fold);
            }
            Path paramPath = Paths.get(folder, DateTimeUtils.generateFileName(fileNamePrefix, ".bin"));
            Files.write(paramPath, tmpBuffer);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 开始升级驱动板
     *
     * @param fileSize
     * @throws IOException
     */
    public void startUpgradeQuDongBan(byte mode, int fileSize) throws IOException {
        // 下发开始升级指令 AA  长度  校验和 模式  程序长度低字节    程序长度高字节 帧尾
        byte[] bytes = ByteUtils.short2BytesLittleEndian(fileSize);
        byte[] command = new byte[9];
        command[0] = (byte) 0xAA;   // 帧头
        command[1] = (byte) command.length;   // 长度
        command[2] = (byte) 0x00;   // 校验和
        command[3] = mode;   // 模式
        command[4] = idTemp[0];   //
        command[5] = idTemp[1];   //
        command[6] = bytes[0];   //
        command[7] = bytes[1];
        command[8] = (byte) 0x55;

        currented = System.currentTimeMillis();
        writeCommand(command);
        isContinued.set(false);

        while (true) {
            if (!isContinued.get()) {
                if (System.currentTimeMillis() - currented > 3000) {
                    log.error("3秒未收到A5回复，重发数据包");
                    currented = System.currentTimeMillis();
                    command[2] = 0;
                    writeCommand(command);
                    isContinued.set(false);
                }
            } else {
                log.warn("收到回复，退出");
                break;
            }
        }
    }

    public void sendUpgradeFile(byte[] buffer, byte mode) throws IOException {
        // 下发升级包
        int readCount = 0;
        int readSize = 0;


        for (int i = 0; i < calculateChunksLength(buffer); i++) {
            int address = i * chunkSize;
            byte[] bytes1 = ByteUtils.short2BytesLittleEndian(address);
            while (true) {
                if (isContinued.get()) {
                    byte[] filePart = new byte[(buffer.length < chunkSize ? buffer.length : chunkSize) + 9];
                    filePart[0] = (byte) 0xAA;   // 帧头
                    filePart[1] = (byte) filePart.length;   // 长度
                    filePart[2] = (byte) 0x00;   // 校验和
                    filePart[3] = mode;   // 模式
                    filePart[4] = idTemp[0];   //
                    filePart[5] = idTemp[1];   //
                    filePart[6] = bytes1[0];   //
                    filePart[7] = bytes1[1];
                    filePart[filePart.length - 1] = (byte) 0x55;

                    readSize = buffer.length - readCount < chunkSize ? buffer.length - readCount : chunkSize;
                    System.arraycopy(buffer, address, filePart, 8, readSize);
                    readCount += readSize;

                    currented = System.currentTimeMillis();
                    writeCommand(filePart);
                    isContinued.set(false);

                    while (true) {
                        if (!isContinued.get()) {
                            if (System.currentTimeMillis() - currented > 3000) {
                                log.error("3秒未收到A5回复，重发数据包");
                                currented = System.currentTimeMillis();
                                filePart[2] = 0;
                                writeCommand(filePart);
                                isContinued.set(false);
                            }
                        } else {
                            log.warn("收到回复，退出");
                            break;
                        }
                    }

                    break;
                }
            }
        }
    }

    public void finishedUpgradeQuDongBan(byte mode, int crc32) throws IOException {
        // 计算文件CRC32
        byte[] bytes1 = ByteUtils.int2BytesLittleEndian((int) crc32);
        // 完成升级包下发
        while (true) {
            if (isContinued.get()) {
                byte[] finBytes = new byte[11];
                finBytes[0] = (byte) 0xAA;   // 帧头
                finBytes[1] = (byte) finBytes.length;   // 长度
                finBytes[2] = (byte) 0x00;   // 校验和
                finBytes[3] = mode;   // 模式
                finBytes[4] = idTemp[0];   //
                finBytes[5] = idTemp[1];   //
                finBytes[6] = bytes1[0];   //
                finBytes[7] = bytes1[1];
                finBytes[8] = bytes1[2];
                finBytes[9] = bytes1[3];
                finBytes[10] = (byte) 0x55;

                writeCommand(finBytes);
                isContinued.set(false);
                break;
            }
        }
    }


    private void decodeMsgAA(byte[] buffer) {
        if (!checkSum(buffer)) {
            log.error("校验和错误，跳过");
            return;
        }

        if (buffer[1] < 0x15) {
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

        // id写入到文件
        try {
            Files.write(Paths.get("driver-ids"), new byte[]{idTemp[0], idTemp[1]});
        } catch (IOException e) {
            log.error(e.getMessage(), e);
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
        AdvancedWebSocket.SEND_MESSAGE_QUEUE.add(format);
        OutputStream outputStream = this.serialPort.getOutputStream();
        outputStream.write(bytes);
        outputStream.flush();
    }

    private boolean checkSum(byte[] buffer) {
        if (buffer.length < 2) {
            return false;
        }
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

    private int calculateChunksLength(byte[] buffer) {
        // 计算商，如果不能整除，则加1
        return (buffer.length % chunkSize == 0) ? buffer.length / chunkSize : (buffer.length / chunkSize) + 1;
    }
}

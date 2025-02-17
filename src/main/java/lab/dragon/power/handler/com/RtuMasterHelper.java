package lab.dragon.power.handler.com;

import com.fazecast.jSerialComm.SerialPort;
import lab.dragon.common.util.ByteUtils;
import lab.dragon.common.util.ThreadPoolUtil;
import lab.dragon.power.DataCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * 对接主控板
 *
 * @author mickey.wang
 */
public class RtuMasterHelper {
    private static final Logger log = LoggerFactory.getLogger(RtuMasterHelper.class);
    private boolean bootloader_pmc = false;
    private boolean bootloader_monitor = false;
    public final AtomicBoolean isContinued = new AtomicBoolean(false);
    int chunkSize = 128; // 128
    long current = System.currentTimeMillis();
    private SerialPort serialPort;

    private RtuMasterHelper() {
    }

    public static RtuMasterHelper createMaster() {
        return new RtuMasterHelper();
    }

    public void openPort(String port) throws IOException {
        this.serialPort = SerialPort.getCommPort(port);
        this.serialPort.setBaudRate(115200);
        this.serialPort.setNumDataBits(8);
        this.serialPort.setNumStopBits(0);
        this.serialPort.setParity(0);

        String tmp;
        if (!this.serialPort.openPort()) {
            tmp = String.format("[主控板]%s打开端口失败", port);
            log.error(tmp);
        } else {
            tmp = String.format("[主控板]%s打开端口成功", port);
            log.info("[主控板]{}打开端口成功", port);
        }
        DataCenter.SEND_MESSAGE_QUEUE.add(tmp);

        ThreadPoolUtil.execute(this::listen);
    }

    public void closePort() {
        log.info("关闭COMM端口");
        DataCenter.SEND_MESSAGE_QUEUE.add("关闭串口");
        isContinued.set(true);
        this.serialPort.closePort();
    }

    public SerialPort getSerialPort() {
        return this.serialPort;
    }

    public void listen() {
        if (!this.serialPort.isOpen()) {
            throw new RuntimeException("serial port is not open.");
        }

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
//                log.info("串口缓冲数据: {}", ByteUtils.toHexPrettyString(read));

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
                DataCenter.SEND_MESSAGE_QUEUE.add(tmp);
                if (buf2[0] == (byte) 0xAA) {
                    decodeMsgAA(buf2);
                }

            } catch (Exception e) {
                log.error("[主控板] 读取数据错误，错误消息： {}", e.getMessage(), e);
            }
        }
    }

    private void decodeMsgAA(byte[] buffer) {
        if (!checkSum(buffer)) {
            log.error("校验和错误，跳过");
            isContinued.set(false);
            return;
        }
        byte tag = buffer[3];
        byte result = buffer[4];
        switch (tag) {
            case (byte) 0x02:
                if (result == (byte) 0xff) {
                    DataCenter.SEND_MESSAGE_QUEUE.add("PMC 进入 Bootloader 模式成功！");
                    bootloader_pmc = true;
                    current = System.currentTimeMillis();
                } else if (result == (byte) 0xee) {
                    bootloader_pmc = false;
                    DataCenter.SEND_MESSAGE_QUEUE.add("PMC 进入 Bootloader 模式失败！");
                } else if (result == (byte) 0xdd) {
                    bootloader_monitor = true;
                    DataCenter.SEND_MESSAGE_QUEUE.add("Monitor 进入 Bootloader 模式成功！");
                    current = System.currentTimeMillis();
                } else if (result == (byte) 0xcc) {
                    bootloader_monitor = false;
                    DataCenter.SEND_MESSAGE_QUEUE.add("Monitor 进入 Bootloader 模式失败！");
                }
                isContinued.set(bootloader_pmc && bootloader_monitor);
                break;
            case (byte) 0x03:
            case (byte) 0x04:
                if (result == (byte) 0xff) {
                    isContinued.set(true);
                    current = System.currentTimeMillis();
                    DataCenter.SEND_MESSAGE_QUEUE.add("PMC 文件接收成功！");
                } else if (result == (byte) 0x00) {
                    isContinued.set(false);
                    DataCenter.SEND_MESSAGE_QUEUE.add("PMC 文件接收失败！");
                }
                break;
            case (byte) 0x05:
                if (result == (byte) 0xff) {
                    isContinued.set(true);
                    current = System.currentTimeMillis();
                    DataCenter.SEND_MESSAGE_QUEUE.add("PMC 升级成功！");
                } else if (result == (byte) 0x00) {
                    isContinued.set(false);
                    DataCenter.SEND_MESSAGE_QUEUE.add("PMC 升级失败！");
                } else if (result == (byte) 0xee) {
                    isContinued.set(false);
                    DataCenter.SEND_MESSAGE_QUEUE.add("PMC CRC错误！");
                }
                break;
            case (byte) 0x06:
            case (byte) 0x07:
                if (result == (byte) 0xff) {
                    isContinued.set(true);
                    current = System.currentTimeMillis();
                    DataCenter.SEND_MESSAGE_QUEUE.add("Monitor 文件接收成功！");
                } else if (result == (byte) 0x00) {
                    isContinued.set(false);
                    DataCenter.SEND_MESSAGE_QUEUE.add("Monitor 文件接收失败！");
                }
                break;
            case (byte) 0x08:
                if (result == (byte) 0xff) {
                    isContinued.set(true);
                    current = System.currentTimeMillis();
                    DataCenter.SEND_MESSAGE_QUEUE.add("Monitor 升级成功！");
                } else if (result == (byte) 0x00) {
                    isContinued.set(false);
                    DataCenter.SEND_MESSAGE_QUEUE.add("Monitor 升级失败！");
                } else if (result == (byte) 0xee) {
                    isContinued.set(false);
                    DataCenter.SEND_MESSAGE_QUEUE.add("Monitor CRC错误！");
                }
                break;
        }
    }

    /**
     * 进入 bootloader 模式
     *
     * @throws IOException
     */
    public void intoBootloader() throws IOException {
        // 下发开始升级指令 AA  长度  校验和 模式  程序长度低字节    程序长度高字节 帧尾
        byte[] command = new byte[6];
        int index = 0;
        command[index] = (byte) 0xAA;           // 0 帧头
        index++;
        command[index] = (byte) command.length; // 1 长度
        index++;
        command[index] = (byte) 0x00;           // 2 校验和
        index++;
        command[index] = (byte) 0x02;           // 3 模式
        index++;
        command[index] = (byte) 0x00;           // 4 data
        index++;
        command[index] = (byte) 0x55;           // 5 帧尾

        current = System.currentTimeMillis();
        writeCommand(command);
        isContinued.set(false);

        while (true) {
            if (!isContinued.get()) {
                if (System.currentTimeMillis() - current > 1000) {
                    log.error("1秒未收到回复，重发数据包");
                    current = System.currentTimeMillis();
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

    /**
     * 开始升级驱动板
     *
     * @param fileSize
     * @throws IOException
     */
    public void startUpgradeDrive(byte mode, int fileSize) throws IOException {
        // 下发开始升级指令 AA  长度  校验和 模式  程序长度低字节    程序长度高字节 帧尾
        byte[] bytes = ByteUtils.short2BytesLittleEndian(fileSize);
        byte[] command = new byte[7];
        int index = 0;
        command[index] = (byte) 0xAA;           // 0 帧头
        index++;
        command[index] = (byte) command.length; // 1 长度
        index++;
        command[index] = (byte) 0x00;           // 2 校验和
        index++;
        command[index] = mode;                  // 3 模式
        index++;
        command[index] = bytes[0];   //
        index++;
        command[index] = bytes[1];
        index++;
        command[index] = (byte) 0x55;           // 5 帧尾


        current = System.currentTimeMillis();
        writeCommand(command);
        isContinued.set(false);

        while (true) {
            if (!isContinued.get()) {
                if (System.currentTimeMillis() - current > 1000) {
                    log.error("1秒未收到回复，重发数据包");
                    current = System.currentTimeMillis();
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

        int chunks = calculateChunksLength(buffer);

        for (int i = 0; i < chunks; i++) {

            String msg = "";
            if (mode == (byte) 0x04) {
                msg = "UPGRADE_DRIVE_PERCENTAGE";
            } else if (mode == (byte) 0x07) {
                msg = "UPGRADE_MAIN_PERCENTAGE";
            }
            msg = msg + ";;" + (i * 100 / chunks) + "%";
            DataCenter.SEND_MESSAGE_QUEUE.add(msg);

            int address = i * chunkSize;
            byte[] bytes1 = ByteUtils.short2BytesLittleEndian(address);
            while (true) {
                if (isContinued.get()) {
                    byte[] filePart = new byte[(Math.min(buffer.length, chunkSize)) + 6];
                    int index = 0;
                    filePart[index] = (byte) 0xAA;              // 0 帧头
                    index++;
                    filePart[index] = (byte) filePart.length;   // 1 长度
                    index++;
                    filePart[index] = (byte) 0x00;              // 2 校验和
                    index++;
                    filePart[index] = mode;                     // 3 模式
                    index++;
                    filePart[index] = 0;                        // 4 data
                    index++;
//                    filePart[4] = bytes1[0];   //
//                    filePart[5] = bytes1[1];
                    filePart[filePart.length - 1] = (byte) 0x55;

                    readSize = Math.min(buffer.length - readCount, chunkSize);
                    System.arraycopy(buffer, address, filePart, index, readSize);
                    readCount += readSize;

                    current = System.currentTimeMillis();
                    writeCommand(filePart);
                    isContinued.set(false);

                    while (true) {
                        if (!isContinued.get()) {
                            if (System.currentTimeMillis() - current > 3000) {
                                log.error("1秒未收到回复，重发数据包");
                                current = System.currentTimeMillis();
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

    public void finishedUpgradeDrive(byte mode, int crc32) throws IOException {
        // 计算文件CRC32
        byte[] bytes1 = ByteUtils.int2BytesLittleEndian((int) crc32);
        // 完成升级包下发
        while (true) {
            if (isContinued.get()) {
                byte[] finBytes = new byte[10];
                int index = 0;
                finBytes[index] = (byte) 0xAA;              // 0 帧头
                index++;
                finBytes[index] = (byte) finBytes.length;   // 1 长度
                index++;
                finBytes[index] = (byte) 0x00;              // 2 校验和
                index++;
                finBytes[index] = mode;                     // 3 模式
                index++;
                index++;
                System.arraycopy(bytes1, 0, finBytes, index, bytes1.length);    // 文件CRC校验值
                finBytes[finBytes.length - 1] = (byte) 0x55;// 帧尾

                writeCommand(finBytes);
                isContinued.set(false);
                break;
            }
        }
    }

    public void writeCommand(byte[] bytes) throws IOException {
        bytes[2] = (byte) ((byte) 0xFF & ByteUtils.sum(bytes));

        String format = String.format("[主控板] 下发命令 [%s]", ByteUtils.toHexPrettyString(bytes));
        log.info(format);
        DataCenter.SEND_MESSAGE_QUEUE.add(format);
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
        boolean b = buffer[2] == (byte) ((byte) 0xFF & summed);
        if (!b)
            DataCenter.SEND_MESSAGE_QUEUE.add(String.format("校验和应为：%s，实际是：%s", ByteUtils.toHexPrettyString(new byte[]{(byte) ((byte) 0xFF & summed)}), ByteUtils.toHexPrettyString(new byte[]{buffer[2]})));
        return b;
    }

    private int calculateChunksLength(byte[] buffer) {
        // 计算商，如果不能整除，则加1
        return (buffer.length % chunkSize == 0) ? buffer.length / chunkSize : (buffer.length / chunkSize) + 1;
    }
}

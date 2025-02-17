package lab.dragon.power.handler.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lab.dragon.common.util.ByteUtils;
import lab.dragon.common.util.ThreadPoolUtil;
import lab.dragon.power.DataCenter;
import lab.dragon.power.api.FirmwareUpgradeWebSocket;
import lab.dragon.power.config.CollectAgreement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 * 对接主控板
 *
 * @author mickey.wang
 */
public class DeviceUpgradeHandler {
    // 单例实例
    private static volatile DeviceUpgradeHandler instance;

    public static final AtomicBoolean isContinued = new AtomicBoolean(false);
    public static final AtomicLong current = new AtomicLong(System.currentTimeMillis());
    private static final Logger log = LoggerFactory.getLogger(DeviceUpgradeHandler.class);
    public static Channel SERVER_CHANNEL;
    int chunkSize = 128;

    // 私有构造方法，防止外部直接创建实例
    private DeviceUpgradeHandler(int port) {
        ThreadPoolUtil.execute(() -> {
            try {
                ServerBootstrap serverBootstrap = CollectAgreement.getInstance().getServerBootstrap();

                AtomicReference<String> message = new AtomicReference<>("");
                DeviceUpgradeHandler.SERVER_CHANNEL = serverBootstrap.bind(port).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        message.set("监听 8000 端口，等待链接...");
                        log.info(message.get());
                    } else {
                        message.set("服务端绑定到 8000 端口失败");
                        log.error(message.get());
                    }
                    DataCenter.SEND_MESSAGE_QUEUE.add(message.get());
                }).sync().channel();

                DeviceUpgradeHandler.SERVER_CHANNEL.closeFuture().sync();

            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    // 获取单例实例，线程安全
    public static DeviceUpgradeHandler getInstance(int port) {
        if (instance == null) {
            synchronized (DeviceUpgradeHandler.class) {
                if (instance == null) {
                    instance = new DeviceUpgradeHandler(port);
                }
            }
        }
        if (SERVER_CHANNEL.isOpen()) {
            DataCenter.SEND_MESSAGE_QUEUE.add("监听 8000 端口，等待链接...");
        }
        return instance;
    }

    /**
     * 开始升级驱动板
     */
    public void startUpgradeDrive(byte mode, int fileSize) throws IOException {
        byte[] command = buildUpgradeCommand(mode, fileSize);
        current.set(System.currentTimeMillis());
        sendCommandWithRetry(command);
    }

    // 发送升级文件数据
    public void sendUpgradeFile(byte[] buffer, byte mode) throws IOException {
        int chunks = calculateChunksLength(buffer);
        for (int i = 0; i < chunks; i++) {
            String msg = String.format("%s;;%d%%", getUpgradeMessage(mode), (i + 1) * 100 / chunks);
            DataCenter.SEND_MESSAGE_QUEUE.add(msg);
            sendFileChunk(buffer, mode, i * chunkSize);
        }
    }

    // 完成升级驱动板
    public void finishedUpgradeDrive(byte mode, int crc32) throws IOException {
        byte[] finBytes = buildFinishedUpgradeCommand(mode, crc32);
        sendCommandWithRetry(finBytes);
    }

    // 构造升级命令
    private byte[] buildUpgradeCommand(byte mode, int fileSize) {
        byte[] bytes = ByteUtils.short2BytesLittleEndian(fileSize);
        byte[] command = new byte[7];
        command[0] = (byte) 0xAA;               // 帧头
        command[1] = (byte) command.length;     // 长度
        command[2] = (byte) 0x00;               // 校验和
        command[3] = mode;                      // 模式
        command[4] = bytes[0];                  //
        command[5] = bytes[1];
        command[6] = (byte) 0x55;               // 帧尾
        return command;
    }

    // 构造完成升级命令
    private byte[] buildFinishedUpgradeCommand(byte mode, int crc32) {
        byte[] bytes = ByteUtils.int2BytesLittleEndian(crc32);
        byte[] finBytes = new byte[9];
        finBytes[0] = (byte) 0xAA;              // 帧头
        finBytes[1] = (byte) finBytes.length;   // 长度
        finBytes[2] = (byte) 0x00;              // 校验和
        finBytes[3] = mode;                     // 模式
        System.arraycopy(bytes, 0, finBytes, 4, bytes.length);
        finBytes[8] = (byte) 0x55;              // 帧尾
        return finBytes;
    }

    // 发送命令并等待确认
    private void sendCommandWithRetry(byte[] command) throws IOException {
        writeCommand(command);
        isContinued.set(false);
        while (true) {
            if (!isContinued.get()) {
                if (System.currentTimeMillis() - current.get() > 3000) {
                    log.error("3秒未收到A5回复，重发数据包");
                    current.set(System.currentTimeMillis());
                    command[2] = 0; // 重置校验和
                    writeCommand(command);
                    isContinued.set(false);
                }
            } else {
                log.warn("收到回复，退出");
                break;
            }
        }
    }

    // 发送文件数据块
    private void sendFileChunk(byte[] buffer, byte mode, int offset) throws IOException {
        byte[] filePart = prepareUpgradePacket(buffer, mode, offset);
        current.set(System.currentTimeMillis());
        writeCommand(filePart);
        isContinued.set(false);
        while (true) {
            if (!isContinued.get()) {
                if (System.currentTimeMillis() - current.get() > 3000) {
                    log.error("3秒未收到A5回复，重发数据包");
                    current.set(System.currentTimeMillis());
                    filePart[2] = 0; // 重置校验和
                    writeCommand(filePart);
                    isContinued.set(false);
                }
            } else {
                log.warn("收到回复，退出");
                break;
            }
        }
    }

    // 准备升级数据包
    private byte[] prepareUpgradePacket(byte[] buffer, byte mode, int address) {
        int readSize = Math.min(buffer.length - address, chunkSize);
        byte[] filePart = new byte[readSize + 7];
        filePart[0] = (byte) 0xAA; // 帧头
        filePart[1] = (byte) filePart.length; // 长度
        filePart[2] = (byte) 0x00; // 校验和
        filePart[3] = mode; // 模式
        System.arraycopy(buffer, address, filePart, 6, readSize);
        filePart[filePart.length - 1] = (byte) 0x55; // 帧尾
        return filePart;
    }

    // 计算数据块数量
    private int calculateChunksLength(byte[] buffer) {
        return (buffer.length % chunkSize == 0) ? buffer.length / chunkSize : (buffer.length / chunkSize) + 1;
    }

    // 获取升级进度消息
    private String getUpgradeMessage(byte mode) {
        switch (mode) {
            case (byte) 0x06: return "UPGRADE_DRIVE_PERCENTAGE";
            case (byte) 0x0A: return "SEND_DRIVE_PARAMETER_PERCENTAGE";
            case (byte) 0x0D: return "UPGRADE_MAIN_PERCENTAGE";
            case (byte) 0x11: return "SEND_MAIN_PARAMETER_PERCENTAGE";
            default: return "UNKNOWN_MODE";
        }
    }

    // 发送命令
    public void writeCommand(byte[] bytes) throws IOException {
        bytes[2] = (byte) ((byte) 0xFF & ByteUtils.sum(bytes));
        log.info("[主控板] 下发命令 [{}]", ByteUtils.toHexPrettyString(bytes));
        DataCenter.SEND_MESSAGE_QUEUE.add(String.format("[主控板] 下发命令 [%s]", ByteUtils.toHexPrettyString(bytes)));
        DeviceUpgradeHandler.SERVER_CHANNEL.write(bytes);
        DeviceUpgradeHandler.SERVER_CHANNEL.flush();
    }

}

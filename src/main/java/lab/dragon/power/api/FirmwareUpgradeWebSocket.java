package lab.dragon.power.api;

import com.fazecast.jSerialComm.SerialPort;
import lab.dragon.common.gson.GsonUtils;
import lab.dragon.common.util.ByteUtils;
import lab.dragon.common.util.CRC32MPEG2;
import lab.dragon.common.util.ThreadPoolUtil;
import lab.dragon.power.DataCenter;
import lab.dragon.power.handler.com.RtuMasterHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Future;

/**
 * WebSocket 服务端，用于处理文件上传、固件升级等操作。
 */
@Component
@ServerEndpoint("/ws-firmware-upgrade") // WebSocket端点的路径
public class FirmwareUpgradeWebSocket {
    // WebSocket发送数据队列，用于缓存发送给客户端的消息
    private static final Logger log = LoggerFactory.getLogger(FirmwareUpgradeWebSocket.class);

    // 驱动板文件流
    private final ByteArrayOutputStream driveFileStream = new ByteArrayOutputStream();
    // 主控板文件流
    private final ByteArrayOutputStream mainFileStream = new ByteArrayOutputStream();

    // 当前WebSocket连接的session
    private Session session;
    // 是否正在上传驱动板文件
    private boolean driveFileUploading = false;
    // 是否正在上传主控板文件
    private boolean mainFileUploading = false;
    // 存储驱动板文件
    private byte[] driveFile = null;
    // 存储主控板文件
    private byte[] mainFile = null;
    // 定时任务，用于定时发送缓存的消息
    private Future<?> scheduleSendTestFuture;

    private RtuMasterHelper rtuMasterHelper;

    /**
     * WebSocket连接打开时调用的方法
     *
     * @param session WebSocket连接的Session对象
     */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("sout输出下中文，打开ws");

        // 监听消息队列并发送
        this.scheduleSendTestFuture = ThreadPoolUtil.getScheduledExecutor().submit(() -> {
            // Continue while the thread is not interrupted
            while (!Thread.currentThread().isInterrupted()) {
                String take;
                try {
                    // Take a message from the queue and send it to the client
                    take = DataCenter.SEND_MESSAGE_QUEUE.take();
                    if (session != null && session.isOpen()) {
                        session.getBasicRemote().sendText(take);
                    }
                } catch (InterruptedException e) {
                    // Handle interruption gracefully and break the loop
                    Thread.currentThread().interrupt(); // Re-interrupt the thread to ensure the interrupt flag is set
                    log.error("线程被中断，处理中断异常" + e.getMessage(), e);
                } catch (IOException e) {
                    // Handle IO exception if sending the message fails
                    log.error("Error sending message: {}", e.getMessage(), e);
                }
            }
        });

        // 初始化实例
        this.rtuMasterHelper = RtuMasterHelper.createMaster();

        StringBuilder tmpMsg = new StringBuilder("PORTS;;");
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            tmpMsg.append(port.getSystemPortName()).append(",");
        }
        DataCenter.SEND_MESSAGE_QUEUE.add(tmpMsg.toString());
    }

    /**
     * 处理客户端消息（字符串类型）
     *
     * @param msg     消息内容
     * @param session WebSocket session
     */
    @OnMessage
    public void onMessage(String msg, Session session) {
        try {
            log.info("[ws recv] session: {}, 收到消息 <= {}", this.session.getId(), msg);

            // 解析指令
            if (msg.contains(";")) {
                String[] split = StringUtils.split(msg, ";");
                processCommand(split);
            } else {
                byte[] command = ByteUtils.parseHexBinary(msg.replaceAll(" ", ""));
                command[2] = 0;
                rtuMasterHelper.writeCommand(command);
            }
        } catch (Exception e) {
            log.error("处理消息时发生异常", e);
            DataCenter.SEND_MESSAGE_QUEUE.add("下发命令失败");
        }
    }

    /**
     * 处理指令消息
     *
     * @param split 分割后的指令数组
     */
    private void processCommand(String[] split) throws IOException {
        switch (split[0].toUpperCase()) {
            case "UPLOAD_DRIVE_START":
                log.info("开始接收驱动板升级文件");
                startFileUpload(true);
                break;
            case "UPLOAD_DRIVE_COMPLETE":
                completeFileUpload(true, split);
                break;
            case "UPGRADE_DRIVE":
                upgradeFile(true);
                break;
            case "UPLOAD_MAIN_START":
                log.info("开始接收主控板升级文件");
                startFileUpload(false);
                break;
            case "UPLOAD_MAIN_COMPLETE":
                completeFileUpload(false, split);
                break;
            case "UPGRADE_MAIN":
                upgradeFile(false);
                break;
            case "OPEN_PORT":
                rtuMasterHelper.openPort(split[1]);
                break;
            case "CLOSE_PORT":
                rtuMasterHelper.closePort();
                break;
            case "BOOTLOADER":
                rtuMasterHelper.intoBootloader();
                break;
            default:
                log.warn("未知指令: {}", split[0]);
        }
    }

    /**
     * 启动文件上传（针对驱动板或主控板）
     *
     * @param isDrive 是否为驱动板
     */
    private void startFileUpload(boolean isDrive) {
        if (isDrive) {
            driveFileStream.reset();
            driveFile = null;
            driveFileUploading = true;
        } else {
            mainFileStream.reset();
            mainFile = null;
            mainFileUploading = true;
        }
    }

    /**
     * 完成文件上传（针对驱动板或主控板）
     *
     * @param isDrive 是否为驱动板
     * @param split   分割后的指令数组
     */
    private void completeFileUpload(boolean isDrive, String[] split) {
        if (isDrive) {
            driveFile = driveFileStream.toByteArray();
            driveFileUploading = false;
            log.info("驱动板升级文件接收完成，文件大小: {}", driveFile.length);
            checkFileSize(split[1], driveFile.length, "驱动板");
        } else {
            mainFile = mainFileStream.toByteArray();
            mainFileUploading = false;
            log.info("主控板升级文件接收完成，文件大小: {}", mainFile.length);
            checkFileSize(split[1], mainFile.length, "主控板");
        }
    }

    /**
     * 检查上传文件大小是否正确
     *
     * @param expectedSize 预期大小
     * @param actualSize   实际大小
     * @param fileType     文件类型（驱动板/主控板）
     */
    private void checkFileSize(String expectedSize, int actualSize, String fileType) {
        if (Integer.parseInt(expectedSize) != actualSize) {
            DataCenter.SEND_MESSAGE_QUEUE.add(fileType + "升级文件上传失败，文件大小异常；应为：" + expectedSize + "，实际接收到 " + actualSize);
        } else {
            DataCenter.SEND_MESSAGE_QUEUE.add(fileType + "升级文件上传成功");
        }
    }

    /**
     * 启动固件升级（针对驱动板或主控板）
     *
     * @param isDrive 是否为驱动板
     */
    private void upgradeFile(boolean isDrive) throws IOException {
        if (isDrive) {
            DataCenter.SEND_MESSAGE_QUEUE.add("UPGRADE_DRIVE_START;;" + driveFileUploading);
            rtuMasterHelper.startUpgradeDrive((byte) 0x03, driveFile.length);
            rtuMasterHelper.sendUpgradeFile(driveFile, (byte) 0x04);
            int crc32Value = CRC32MPEG2.computeCRC32MPEG2LittleEndian(driveFile);
            rtuMasterHelper.finishedUpgradeDrive((byte) 0x05, crc32Value);
            DataCenter.SEND_MESSAGE_QUEUE.add("UPGRADE_DRIVE_COMPLETE;;" + driveFileUploading);
        } else {
            DataCenter.SEND_MESSAGE_QUEUE.add("UPGRADE_MAIN_START;;" + mainFileUploading);
            rtuMasterHelper.startUpgradeDrive((byte) 0x06, mainFile.length);
            rtuMasterHelper.sendUpgradeFile(mainFile, (byte) 0x07);
            int crc32Value = CRC32MPEG2.computeCRC32MPEG2LittleEndian(mainFile);
            rtuMasterHelper.finishedUpgradeDrive((byte) 0x08, crc32Value);
            DataCenter.SEND_MESSAGE_QUEUE.add("UPGRADE_MAIN_COMPLETE;;" + mainFileUploading);
        }
    }

    /**
     * WebSocket接收到文件数据时调用的方法
     *
     * @param session  WebSocket连接的Session对象
     * @param fileData 接收到的文件数据
     * @param last     是否是最后一段文件数据
     */
    @OnMessage
    public void onMessage(Session session, byte[] fileData, boolean last) throws IOException {
        log.info("driveFileUploading: {}, file size: {}; mainFileUploading: {}", driveFileUploading, fileData.length, mainFileUploading);
        // 写入文件流
        if (driveFileUploading) {
            driveFileStream.write(fileData);
        }
        if (mainFileUploading) {
            mainFileStream.write(fileData);
        }
    }

    /**
     * WebSocket连接出现错误时调用的方法
     *
     * @param throwable 异常信息
     */
    @OnError
    public void onError(Throwable throwable) throws IOException {
        log.error("websocket捕捉到异常，导致websocket关闭。异常信息：{}", throwable.getMessage(), throwable);
        this.session.close();
    }

    /**
     * WebSocket连接关闭时调用的方法
     */
    @OnClose
    public void onClosing() {

        // 取消定时任务
        if (this.scheduleSendTestFuture != null) {
            // Interrupt the task
            this.scheduleSendTestFuture.cancel(true);
        }

        // 重置文件流和相关状态
        driveFileStream.reset();
        mainFileStream.reset();
        driveFile = null;
        mainFile = null;
        driveFileUploading = false;
        mainFileUploading = false;

        log.info("[ws]断开连接：{}", this.session.getId());
        try {
            // 关闭WebSocket连接
            if (this.session != null && this.session.isOpen()) {
                this.session.close();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}

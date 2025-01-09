package lab.dragon.api;

import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import lab.dragon.common.util.ByteUtils;
import lab.dragon.common.util.ThreadPoolUtil;
import lab.dragon.config.ConstantConfiguration;
import lab.dragon.modbus.RtuMasterHelper;
import lab.dragon.util.CRC32MPEG2;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


@Component
@ServerEndpoint("/advanced-ws")
public class AdvancedWebSocket {
    /**
     * websocket发送数据队列
     */
    public static final LinkedBlockingQueue<String> SEND_MESSAGE_QUEUE = new LinkedBlockingQueue<>();
    private final Logger log = LoggerFactory.getLogger(AdvancedWebSocket.class);

    private final ByteArrayOutputStream mainFileStream = new ByteArrayOutputStream();
    // 驱动板文件流
    private final ByteArrayOutputStream driveFileStream = new ByteArrayOutputStream();
    /**
     * 当前websocket的session标识
     */
    private Session session;
    private RtuMasterHelper masterHelper;
    // 驱动板上传状态
    private boolean driveFileUploading = false;
    private boolean mainFileUploading = false;
    private byte[] driveFile = null;
    private byte[] mainFile = null;
    private ScheduledFuture<?> scheduleSendTestFuture;

    private void loadModbusConfig() {
        String commId = ConstantConfiguration.commIds[2];
        try {
            this.masterHelper = RtuMasterHelper.createMaster(commId);
            ThreadPoolUtil.execute(this.masterHelper::listen);
        } catch (SerialPortInvalidPortException e) {
            String error = "[" + commId + "] 主控板端口不存在或打开失败，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(error);
        }
    }

    /**
     * 打开连接
     *
     * @param session websocket连接标识符
     */
    @OnOpen
    public void onOpen(Session session) {
        log.info("[ws]创建一个连接：{}", session.getId());
        this.session = session;

        loadModbusConfig();

        this.scheduleSendTestFuture = ThreadPoolUtil.getScheduledExecutor().scheduleWithFixedDelay(() -> {
            try {
                if (!SEND_MESSAGE_QUEUE.isEmpty()) {
                    String take = SEND_MESSAGE_QUEUE.take();
                    session.getBasicRemote().sendText(take);
                }
            } catch (IOException | InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }, 0, 1, TimeUnit.MILLISECONDS);
    }

    /**
     * 接收信息
     *
     * @param msg 接收到的消息
     */
    @OnMessage
    public void onMessage(String msg, Session session) {
        try {
            log.info("[ws recv] session: {}, 收到消息 <= {}", this.session.getId(), msg);

            // 发过来的数据中包含 ;
            if (msg.contains(";")) {
                String[] split = StringUtils.split(msg, ";");

                if (StringUtils.equalsIgnoreCase(split[0], "UPLOAD_DRIVE_START")) {
                    log.info("开始接收驱动板升级文件");
                    driveFileStream.reset();
                    driveFile = null;
                    driveFileUploading = true;
                } else if (StringUtils.equalsIgnoreCase(split[0], "UPLOAD_DRIVE_COMPLETE")) {
                    driveFile = driveFileStream.toByteArray();
                    driveFileUploading = false;
                    log.info("驱动板升级文件接收完成，文件大小: {}", driveFile.length);
                    if (Integer.parseInt(split[1]) != driveFile.length) {
                        SEND_MESSAGE_QUEUE.add("驱动板升级文件上传失败，文件大小异常；应为：" + split[1] + "，实际接收到 " + driveFile.length);
                    } else {
                        SEND_MESSAGE_QUEUE.add("驱动板升级文件上传成功");
                    }
                } else if (StringUtils.equalsIgnoreCase(split[0], "UPGRADE_DRIVE")) {
                    SEND_MESSAGE_QUEUE.add("UPGRADE_DRIVE_START;;" + driveFileUploading);

                    this.masterHelper.startUpgradeQuDongBan((byte) 0x05, driveFile.length);
                    this.masterHelper.sendUpgradeFile(driveFile, (byte) 0x06);

                    int crc32Value = CRC32MPEG2.computeCRC32MPEG2(driveFile);
                    this.masterHelper.finishedUpgradeQuDongBan((byte) 0x07, crc32Value);

                    SEND_MESSAGE_QUEUE.add("UPGRADE_DRIVE_COMPLETE;;" + driveFileUploading);
                } else if (StringUtils.equalsIgnoreCase(split[0], "SEND_DRIVE_PARAMETER")) {
                    SEND_MESSAGE_QUEUE.add("SEND_DRIVE_PARAMETER_START;;" + driveFileUploading);

                    this.masterHelper.startUpgradeQuDongBan((byte) 0x08, driveFile.length);
                    this.masterHelper.sendUpgradeFile(driveFile, (byte) 0x0A);

                    int crc32Value = CRC32MPEG2.computeCRC32MPEG2(driveFile);
                    this.masterHelper.finishedUpgradeQuDongBan((byte) 0x0B, crc32Value);

                    SEND_MESSAGE_QUEUE.add("SEND_DRIVE_PARAMETER_COMPLETE;;" + driveFileUploading);
                } else if (StringUtils.equalsIgnoreCase(split[0], "UPLOAD_MAIN_START")) {
                    log.info("开始接收主控板升级文件");
                    mainFileStream.reset();
                    mainFile = null;
                    mainFileUploading = true;
                } else if (StringUtils.equalsIgnoreCase(split[0], "UPLOAD_MAIN_COMPLETE")) {
                    mainFile = mainFileStream.toByteArray();
                    mainFileUploading = false;
                    log.info("主控板升级文件接收完成，文件大小: {}", mainFile.length);
                    if (Integer.parseInt(split[1]) != mainFile.length) {
                        SEND_MESSAGE_QUEUE.add("主控板升级文件上传失败，文件大小异常；应为：" + split[1] + "，实际接收到 " + mainFile.length);
                    } else {
                        SEND_MESSAGE_QUEUE.add("主控板升级文件上传成功");
                    }
                } else if (StringUtils.equalsIgnoreCase(split[0], "UPGRADE_MAIN")) {
                    SEND_MESSAGE_QUEUE.add("UPGRADE_MAIN_START;;" + mainFileUploading);

                    this.masterHelper.startUpgradeQuDongBan((byte) 0x0C, mainFile.length);

                    this.masterHelper.sendUpgradeFile(mainFile, (byte) 0x0D);

                    int crc32Value = CRC32MPEG2.computeCRC32MPEG2(mainFile);
                    this.masterHelper.finishedUpgradeQuDongBan((byte) 0x0E, crc32Value);

                    SEND_MESSAGE_QUEUE.add("UPGRADE_MAIN_COMPLETE;;" + mainFileUploading);
                } else if (StringUtils.equalsIgnoreCase(split[0], "SEND_MAIN_PARAMETER")) {
                    SEND_MESSAGE_QUEUE.add("SEND_MAIN_PARAMETER_START;;" + mainFileUploading);

                    this.masterHelper.startUpgradeQuDongBan((byte) 0x0F, mainFile.length);

                    this.masterHelper.sendUpgradeFile(mainFile, (byte) 0x11);

                    int crc32Value = CRC32MPEG2.computeCRC32MPEG2(mainFile);
                    this.masterHelper.finishedUpgradeQuDongBan((byte) 0x12, crc32Value);

                    SEND_MESSAGE_QUEUE.add("SEND_MAIN_PARAMETER_COMPLETE;;" + mainFileUploading);

                }
            } else if (StringUtils.startsWith(msg, "READ_PARAMETER")) {
                String type = StringUtils.substring(msg, 15);
                this.masterHelper.writeMode(DatatypeConverter.parseHexBinary(type)[0]);
            } else if (StringUtils.startsWith(msg, "SPD;0")) {
                this.masterHelper.writeZeroSpd();
            } else {
                byte[] parseHexBinary = ByteUtils.parseHexBinary(msg.replaceAll(" ", ""));
                parseHexBinary[2] = 0;
                this.masterHelper.writeCommand(parseHexBinary);
            }
        } catch (IOException e) {
            this.masterHelper.isContinued.set(false);
            log.error(e.getMessage(), e);
            SEND_MESSAGE_QUEUE.add("下发命令失败");
        }
    }

    @OnMessage
    public void onMessage(Session session, byte[] fileData, boolean last) throws IOException {
        log.info("[ws recv] file Data length: {}", fileData.length);

        if (driveFileUploading) {
            driveFileStream.write(fileData);
        }
        if (mainFileUploading) {
            mainFileStream.write(fileData);
        }
    }

    /**
     * 接收错误信息
     *
     * @param throwable
     */
    @OnError
    public void onError(Throwable throwable) throws IOException {
        log.error("websocket捕捉到异常，导致websocket关闭。异常信息：{}", throwable.getMessage(), throwable);
        this.session.close();
    }

    @OnClose
    public void onClosing() {

        if (this.scheduleSendTestFuture != null) {
            this.scheduleSendTestFuture.cancel(false);
        }
        if (this.masterHelper != null) {
            this.masterHelper.isContinued.set(false);
            this.masterHelper.getSerialPort().closePort();
            this.masterHelper.close();
        }
        driveFileStream.reset();
        mainFileStream.reset();
        driveFile = null;
        mainFile = null;
        driveFileUploading = false;
        mainFileUploading = false;

        log.info("[ws]断开连接：{}", this.session.getId());
        try {
            this.session.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}

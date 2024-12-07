package lab.dragon.api;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import lab.dragon.common.util.ThreadPoolUtil;
import lab.dragon.config.ConstantConfiguration;
import lab.dragon.modbus.RtuMasterHelper;
import lab.dragon.util.CRC32MPEG2;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
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
    /**
     * 当前websocket的session标识
     */
    private Session session;
    private RtuMasterHelper masterHelper;
    private boolean uploading = false;
    private byte[] uploadFile = null;

    private final ByteArrayOutputStream fileStream = new ByteArrayOutputStream();
    private ScheduledFuture<?> scheduleSendTestFuture;

    /**
     * spring 注入完成后调用的，相当于构造函数
     */
    @PostConstruct
    public void onComponent() {

    }

    private void loadModbusConfig() {
        String commId = ConstantConfiguration.commIds[2];
        try {
            SerialPort.getCommPort(commId);
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
        this.session = session;

        loadModbusConfig();

        log.info("[ws]创建一个连接：{}", session.getId());

        this.scheduleSendTestFuture = ThreadPoolUtil.getScheduledExecutor().scheduleWithFixedDelay(() -> {
            try {
                if (!SEND_MESSAGE_QUEUE.isEmpty()) {
                    String take = SEND_MESSAGE_QUEUE.take();
                    session.getBasicRemote().sendText(take);
                }
            } catch (IOException | InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }, 0, 5, TimeUnit.MILLISECONDS);
    }

    /**
     * 接收信息
     *
     * @param msg 接收到的消息，json格式，由 type 和 json 字符串两部分组成，例如："{"type": "command", "json": "{}"}"
     */
    @OnMessage
    public void onMessage(String msg, Session session) {
        try {
            log.info("[ws recv] session: {}, 收到消息 <= {}", this.session.getId(), msg);

            if (StringUtils.startsWith(msg, "UPLOAD_START")) {
                fileStream.reset();
                uploading = true;
                String type = StringUtils.substring(msg, 13, 15);
                String substring = StringUtils.substring(msg, 16);
                int fileSize = Integer.parseInt(substring);
                if (Objects.equals(type, "05")) {
                    this.masterHelper.startUpgradeQuDongBan((byte) 0x05, fileSize);
                } else if (Objects.equals(type, "08")) {
                    this.masterHelper.startUpgradeQuDongBan((byte) 0x08, fileSize);
                } else if (Objects.equals(type, "0C")) {
                    this.masterHelper.startUpgradeQuDongBan((byte) 0x0C, fileSize);
                } else if (Objects.equals(type, "0F")) {
                    this.masterHelper.startUpgradeQuDongBan((byte) 0x0F, fileSize);
                }
            } else if (StringUtils.startsWith(msg, "UPLOAD_COMPLETE")) {
                uploading = false;
                uploadFile = fileStream.toByteArray();
                String type = StringUtils.substring(msg, 16);

                if (Objects.equals(type, "05")) {
                    this.masterHelper.sendUpgradeFile(uploadFile, (byte) 0x06);

                    int crc32Value = CRC32MPEG2.computeCRC32MPEG2(uploadFile);
                    this.masterHelper.finishedUpgradeQuDongBan((byte) 0x07, crc32Value);
                }  else if (Objects.equals(type, "08")) {
                    this.masterHelper.sendUpgradeFile(uploadFile, (byte) 0x0A);

                    int crc32Value = CRC32MPEG2.computeCRC32MPEG2(uploadFile);
                    this.masterHelper.finishedUpgradeQuDongBan((byte) 0x0B, crc32Value);
                } else if (Objects.equals(type, "0C")) {
                    this.masterHelper.sendUpgradeFile(uploadFile, (byte) 0x0D);

                    int crc32Value = CRC32MPEG2.computeCRC32MPEG2(uploadFile);
                    this.masterHelper.finishedUpgradeQuDongBan((byte) 0x0E, crc32Value);
                } else if (Objects.equals(type, "0F")) {
                    this.masterHelper.sendUpgradeFile(uploadFile, (byte) 0x11);

                    int crc32Value = CRC32MPEG2.computeCRC32MPEG2(uploadFile);
                    this.masterHelper.finishedUpgradeQuDongBan((byte) 0x12, crc32Value);
                }
            } else if (StringUtils.startsWith(msg, "READ_PARAMETER")) {
                String type = StringUtils.substring(msg, 15);
                this.masterHelper.writeMode(DatatypeConverter.parseHexBinary(type)[0]);
            }  else if (StringUtils.startsWith(msg, "SPD;0")) {
                this.masterHelper.writeZeroSpd();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    @OnMessage
    public void onMessage(Session session, byte[] fileData, boolean last) throws IOException {
        log.info("[ws recv] file Data length: {}", fileData.length);

        if (uploading) {
            fileStream.write(fileData);
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
            this.masterHelper.close();
        }

        log.info("[ws]断开连接：{}", this.session.getId());
        try {
            this.session.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}

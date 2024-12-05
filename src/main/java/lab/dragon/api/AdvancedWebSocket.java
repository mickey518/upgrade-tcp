package lab.dragon.api;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import com.google.gson.JsonSyntaxException;
import lab.dragon.common.gson.GsonUtils;
import lab.dragon.config.ConstantConfiguration;
import lab.dragon.entity.WsConnectMessage;
import lab.dragon.entity.WsConnectMessageEnum;
import lab.dragon.modbus.RtuMasterHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;


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
        } catch (SerialPortInvalidPortException e) {
            String error = "[" + commId + "] 主控板端口不存在或打开失败，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
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

    }

    /**
     * 接收信息
     *
     * @param msg 接收到的消息，json格式，由 type 和 json 字符串两部分组成，例如："{"type": "command", "json": "{}"}"
     */
    @OnMessage
    public void onMessage(String msg, Session session) {
        log.info("[ws recv] session: {}, 收到消息 =》 {}", this.session.getId(), msg);

        try {
            WsConnectMessage wsConnectMessage = GsonUtils.fromJson(msg, WsConnectMessage.class);
            log.info("{}", wsConnectMessage.toString());
            if (WsConnectMessageEnum.upgrade.equals(wsConnectMessage.getType())) {

            }
        } catch (ClassCastException e) {
            String error = "数据类型转换错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
        } catch (JsonSyntaxException e) {
            String error = "json格式错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
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

        if (this.masterHelper != null) {
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

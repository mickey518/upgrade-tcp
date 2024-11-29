package lab.dragon.api;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import com.google.gson.JsonSyntaxException;
import com.serotonin.modbus4j.exception.ModbusInitException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import jssc.SerialPortException;
import lab.dragon.ModbusWorker;
import lab.dragon.common.gson.GsonUtils;
import lab.dragon.config.SerialPortConfig;
import lab.dragon.entity.WsConnectMessage;
import lab.dragon.entity.WsConnectMessageEnum;
import lab.dragon.modbus.ModbusUtil;
import lab.dragon.modbus.RtuMasterHelper;
import lab.dragon.common.util.DateTimeUtils;
import lab.dragon.common.util.ThreadPoolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;


@Component
@ServerEndpoint("/ws")
public class ConnectionWebSocket {
    private final Logger log = LoggerFactory.getLogger(ConnectionWebSocket.class);

    /**
     * websocket发送数据队列
     */
    public static final LinkedBlockingQueue<String> SEND_MESSAGE_QUEUE = new LinkedBlockingQueue<>();

    public static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private static final AtomicInteger onlineCount = new AtomicInteger(0);
    /**
     * 串口配置文件，多个端口之间用分号进行分割
     */
    private final Path commPortConfigFile = Paths.get("com-port.txt");
    /**
     * 当前websocket的session标识
     */
    private Session session;

    /**
     * 伺服控制modbus模块
     */
    private ModbusUtil servoModbusUtil;
    /**
     * 读取伺服控制器的定时线程
     */
    private ScheduledFuture<?> scheduledReadServoFuture;
    /**
     * 读取传感器数据的定时线程
     */
    private ScheduledFuture<?> scheduledReadSensorFuture;
    private ScheduledFuture<?> scheduleSendTestFuture;
    private SerialPortConfig sensorPortConfig;
    private ModbusWorker modbusWorker;
    private RtuMasterHelper masterHelper;
    /**
     * 报警信息代码映射表
     */
    private Map<String, String> warnMessages;

    /**
     * spring 注入完成后调用的，相当于构造函数
     */
    @PostConstruct
    public void onComponent() {
        log.info("websocket post construct!!!");
        if (Files.notExists(commPortConfigFile)) {
            String error = "缺少配置文件 [com-port.txt]，需要提供串口配置文件；配置文件中第一个表示伺服控制器串口，第二个表示传感器串口";
            log.error(error);
        }
        // 加载告警代码含义转换映射表
        try {
            warnMessages = GsonUtils.loadFromFile("warn.json", Map.class);
        } catch (IOException e) {
            String error = "缺少报警信息转换映射表 warn.json";
            log.error(error);
        }
    }

    private void loadModbusConfig() {
        try {
            byte[] bytes = Files.readAllBytes(commPortConfigFile);

            String commString = new String(bytes);

            String[] commIds = commString.split(";");

            // 打开伺服电机控制端口
            try {
                SerialPort.getCommPort(commIds[0]);
                SerialPortConfig serialPortConfig = new SerialPortConfig(commIds[0]);
                serialPortConfig.setStartIndex(0);
                servoModbusUtil = new ModbusUtil(serialPortConfig);
            } catch (ModbusInitException | SerialPortException | SerialPortInvalidPortException e) {
                String error = "[" + commIds[0] + "] 伺服电机控制端口不存在或打开失败，错误消息：" + e.getMessage();
                log.error(error, e);
                SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
            }

            // 打开动态扭矩传感器端口
            try {
                // 创建一个定时获取传感器数据的计划线程
                SerialPort.getCommPort(commIds[1]);
                sensorPortConfig = new SerialPortConfig(commIds[1]);
                sensorPortConfig.setStartIndex(0);
                this.modbusWorker = new ModbusWorker(sensorPortConfig);
                this.scheduledReadSensorFuture = ThreadPoolUtil.getScheduledExecutor().scheduleAtFixedRate(this.modbusWorker, 373, 100, TimeUnit.MILLISECONDS);
            } catch (ModbusInitException | SerialPortException | SerialPortInvalidPortException e) {
                String error = "[" + commIds[1] + "] 动态扭矩传感器端口不存在或打开失败，错误消息：" + e.getMessage();
                log.error(error, e);
                SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
            }

            try {
                SerialPort.getCommPort(commIds[2]);
                this.masterHelper = RtuMasterHelper.createMaster(commIds[2]);
                ThreadPoolUtil.execute(() -> this.masterHelper.listen());
            } catch ( SerialPortInvalidPortException e) {
                String error = "[" + commIds[2] + "] 主控板端口不存在或打开失败，错误消息：" + e.getMessage();
                log.error(error, e);
                SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
            }

        } catch (IOException e) {
            String error = "配置文件 [com-port.txt] 读取异常，错误消息：" + e.getMessage();
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

        log.info("[ws]创建一个连接：{}，连接总量：{}", session.getId(), onlineCount.addAndGet(1));

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

        // 定时获取伺服控制器报警记录的定时线程
        this.scheduledReadServoFuture = ThreadPoolUtil.getScheduledExecutor().scheduleAtFixedRate(() -> {
            // 读取伺服报警记录
            try {
                readServoValues();
                readServoWarns();
            } catch (ModbusTransportException | InterruptedException e) {
                log.error(e.getMessage(), e);
            }

        }, 0, 1, TimeUnit.SECONDS);

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
            if (WsConnectMessageEnum.write.equals(wsConnectMessage.getType())) {
                log.info("received write command: {}", wsConnectMessage.getJson());
                Map<String, Integer> map = GsonUtils.fromJsonToMap(wsConnectMessage.getJson(), String.class, Integer.class);
                for (Map.Entry<String, Integer> entry : map.entrySet()) {
                    int key = Integer.parseInt(entry.getKey());
                    servoModbusUtil.writeRegister(key < 1000 ? key + 10000 : key, (int) entry.getValue());
                }

                readServoValues();

            } else if (WsConnectMessageEnum.writeBatt.equals(wsConnectMessage.getType())) {
                log.info("received write batt spd value: {}", wsConnectMessage.getJson());
                Map<String, Integer> map = GsonUtils.fromJsonToMap(wsConnectMessage.getJson(), String.class, Integer.class);
                // 下发速度参数
                this.masterHelper.writeSpd(map.get("spd"));
            }  else if (WsConnectMessageEnum.writeMode.equals(wsConnectMessage.getType())) {
                Map<String, Integer> map = GsonUtils.fromJsonToMap(wsConnectMessage.getJson(), String.class, Integer.class);
                // 下发速度参数
                this.masterHelper.writeMode(map.get("mode"));
            } else if (WsConnectMessageEnum.savelog.equals(wsConnectMessage.getType())) {
                Path logPath = Paths.get("logs", DateTimeUtils.generateFileName("操作记录-", ".txt"));
                Files.createFile(logPath);
                Files.write(logPath, wsConnectMessage.getJson().getBytes(StandardCharsets.UTF_8));
            }
        } catch (ClassCastException e) {
            String error = "数据类型转换错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
        } catch (JsonSyntaxException e) {
            String error = "json格式错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
        } catch (ModbusTransportException e) {
            String error = "串口写入错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
        } catch (IOException e) {
            String error = "websocket发送数据错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
        } catch (InterruptedException e) {
            String error = "向发送websocket队列写入数据错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
        }
    }

    /**
     * 读取寄存器中的值
     *
     * @throws ModbusTransportException
     */
    private void readServoValues() throws ModbusTransportException, InterruptedException {
        Map<Integer, Object> result = servoModbusUtil.readServoValues();
        SEND_MESSAGE_QUEUE.put(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.result).json(GsonUtils.toJson(result)).build()));
    }

    private void readServoWarns() throws ModbusTransportException, InterruptedException {
        List<String> warnStrings = new ArrayList<>();

        if (servoModbusUtil != null) {
            Map<Integer, Object> readServoWarns = servoModbusUtil.readServoWarns();
            String warnCode = "0";
            for (Object value : readServoWarns.values()) {
                short[] warnCodes = (short[]) value;

                for (short tmp : warnCodes) {
                    warnCode = String.valueOf(tmp);
                    if (!"0".equals(warnCode)) {
                        // 转换错误码
                        String msg = warnMessages.get(warnCode);
                        warnStrings.add(msg);
                    }
                }
            }
        }


        if (!warnStrings.isEmpty()) {
            SEND_MESSAGE_QUEUE.put(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.warn).json(GsonUtils.toJson(warnStrings)).build()));
        }
    }

    /**
     * 接收错误信息
     *
     * @param throwable
     */
    @OnError
    public void onError(Throwable throwable) throws IOException {
        log.error(throwable.getMessage(), throwable);
        this.session.close();
    }

    @OnClose
    public void onClosing() {

        // 关闭伺服获取数据参数定时线程
        if (this.scheduledReadServoFuture != null) {
            this.scheduledReadServoFuture.cancel(false);
        }
        if (this.servoModbusUtil != null) {
            this.servoModbusUtil.close();
        }

        if (this.scheduledReadSensorFuture != null) {
            this.scheduledReadSensorFuture.cancel(false);
        }
        if (this.modbusWorker != null) {
            this.modbusWorker.close();
        }

        if (this.scheduleSendTestFuture != null) {
            this.scheduleSendTestFuture.cancel(false);
        }

        log.info("[ws]断开连接：{}，连接总量：{}", this.session.getId(), onlineCount.addAndGet(-1));
        try {
            this.session.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}

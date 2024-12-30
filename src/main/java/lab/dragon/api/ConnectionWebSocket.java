package lab.dragon.api;

import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import com.google.gson.JsonSyntaxException;
import com.serotonin.modbus4j.exception.ModbusInitException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import jssc.SerialPortException;
import lab.dragon.ModbusWorker;
import lab.dragon.common.gson.GsonUtils;
import lab.dragon.common.util.DateTimeUtils;
import lab.dragon.common.util.ThreadPoolUtil;
import lab.dragon.config.ConstantConfiguration;
import lab.dragon.config.SerialPortConfig;
import lab.dragon.entity.WsConnectMessage;
import lab.dragon.entity.WsConnectMessageEnum;
import lab.dragon.modbus.ModbusUtil;
import lab.dragon.modbus.RtuMasterHelper;
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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;


@Component
@ServerEndpoint("/ws")
public class ConnectionWebSocket {
    /**
     * websocket发送数据队列
     */
    public static final LinkedBlockingQueue<String> SEND_MESSAGE_QUEUE = new LinkedBlockingQueue<>();
    public static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final AtomicInteger onlineCount = new AtomicInteger(0);
    private final Logger log = LoggerFactory.getLogger(ConnectionWebSocket.class);

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

    /**
     * 动扭传感器处理线程
     */
    private ModbusWorker modbusWorker;
    /**
     * 主控板处理线程
     */
    private RtuMasterHelper masterHelper;
    /**
     * 报警信息代码映射表
     */
    private static Map<String, String> warnMessages;
    private static boolean adjustment = false;

    /**
     * spring 注入完成后调用的，相当于构造函数
     */
    @PostConstruct
    public void onComponent() {

        // 加载告警代码含义转换映射表
        try {
            ConnectionWebSocket.warnMessages = GsonUtils.loadFromFile("warn.json", Map.class);
        } catch (IOException e) {
            String error = "缺少报警信息转换映射表 warn.json";
            log.error(error);
        }
    }

    /**
     * 读取系统参数文件
     *
     * @return 系统参数文件映射map
     */
    private Map readParameter() {
        Map map;
        try {
            map = GsonUtils.loadFromFile("parameter.json", Map.class);
        } catch (IOException e) {
            map = new HashMap();
        }
        if (!map.containsKey("spd-gain")) {
            map.put("spd-gain", 1.0081f);
        }
        SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.readParameter).json(GsonUtils.toJson(map)).build()));
        return map;
    }

    /**
     * 保存系统参数
     *
     * @param obj
     */
    private void writeParameter(Object obj) {
        try {
            GsonUtils.writeToFile(obj, "parameter.json", StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            log.error("保存参数失败， e: {}", e.getMessage(), e);
        }
    }

    private void loadModbusConfig() {
        // 打开伺服电机控制端口
        String commId = ConstantConfiguration.commIds[0];
        try {
            SerialPortConfig serialPortConfig = new SerialPortConfig(commId);
            serialPortConfig.setStartIndex(0);
            servoModbusUtil = new ModbusUtil(serialPortConfig);
        } catch (ModbusInitException | SerialPortException | SerialPortInvalidPortException e) {
            String error = "[" + commId + "] 伺服电机控制端口不存在或打开失败，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
        }

        // 打开动态扭矩传感器端口
        commId = ConstantConfiguration.commIds[1];
        try {
            // 创建一个定时获取传感器数据的计划线程
            SerialPortConfig sensorPortConfig = new SerialPortConfig(commId);
            sensorPortConfig.setStartIndex(0);
            this.modbusWorker = new ModbusWorker(sensorPortConfig);
            this.scheduledReadSensorFuture = ThreadPoolUtil.getScheduledExecutor().scheduleAtFixedRate(this.modbusWorker, 373, 100, TimeUnit.MILLISECONDS);
        } catch (ModbusInitException | SerialPortException | SerialPortInvalidPortException e) {
            String error = "[" + commId + "] 动态扭矩传感器端口不存在或打开失败，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(GsonUtils.toJson(WsConnectMessage.builder().type(WsConnectMessageEnum.error).json(error).build()));
        }

        commId = ConstantConfiguration.commIds[2];
        try {
            this.masterHelper = RtuMasterHelper.createMaster(commId);
            ThreadPoolUtil.execute(() -> this.masterHelper.listen());
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
        log.info("[ws]创建一个连接：{}，连接总量：{}", session.getId(), onlineCount.addAndGet(1));

        this.session = session;

        // 连上 websocket 时，监听 websocket 发送队列，有数据时发送到 socket 前端，session 关闭时，循环中断。
        ThreadPoolUtil.execute(() -> {
            while (this.session.isOpen()) {
                try {
                    String take = SEND_MESSAGE_QUEUE.take();
                    session.getBasicRemote().sendText(take);
                } catch (IOException | InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }
        });

        readParameter();

        loadModbusConfig();

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

            if (WsConnectMessageEnum.write.equals(wsConnectMessage.getType())) {

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
                Integer spd = map.get("spd");
                // 校准过程中不接收其他转速指令，除了0
                if (!adjustment) {
                    this.masterHelper.writeSpd(spd);
                }

            } else if (WsConnectMessageEnum.writeMode.equals(wsConnectMessage.getType())) {
                Map<String, Integer> map = GsonUtils.fromJsonToMap(wsConnectMessage.getJson(), String.class, Integer.class);
                // 下发速度参数
                if (!adjustment) {
                    this.masterHelper.writeMode(map.get("mode"));
                }

            } else if (WsConnectMessageEnum.savelog.equals(wsConnectMessage.getType())) {
                Path logPath = Paths.get("logs", DateTimeUtils.generateFileName("操作记录-", ".txt"));
                Files.createFile(logPath);
                Files.write(logPath, wsConnectMessage.getJson().getBytes(StandardCharsets.UTF_8));
            } else if (WsConnectMessageEnum.writeParameter.equals(wsConnectMessage.getType())) {
                writeParameter(GsonUtils.fromJson(wsConnectMessage.getJson()));
            } else if (WsConnectMessageEnum.adjustment.equals(wsConnectMessage.getType())) {

                // 校准过程中不接收其他指令
                adjustment = true;
                // 先将速度设置为 0
                this.masterHelper.writeZeroSpd();
                this.masterHelper.writeSpd(0);
                // 下发校准指令
                this.masterHelper.writeMode(3);

            } else if (WsConnectMessageEnum.adjustment_end.equals(wsConnectMessage.getType())) {
                this.masterHelper.writeZeroSpd();
                adjustment = false;
            }  else if (WsConnectMessageEnum.stop.equals(wsConnectMessage.getType())) {
                this.masterHelper.writeZeroSpd();
                this.masterHelper.writeSpd(0);
                adjustment = false;
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

    /**
     * 读取寄存器中的报警记录
     *
     * @throws ModbusTransportException
     * @throws InterruptedException
     */
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
                        String msg = ConnectionWebSocket.warnMessages.get(warnCode);
                        warnStrings.add(msg);
                        log.error("[伺服电机]报警：【{}】【{}】", warnCode, msg);
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
        log.error("websocket捕捉到异常，导致websocket关闭。异常信息：{}", throwable.getMessage(), throwable);
        this.session.close();
    }

    @OnClose
    public void onClosing() {
        log.info("[ws]断开连接：{}，连接总量：{}", this.session.getId(), onlineCount.addAndGet(-1));

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

        if (this.masterHelper != null) {
            this.masterHelper.close();
        }

        try {
            this.session.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}

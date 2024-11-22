package lab.dragon.api;

import com.google.gson.JsonSyntaxException;
import com.serotonin.modbus4j.exception.ModbusInitException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import lab.dragon.Main;
import lab.dragon.ModbusWorker;
import lab.dragon.config.SerialPortConfig;
import lab.dragon.entity.WsConnectMessage;
import lab.dragon.modbus.ModbusUtil;
import lab.dragon.util.DateTimeUtils;
import lab.dragon.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;


@Slf4j
@Component
@ServerEndpoint("/ws")
public class ConnectionWebSocket {
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
     * 传感器modbus控制模块
     */
    private ModbusUtil sensorModbusUtil;
    private ScheduledFuture<?> scheduledReadWarnFuture;
    private ScheduledFuture<?> scheduledReadSensorFuture;
    private SerialPortConfig sensorPortConfig;

    @PostConstruct
    public void onComponent() {
        if (Files.notExists(commPortConfigFile)) {
            String error = "缺少配置文件 [com-port.txt]，需要提供串口配置文件；配置文件中第一个表示伺服控制器串口，第二个表示传感器串口";
            log.error(error);
        }
    }

    private void loadModbusConfig() {
        try {
            byte[] bytes = Files.readAllBytes(commPortConfigFile);

            String commString = new String(bytes);

            String[] commIds = commString.split(";");

            sensorPortConfig = new SerialPortConfig(commIds[1]);
            sensorPortConfig.setStartIndex(0);

            SerialPortConfig serialPortConfig = new SerialPortConfig(commIds[0]);
            serialPortConfig.setStartIndex(0);
            servoModbusUtil = new ModbusUtil(serialPortConfig);

        } catch (IOException | ModbusInitException e) {
            log.error(e.getMessage(), e);
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

        try {
            // 创建一个定时获取传感器数据的计划线程
            ModbusWorker modbusWorker = new ModbusWorker(sensorPortConfig);
            this.scheduledReadSensorFuture = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(modbusWorker, 373, 100, TimeUnit.MILLISECONDS);


            Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
                try {
                    if (!SEND_MESSAGE_QUEUE.isEmpty()) {
                        String take = SEND_MESSAGE_QUEUE.take();
                        session.getBasicRemote().sendText(take);
                    }
                } catch (IOException | InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            }, 0, 5, TimeUnit.MILLISECONDS);

//            // 读取伺服控制器寄存器中的参数
//            if (servoModbusUtil != null) {
//                readServoValues();
//            }

            // 定时获取伺服控制器报警记录的定时线程
            this.scheduledReadWarnFuture = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                // 读取伺服报警记录
                try {
                    readServoValues();
                    readServoWarns();
                } catch (ModbusTransportException | IOException e) {
                    log.error(e.getMessage(), e);
                }

            }, 0, 1, TimeUnit.SECONDS);


        } catch (ModbusInitException e) {
            log.error(e.getMessage(), e);
        }

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
            WsConnectMessage wsConnectMessage = JsonUtils.decodeJson(msg, WsConnectMessage.class);

            // 配置串口信息
//            if ("config".equals(wsConnectMessage.getType())) {
//
//            } else if ("read".equals(wsConnectMessage.getType())) {
////                log.info("received read command: {}", wsConnectMessage.getJson());
//            } else
            if ("write".equals(wsConnectMessage.getType())) {
                log.info("received write command: {}", wsConnectMessage.getJson());
                Map<String, Integer> map = JsonUtils.decodeJson(wsConnectMessage.getJson(), Map.class);
                for (Map.Entry<String, Integer> entry : map.entrySet()) {
                    int key = Integer.parseInt(entry.getKey());
                    servoModbusUtil.writeRegister(key < 1000 ? key + 10000 : key, (int) entry.getValue());
                }

//                Thread.sleep(100);
//
//                log.info("读取伺服电机的值. ");
                readServoValues();

            } else if ("savelog".equals(wsConnectMessage.getType())) {
                Path logPath = Paths.get("logs", DateTimeUtils.generateFileName("log-", ".txt"));
                Files.createFile(logPath);
                Files.write(logPath, wsConnectMessage.getJson().getBytes(StandardCharsets.UTF_8));
            }
        } catch (ClassCastException e) {
            String error = "数据类型转换错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(JsonUtils.encodeJson(WsConnectMessage.builder().type("error").json(error).build()));
        } catch (JsonSyntaxException e) {
            String error = "json格式错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(JsonUtils.encodeJson(WsConnectMessage.builder().type("error").json(error).build()));
        } catch (ModbusTransportException e) {
            String error = "串口写入错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(JsonUtils.encodeJson(WsConnectMessage.builder().type("error").json(error).build()));
        } catch (IOException e) {
            String error = "websocket发送数据错误，错误消息：" + e.getMessage();
            log.error(error, e);
            SEND_MESSAGE_QUEUE.add(JsonUtils.encodeJson(WsConnectMessage.builder().type("error").json(error).build()));
        }
//        catch (InterruptedException e) {
//            String error = "错误消息：" + e.getMessage();
//            log.error(error, e);
//            SEND_MESSAGE_QUEUE.add(JsonUtils.encodeJson(WsConnectMessage.builder().type("error").json(error).build()));
//        }
    }

    /**
     * 读取寄存器中的值
     *
     * @throws IOException
     * @throws ModbusTransportException
     */
    private void readServoValues() throws IOException, ModbusTransportException {
        Map<Integer, Object> result = servoModbusUtil.readServoValues();
        SEND_MESSAGE_QUEUE.add(JsonUtils.encodeJson(WsConnectMessage.builder().type("result").json(JsonUtils.encodeJson(result)).build()));
    }

    private void readServoWarns() throws IOException, ModbusTransportException {
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
                        String msg = Main.WARN_MESSAGES.get(warnCode);
                        warnStrings.add(msg);
                    }
                }
            }
        }


        if (!warnStrings.isEmpty()) {
            SEND_MESSAGE_QUEUE.add(JsonUtils.encodeJson(WsConnectMessage.builder().type("warn").json(JsonUtils.encodeJson(warnStrings)).build()));
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

        if (this.scheduledReadWarnFuture != null) {
            this.scheduledReadWarnFuture.cancel(false);
        }
        if (this.scheduledReadSensorFuture != null) {
            this.scheduledReadSensorFuture.cancel(false);
        }
        this.sensorModbusUtil.close();
        this.servoModbusUtil.close();

        log.info("[ws]断开连接：{}，连接总量：{}", this.session.getId(), onlineCount.addAndGet(-1));
        try {
            this.session.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}

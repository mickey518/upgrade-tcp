package lab.dragon;

import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.exception.ModbusInitException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import lab.dragon.api.ConnectionWebSocket;
import lab.dragon.config.SerialPortConfig;
import lab.dragon.entity.WsConnectMessage;
import lab.dragon.modbus.SerialPortWrapperImpl;
import lab.dragon.util.ByteUtils;
import lab.dragon.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
public class ModbusWorker implements Runnable {

    private String portId;
    private ModbusMaster master;
    private int slaveId;

    public ModbusWorker(SerialPortConfig serialPortConfig) throws ModbusInitException {
        this.portId = serialPortConfig.getCommPortId();
        this.slaveId = serialPortConfig.getSlaveId();

        SerialPortWrapperImpl serialPortWrapper = new SerialPortWrapperImpl(serialPortConfig.getCommPortId(),
                serialPortConfig.getBaudRate(),
                serialPortConfig.getDataBits(),
                serialPortConfig.getStopBits(),
                serialPortConfig.getParity(),
                serialPortConfig.getFlowControlIn(),
                serialPortConfig.getFlowControlOut());
        ModbusFactory modbusFactory = new ModbusFactory();
        this.master = modbusFactory.createRtuMaster(serialPortWrapper);
        this.master.init();
    }

    @Override
    public void run() {
        try {
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(this.slaveId, 4, 4);

            ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) this.master.send(request);

            if (response.isException()) {
                log.error("[modbus][{}]读取保持寄存器错误，错误信息是: {}", this.portId, response.getExceptionMessage());
                throw new RuntimeException(response.getExceptionMessage());
            } else {
                byte[] responseData = response.getData();
                byte[] bytes = new byte[4];
                Map<Integer, Object> result = new HashMap<>(2);

                if (responseData.length >= 4) {
                    System.arraycopy(responseData, 0, bytes, 0, bytes.length);
                    int anInt4 = ByteUtils.bytes2IntBigEndian(bytes);
                    float aFloat4 = anInt4 / 1000.0f;
                    result.put(4, aFloat4);
                }
                if (responseData.length >= 8) {
                    System.arraycopy(responseData, bytes.length, bytes, 0, bytes.length);
                    int anInt6 = ByteUtils.bytes2IntBigEndian(bytes);
                    result.put(6, anInt6);
                }

                log.info("传感器数据：{}", JsonUtils.encodeJson(result));
                ConnectionWebSocket.SEND_MESSAGE_QUEUE.put(JsonUtils.encodeJson(WsConnectMessage.builder().type("result-sensor").json(JsonUtils.encodeJson(result)).build()));
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}

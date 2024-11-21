package lab.dragon;

import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.exception.ModbusInitException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import lab.dragon.config.SerialPortConfig;
import lab.dragon.entity.WsConnectMessage;
import lab.dragon.modbus.SerialPortWrapperImpl;
import lab.dragon.util.ByteUtils;
import lab.dragon.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.Session;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ModbusWorker implements Runnable {

    private String portId;
    private ModbusMaster master;
    private int slaveId;
    private final Session session;

//    private ModbusWorker() {}

    public ModbusWorker(SerialPortConfig serialPortConfig, Session session) throws ModbusInitException {
        this.portId = serialPortConfig.getCommPortId();
        this.slaveId = serialPortConfig.getSlaveId();
        this.session = session;
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
                System.arraycopy(responseData, 0, bytes, 0, 4);
                float aFloat4 = ByteUtils.bytes2Float(bytes);
                System.arraycopy(responseData, 4, bytes, 0, 4);
                float aFloat6 = ByteUtils.bytes2Float(bytes);

                Map<Integer, Object> result = new HashMap<>(2);
                result.put(4, aFloat4);
                result.put(6, aFloat6);

                session.getBasicRemote().sendText(JsonUtils.encodeJson(WsConnectMessage.builder().type("result-sensor").json(JsonUtils.encodeJson(result)).build()));
            }

        } catch (ModbusTransportException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}

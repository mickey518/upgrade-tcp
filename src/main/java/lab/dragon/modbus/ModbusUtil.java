package lab.dragon.modbus;

import com.fazecast.jSerialComm.SerialPort;
import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.exception.ModbusInitException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import com.serotonin.modbus4j.msg.WriteRegisterRequest;
import com.serotonin.modbus4j.msg.WriteRegisterResponse;
import io.netty.buffer.ByteBufUtil;
import lab.dragon.api.ConnectionWebSocket;
import lab.dragon.config.SensorProperty;
import lab.dragon.config.SensorPropertyConfig;
import lab.dragon.config.SerialPortConfig;
import lab.dragon.util.ByteUtils;
import lab.dragon.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ModbusUtil {

    private final SerialPortConfig serialPortConfig;
    private final ModbusMaster master;

    public ModbusUtil(SerialPortConfig serialPortConfig) throws ModbusInitException {
        this.serialPortConfig = serialPortConfig;
        SerialPortWrapperImpl serialPortWrapper = new SerialPortWrapperImpl(this.serialPortConfig.getCommPortId(), this.serialPortConfig.getBaudRate(), this.serialPortConfig.getDataBits(), this.serialPortConfig.getStopBits(), this.serialPortConfig.getParity(), this.serialPortConfig.getFlowControlIn(), this.serialPortConfig.getFlowControlOut());
        ModbusFactory modbusFactory = new ModbusFactory();
        this.master = modbusFactory.createRtuMaster(serialPortWrapper);
        log.info("[modbus][{}]初始化串口连接；{}", this.serialPortConfig.getCommPortId(), serialPortWrapper.toString());
        this.master.init();
        log.info("[modbus][{}]串口连接成功！", this.serialPortConfig.getCommPortId());

    }

    public static List<String> getCommPortIds() {
        List<String> commPortIds = new ArrayList<>();

        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            commPortIds.add(port.getSystemPortName());
        }
        return commPortIds;
    }

    /**
     * 读取寄存器中的值
     *
     * @param start   开始内存地址
     * @param len     读取的长度
     * @return ReadHoldingRegistersResponse 对象
     * @throws ModbusTransportException
     */
    public byte[] readHoldingRegisters(int start, int len) throws ModbusTransportException {

        ConnectionWebSocket.lock.writeLock().lock();
        log.info("[lock] read func locked. {}", ConnectionWebSocket.lock);
        try {
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(this.serialPortConfig.getSlaveId(), start, len);

            log.info("this.master: {}", this.master);

            ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) this.master.send(request);

            if (response.isException()) {
                log.error("[modbus][{}]读取保持寄存器错误，错误信息是: {}", this.serialPortConfig.getCommPortId(), response.getExceptionMessage());
                throw new RuntimeException(response.getExceptionMessage());
            }
            return response.getData();
        } finally {
            ConnectionWebSocket.lock.writeLock().unlock();
            log.info("[lock] read func unlocked. {}", ConnectionWebSocket.lock);
        }
    }

    public void writeRegister(int offset, int value) throws ModbusTransportException {

        ConnectionWebSocket.lock.writeLock().lock();
        log.info("[lock] write func locked. {}", ConnectionWebSocket.lock);
        try {
            WriteRegisterRequest request = new WriteRegisterRequest(this.serialPortConfig.getSlaveId(), offset, value);

            log.info("this.master: {}", this.master);

            WriteRegisterResponse response = (WriteRegisterResponse) this.master.send(request);


            if (response.isException()) {
                log.error("[modbus][{}]写保持寄存器错误，错误信息是: {}", this.serialPortConfig.getCommPortId(), response.getExceptionMessage());
            } else {
                log.info("[modbus][{}]写保持寄存器成功", this.serialPortConfig.getCommPortId());
            }
        } finally {
            ConnectionWebSocket.lock.writeLock().unlock();
            log.info("[lock] write func unlocked. {}", ConnectionWebSocket.lock);

        }
    }

    private Map<Integer, Object> readModbusValues(List<SensorProperty> propertyList) throws ModbusTransportException {
        // 读取传感器数据
        Map<Integer, Object> result = new HashMap<>(propertyList.size());
        for (int i = 0; i < propertyList.size(); i++) {
            byte[] bytes = readHoldingRegisters(this.serialPortConfig.getStartIndex() + propertyList.get(i).getAddress(), propertyList.get(i).getLength() / 2);
            log.debug("read bytes: [{}] [{}] {}", propertyList.get(i).getSlaveId(), this.serialPortConfig.getStartIndex() + propertyList.get(i).getAddress(), ByteBufUtil.hexDump(bytes));
            switch (propertyList.get(i).getAClass().getSimpleName()) {
                case "float":
                    float aFloat = ByteUtils.bytes2Float(bytes);
                    result.put(propertyList.get(i).getAddress(), propertyList.get(i).getOutputConvertFunc().apply(aFloat));
                    break;
                case "int":
                    int anInt;
                    if (propertyList.get(i).isUnsigned()) {
                        anInt = Integer.parseUnsignedInt(ByteBufUtil.hexDump(bytes), 16);
                    } else {
                        anInt = Integer.parseInt(ByteBufUtil.hexDump(bytes), 16);
                    }
                    result.put(propertyList.get(i).getAddress(), propertyList.get(i).getOutputConvertFunc().apply(anInt));
                    break;
                case "short":
                    short anShort;
                    if (propertyList.get(i).isUnsigned()) {
                        anInt = Integer.parseUnsignedInt(ByteBufUtil.hexDump(bytes), 16);
                    } else {
                        anInt = Integer.parseInt(ByteBufUtil.hexDump(bytes), 16);
                    }

                    result.put(propertyList.get(i).getAddress(), propertyList.get(i).getOutputConvertFunc().apply(anInt));
                    break;
                default:
                    short[] shorts = new short[propertyList.get(i).getLength() / 2];
                    byte[] tmp = new byte[]{bytes[i * 2], bytes[i * 2 + 1]};
                    if (propertyList.get(i).isUnsigned()) {
                        for (int j = 0; j < shorts.length; j++) {
                            shorts[i] = (short) Integer.parseUnsignedInt(ByteBufUtil.hexDump(tmp), 16);
                        }
                    } else {
                        for (int j = 0; j < shorts.length; j++) {
                            shorts[i] = (short) Integer.parseInt(ByteBufUtil.hexDump(tmp), 16);
                        }
                    }
                    result.put(propertyList.get(i).getAddress(), propertyList.get(i).getOutputConvertFunc().apply(shorts));
                    break;
            }
        }
        log.info("读取串口寄存器数据: {}", JsonUtils.encodeJson(result));
        return result;
    }

    public Map<Integer, Object> readSensorValues() throws ModbusTransportException {
        return readModbusValues(SensorPropertyConfig.sensorPropertyList);
    }

    public Map<Integer, Object> readServoValues() throws ModbusTransportException {
        return readModbusValues(SensorPropertyConfig.servoPropertyList);
    }

    public Map<Integer, Object> readServoWarns() throws ModbusTransportException {
        return readModbusValues(SensorPropertyConfig.servoWarnPropertyList);
    }

    public synchronized void close() {
        if (this.master != null) {
            log.info("[modbus][{}]释放串口连接资源", this.serialPortConfig.getCommPortId());
            this.master.destroy();
        }
    }
}

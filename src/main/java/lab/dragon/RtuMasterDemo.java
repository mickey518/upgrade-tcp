package lab.dragon;

import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import com.serotonin.modbus4j.msg.WriteRegisterRequest;
import com.serotonin.modbus4j.msg.WriteRegisterResponse;
import jssc.SerialPort;
import lab.dragon.common.util.ByteUtils;
import lab.dragon.modbus.SerialPortWrapperImpl;

import java.util.Arrays;

public class RtuMasterDemo {

    public static void main(String[] args) throws Exception{
//        createRtuMaster();

        long summed = ByteUtils.sum(new byte[]{(byte) 0xAA, (byte) 0x13, (byte) 0x09, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0xE8, (byte) 0x03,
                (byte) 0xE8, (byte) 0x08, (byte) 0x16, (byte) 0x16,
                (byte) 0x16, (byte) 0x16, (byte) 0x16, (byte) 0x16,
                (byte) 0x16, (byte) 0x16, (byte) 0x55
        });
        System.out.println("sum: " + summed + "; 0xFF is: " + String.format("%02X", (byte)(0xFF & summed)));
    }

    private static void createRtuMaster() throws Exception{

        // 设置串口参数，串口是COM1，波特率是9600
        SerialPortWrapperImpl wrapper = new SerialPortWrapperImpl("COM4", 115200,
                SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        ModbusFactory modbusFactory = new ModbusFactory();


        ModbusMaster master = modbusFactory.createRtuMaster(wrapper);
        master.init();

        // 从站设备ID是1
        int slaveId = 1;

        // 读取保持寄存器
        readHoldingRegisters(master, slaveId, 816, 4);
        // 将地址为0的保持寄存器数据修改为0
        writeRegister(master, slaveId, 10638, 400);
//        // 再读取保持寄存器
//        readHoldingRegisters(master, slaveId, 0, 3);
    }

    private static void readHoldingRegisters(ModbusMaster master, int slaveId, int start, int len) throws Exception{
        ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(slaveId, start, len);
        ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) master.send(request);
        if (response.isException()){
            System.out.println("读取保持寄存器错误，错误信息是" + response.getExceptionMessage());
        }else {
            System.out.println("读取保持寄存器=" + Arrays.toString(response.getShortData()));
        }
    }

    private static void writeRegister(ModbusMaster master, int slaveId, int offset, int value) throws Exception{
        WriteRegisterRequest request = new WriteRegisterRequest(slaveId, offset, value);
        WriteRegisterResponse response = (WriteRegisterResponse) master.send(request);
        if (response.isException()){
            System.out.println("写保持寄存器错误，错误信息是" + response.getExceptionMessage());
        }else{
            System.out.println("写保持寄存器成功");
        }
    }
}
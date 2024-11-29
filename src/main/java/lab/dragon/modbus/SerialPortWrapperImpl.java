package lab.dragon.modbus;

import com.serotonin.modbus4j.serial.SerialPortWrapper;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;

public class SerialPortWrapperImpl implements SerialPortWrapper {
    private static final Logger log = LoggerFactory.getLogger(SerialPortWrapperImpl.class);
    private final SerialPort port;
    private final int baudRate;
    private final int dataBits;
    private final int stopBits;
    private final int parity;

    public SerialPortWrapperImpl(String commPortId, int baudRate, int dataBits, int stopBits, int parity) throws SerialPortException {

        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;

        port = new SerialPort(commPortId);

    }

    @Override
    public void close() throws Exception {
        port.closePort();
        log.debug("Serial port {} closed", port.getPortName());
    }

    @Override
    public void open() throws SerialPortException {
            port.openPort();
            port.setParams(this.getBaudRate(), this.getDataBits(), this.getStopBits(), this.getParity());
            log.debug("Serial port {} opened", port.getPortName());
    }

    @Override
    public String toString() {
        return "SerialPortWrapperImpl{" +
                "port=" + port.getPortName() +
                ", baudRate=" + baudRate +
                ", dataBits=" + dataBits +
                ", stopBits=" + stopBits +
                ", parity=" + parity +
                '}';
    }

    @Override
    public InputStream getInputStream() {
        return new SerialInputStream(port);
    }

    @Override
    public OutputStream getOutputStream() {
        return new SerialOutputStream(port);
    }


    @Override
    public int getBaudRate() {
        return baudRate;
    }

    @Override
    public int getDataBits() {
        return dataBits;
    }

    @Override
    public int getStopBits() {
        return stopBits;
    }

    @Override
    public int getParity() {
        return parity;
    }
}
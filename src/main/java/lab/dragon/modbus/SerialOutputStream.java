package lab.dragon.modbus;

import jssc.SerialPort;
import jssc.SerialPortException;

import java.io.IOException;
import java.io.OutputStream;

public class SerialOutputStream extends OutputStream {
    SerialPort serialPort;

    public SerialOutputStream(SerialPort sp) {
        serialPort = sp;
    }

    @Override
    public void write(int b) throws IOException {
        try {
            serialPort.writeInt(b);
        } catch (SerialPortException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);

    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        byte[] buffer = new byte[len];
        System.arraycopy(b, off, buffer, 0, len);
        try {
            serialPort.writeBytes(buffer);
        } catch (SerialPortException e) {
            throw new IOException(e);
        }
    }

}
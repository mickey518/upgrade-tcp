package lab.dragon.modbus;

import jssc.SerialPort;

import java.io.IOException;
import java.io.InputStream;

public class SerialInputStream extends InputStream {

    private SerialPort serialPort;
    private int defaultTimeout = 50;

    public SerialInputStream(SerialPort sp) {
        serialPort = sp;
    }

    public void setTimeout(int time) {
        defaultTimeout = time;
    }

    @Override
    public int read() throws IOException {
        return read(defaultTimeout);
    }

    public int read(int timeout) throws IOException {
        byte[] buf = new byte[1];
        try {
            if (timeout > 0) {
                buf = serialPort.readBytes(1, timeout);
            } else {
                buf = serialPort.readBytes(1);
            }
            return buf[0];
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return read(buf, 0, buf.length);
    }

    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {

        if (buf.length < offset + length) {
            length = buf.length - offset;
        }

        int available = this.available();

        if (available > length) {
            available = length;
        }

        try {
            byte[] readBuf = serialPort.readBytes(available);
            System.arraycopy(readBuf, 0, buf, offset, readBuf.length);
            return readBuf.length;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public int blockingRead(byte[] buf) throws IOException {
        return blockingRead(buf, 0, buf.length, defaultTimeout);
    }

    public int blockingRead(byte[] buf, int timeout) throws IOException {
        return blockingRead(buf, 0, buf.length, timeout);
    }

    public int blockingRead(byte[] buf, int offset, int length) throws IOException {
        return blockingRead(buf, offset, length, defaultTimeout);
    }

    public int blockingRead(byte[] buf, int offset, int length, int timeout) throws IOException {
        if (buf.length < offset + length) {
            throw new IOException("Not enough buffer space for serial data");
        }

        if (timeout < 1) {
            return read(buf, offset, length);
        }

        try {
            byte[] readBuf = serialPort.readBytes(length, timeout);
            System.arraycopy(readBuf, 0, buf, offset, length);
            return readBuf.length;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public int available() throws IOException {
        int ret;
        try {
            ret = serialPort.getInputBufferBytesCount();
            if (ret >= 0) {
                return ret;
            }
        } catch (Exception e) {
            //TODO 异常处理
        }
        return 0;
    }
}
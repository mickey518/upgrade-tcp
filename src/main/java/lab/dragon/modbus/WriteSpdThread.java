package lab.dragon.modbus;

import lab.dragon.common.util.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class WriteSpdThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(WriteSpdThread.class);
    private final byte[] idTemp;
    public AtomicInteger spd = new AtomicInteger(0);
    private RtuMasterHelper rtuMasterHelper;
    private int index = 1;
    private final int sleeped = 10;

    public WriteSpdThread(RtuMasterHelper rtuMasterHelper, byte[] idTemp) {
        this.rtuMasterHelper = rtuMasterHelper;
        this.idTemp = idTemp;
    }

    @Override
    public void run() {
        /*
        0     1     2     3    4        5       6          7         8            9       10
        帧头  长度  检验和 模式  id低位    id高位  速度低位    速度高位    母线电压低位  母线电压高位  帧尾
         */
        while (!this.isInterrupted()) {
            if (spd.shortValue() != 0) {

                byte[] bytes = new byte[11];

                bytes[0] = (byte) 0xAA;
                bytes[bytes.length - 1] = (byte) 0x55;
                bytes[1] = (byte) bytes.length;
                bytes[2] = 0;
                bytes[3] = 0;
                bytes[4] = idTemp[0];
                bytes[5] = idTemp[1];
                byte[] bytesLittleEndian = ByteUtils.short2BytesLittleEndian(-spd.shortValue());
                System.arraycopy(bytesLittleEndian, 0, bytes, 6, 2);
                bytes[8] = 0;
                bytes[9] = 0;

                try {
                    if (index == 500/sleeped) {
                        rtuMasterHelper.writeCommand(bytes);
                        index = 1;
                    }
                    Thread.sleep(sleeped);
                    index++;
                    bytes[2] = 0;
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}

package lab.dragon.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SerialPortConfig {
    /**
     * 端口名称
     */
    private String commPortId = "COM1";
    /**
     * 波特率
     */
    private Integer baudRate = 115200;
    /**
     * 数据位
     */
    private Integer dataBits = 8;
    /**
     * 停止位
     */
    private Integer stopBits = 1;
    /**
     * 校验位
     */
    private Integer parity = 0;

    private Integer flowControlIn = 0;
    private Integer flowControlOut = 0;

    private Integer slaveId = 1;
    private Integer startIndex = 0;

    public SerialPortConfig(String commPortId) {
        this.commPortId = commPortId;
    }
}

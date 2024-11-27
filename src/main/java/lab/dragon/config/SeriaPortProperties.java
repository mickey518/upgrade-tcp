package lab.dragon.config;


import java.util.List;


public class SeriaPortProperties {
    private List<SerialPortConfig> serialPortConfigList;

    public List<SerialPortConfig> getSerialPortConfigList() {
        return serialPortConfigList;
    }

    public void setSerialPortConfigList(List<SerialPortConfig> serialPortConfigList) {
        this.serialPortConfigList = serialPortConfigList;
    }
}

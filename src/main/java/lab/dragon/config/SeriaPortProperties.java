package lab.dragon.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SeriaPortProperties {
    private List<SerialPortConfig> serialPortConfigList;
}

package lab.dragon.api;

import lab.dragon.modbus.ModbusUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("port")
public class ConfigurationController {

    @GetMapping("")
    public List<String> getCommPortIds() {
        return ModbusUtil.getCommPortIds();
    }
}

package lab.dragon;

import lab.dragon.config.ConstantConfiguration;
import lab.dragon.config.SensorPropertyConfig;
import lab.dragon.util.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(Main.class, args);
        SpringContextUtils.setApplicationContext(run);

        SensorPropertyConfig.config();

        configCommPort();
    }

    private static void configCommPort() {
        try {
            // 读取串口配置文件
            Path commPortConfigFile = Paths.get("com-port.txt");
            // 判断文件是否存在
            if (Files.notExists(commPortConfigFile)) {
                String error = "缺少配置文件 [com-port.txt]，需要提供串口配置文件；配置文件中第一个表示伺服控制器串口，第二个表示传感器串口";
                log.error(error);
            }

            byte[] bytes = Files.readAllBytes(commPortConfigFile);
            if (bytes.length == 0) {
                log.error("串口配置文件 [com-port.txt] 为空，使用默认配置");
                ConstantConfiguration.commIds[0] = "COM4";
                ConstantConfiguration.commIds[1] = "COM3";
                ConstantConfiguration.commIds[2] = "COM7";
            } else {
                String commString = new String(bytes);
                log.info("读取串口配置文件: {}", commString);
                String[] split = commString.split(";");
                ConstantConfiguration.commIds[0] = split[0];
                ConstantConfiguration.commIds[1] = split[1];
                ConstantConfiguration.commIds[2] = split[2];
            }
        } catch (IOException e) {
            String error = "串口配置文件 【com-port.txt】 无法打开";
            log.error(error);
        }
    }
}
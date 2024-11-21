package lab.dragon;

import lab.dragon.config.SensorPropertyConfig;
import lab.dragon.util.JsonUtils;
import lab.dragon.util.SpringContextUtils;
import lab.dragon.util.ThreadPoolUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@SpringBootApplication
@ComponentScan("lab.dragon")
public class Main {

    public static final ScheduledExecutorService SCHEDULEDEXECUTOR = ThreadPoolUtil.getScheduledExecutor();
    public static Map<String, String> WARN_MESSAGES = new HashMap<>();
    public static void main(String[] args) {
        System.out.println("Hello world!");
        ConfigurableApplicationContext run = SpringApplication.run(Main.class, args);
        SpringContextUtils.setApplicationContext(run);

        // 加载告警代码含义转换映射表
        try {
            WARN_MESSAGES = JsonUtils.loadFromFile("warn.json", Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        SensorPropertyConfig.config();
    }
}
package lab.dragon;

import lab.dragon.config.SensorPropertyConfig;
import lab.dragon.util.SpringContextUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        System.out.println("Hello world!");
        ConfigurableApplicationContext run = SpringApplication.run(Main.class, args);
        SpringContextUtils.setApplicationContext(run);

        SensorPropertyConfig.config();
    }
}
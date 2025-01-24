package lab.dragon.power.util;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

public class SpringContextUtils {
    public static ConfigurableApplicationContext applicationContext;

    public static void setApplicationContext(ConfigurableApplicationContext applicationContext) {
        SpringContextUtils.applicationContext = applicationContext;
    }

    public static String getProperty(String key) {
        if (applicationContext == null) {
            return "";
        }
        return applicationContext.getEnvironment().getProperty(key);
    }

    public static ConfigurableEnvironment getEnvironment() {
        return applicationContext.getEnvironment();
    }

    public static <T> T getBean(Class<T> c) {
        if (applicationContext != null) {
            return applicationContext.getBean(c);
        }
        return null;
    }
}

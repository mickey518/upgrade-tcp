package lab.dragon.api;

import lab.dragon.util.ThreadPoolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("system")
public class MainController {


    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @GetMapping("shutdown")
    public int shutdown() {
        try {
            Runtime runtime = Runtime.getRuntime();
            String command = "shutdown /s /t 5";
            log.error("[SYSTEM]执行关机命令");
            runtime.exec(command);
            runtime.runFinalization();
            ThreadPoolUtil.execute(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.exit(0);
            });
            return 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

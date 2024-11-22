package lab.dragon.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("system")
@Slf4j
public class MainController {

    @GetMapping("shutdown")
    public void shutdown() {
        try {
            Runtime runtime = Runtime.getRuntime();
            String command = "shutdown /s /t 60";
            runtime.exec(command);
            runtime.runFinalization();
            System.exit(0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

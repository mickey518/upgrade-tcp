package lab.dragon.api;

import lab.dragon.common.util.SystemUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("system")
public class MainController {

    @GetMapping("shutdown")
    public int shutdown() {
        try {
            return SystemUtils.shutdown(5);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

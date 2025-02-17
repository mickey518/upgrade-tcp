package lab.dragon.power;

import com.fazecast.jSerialComm.SerialPort;
import lab.dragon.common.gson.GsonUtils;
import lab.dragon.common.util.SystemUtils;
import lab.dragon.power.handler.com.RtuMasterHelper;
import lab.dragon.power.util.SpringContextUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(Main.class, args);
        SpringContextUtils.setApplicationContext(run);

        // 设置控制台编码
        try {
            Process process = Runtime.getRuntime().exec("cmd /c chcp 936");
            process.waitFor();
            System.out.println("process 测试下中文");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        List<String> commPortIds = new ArrayList<>();

        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            commPortIds.add(port.getSystemPortName());
        }
        System.out.println(GsonUtils.toJson(commPortIds));

        // 自动打开网页
        String url = "http://127.0.0.1:4000/index-com.html";
        SystemUtils.openUrl(url);

//        try {
//            Files.write(Paths.get("1234.txt"), new byte[]{(byte)0x11,(byte)0x22,(byte)0x33,(byte)0x44});
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        try {
//            Files.write(Paths.get("4321.txt"), new byte[]{(byte)0x44,(byte)0x33,(byte)0x22,(byte)0x11});
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }
}
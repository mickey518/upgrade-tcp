package lab.dragon.api;

import lab.dragon.common.util.ByteUtils;
import lab.dragon.common.util.ThreadPoolUtil;
import lab.dragon.config.ConstantConfiguration;
import lab.dragon.modbus.RtuMasterHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * @author mickey.wang
 */
@RestController
@RequestMapping("advanced")
public class AdvancedWebApi {
    private final Logger log = LoggerFactory.getLogger(AdvancedWebApi.class);

    @PostMapping("upgrade")
    public ResponseEntity<String> uploadUpgradeFile(@RequestParam("file") MultipartFile upgradeFile) {
        if (upgradeFile == null || upgradeFile.isEmpty()) {
            return ResponseEntity.badRequest().body("未选择文件");
        }

        try {
            // 获取文件名
            String fileName = StringUtils.cleanPath(Objects.requireNonNull(upgradeFile.getOriginalFilename()));
            log.info("接收到升级文件包，文件名：{}，文件大小：{}，文件类型：{}",fileName, upgradeFile.getSize(), upgradeFile.getContentType());

            RtuMasterHelper master = RtuMasterHelper.createMaster(ConstantConfiguration.commIds[2]);
            ThreadPoolUtil.execute(master::listen);

            // 下发开始升级指令 AA  长度  校验和 模式  程序长度低字节    程序长度高字节 帧尾
            byte[] bytes = ByteUtils.short2BytesLittleEndian((int) upgradeFile.getSize());
            byte[] command = new byte[9];
            command[0] = (byte) 0xAA;   // 帧头
            command[1] = (byte) command.length;   // 长度
            command[2] = (byte) 0x00;   // 校验和
            command[3] = (byte) 0x05;   // 模式
            command[4] = (byte) 0x71;   //
            command[5] = (byte) 0xC0;   //
            command[6] = bytes[0];   //
            command[7] = bytes[1];
            command[8] = (byte) 0x55;

            master.writeCommand(command);
            // 下发升级包 (int)(upgradeFile.getSize() / 128) +
            for (int i = 0; i < 1; i++) {
                int address = i * 128;
                byte[] bytes1 = ByteUtils.short2BytesLittleEndian(address);
//                while (true) {
//                    if (master.isContinued.get()) {
                        byte[] filePart = new byte[128+9];
                        filePart[0] = (byte) 0xAA;   // 帧头
                        filePart[1] = (byte) filePart.length;   // 长度
                        filePart[2] = (byte) 0x00;   // 校验和
                        filePart[3] = (byte) 0x06;   // 模式
                        filePart[4] = (byte) 0x71;   //
                        filePart[5] = (byte) 0xC0;   //
                        filePart[6] = bytes1[0];   //
                        filePart[7] = bytes1[1];
                        filePart[filePart.length - 1] = (byte) 0x55;

                        System.arraycopy(upgradeFile.getBytes(), address, filePart, 8, 128);

                        master.writeCommand(filePart);
//                        break;
//                    }
//                }
            }

            // 计算文件CRC32
            long crc32 = ByteUtils.calculateCRC32(upgradeFile.getBytes());
            byte[] bytes1 = ByteUtils.int2BytesLittleEndian((int) crc32);
            // 完成升级包下发
//            while (true) {
//                if (master.isContinued.get()) {
                    byte[] finBytes = new byte[11];
                    finBytes[0] = (byte) 0xAA;   // 帧头
                    finBytes[1] = (byte) finBytes.length;   // 长度
                    finBytes[2] = (byte) 0x00;   // 校验和
                    finBytes[3] = (byte) 0x07;   // 模式
                    finBytes[4] = (byte) 0x71;   //
                    finBytes[5] = (byte) 0xC0;   //
                    finBytes[6] = bytes1[0];   //
                    finBytes[7] = bytes1[1];
                    finBytes[8] = bytes1[2];
                    finBytes[9] = bytes1[3];
                    finBytes[10] = (byte) 0x55;

                    master.writeCommand(finBytes);
//                    break;
//                }
//            }

            return ResponseEntity.ok("上传成功");
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            return ResponseEntity.status(500).body("{\"message\": \"文件上传失败: " + e.getMessage() + "\"}");
        }
    }
}

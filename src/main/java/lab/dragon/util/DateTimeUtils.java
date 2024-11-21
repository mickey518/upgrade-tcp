package lab.dragon.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateTimeUtils {

    public static String generateFileName(String prefix, String suffix) {
        // 获取当前日期并格式化
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String dateStr = dateFormat.format(new Date());

        // 拼接文件名
        return prefix + dateStr + suffix;
    }
}

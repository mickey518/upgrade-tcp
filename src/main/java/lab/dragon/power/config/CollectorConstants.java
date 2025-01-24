package lab.dragon.power.config;

/**
 * 常量值
 *
 * @author mickey.wang
 */
public class CollectorConstants {


    /**
     * 上行数据协议允许最小长度
     */
    public static final int DATA_MIN_RECEIVED_LENGTH = 5;
    /**
     * 上行数据允许的最大长度
     */
    public static final int DATA_MAX_FRAME_LENGTH = 128;
    /**
     * 长度字段所在位置的偏移量
     */
    public static final int DATA_LENGTH_OFFSET = 1;
    /**
     * 长度信息占据的字节数
     */
    public static final int DATA_LENGTH_FIELD_LENGTH = 4;
    /**
     * data 数据的偏移量
     */
    public static final int DATA_LENGTH_ADJUSTMENT = -3;
    /**
     * 要删除的头部帧字节数
     */
    public static final int DATA_INITIAL_BYTES_TO_STRIP = 1;
    /**
     * 帧头内容
     */
    public static final byte DATA_FRAME_UP_HEADER_BYTE = (byte) 0xA5;

    private CollectorConstants() {
        throw new IllegalStateException("Cannot create an utility class instance");
    }

}

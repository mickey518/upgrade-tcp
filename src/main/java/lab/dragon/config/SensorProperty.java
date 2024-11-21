package lab.dragon.config;

import lombok.Builder;
import lombok.Getter;

import java.util.function.Function;

/**
 * 传感器字段属性
 */
@Builder
@Getter
public class SensorProperty {
    /**
     * 通信地址
     */
    private int slaveId;
    /**
     * 寄存器的地址
     */
    private int address;
    /**
     * 要读取的字段长度
     */
    private int length;
    /**
     * 字段类型
     */
    private Class<?> aClass;
    /**
     * 有无符号位
     */
    private boolean unsigned;
    /**
     * 输出转换公式
     */
    private Function<Object, Object> outputConvertFunc;
}

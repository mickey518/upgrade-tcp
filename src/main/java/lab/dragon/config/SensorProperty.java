package lab.dragon.config;

import java.util.function.Function;

/**
 * 传感器字段属性
 */
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



    private SensorProperty(SensorProperty.Builder builder) {
        this.slaveId = builder.slaveId;
        this.address = builder.address;
        this.length = builder.length;
        this.aClass = builder.aClass;
        this.unsigned = builder.unsigned;
        this.outputConvertFunc = builder.outputConvertFunc;
    }

    public static class Builder {
        private int slaveId;
        private int address;
        private int length;
        private Class<?> aClass;
        private boolean unsigned;
        private Function<Object, Object> outputConvertFunc;

        public SensorProperty.Builder slaveId(int slaveId) {
            this.slaveId = slaveId;
            return this;
        }
        public SensorProperty.Builder address(int address) {
            this.address = address;
            return this;
        }

        public SensorProperty.Builder length(int length) {
            this.length = length;
            return this;
        }

        public SensorProperty.Builder aClass(Class<?> aClass) {
            this.aClass = aClass;
            return this;
        }

        public SensorProperty.Builder unsigned(boolean unsigned) {
            this.unsigned = unsigned;
            return this;
        }

        public SensorProperty.Builder outputConvertFunc(Function<Object, Object> outputConvertFunc) {
            this.outputConvertFunc = outputConvertFunc;
            return this;
        }

        public SensorProperty build() {
            return new SensorProperty(this);
        }
    }

    public static SensorProperty.Builder builder() {
        return new SensorProperty.Builder();
    }

    public int getSlaveId() {
        return slaveId;
    }

    public int getAddress() {
        return address;
    }

    public int getLength() {
        return length;
    }

    public Class<?> getAClass() {
        return aClass;
    }

    public boolean isUnsigned() {
        return unsigned;
    }

    public Function<Object, Object> getOutputConvertFunc() {
        return outputConvertFunc;
    }
}

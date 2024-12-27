package lab.dragon.config;

import java.util.ArrayList;
import java.util.List;

public class SensorPropertyConfig {

    public static List<SensorProperty> sensorPropertyList = new ArrayList<>();

    public static List<SensorProperty> servoPropertyList = new ArrayList<>();

    public static List<SensorProperty> servoWarnPropertyList = new ArrayList<>();

    public static void config() {
        configSensor();
        configServo();
        configServoWarn();
    }

    private static void configServoWarn() {
        servoWarnPropertyList.add(SensorProperty.builder().slaveId(1)
                .address(2000).length(20).aClass(short[].class).unsigned(true)
                .outputConvertFunc(x -> x).build());
    }

    private static void configServo() {
        // 正向转速限幅
        servoPropertyList.add(SensorProperty.builder().slaveId(1)
                .address(602).length(2).aClass(short.class).unsigned(true)

                .outputConvertFunc(x -> x).build());
        // 负向转速限幅
        servoPropertyList.add(SensorProperty.builder().slaveId(1)
                .address(604).length(2).aClass(short.class).unsigned(true)
                .outputConvertFunc(x -> x).build());
        // JOG速度
        servoPropertyList.add(SensorProperty.builder().slaveId(1)
                .address(638).length(2).aClass(short.class).unsigned(true)
                .outputConvertFunc(x -> x).build());
        // 正转转矩内部限制
        servoPropertyList.add(SensorProperty.builder().slaveId(1)
                .address(816).length(2).aClass(short.class).unsigned(true)
                .outputConvertFunc(x -> x).build());
        // 反转转矩内部限制
        servoPropertyList.add(SensorProperty.builder().slaveId(1)
                .address(818).length(2).aClass(short.class).unsigned(true)
                .outputConvertFunc(x -> x).build());
        // 辅助操作
        servoPropertyList.add(SensorProperty.builder().slaveId(1)
                .address(3004).length(2).aClass(short.class).unsigned(true)
                .outputConvertFunc(x -> x).build());
    }

    private static void configSensor() {
        //        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0).length(4).aClass(float.class)
//                .outputConvertFunc(x -> x).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(2).length(4).aClass(float.class)
//                .outputConvertFunc(x -> x).build());
        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
                .address(4).length(4).aClass(int.class).unsigned(true)
                .outputConvertFunc(x -> (int) x / 1000.0f).build());
        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
                .address(6).length(4).aClass(int.class).unsigned(false)
                .outputConvertFunc(x -> x).build());

//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x18).length(4).aClass(float.class)
//                .outputConvertFunc(x -> x).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x1A).length(4).aClass(int.class).unsigned(true)
//                .outputConvertFunc(x -> (int) x / 1000.0f).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x1C).length(4).aClass(float.class)
//                .outputConvertFunc(x -> x).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x1E).length(4).aClass(int.class).unsigned(true)
//                .outputConvertFunc(x -> (int) x / 1000.0f).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x54).length(2).aClass(short.class).unsigned(true)
//                .outputConvertFunc(x -> x).build());
//
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x103).length(2).aClass(short.class).unsigned(true)
//                .outputConvertFunc(x -> x).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x104).length(4).aClass(float.class)
//                .outputConvertFunc(x -> x).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x106).length(4).aClass(float.class)
//                .outputConvertFunc(x -> x).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x108).length(4).aClass(float.class)
//                .outputConvertFunc(x -> x).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x10A).length(4).aClass(float.class)
//                .outputConvertFunc(x -> x).build());
//
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x15A).length(2).aClass(short.class).unsigned(true)
//                .outputConvertFunc(x -> x).build());
//
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0x201).length(2).aClass(short.class).unsigned(true)
//                .outputConvertFunc(x -> x).build());
//        sensorPropertyList.add(SensorProperty.builder().slaveId(2)
//                .address(0xF004).length(4).aClass(float.class)
//                .outputConvertFunc(x -> x).build());
    }
}

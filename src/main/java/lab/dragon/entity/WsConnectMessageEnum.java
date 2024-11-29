package lab.dragon.entity;

/**
 * @author mickey.wang
 */
public enum WsConnectMessageEnum {

    write("write"),
    writeBatt("write-batt"),
    result("result"),
    resultSensor("result-sensor"),
    resultBatt("result-batt"),
    savelog("savelog"),
    warn("warn"),
    error("error");


    private final String type;

    WsConnectMessageEnum(String type) {
        this.type = type;
    }
}

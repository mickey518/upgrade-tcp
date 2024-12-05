package lab.dragon.entity;

import com.google.gson.annotations.SerializedName;

/**
 * @author mickey.wang
 */
public enum WsConnectMessageEnum {
    @SerializedName("read-arameter")
    readParameter,
    @SerializedName("write-arameter")
    writeParameter,
    @SerializedName("write")
    write,
    @SerializedName("write-batt")
    writeBatt,
    @SerializedName("write-mode")
    writeMode,
    @SerializedName("result")
    result,
    @SerializedName("result-sensor")
    resultSensor,
    @SerializedName("result-batt")
    resultBatt,
    @SerializedName("savelog")
    savelog,
    @SerializedName("warn")
    warn,
    @SerializedName("error")
    error,
    @SerializedName("upgrade")
    upgrade
}

package lab.dragon.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class WsConnectMessage {
    private String type;

    private String json;


}

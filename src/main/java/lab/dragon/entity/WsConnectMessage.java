package lab.dragon.entity;

public class WsConnectMessage {
    private WsConnectMessageEnum type;

    private String json;

    private WsConnectMessage(Builder builder) {
        this.type = builder.type;
        this.json = builder.json;
    }

    public static class Builder {
        private WsConnectMessageEnum type;

        private String json;

        public Builder type(WsConnectMessageEnum type) {
            this.type = type;
            return this;
        }

        public Builder json(String json) {
            this.json = json;
            return this;
        }

        public WsConnectMessage build() {
            return new WsConnectMessage(this);
        }
    }

    public static WsConnectMessage.Builder builder() {
        return new WsConnectMessage.Builder();
    }

    public WsConnectMessageEnum getType() {
        return type;
    }

    public String getJson() {
        return json;
    }

}

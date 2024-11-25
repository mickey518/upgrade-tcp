package lab.dragon.entity;

public class WsConnectMessage {
    private String type;

    private String json;

    private WsConnectMessage(Builder builder) {
        this.type = builder.type;
        this.json = builder.json;
    }

    public static class Builder {
        private String type;

        private String json;

        public Builder type(String type) {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }
}

package lab.dragon.util.gson;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在反序列化 JSON 时，Gson 默认将数字解析为 Double 类型。
 * 这通常发生在 Map<String, Object> 这样的结构中，因为 Gson 在处理不确定的值类型时会默认选择 Double。
 */
public class MapDeserializer implements JsonDeserializer<Map<String, Object>> {
    @Override
    public Map<String, Object> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        Map<String, Object> map = new HashMap<>();
        JsonObject jsonObject = json.getAsJsonObject();
        
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            JsonElement value = entry.getValue();
            
            if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    // 判断是否为整数（例如没有小数点）
                    if (primitive.getAsString().contains(".")) {
                        map.put(entry.getKey(), primitive.getAsDouble());
                    } else {
                        map.put(entry.getKey(), primitive.getAsInt());
                    }
                } else if (primitive.isBoolean()) {
                    map.put(entry.getKey(), primitive.getAsBoolean());
                } else {
                    map.put(entry.getKey(), primitive.getAsString());
                }
            } else if (value.isJsonObject()) {
                map.put(entry.getKey(), context.deserialize(value, Map.class));
            } else if (value.isJsonArray()) {
                map.put(entry.getKey(), context.deserialize(value, List.class));
            } else {
                map.put(entry.getKey(), null);
            }
        }
        
        return map;
    }
}

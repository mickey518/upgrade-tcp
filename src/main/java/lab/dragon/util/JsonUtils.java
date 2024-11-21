package lab.dragon.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import lab.dragon.util.gson.GsonIgnore;
import lab.dragon.util.gson.MapDeserializer;
import lab.dragon.util.gson.ZoneIdAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * JSON serializer
 *
 * @author wangmeng
 */
public class JsonUtils {
    private JsonUtils() {
        throw new IllegalStateException("Cannot create an utility class instance");
    }

    private static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(Map.class, new MapDeserializer())
            // 修复JAVA17下序列化出错问题
            .registerTypeHierarchyAdapter(ZoneId.class, new ZoneIdAdapter())
            .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
            .setLongSerializationPolicy(LongSerializationPolicy.STRING)
            .serializeSpecialFloatingPointValues()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                    // 包含这个注解的，直接忽略
                    return fieldAttributes.getAnnotation(GsonIgnore.class) != null;
                }

                @Override
                public boolean shouldSkipClass(Class<?> aClass) {
                    return aClass.isAnnotationPresent(GsonIgnore.class);
                }
            })

            .create();
    private static final Gson PRETTY_GSON = GSON.newBuilder().setPrettyPrinting()
            .serializeNulls()
            .create();

    public static <T> T decodeJson(String jsonStr, Class<T> objClass) {
        return GSON.fromJson(jsonStr, objClass);
    }

    public static <T> Collection<T> decodeJsonToCollection(String jsonStr, Class<T> objClass) {
        return GSON.fromJson(jsonStr, TypeToken.getParameterized(Collection.class, objClass).getType());
    }

    /**
     * serialize object to json string.
     * date format yyyy-MM-dd HH:mm:ss.SSS
     * serialize nulls
     *
     * @param jsonObject object
     * @param <T>        T extend Object
     * @return json string
     */
    public static <T> String encodeJson(T jsonObject) {
        return GSON.toJson(jsonObject);
    }

    /**
     * serialize object to json string.
     * date format yyyy-MM-dd HH:mm:ss.SSS
     * serialize nulls
     *
     * @param jsonObject object
     * @param <T>        T extend Object
     * @return json string
     */
    public static <T> String encodeJson(T jsonObject, Class<?> clazz) {
        return GSON.toJson(jsonObject, clazz);
    }

    /**
     * serialize object to pretty json string.
     * date format yyyy-MM-dd HH:mm:ss.SSS
     * serialize nulls
     * pretty format
     *
     * @param jsonObject object.
     * @param <T>        T extend Object.
     * @return pretty json string.
     */
    public static <T> String encodePrettyJson(T jsonObject) {
        return PRETTY_GSON.toJson(jsonObject);
    }

    /**
     * 将文件里的字符串序列化为泛型类
     * @param fileName 文件名
     * @param tClass 泛型类
     * @param <T> 序列化后的实体对象
     * @return 序列化后的实体对象
     * @throws IOException IOException
     */
    public static <T> T loadFromFile(String fileName, Class<T> tClass) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(fileName));
        String content = new String(bytes, StandardCharsets.UTF_8);
        return GSON.fromJson(content, tClass);
    }

    public static <T> List<T> loadListFromFile(String fileName, Class<T> tClass) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(fileName));
        String content = new String(bytes, StandardCharsets.UTF_8);
        return GSON.fromJson(content, TypeToken.getParameterized(List.class, tClass).getType());
    }

    /**
     * 将对象以json格式写入到文件中
     * @param object 需要写入的object对象
     * @param fileName 需要保存到的文件位置
     * @throws IOException IOException
     */
    public static void writeToFile(Object object, String fileName) throws IOException {
        Files.write(Paths.get(fileName), PRETTY_GSON.toJson(object).getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void writeToFile(Object object, Path file) throws IOException {
        Files.write(file, PRETTY_GSON.toJson(object).getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void writeToFile(Object object, String fileName, OpenOption openOption) throws IOException {
        Files.write(Paths.get(fileName), PRETTY_GSON.toJson(object).getBytes(StandardCharsets.UTF_8), openOption);
    }
}

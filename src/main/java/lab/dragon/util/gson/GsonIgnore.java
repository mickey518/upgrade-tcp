package lab.dragon.util.gson;

import java.lang.annotation.*;

/**
 * gson 序列化时忽略字段
 * @author mickey_wang
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GsonIgnore {
}

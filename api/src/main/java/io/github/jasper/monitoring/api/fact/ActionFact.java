package io.github.jasper.monitoring.api.fact;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法参数到 Fact 的显式绑定注解。
 * <p>
 * 用途：
 * 让框架在调用点安全提取业务参数并写入监测事实。
 * 例如把 exportRequest.rowCount 绑定为 BuiltInFacts.DataCount。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ActionFact {

    /** @return 参数绑定要填充的 Fact 类型 token。 */
    Class<? extends FactType<?>> value();

    /**
     * @return 相对参数对象的可选属性路径。
     * 示例：path="profile.departmentId" 表示从参数.profile.departmentId 提取值。
     */
    String path() default "";
}

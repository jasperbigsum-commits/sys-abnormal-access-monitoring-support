package io.github.jasper.monitoring.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个业务动作的统一监测标记。
 *
 * <p>当前 Spring Starter 只会在 Servlet MVC 的 {@code HandlerMethod} 上自动读取本注解：方法注解
 * 覆盖类型注解，并在请求完成后记录事件。它不会自动拦截 Service、消息消费者、定时任务或同类自调用；
 * 这些入口应通过 {@code ActionEventRecorder} 的注册式方法调用埋点。</p>
 *
 * <p>注解只描述稳定的静态元数据。动作编码、事件类别、资源类别和规则标记在所有入口中保持一致；
 * 动态资源 ID、导出数量、时延和业务原因码必须由运行时草稿补充，不能写入注解。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MonitorAction {
    /**
     * 首选的稳定业务动作编码，支持 {@code @MonitorAction("report:export")} 简写。
     *
     * <p>编码由宿主服务端定义，不能来自请求参数；建议采用 {@code 域:动作} 格式，例如
     * {@code report:export}。若同时设置 {@link #action()}，两个值必须一致。</p>
     *
     * @return 稳定业务动作编码；与 {@link #action()} 至少填写一个
     */
    String value() default "";

    /**
     * 具名形式的稳定业务动作编码，保留以兼容既有调用。
     *
     * @return 稳定业务动作编码；与 {@link #value()} 至少填写一个
     */
    String action() default "";

    /**
     * 服务端观察到的通用安全事件类别。
     *
     * <p>默认查询类别，普通读取动作不需要额外枚举；导出、登录失败、授权变更等特殊语义才显式设置。
     * 该属性不是业务动作枚举，业务差异由 {@link #value()} 或 {@link #action()} 表达。</p>
     *
     * @return 通用安全事件类别
     */
    SecurityEventType eventType() default SecurityEventType.QUERY;

    /**
     * 固定的逻辑资源类别，例如 {@code report} 或 {@code account}。
     *
     * @return 静态资源类别；动态资源 ID 应由方法调用草稿设置
     */
    String resourceType() default "";

    /**
     * 用于规则选择的静态标记，例如 {@code sensitive-data} 或 {@code privileged-operation}。
     *
     * <p>记录时每个标记都会映射为 {@code monitor.rule-tag.&lt;tag&gt;=true} 属性；规则可以据此
     * 判断动作特征，但标记本身不会直接触发控制。标记仅可包含字母、数字、点、下划线或连字符，记录时
     * 会规范化为小写，且不能包含敏感数据。</p>
     *
     * @return 不可变的静态规则标记集合
     */
    String[] ruleTags() default {};

    /**
     * Optional host enrichers that contribute dynamic facts for this action invocation.
     *
     * @return framework-neutral enricher implementation types
     */
    Class<? extends MonitorActionEnricher>[] enrichers() default {};
}

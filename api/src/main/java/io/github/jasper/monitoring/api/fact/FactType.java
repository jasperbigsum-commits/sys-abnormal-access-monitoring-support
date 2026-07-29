package io.github.jasper.monitoring.api.fact;

/**
 * Fact 类型 token（强类型标识，不承载数据）。
 * <p>
 * 用途：
 * 1) 在编译期把 Fact 值类型绑定为 T，避免字符串 key + Object 的弱类型错误；<br>
 * 2) 在运行时作为目录注册和规则声明的唯一键。<br>
 * 示例：BuiltInFacts.ResourceId implements FactType&lt;String&gt;。
 */
public interface FactType<T> {
}

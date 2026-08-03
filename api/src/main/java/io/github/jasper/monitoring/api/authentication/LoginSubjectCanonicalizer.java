package io.github.jasper.monitoring.api.authentication;

/**
 * 将宿主登录别名映射为同一规范账号主体的扩展点。
 *
 * <p>实现不得把原始登录名写入日志或持久化存储，返回值只会作为生成 opaque subject
 * 的瞬时输入。同一账号的邮箱、手机号、用户名等别名需要命中同一控制时，宿主应提供
 * 能稳定完成别名归一化的实现。</p>
 */
public interface LoginSubjectCanonicalizer {
    /**
     * 生成用于主体密钥派生的规范账号值。
     *
     * @param subject 本次认证尝试的临时登录主体
     * @return 非空、非空白且不超过 256 个字符的规范账号值
     */
    String canonicalize(LoginSubjectInput subject);
}

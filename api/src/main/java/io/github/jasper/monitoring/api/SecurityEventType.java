package io.github.jasper.monitoring.api;

/**
 * 服务端观测到的安全活动标准化类别。
 *
 * <p>应选择最具体的类别，以保证内置规则和审计报表在不同宿主系统中的含义一致。</p>
 */
public enum SecurityEventType {
    /** 登录成功。 */
    LOGIN_SUCCESS,
    /** 登录失败。 */
    LOGIN_FAILURE,
    /** 多因素认证失败。 */
    MFA_FAILURE,
    /** 登出。 */
    LOGOUT,
    /** 创建会话。 */
    SESSION_CREATED,
    /** 使会话失效。 */
    SESSION_REVOKED,
    /** 检测到并发会话。 */
    SESSION_CONCURRENT,
    /** 访问被允许。 */
    ACCESS_ALLOWED,
    /** 访问被拒绝。 */
    ACCESS_DENIED,
    /** 资源范围授权被拒绝。 */
    RESOURCE_SCOPE_DENIED,
    /** 查询操作。 */
    QUERY,
    /** 查看敏感数据。 */
    VIEW_SENSITIVE,
    /** 导出数据。 */
    EXPORT,
    /** 批量业务操作。 */
    BULK_OPERATION,
    /** 创建业务对象。 */
    CREATE,
    /** 更新业务对象。 */
    UPDATE,
    /** 删除业务对象。 */
    DELETE,
    /** 批量更新。 */
    BATCH_UPDATE,
    /** 授予角色。 */
    ROLE_GRANT,
    /** 撤销角色。 */
    ROLE_REVOKE,
    /** 创建管理员。 */
    ADMIN_CREATE,
    /** 禁用账号。 */
    ACCOUNT_DISABLE,
    /** 修改监测规则。 */
    RULE_CHANGE,
    /** 修改审计配置。 */
    AUDIT_CONFIG_CHANGE,
    /** 修改安全开关。 */
    SECURITY_SWITCH_CHANGE
}

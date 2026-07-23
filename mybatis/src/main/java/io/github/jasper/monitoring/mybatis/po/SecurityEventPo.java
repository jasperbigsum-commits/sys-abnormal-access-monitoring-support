package io.github.jasper.monitoring.mybatis.po;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Persistent representation of one {@code security_event} row. */
@Getter
@Setter
public final class SecurityEventPo {
    /** 事件唯一标识。 */
    private String eventId;
    /** 事件来源系统标识。 */
    private String systemId;
    /** 服务端记录的安全事件类型。 */
    private SecurityEventType eventType;
    /** 事件实际发生的时间。 */
    private Instant occurredAt;
    /** 监测组件接收事件的时间。 */
    private Instant receivedAt;
    /** 服务端确认的用户标识。 */
    private String userId;
    /** 用户账号类型。 */
    private AccountType accountType;
    /** 请求来源 IP 地址。 */
    private String sourceIp;
    /** 设备标识的哈希值。 */
    private String deviceIdHash;
    /** 会话标识的哈希值。 */
    private String sessionIdHash;
    /** 请求关联标识。 */
    private String requestId;
    /** 分布式链路追踪标识。 */
    private String traceId;
    /** 被监测的操作标识。 */
    private String action;
    /** 被监测操作的执行结果。 */
    private SecurityEventResult result;
    /** 操作结果的原因码。 */
    private String reasonCode;
    /** 被访问资源的类型。 */
    private String resourceType;
    /** 被访问资源的标识。 */
    private String resourceId;
    /** 事件所属的组织或租户范围。 */
    private String orgScope;
    /** 本次操作涉及的数据数量。 */
    private long dataCount;
    /** 本次操作的处理耗时，单位为毫秒。 */
    private long latencyMs;
}

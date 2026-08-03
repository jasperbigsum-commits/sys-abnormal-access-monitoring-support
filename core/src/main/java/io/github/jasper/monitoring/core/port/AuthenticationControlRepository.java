package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.ControlCommand;
import java.time.Instant;
import java.util.List;

/**
 * 认证预检使用的活动控制只读查询边界。
 *
 * <p>实现必须按系统、主体和查询时刻过滤，只返回仍处于有效期内的控制命令。</p>
 */
public interface AuthenticationControlRepository {
    /**
     * 查询指定主体当前生效的认证补充控制。
     *
     * @param systemId 控制所属的稳定系统标识
     * @param subject opaque 登录主体或带 {@code ip:} 前缀的来源 IP 主体
     * @param at 判定控制是否生效的时刻
     * @return 活动控制命令列表；没有命中时返回空列表，不返回 {@code null}
     */
    List<ControlCommand> findActive(String systemId, String subject, Instant at);
}

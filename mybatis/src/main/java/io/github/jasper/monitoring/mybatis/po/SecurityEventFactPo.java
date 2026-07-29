package io.github.jasper.monitoring.mybatis.po;

import io.github.jasper.monitoring.api.fact.FactSource;
import lombok.Getter;
import lombok.Setter;

/** 安全事件事实的持久化字段模型；不应保存未脱敏的敏感原文。 */
@Getter
@Setter
public final class SecurityEventFactPo {
    private String eventId;
    private String factKey;
    private String valueType;
    private String valueText;
    private FactSource sourceType;
}

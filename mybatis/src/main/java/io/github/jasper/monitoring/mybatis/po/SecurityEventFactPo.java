package io.github.jasper.monitoring.mybatis.po;

import io.github.jasper.monitoring.api.fact.FactSource;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class SecurityEventFactPo {
    private String eventId;
    private String factKey;
    private String valueType;
    private String valueText;
    private FactSource sourceType;
}

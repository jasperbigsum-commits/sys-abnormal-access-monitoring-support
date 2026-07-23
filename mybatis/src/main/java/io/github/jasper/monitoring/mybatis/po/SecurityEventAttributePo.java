package io.github.jasper.monitoring.mybatis.po;

import lombok.Getter;
import lombok.Setter;

/** Persistent representation of one {@code security_event_attribute} row. */
@Getter
@Setter
public final class SecurityEventAttributePo {
    /** 受控扩展属性的键。 */
    private String attributeKey;
    /** 受控扩展属性的值。 */
    private String attributeValue;
}

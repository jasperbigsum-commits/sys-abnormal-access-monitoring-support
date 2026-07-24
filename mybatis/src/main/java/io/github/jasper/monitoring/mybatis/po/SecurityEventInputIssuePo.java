package io.github.jasper.monitoring.mybatis.po;

import lombok.Getter;
import lombok.Setter;

/** Persistent representation of one {@code security_event_input_issue} row. */
@Getter
@Setter
public final class SecurityEventInputIssuePo {
    /** 事件唯一标识。 */
    private String eventId;
    /** 事件内稳定问题序号。 */
    private int issueIndex;
    /** 受影响规则的稳定标识。 */
    private String ruleId;
    /** 受影响事实的稳定名称。 */
    private String factName;
    /** 受控输入质量问题码。 */
    private String issueCode;
    /** 受控事实来源类别。 */
    private String sourceType;
}

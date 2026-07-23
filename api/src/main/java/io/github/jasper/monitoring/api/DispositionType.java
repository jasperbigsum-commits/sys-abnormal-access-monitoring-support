package io.github.jasper.monitoring.api;

/** 告警生命周期决策对应的不可变审计记录类型。 */
public enum DispositionType {
    /** 操作人员确认已接手告警。 */
    ACKNOWLEDGED,
    /** 已开始调查或处置。 */
    IN_PROGRESS,
    /** 告警已处置完成。 */
    CLOSED,
    /** 告警被认定为误报。 */
    FALSE_POSITIVE
}

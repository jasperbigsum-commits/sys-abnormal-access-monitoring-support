package io.github.jasper.monitoring.api;

/**
 * 安全告警当前所处的生命周期状态。
 *
 * <p>为保证可审计性，状态变化应通过只追加的处置记录保存。</p>
 */
public enum AlertStatus {
    /** 尚未完成研判。 */
    NEW,
    /** 操作人员已确认接手研判。 */
    ACKNOWLEDGED,
    /** 正在调查或处置。 */
    IN_PROGRESS,
    /** 告警已处置完成。 */
    CLOSED,
    /** 已复核并确认不是安全事件。 */
    FALSE_POSITIVE;

    /**
     * 判断告警是否仍需运维或安全人员关注。
     *
     * @return 当告警未关闭且未标记为误报时返回 {@code true}
     */
    public boolean isOpen() {
        return this != CLOSED && this != FALSE_POSITIVE;
    }
}

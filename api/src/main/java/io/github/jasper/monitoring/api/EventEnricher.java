package io.github.jasper.monitoring.api;

/**
 * 为安全事件补充经批准的非敏感字段的可选宿主扩展点。
 *
 * <p>实现必须保持原事件的安全语义，不得加入凭据、浏览器会话数据（Cookie）、令牌或原始敏感载荷。</p>
 */
public interface EventEnricher {
    /**
     * 在监测前为事件补充宿主系统批准的上下文。
     *
     * @param draft 集成适配器创建且已清洗的事件草稿
     * @param request 可信请求事实
     * @param identity 由宿主后端解析的身份
     * @return 需持久化和评估的事件草稿，不能为 {@code null}
     */
    SecurityEventDraft enrich(SecurityEventDraft draft, MonitoringRequestContext request, IdentityContext identity);
}

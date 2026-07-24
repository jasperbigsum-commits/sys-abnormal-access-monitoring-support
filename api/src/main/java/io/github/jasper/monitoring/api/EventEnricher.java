package io.github.jasper.monitoring.api;

/**
 * 为安全事件补充经批准的非敏感字段的可选宿主扩展点。
 *
 * <p>实现只能补充已审核的业务摘要，例如资源类型/标识、组织范围、数据量、服务端测得时延、
 * 稳定原因码和非敏感扩展属性。它必须保留适配器已经确定的事件类别、动作、结果、来源 IP、
 * 请求/链路标识、身份、会话散列和服务端时间；不得加入凭据、浏览器会话数据（Cookie）、令牌、
 * 原始请求/响应体或原始敏感载荷。</p>
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

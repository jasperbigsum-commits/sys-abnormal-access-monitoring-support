package io.github.jasper.monitoring.audit.spring3.monitoring;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.ActionFact;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用于对比显式埋点和注解埋点的 Spring3 验收 Controller。
 *
 * <p>本类故意提供六个路由：其中五个展示不同的业务采集或结果处理方式，另一个是“只有上下文、没有业务事件”的反例，方便集成者把示例和自己的业务代码逐一对照：</p>
 * <ol>
 *     <li>{@link #loginFailure()}：显式构造认证失败结果，不依赖 HTTP 响应状态推断业务含义；</li>
 *     <li>{@link #export(AuditExportRequest)}：显式提交服务端选择的资源标识和数据量，
 *     客户端请求体只作为触发入口；</li>
 *     <li>{@link #annotatedQuery()}：只使用 {@code @MonitorAction}，验证无额外 Fact 的固定 MVC 动作；</li>
 *     <li>{@link #annotatedExport(AuditExportRequest)}：使用 {@code @ActionFact} 从受限参数路径提取
 *     {@code data_count}，验证方法参数事实绑定；</li>
 *     <li>{@link #annotatedExportDenied(AuditExportRequest)}：通过 {@code 403} 响应验证注解切面
 *     对成功、拒绝和异常结果的分类。</li>
 * </ol>
 *
 * <p>{@link #contextOnly()} 是反例：它只读取请求上下文，不声明 Action，也不调用
 * {@code MonitoringService}，因此不应产生业务事件。内置 Action 的定义、Fact 的校验和编码、
 * 规则评估、告警持久化以及控制编排由组件完成；集成者仍需在真实业务决策点选择入口并提供可信 Fact。</p>
 */
@RestController
@RequestMapping("/audit")
public class MonitoringFixtureController {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private final MonitoringService monitoring;
    private final MonitoringContextAccessor contexts;

    public MonitoringFixtureController(MonitoringService monitoring, MonitoringContextAccessor contexts) {
        this.monitoring = monitoring;
        this.contexts = contexts;
    }

    /**
     * 显式提交一次登录失败事件。
     *
     * <p>这个方法用于说明认证失败不能只依赖请求上下文自动产生。认证 Service 已经知道失败原因后，
     * 应通过 {@code ActionExecution} 明确传入 Action、身份、请求上下文和失败结果。登录失败内置
     * Action 当前没有强制 Fact，因此这里只需要可信身份、请求上下文和失败原因。</p>
     *
     * <p>验收观察点：响应返回事件 ID；后续 AUTH-01、AUTH-02、AUTH-03 规则可以使用该事件；
     * 事件中的失败原因来自服务端认证分支，而不是请求体字段。</p>
     *
     * @return 新建事件的 ID 和 Action 编码
     */
    @PostMapping("/login-failure")
    public Map<String, Object> loginFailure() {
        return response(monitoring.monitor(ActionExecution.of(BuiltInActions.LoginFailure.class,
            contexts.requestContext(), contexts.identityContext(), ActionOutcome.failure(
                "INVALID_PASSWORD", ActionOutcome.ExceptionClassification.AUTHORIZATION, 0L))));
    }

    /**
     * 显式提交一次导出事件。
     *
     * <p>请求体参数被故意命名为 {@code ignored}，表示本路由只演示埋点入口，不演示真实导出。
     * 资源 ID 和数据量由本类中的服务端常量选择，来源分别记录为默认可信请求/宿主事实和
     * {@link FactSource#HOST_PROVIDER}。真实系统应在导出 Service 完成授权、查询和计数后，
     * 用实际业务结果替换这些值，再调用同一个程序化入口。</p>
     *
     * <p>与 {@link #annotatedExport(AuditExportRequest)} 的区别是：显式入口能提交执行后才知道的
     * 事实，例如最终生成行数、实际影响行数、事务结果和下游返回结果；注解参数提取不应承担这些职责。</p>
     *
     * @param ignored 仅用于触发 HTTP 请求，本示例不把客户端字段当作事实
     * @return 新建事件的 ID 和 Action 编码
     */
    @PostMapping("/export")
    public Map<String, Object> export(@RequestBody AuditExportRequest ignored) {
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, "audit-export-2026")
            .put(BuiltInFacts.DataCount.class, Long.valueOf(SERVER_REPORTED_ROW_COUNT)).build();
        return response(monitoring.monitor(ActionExecution.of(BuiltInActions.ReportExport.class,
            contexts.requestContext(), contexts.identityContext(), ActionOutcome.success(0L),
            facts, FactSource.HOST_PROVIDER)));
    }

    /**
     * 注解式查询动作采集示例。
     *
     * <p><strong>用例编号</strong>：IA-03。</p>
     * <p><strong>验证核心点</strong>：`@MonitorAction` 在成功路径应产出与动作契约一致的事件类型与结果。</p>
     * <p><strong>注意细节</strong>：仅声明动作不等于声明业务事实；事实仍由显式埋点或事实绑定提供。</p>
     *
     * <p>这里不添加 {@code @ActionFact}，所以切面只根据方法执行结果创建查询事件。
     * 该方式适合动作含义固定、无需从参数读取规则输入的 MVC 方法；如果规则需要行数、
     * 资源 ID 或敏感级别，应改用显式入口或增加明确的 Fact 绑定。</p>
     *
     * @return 固定的成功响应
     */
    @GetMapping("/annotated-query")
    @MonitorAction(BuiltInActions.Query.class)
    public Map<String, Object> annotatedQuery() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", "ok");
        return body;
    }

    /**
     * 只读取请求上下文的对照接口。
     *
     * <p>Starter 会为每个请求准备请求 ID、路径、方法、来源 IP 等上下文，但上下文本身不是
     * 业务 Action。该方法不调用 {@code MonitoringService}，用于 IA-02 验收“没有业务动作就没有
     * 业务事件”的边界。</p>
     *
     * @return 当前请求 ID，仅用于确认上下文已建立
     */
    @GetMapping("/context-only")
    public Map<String, Object> contextOnly() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("requestId", contexts.requestContext().getRequestId());
        return body;
    }

    /**
     * 注解动作 + 嵌套参数事实绑定示例。
     *
     * <p><strong>用例编号</strong>：IA-04。</p>
     * <p><strong>验证核心点</strong>：`@ActionFact(path = "report.rows")` 能将嵌套入参映射为强类型 Fact，
     * 并在入库事实中保留 METHOD_PARAMETER 来源。</p>
     * <p><strong>注意细节</strong>：路径解析失败或类型不匹配应在采集阶段显式失败，避免脏事实入库。</p>
     *
     * <p>该方式适合 Fact 已经稳定存在于公开方法参数、且在方法执行前即可安全读取的场景。
     * {@code report.rows} 只示范参数路径解析，不代表可以信任客户端声明的最终行数；真实导出、
     * 批量更新和事务结果仍应在业务 Service 中用 {@code HOST_PROVIDER} 显式提交。</p>
     *
     * @param ignored 请求参数对象；切面从其公开属性路径读取测试用的行数
     * @return 固定的成功响应
     */
    @PostMapping("/annotated-export")
    @MonitorAction(BuiltInActions.SensitiveView.class)
    public Map<String, Object> annotatedExport(
            @RequestBody @ActionFact(value = BuiltInFacts.DataCount.class, path = "report.rows")
            AuditExportRequest ignored) {
        return exportResponse();
    }

    /**
     * 注解式拒绝结果分类示例。
     *
     * <p><strong>用例编号</strong>：IA-03。</p>
     * <p><strong>验证核心点</strong>：当控制器返回 403 时，注解动作应将结果分类为 DENIED，而非 SUCCESS。</p>
     * <p><strong>注意细节</strong>：拒绝分类依赖响应语义，不应由客户端自报字段决定。</p>
     *
     * <p>该方法返回 {@code ResponseEntity} 的 {@code 403}，切面据此把动作结果记为拒绝。
     * 它用于说明注解只能观察方法结果，不能替代资源授权；真实授权仍应在业务副作用前由宿主
     * 的认证/授权逻辑完成。</p>
     *
     * @param ignored 请求参数对象，仅用于保持与导出接口相同的调用形状
     * @return 403 响应，供注解切面分类
     */
    @PostMapping("/annotated-export-denied")
    @MonitorAction(BuiltInActions.Query.class)
    public ResponseEntity<Map<String, Object>> annotatedExportDenied(@RequestBody AuditExportRequest ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exportResponse());
    }

    /** 返回注解示例使用的固定响应，同时避免把生成结果误认为监测事实。 */
    private static Map<String, Object> exportResponse() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rowCount", Long.valueOf(SERVER_REPORTED_ROW_COUNT));
        return body;
    }

    /** 将显式监测结果压缩成测试响应；完整事件保存在组件仓储中供验收查询。 */
    private static Map<String, Object> response(SecurityEventAssembler.AssemblyResult outcome) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("eventId", outcome.getEvent().getEventId());
        body.put("action", outcome.getEvent().getAction());
        return body;
    }
}

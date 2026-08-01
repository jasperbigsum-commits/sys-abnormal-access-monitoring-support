package io.github.jasper.monitoring.audit.spring3.monitoring;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.authentication.AuthenticationMonitor;
import io.github.jasper.monitoring.api.authentication.AuthenticationStage;
import io.github.jasper.monitoring.api.authentication.LoginSubjectInput;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.spring.support.MonitoringRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
 *     <li>{@link #annotatedExport(AuditExportRequest)}：委托普通 Service，在注解作用域内追加服务端
 *     {@code data_count}，验证运行时事实采集；</li>
 *     <li>{@link #annotatedExportDenied(AuditExportRequest)}：通过 {@code 403} 响应验证注解切面
 *     对成功、拒绝和异常结果的分类。</li>
 * </ol>
 *
 * <p>{@link #contextOnly()} 是反例：它只读取请求上下文，不声明 Action，也不调用
 * {@code MonitoringRecorder}，因此不应产生业务事件。内置 Action 的定义、Fact 的校验和编码、
 * 规则评估、告警持久化以及控制编排由组件完成；集成者仍需在真实业务决策点选择入口并提供可信 Fact。</p>
 */
@RestController
@RequestMapping("/audit")
public class MonitoringFixtureController {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private final MonitoringRecorder monitoringRecorder;
    private final MonitoringContextAccessor contexts;
    private final AnnotatedMonitoringService annotatedMonitoring;
    private final AuthenticationMonitor authenticationMonitor;

    public MonitoringFixtureController(MonitoringRecorder monitoringRecorder, MonitoringContextAccessor contexts,
            AnnotatedMonitoringService annotatedMonitoring, AuthenticationMonitor authenticationMonitor) {
        this.monitoringRecorder = monitoringRecorder;
        this.contexts = contexts;
        this.annotatedMonitoring = annotatedMonitoring;
        this.authenticationMonitor = authenticationMonitor;
    }

    /**
     * 显式提交一次登录失败事件。
     *
     * <p>这个方法用于说明认证失败不能只依赖请求上下文自动产生。认证 Service 已经知道失败原因后，
     * 只向 {@code AuthenticationMonitor} 提交登录用户、认证阶段和规范原因码；门面自动使用当前可信
     * 请求上下文，并生成受保护的登录主体 Fact。</p>
     *
     * <p>验收观察点：响应确认记录状态；后续 AUTH-01、AUTH-02、AUTH-03 规则可以使用该事件；
     * 事件中的失败原因来自服务端认证分支，而不是请求体字段。</p>
     *
     * @return 已记录状态和 Action 编码
     */
    @PostMapping("/login-failure")
    public Map<String, Object> loginFailure(@RequestHeader("X-Audit-Principal") String loginUser) {
        authenticationMonitor.recordDenied(new LoginSubjectInput(loginUser, "audit"),
            AuthenticationStage.CREDENTIAL, BuiltInReasonCodes.Authentication.INVALID_CREDENTIAL);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("action", "auth:login");
        result.put("status", "recorded");
        return result;
    }

    /**
     * 显式提交一次导出事件。
     *
     * <p>请求体参数被故意命名为 {@code ignored}，表示本路由只演示埋点入口，不演示真实导出。
     * 资源 ID 和数据量由本类中的服务端常量选择，Recorder 将事实来源记录为
     * {@code HOST_PROVIDER}。真实系统应在导出 Service 完成授权、查询和计数后，
     * 用实际业务结果替换这些值，再调用同一个程序化入口。</p>
     *
     * <p>与 {@link #annotatedExport(AuditExportRequest)} 的区别是：这里直接构造完整执行对象；后者由
     * 注解切面建立调用作用域，Service 只追加执行后才知道的事实。</p>
     *
     * @param ignored 仅用于触发 HTTP 请求，本示例不把客户端字段当作事实
     * @return 新建事件的 ID 和 Action 编码
     */
    @PostMapping("/export")
    public Map<String, Object> export(@RequestBody AuditExportRequest ignored) {
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, "audit-export-2026")
            .put(BuiltInFacts.DataCount.class, Long.valueOf(SERVER_REPORTED_ROW_COUNT)).build();
        return response(monitoringRecorder.record(BuiltInActions.ReportExport.class,
            ActionOutcome.success(0L), facts));
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
     * 业务 Action。该方法不调用 {@code MonitoringRecorder}，用于 IA-02 验收“没有业务动作就没有
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
     * 普通 Service 注解动作 + 运行时事实示例。
     *
     * <p><strong>用例编号</strong>：IA-04。</p>
     * <p><strong>验证核心点</strong>：普通 Service 方法内可追加执行后得到的 Fact，
     * 并在入库事实中保留 HOST_PROVIDER 来源。</p>
     * <p><strong>注意细节</strong>：客户端上报的行数不会覆盖服务端计算结果。</p>
     *
     * <p>Controller 通过 Spring 代理调用带 {@code @MonitorAction} 的 Service。Service 在完成业务计算后
     * 调用 {@code MonitoringFacts.put}，客户端请求中的 {@code report.rows} 不参与事实采集。</p>
     *
     * @param ignored 请求参数对象；客户端行数仅用于证明它不能覆盖服务端事实
     * @return 固定的成功响应
     */
    @PostMapping("/annotated-export")
    public Map<String, Object> annotatedExport(@RequestBody AuditExportRequest ignored) {
        return exportResponse(annotatedMonitoring.export(ignored));
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
        return exportResponse(SERVER_REPORTED_ROW_COUNT);
    }

    private static Map<String, Object> exportResponse(long rowCount) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rowCount", Long.valueOf(rowCount));
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

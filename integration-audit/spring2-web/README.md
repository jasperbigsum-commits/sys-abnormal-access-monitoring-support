# Spring Boot 2 集成审计宿主

这是一个可运行的 Spring Boot 2.7.18 / `javax.servlet` 宿主夹具。它用于证明异常访问监测组件能在真实 HTTP 请求、MyBatis 持久化、Shiro 身份、资源授权、宿主控制动作和管理审计之间形成闭环。

它是**验收参考实现，不是生产脚手架**。H2、固定测试身份、内存计数的通知渠道、演示性验证码/限流/会话撤销逻辑及 `AuditFixture*` 表都必须由目标宿主的真实实现替换。

完整验收矩阵见[集成审计与基础项目验收](../../docs/集成审计与基础项目验收.md)，组件总览见[根 README](../../README.md)。

## 运行环境

| 项目 | 当前夹具配置 | 生产宿主应做什么 |
| --- | --- | --- |
| Spring Boot | 2.7.18 | 保持使用 `sys-abnormal-access-monitoring-spring2-starter` |
| Servlet API | `javax.servlet` | 不要在 Boot 2 项目中引入 `jakarta.servlet.*` |
| JDK | Java 8 兼容 | 使用与宿主 Boot 2 版本兼容的 JDK |
| 数据库 | 内存 H2，MySQL 兼容模式 | 使用生产数据源和受控迁移 |
| 持久化 | MyBatis `SqlSessionFactory` | 提供宿主的 `SqlSessionFactory` |
| 身份和授权 | Shiro + `X-Audit-Principal` 测试过滤器 | 接入已有 SSO、会话或安全框架 |
| 控制和通知 | `@ControlTrigger` 与模拟 `NotificationChannel` | 接入真实验证码、限流、MFA、审批、会话、通知设施 |

## 快速运行与验收

从仓库根目录执行：

```powershell
mvn -pl integration-audit/spring2-web -am test
mvn -pl integration-audit/spring2-web -am spring-boot:run
```

测试会启动真实嵌入式 Web 容器，并使用 H2、MyBatis 和 Shiro 跑完整 HTTP 验收。报告位于：

```text
integration-audit/spring2-web/target/surefire-reports/
```

验证两个 Boot 宿主的编号完整性和对称性：

```powershell
mvn -pl integration-audit verify
```

门禁要求每个宿主各自唯一覆盖 `TC-01` 至 `TC-18`、`IA-01` 至 `IA-12`。编号遗漏、重复或 Boot 2/3 不对称都会失败。

## 当前模拟环境

`src/main/resources/application.yml` 为夹具启用以下配置：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:audit-spring2;MODE=MySQL;DB_CLOSE_DELAY=0
    username: sa
    password:
    driver-class-name: org.h2.Driver

abnormal:
  access:
    monitor:
      system-id: audit-spring2-web
      mode: ENFORCE
      frontend:
        enabled: false
      instrumentation:
        enabled: true
      authentication:
        subject-key: ${MONITORING_AUTH_SUBJECT_KEY}
        control-failure-policy: OBSERVE_ONLY
      trusted-proxies: [127.0.0.1/32, "::1/128"]
```

配置含义与生产替换要求：

| 配置 | 夹具目的 | 生产要求 |
| --- | --- | --- |
| `system-id` | 作为审计数据的稳定分区标识 | 每个宿主服务使用稳定且唯一的值；不要用环境临时名称替代 |
| `mode: ENFORCE` | 验收宿主需验证真实控制动作被调用 | 初次接入必须使用 `OBSERVE`；只有所有控制处理器验收通过后才切换 `ENFORCE` |
| `frontend.enabled: false` | 本夹具不接收浏览器补充信号 | 前端信号仅可作为补充事实，不能替代身份、授权或控制依据 |
| `instrumentation.enabled: true` | 采集 MVC 上的 `@MonitorAction` | 仅覆盖已声明注解的方法；Service、任务和消息消费者仍需程序化埋点 |
| `trusted-proxies` | 将本地测试调用视为可信转发 | 只配置受控网关的固定地址/CIDR；不得信任全网或由客户端决定 IP |
| `authentication.*` | 默认注册认证门面并生成稳定的 opaque subject | 从密钥管理设施注入至少 32 字节随机密钥的 Base64 编码；所有实例保持一致，宿主只传 `login_user` 和 realm |

`Spring2AuditApplication` 启动时依次运行组件的 `db/monitoring-schema.sql` 和夹具的 `db/audit-fixture-schema.sql`，并构造 MyBatis `SqlSessionFactory`。此做法仅便于演示；生产必须把监测 Schema 纳入 Flyway、Liquibase 或既有变更流程，不应在应用启动时重复执行 DDL。

## 验收数据库表归属

启动顺序先加载组件的 `monitoring-schema.sql`，再加载本模块的 `audit-fixture-schema.sql`。两类表共用
H2 数据源只为模拟一次真实集成；它们的**所有权、迁移责任和生产替换方式完全不同**。

| 归属 | 表 | 夹具中的用途 | 生产处理方式 |
| --- | --- | --- | --- |
| 宿主系统 | `audit_account`、`audit_session`、`audit_user_role` | 模拟账号状态、会话和角色关系 | 替换为宿主的身份、会话和权限数据表 |
| 宿主系统 | `audit_report`、`audit_report_row` | 模拟受组织范围保护的报告资源及导出数据 | 替换为宿主真实业务资源与数据表 |
| 宿主系统 | `audit_control_state` | 模拟控制处理器的幂等副作用与 TTL | 替换为验证码、限流、会话或审批设施自己的状态存储 |
| 宿主系统 | `audit_export_ledger` | 模拟导出业务台账，计算当日累计行数 | 替换为宿主导出流水或可核验的业务统计来源 |
| 宿主系统 | `audit_notification_attempt` | 模拟外部通知渠道的调用尝试 | 替换为宿主通知供应商日志或投递观测，不替代组件投递状态 |
| 监测组件内部 | `monitoring_security_event`、`monitoring_rule_observation`、`monitoring_security_event_fact`、`monitoring_security_event_role`、`monitoring_security_event_attribute`、`monitoring_security_event_input_issue` | 事件、规则证据、规范化 Fact、角色、属性与输入质量 | 由组件 MyBatis Schema 和仓储管理，纳入组件数据库迁移 |
| 监测组件内部 | `monitoring_security_rule`、`monitoring_security_alert`、`monitoring_alert_event_link`、`monitoring_alert_disposition`、`monitoring_security_whitelist` | 规则版本、告警、证据关联、处置历史与白名单 | 由组件管理服务维护；宿主不得直接绕过服务修改 |
| 监测组件内部 | `monitoring_control_action`、`monitoring_control_action_attempt`、`monitoring_notification_delivery`、`monitoring_management_audit` | 控制状态机与尝试记录、通知投递状态、管理审计 | 由组件持久状态机与管理服务维护 |

注意事项：

- `audit_control_state` 是宿主处理器的测试替身，**不是**组件内部的 `monitoring_control_action` 或 `monitoring_control_action_attempt`。
- `audit_notification_attempt` 是模拟下游渠道的观测记录，**不是**组件内部的 `monitoring_notification_delivery`。
- 生产环境可将宿主表和组件内部表置于同一数据库或不同数据库/Schema；无论物理部署如何，必须保持迁移责任、访问权限与数据所有权分离。
- 宿主只在真实业务决策点读取自己的业务表并提交可信事实；不得直接插入或更新组件内部表来伪造事件、告警、控制或管理结果。

## 最小接入步骤

### 1. 引入匹配的 Starter 与 MyBatis

通过 BOM 管理版本，只引入 Boot 2 Starter：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.jasperbigsum-commits</groupId>
      <artifactId>sys-abnormal-access-monitoring-bom</artifactId>
      <version>${monitoring.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependency>
  <groupId>io.github.jasperbigsum-commits</groupId>
  <artifactId>sys-abnormal-access-monitoring-spring2-starter</artifactId>
</dependency>
<dependency>
  <groupId>io.github.jasperbigsum-commits</groupId>
  <artifactId>sys-abnormal-access-monitoring-mybatis</artifactId>
</dependency>
```

宿主还必须提供已配置的 `DataSource` 和 `SqlSessionFactory`。监测组件的生产仓储只有 MyBatis 实现；不要以内存仓储替代生产持久化。

### 2. 先以观察模式启动

生产环境初始配置应采用：

```yaml
abnormal:
  access:
    monitor:
      system-id: order-service
      mode: OBSERVE
      instrumentation:
        enabled: true
      trusted-proxies: [10.20.0.0/16]
      notification:
        channel: security-webhook
        retry-enabled: true
        max-attempts: 3
        retry-delay: 1m
        lease-duration: 5m
```

在 `OBSERVE` 下仍会采集事件、评估规则、保存告警和管理审计，但不应把规则输出变成宿主业务副作用。先完成事件字段完整率、误报、告警分派、通知和管理授权的验证，再评审是否切换 `ENFORCE`。

### 3. 从可信安全上下文派生身份

夹具在 `AuditShiroRbacConfiguration` 中提供 `IdentityContextProvider`。它只从已认证的 Shiro `Subject` 构造 `IdentityContext`；测试请求使用 `X-Audit-Principal` 仅用于模拟该认证过程。

目标宿主应从已经验证的 JWT、SSO、会话或安全上下文中提供用户、账号类型、角色、设备/会话哈希等信息。不得从请求体、查询参数、前端 header 或客户端上报的组织字段直接构造身份。

### 4. 在真实资源决策点执行授权

夹具的 `AuditReportAuthorizationInterceptor` 在报告 Controller 执行前调用 `ResourceAccessGuard`，并且：

1. 先加载目标资源及其组织范围。
2. 使用服务端派生身份与 `ResourceScopeAuthorizer` 判断权限。
3. 跨组织时返回 404，不泄露资源存在性；其他拒绝返回 403。
4. 只有授权通过时才把报告对象放入请求属性供后续导出使用。

目标宿主应把同样的模式放在 Controller、Service 或网关之后的**真实授权决策点**。仅记录请求上下文不会自动产生登录、查询、导出或权限变更等业务事件。

### 5. 在业务事实产生处提交强类型事件

夹具的 `ReportExportService` 使用 `@MonitorAction` 建立动作作用域，在服务端完成数据准备后提交资源 ID、行数和敏感级别，并在生成文件前同步决策：

```java
MonitoringFacts.put(BuiltInFacts.ResourceId.class, reportId);
MonitoringFacts.put(BuiltInFacts.DataCount.class, Long.valueOf(rows));
MonitoringFacts.put(BuiltInFacts.Sensitivity.class, "high");
MonitoringGate.checkpoint();
```

关键要求：

- 资源、数据量、权限变化和敏感级别必须来自服务端已校验的业务结果。
- 客户端传入的报告 ID、组织、数量或状态不能覆盖服务端事实（IA-05）。
- 高风险导出必须在事实完整后同步决策，并在 XLSX 生成前阻断（TC-08、TC-09）。
- 密码、Token、Cookie、密钥和原始请求/响应载荷不得写入事实、属性、异常原因或日志（TC-15）。

### 6. 使用注解处理 MVC 与普通 Service 动作

完整的作用域架构、调用时序、并发与代理边界见[注解监听与运行时事实作用域](../../docs/注解监听与运行时事实作用域.md)。

`MonitoringFixtureController` 展示了两种注解模式：

```java
@GetMapping("/queries")
@MonitorAction(BuiltInActions.Query.class)
public Map<String, Object> query() {
    return Collections.singletonMap("status", "ok");
}

@Service
public class ExportService {
@MonitorAction(BuiltInActions.SensitiveView.class)
public ExportResult export(ExportRequest request) {
    ExportResult result = createExport(request);
    MonitoringFacts.put(BuiltInFacts.DataCount.class,
        Long.valueOf(result.getRowCount()));
    return result;
}
}
```

`@MonitorAction` 负责将成功/拒绝结果转换为事件（IA-03）；普通 Service 可在当前作用域中用 `MonitoringFacts.put` 追加服务端事实（IA-04）。注解不会自动信任请求体。异步任务和需要完整上下文控制的高风险场景应使用程序化入口。

### 7. 注册真实且幂等的控制处理器

夹具通过 `AuditControlActions` 上的 `@ControlTrigger` 注册验证码、限流、会话撤销、MFA、拒绝和审批处理器：

```java
@ControlTrigger(ControlActionType.REVOKE_SESSION)
public ControlExecution revokeSession(ControlCommand command) {
    boolean first = fixtureRepository.activateControl(
        command.getIdempotencyKey(), command.getSubject(),
        command.getAction().name(), command.getExpiresAt());
    if (first) {
        sessionService.revokeSessions(command.getSubject());
    }
    return ControlExecution.succeeded(command.getIdempotencyKey());
}
```

生产处理器必须：

- 严格按 `command.getSubject()` 指定的范围执行，不得扩大到未经规则选定的用户、会话或 IP。
- 以 `idempotencyKey` 防止重放和并发重复执行；成功重放不得重复副作用或延长 TTL（TC-11、TC-13）。
- 返回明确的成功、失败或跳过结果；不要吞掉外部控制服务错误。
- 在 `ENFORCE` 前覆盖内置规则可能产生的全部控制类型。fallback / `SKIPPED` 触发器不满足 ENFORCE 能力（IA-09、IA-10）。

启动期会拒绝非法签名、`RECORD` 动作和重复的 `@ControlTrigger` 声明（IA-08）。

### 8. 接入管理服务和通知

提供 `ManagementAuthorizer` Bean 后，Starter 暴露：

- `SecurityEventQueryService`
- `AlertManagementService`
- `RuleCatalogService`
- `WhitelistManagementService`
- `ControlManagementService`

夹具 Controller 从已认证身份构造 `ManagementActor`，再调用这些服务；它没有把操作者 ID 或系统范围作为客户端 DTO 字段接收。生产 Controller 也必须从服务端安全上下文派生 `ManagementActor`，并让 `ManagementAuthorizer` 在读取或写入前检查资源范围。管理服务会写入成功/拒绝审计，规则和告警写操作使用乐观锁与追加历史。

`AuditNotificationChannel` 故意前两次抛出异常，以验证告警提交后再进行有限持久重试（TC-14）。生产应实现 `NotificationChannel` 并使用下游支持的投递幂等键；通知失败不能回滚已持久化告警或业务事务。

## 夹具功能与验收映射

| 场景 | 示例位置 | 关键验收编号 |
| --- | --- | --- |
| 登录失败、挑战和限流 | `security/AuthenticationController`、`AuditAuthenticationService` | TC-01、TC-02、TC-03、TC-17 |
| 报告资源范围授权 | `security/AuditReportAuthorizationInterceptor` | TC-05、IA-07 |
| 查询遍历与阈值控制 | `report/ReportQueryService`、`ReportQueryController` | TC-06、TC-07 |
| 导出同步决策与台账 | `report/ReportExportService` | TC-08、TC-09、IA-05 |
| 权限提升拒绝 | `privilege/PrivilegeGrantService` | TC-10 |
| 管理、白名单、规则和处置 | `management/*Controller` | TC-04、TC-12、TC-16、TC-18、IA-11 |
| 控制幂等 | `control/AuditControlActions` | TC-11、TC-13、IA-08 至 IA-10 |
| 通知重试 | `notification/AuditNotificationChannel` | TC-14 |
| 注解动作与事实 | `monitoring/MonitoringFixtureController` | IA-02 至 IA-06 |

## 生产切换检查

在切换 `ENFORCE` 前，至少完成以下检查：

1. `monitoring-schema.sql` 已通过受控迁移执行，索引、备份恢复和权限已验证。
2. 认证、授权、会话和组织范围全部由服务端可信安全上下文派生。
3. 每个关键业务动作都有 Action、Fact、来源和真实决策点映射；没有以客户端字段替代服务端事实。
4. 每个规则会产生的控制动作都有真实、幂等、可观测的宿主处理器。
5. 管理 API 已加宿主认证，`ManagementAuthorizer` 已按系统范围和操作授权。
6. 通知渠道、失败分类、重试、告警去重和人工处置已演练。
7. 在 `OBSERVE` 连续运行 2 至 4 周，完成阈值确认、误报复核、敏感数据抽检和性能压测。

不要复制夹具中的 H2、`X-Audit-Principal`、固定账号、`AuditFixture*` Mapper、`ScriptRunner` 建表或模拟通知逻辑到生产环境。

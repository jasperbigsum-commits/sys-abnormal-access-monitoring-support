# 自建系统异常访问监测与控制组件

面向 Spring Boot 2.7 和 Spring Boot 3 宿主的强类型异常访问监测组件。宿主保留认证、资源授权、会话和实际控制权；组件负责把可信服务端事实转换为事件，执行 14 条内置规则，持久化告警与控制，并提供可直接注入 Controller 的管理服务。

生产运行只有 MyBatis 实现，不提供内存仓储或自动建表。初次接入必须使用 `OBSERVE`；`ENFORCE` 会在启动期验证内置规则可能产生的全部控制类型均有可执行处理器，默认跳过处理器不计入能力。

## 模块

| 模块 | 单一责任 |
| --- | --- |
| `api` | 强类型 Action、Fact、Rule、Control 和管理服务契约 |
| `core` | 事件装配、规则评估、告警、持久控制和管理用例 |
| `web-contract` | 前端补充信号及 JSON Schema |
| `mybatis` | 唯一生产持久化适配器、Mapper 和 Schema |
| `spring-support` | Boot 2/3 共用的 Spring 适配 |
| `spring2-starter` / `spring3-starter` | 对称自动配置 |
| `integration-audit` | Boot 2/3 的真实 HTTP、H2/MyBatis 和管理审计验收 |
| `maven-plugin` | 宿主接入模板 |
| `bom` | 依赖版本管理 |

## 设计边界

- Action 由 `Class<? extends ActionType>` 标识。内置和自定义 Action 使用同一类型系统，不接受自由字符串。
- Action 的静态语义只在 `ActionCatalog` 注册；目录冻结后才能创建运行时。
- Fact 使用 `FactType<T>` 保留值类型。Action 明确声明必需/可选 Fact 及允许来源。
- `FactBinding` 决定某个 `ActionFactProvider` 适用于具体 Action 还是 Action Contract；Provider 自身不决定适用范围。
- 内置规则只接受声明过的 Fact 和来源。`CLIENT_SUPPLEMENTAL` 永远不能替代服务端身份、授权或控制依据。
- `MonitoringService` 是程序化监测入口；MVC `@MonitorAction` 只是同一入口的适配器。
- 管理服务在构造时绑定 `ManagementAuthorizer`，调用方不能临时传入或替换授权器。
- 控制执行采用 MyBatis 持久状态机和幂等键；审批、重试、失败和尝试历史均可审计。

## 快速接入

通过 BOM 管理版本，并只引入一个与宿主 Boot 主版本匹配的 Starter：

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
```

1. 通过受控迁移执行 `mybatis/src/main/resources/db/monitoring-schema.sql`。
2. 配置宿主的数据源和 `SqlSessionFactory`。
3. 注册服务端 `IdentityContextProvider`、`ResourceScopeAuthorizer` 和可信代理策略。
4. 以 `OBSERVE` 启动并验证事件、告警、管理授权和审计。
5. 为所有内置规则控制类型注册真实、幂等的 `ControlHandler`，验收后再切换 `ENFORCE`。

```yaml
abnormal:
  access:
    monitor:
      system-id: order-service
      mode: OBSERVE
      instrumentation:
        enabled: true
      trusted-proxies: [10.0.0.0/8]
```

### 强类型调用

内置 Action 可直接使用；自定义 Action 必须是 `final` 类型并在启动期注册：

```java
public final class ReportPreview implements ActionType {
    private ReportPreview() { }
}

ActionCatalog catalog = new ActionCatalog();
BuiltInActions.registerInto(catalog);
catalog.register(ReportPreview.class,
    ActionDefinition.builder("report:preview")
        .eventType(SecurityEventType.QUERY)
        .resourceType("report")
        .optional(BuiltInFacts.ResourceId.class, FactSource.HOST_PROVIDER)
        .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
        .build());
catalog.freeze();
```

程序化入口显式提交 Action、可信上下文、结果、Fact 和来源：

```java
monitoringService.monitor(ActionExecution.of(
    BuiltInActions.ReportExport.class,
    requestContext,
    identityContext,
    ActionOutcome.success(latencyMs),
    ActionFacts.builder()
        .put(BuiltInFacts.ResourceId.class, reportId)
        .put(BuiltInFacts.DataCount.class, exportedRows)
        .build(),
    FactSource.HOST_PROVIDER));
```

MVC 固定动作使用类型化注解：

```java
@MonitorAction(BuiltInActions.Query.class)
@GetMapping("/reports")
public List<Report> list() {
    return reportService.list();
}
```

注解不读取参数、请求体、响应体或异常文本。需要资源 ID、数量等规则 Fact 时，应由宿主在可信服务端路径使用程序化入口或受约束的 `FactBinding`。

## 管理侧

宿主提供一个 `ManagementAuthorizer` Bean 后，Starter 暴露以下服务 Bean，Controller 可直接注入：

- `SecurityEventQueryService`
- `AlertManagementService`
- `RuleCatalogService`
- `WhitelistManagementService`
- `ControlManagementService`

组件不发布 URL，也不规定前端。宿主负责 HTTP 认证、DTO 映射和前端实现；服务内部负责系统范围授权、乐观锁、事务和 `management_audit` 成功/拒绝记录。

## 验证

```bash
mvn clean verify -DskipTests=false
mvn -pl integration-audit/spring2-web -am test
mvn -pl integration-audit/spring3-web -am test
```

严禁写入密码、令牌、Cookie、密钥或未经批准的原始请求/响应载荷。前端信号只作为补充证据，服务端身份和授权始终具有权威性。

文档按责任人组织，入口见[组织角色与文档管理](docs/组织角色与文档管理.md)。接入细节见[集成指南](docs/集成指南.md)，运行与事务见[架构与运维说明](docs/架构与运维说明.md)，验收证据见[集成审计与基础项目验收](docs/集成审计与基础项目验收.md)。English overview: [README.en.md](README.en.md).

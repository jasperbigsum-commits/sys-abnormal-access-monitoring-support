# 自建系统异常访问监测与控制组件

将方案 A 的事件采集、规则判定、告警、控制和审计封装为 Maven Reactor 组件。宿主系统保留认证、会话、数据权限和实际阻断能力；本组件提供统一事件模型、14 条基线规则、告警闭环、MyBatis 审计仓储和前端采集契约，避免多个系统重复实现。

适用于 Spring Boot 2.7.x（`javax.servlet`）和 Spring Boot 3.x（`jakarta.servlet`）。这里的“Spring 2/3”指 Spring Boot 主版本，不支持传统 Spring Framework 2.x/3.x。

## 模块一览

| 模块 | 责任 |
| --- | --- |
| `api` | 框架无关的数据模型与宿主 SPI |
| `core` | 规则、告警、控制、授权记录和内存仓储 |
| `web-contract` | 浏览器信号模型、校验器与 JSON Schema |
| `mybatis` | MyBatis 仓储、Mapper 与数据库迁移脚本 |
| `integration-audit` | 独立的 Boot 2 / Boot 3 最小 Web 宿主、HTTP 集成验收与 Surefire 审计证据 |
| `spring-support` | 通用 Spring 支持类 |
| `spring2-starter` / `spring3-starter` | Boot 2 / Boot 3 自动装配 |
| `maven-plugin` | `initialize` 初始化模板目标 |
| `bom` | 组件版本管理 |

## 核心包分层 / Core package layers

`core` 仅依赖 `api` 和 JDK；Spring、Servlet、MyBatis 与数据库细节都留在外层模块。公开类型按职责而非技术框架划分：

| 包 / Package | 职责 / Responsibility |
| --- | --- |
| `core.domain` | 告警、事件、控制、白名单等不可变领域模型 / immutable monitoring domain model |
| `core.domain.rule` | 纯规则策略与基线规则，不写库、不通知、不执行控制 / deterministic rules without side effects |
| `core.application` | 监测记录、告警生命周期、动作埋点等用例编排 / use-case orchestration |
| `core.port` | 仓储、通知、控制和事务边界等可替换接口 / replaceable persistence and integration ports |
| `core.infrastructure.memory` | 仅用于测试和本地观察模式的内存适配器 / local and test-only adapter |

宿主实现只能通过 `api` SPI 与 `core.port` 接入；规则实现不得反向依赖 Spring、MyBatis、Servlet 或业务授权框架。`core` 下旧的扁平包名不再存在，升级调用方需要按上表更新 import。

## 快速接入

生产接入按七步验收：唯一 Starter、受控 Schema 迁移、可信身份/授权/代理 SPI、`OBSERVE` 配置、安全测试事件、真实控制处理器测试，最后才启用 `ENFORCE`。逐步命令、遗漏检查和高级能力见[集成指南](docs/集成指南.md#十五分钟接入路径)。

先通过 BOM 管理版本，再按宿主 Boot 大版本引入**一个** Starter：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.jasperbigsum-commits</groupId>
            <artifactId>sys-abnormal-access-monitoring-bom</artifactId>
            <version>${abnormal-access-monitoring.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Spring Boot 2.7.x；Boot 3.x 改为 spring3-starter -->
<dependency>
    <groupId>io.github.jasperbigsum-commits</groupId>
    <artifactId>sys-abnormal-access-monitoring-spring2-starter</artifactId>
</dependency>
```

执行 `mybatis/src/main/resources/db/monitoring-schema.sql` 的受控数据库迁移，注册宿主 SPI，并以观察模式启动：

```yaml
abnormal:
  access:
    monitor:
      system-id: order-service
      mode: OBSERVE
      frontend:
        enabled: true
      instrumentation:
        enabled: true
      mdc:
        enabled: true
        trace-id-key: traceId
      trusted-proxies: [10.0.0.0/8]
```

仅在控制处理器、规则阈值和故障回退完成验收后改为 `ENFORCE`。该模式至少需要一个宿主 `ControlHandler`，否则应用启动失败，防止出现“已开启阻断”但没有实际执行能力的假象。

## 宿主接入边界

必须实现并注册 `IdentityContextProvider`、`ResourceScopeAuthorizer` 和 `TrustedProxyResolver`。业务资源访问通过 `ResourceAccessGuard` 调用宿主授权器，组件只记录允许或拒绝结果，绝不把拒绝变为允许。未配置资源授权器时默认拒绝。

生产环境配置数据源和任意兼容的 MyBatis `SqlSessionFactory` 后自动使用 `MyBatisMonitoringRepository`；没有它时会回退内存仓储，仅适用于本地开发或测试。两个 Starter 不再传递或锁定 `mybatis-spring-boot-starter`，因此宿主可按自身 Boot 生态选择并升级 MyBatis 集成版本；组件只要求 MyBatis 3.5.x 核心 API（默认构建版本为 `3.5.19`）。组件不自动建表，也不发布业务端点。前端补充信息由宿主后端 API 接收；组件统一 `FrontendSignal` v1 语义和 `FrontendSignalRecorder.record(signal, serverContext)` 入口，但不规定 URL、认证或响应 JSON。宿主负责认证、请求体大小限制、限流、JSON Schema 校验和可信服务器上下文构造，详见[前端补充信息统一接入](docs/集成指南.md#6-前端补充信息统一接入)。

MyBatis 适配器内按人类可定位的职责组织：根包保留仓储、注册器、Mapper 与单一 `InstantTypeHandler`；`mybatis.po` 专门存放表行映射与规则版本查询投影。PO 不进入 `api`，也不替代 `core.domain` 的不可变领域对象；仓储是二者间唯一的转换位置。

`MonitoringRepository.inTransaction(...)` 是监测审计的一致性边界：一次事件记录中的事件、规则判定、告警摘要和关联会在一个 MyBatis 管理会话中提交；告警状态和处置记录同样原子提交。通知和宿主控制在提交后执行，避免无法回滚的外部副作用。该边界刻意不加入宿主业务事务；若业务写入与审计必须跨资源原子，应由宿主采用 transactional outbox 或实现其批准的事务适配器。

`EventEnricher` 是可选的 API，但 Starter 不会扫描或自动调用它。宿主如确有已审批的非敏感字段需要补充，应在构造完成事件后显式调用，再把返回草稿交给 `ActionEventRecorder.record(...)` 或 `SecurityMonitor.record(...)`。

不要写入密码、令牌、Cookie、密钥、未经批准的请求体或响应体。用户、角色、源 IP、会话和最终授权结论均由服务端建立；前端数据只能作为补充证据。

## 动作埋点与控制触发

Starter 默认开启 `abnormal.access.monitor.instrumentation.enabled`。全局 MVC 拦截器只处理带有 `@MonitorAction` 的 Servlet MVC `HandlerMethod`；方法注解优先于类型注解，并且只在请求完成后记录最终 `SUCCESS`、`DENIED` 或 `FAILURE`。它不会拦截 Service、消息消费、定时任务、同类自调用，也不会读取或保存方法参数、请求体、响应体及异常文本。

`@MonitorAction` 只保存静态、统一的动作定义：

| 属性 | 用途 |
| --- | --- |
| `value` | 首选短写动作编码，例如 `@MonitorAction("report:export")` |
| `action` | 与既有代码兼容的具名动作编码；同时填写时必须与 `value` 完全一致 |
| `eventType` | 通用事件类别，默认 `QUERY`；只有导出、登录失败、授权变更等特殊语义才需要设置 |
| `resourceType` | 固定逻辑资源类别，例如 `report`；动态资源 ID 不应写入注解 |
| `ruleTags` | 固定规则选择标记；记录时映射为 `monitor.rule-tag.<tag>=true` 属性 |

```java
@MonitorAction(
    value = "report:export",
    eventType = SecurityEventType.EXPORT,
    resourceType = "report",
    ruleTags = {"sensitive-data", "bulk-operation"}
)
@GetMapping("/reports/orders")
public Report exportOrders() {
    return reportService.exportOrders();
}
```

服务层、消息消费和定时任务使用 `MonitoringActionRegistry` 统一注册静态定义，再以 `ActionEventRecorder` 方法调用埋点；不需要为每个业务动作创建新的枚举：

```java
@Bean
MonitoringActionRegistry monitoringActions() {
    return new MonitoringActionRegistry()
        .register(MonitorActionDefinition.builder("report:export")
            .eventType(SecurityEventType.EXPORT)
            .resourceType("report")
            .ruleTags("sensitive-data", "bulk-operation")
            .build());
}

// Service、MQ 消费者或任务中：request 和 identity 必须来自可信宿主上下文。
recorder.record(recorder.draft("report:export", request, identity)
    .resourceId(reportId)
    .dataCount(exportedRows)
    .latencyMs(elapsedMs)
    .result(SecurityEventResult.SUCCESS)
    .reasonCode("EXPORT_COMPLETED")
    .build());
```

`draft(...)` 已填充动作、事件类别、可信 IP、请求 ID、追踪 ID、身份、角色、会话和服务端时间；调用方只补充动态资源 ID、数量、时延、原因码及已批准属性。未注册的动作编码会失败，防止同一动作在不同入口产生不一致语义。涉及业务副作用前控制的流程仍应先执行宿主授权/控制；注解记录只是解耦的完成后审计。

要把规则产生的控制动作绑定到宿主方法，可在 Spring Bean 的公开方法上使用 `@ControlTrigger`。方法必须接收唯一的 `ControlCommand` 参数，并返回 `void` 或 `ControlExecution`；Starter 自动发现并适配为 `ControlHandler`，核心不依赖宿主框架或业务实现。完整示例和边界见[集成指南](docs/集成指南.md#3-动作埋点与控制触发)。

## 规则管理边界

运行时基线和宿主代码规则属于 `InternalRuleRegistry`：Starter 收集默认规则、`DetectionRule` Bean 与 `InternalRuleContributor`，在创建监测器前冻结。管理端通过 `InternalRuleRegistry.entries()` 查询它们时，来源为 `INTERNAL`、`mutable=false`；发布后不能在线修改。

`MonitoringAdministrationMapper.findRuleVersions()` 读取 `security_rule` 中的持久化动态规则版本，`setRuleEnabled(...)` 仅改变对应版本的管理启停标记。当前组件**不会**自动把数据库规则编译或装载成运行时 `DetectionRule`；要让动态规则实际参与判定，必须由宿主实现经过审批、校验和灰度的动态加载器。管理页应把内部规则清单和持久化版本清单分开展示，不能把前者当作可在线编辑项。

## 链路追踪

启用 `abnormal.access.monitor.mdc.enabled`（默认 `true`）后，请求适配器会以 `mdc.trace-id-key`（默认 `traceId`）把事件 `traceId` 同步到 SLF4J MDC。优先级为入站追踪头、当前 MDC、组件生成值；请求结束会恢复原 MDC，避免线程复用串链。消息与任务场景应由宿主传播 MDC，并在 `MonitoringRequestContext.traceId(...)` 中传入同一链路标识。

## 初始化模板

在宿主 Maven 工程中生成安全默认配置、SPI、控制器模板和前端示例。已存在文件不会被覆盖：

```powershell
mvn io.github.jasperbigsum-commits:sys-abnormal-access-monitoring-maven-plugin:${abnormal-access-monitoring.version}:initialize `
  '-Dabnormal.access.monitor.outputDirectory=src/main/resources/abnormal-access-monitoring' `
  '-Dabnormal.access.monitor.packageName=com.example.orders.monitoring' `
  '-Dabnormal.access.monitor.systemId=order-service'
```

目标会创建且只创建以下 5 个文件：`application-abnormal-access-monitoring.yml`、`host-spi/HostMonitoringSpi.java`、`host-spi/HostMonitoringActions.java`、`host-spi/HostControlHandler.java` 和 `frontend-signal-v1.example.json`。配置模板包含 `instrumentation` 与 `mdc` 默认项；`HostMonitoringActions` 提供 `MonitoringActionRegistry` 的注册式动作样例。Java 模板需要移入宿主 `src/main/java` 并注册为 Spring Bean；模板的匿名身份、默认拒绝和不执行控制均是安全默认实现，不能直接用于生产。

## 文档与验证

- [集成指南](docs/集成指南.md)：十五分钟接入、常见遗漏、高级功能、前端契约与生产验收。
- [错误规范](docs/错误规范.md)：13 个稳定错误码、异常层级、重试建议和宿主协议映射边界。
- [架构与运维说明](docs/架构与运维说明.md)：模块隔离、调用流程、事务与提交后副作用、上线迁移和巡检。
- [功能与优化路线图](docs/路线图.md)：M0-M3 优先级、范围、完成信号和明确延期项。
- [1.0 最小上线验收与版本排期](docs/1.0最小上线验收与版本排期.md)：以最小验收成果为优先级的 1.0 遗留清单、发布门禁和 1.1/1.2 候选范围。
- [领域模型与数据设计](docs/领域模型与数据设计.md)：聚合边界、表映射、规则契约和最小验收。
- [MyBatis 标准化 ORM 与架构设计评审稿](docs/MyBatis标准化ORM与架构设计评审稿.md)：ORM 映射、全量表字段字典、事务边界、核心调用链和待决评审项。
- [集成审计与基础项目验收](docs/集成审计与基础项目验收.md)：Boot 2/3 可启动审计项目、HTTP 验收路由、Surefire 证据路径与风险定位方式。
- [Javadoc 生成说明](docs/Javadoc生成说明.md)：发布 Javadoc JAR 和聚合 HTML 的命令。
- [Architecture and transaction boundaries (English)](docs/architecture-and-transaction-boundaries.en.md)：layering, transaction semantics, and MyBatis compatibility in English.
- [English README](README.en.md)：English quick start and integration boundaries.
- 前端契约：`web-contract/src/main/resources/frontend-signal.schema.json`。

完整构建与测试：

```bash
mvn clean verify -DskipTests=false
```

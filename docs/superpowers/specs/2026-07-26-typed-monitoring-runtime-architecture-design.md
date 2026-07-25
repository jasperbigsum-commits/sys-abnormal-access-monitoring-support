# 类型化监测运行时与项目结构重构设计

日期：2026-07-26
状态：已批准，等待实施计划

## 1. 背景

项目尚未被宿主系统使用，因此本次重构按新的公开契约直接演进，不保留旧包、弃用类型或双轨兼容层。

当前结构的主要问题不是单纯的代码行重复，而是多个组件同时拥有同一项决定权：

- `MonitorAction`、`MonitorActionDefinition` 和记录器都可以决定 action 的语义。
- 字符串属性、事件质量策略和规则分别解释同一个事实名称。
- `SecurityEventDraft`、拦截器、前端映射器和领域事件都可以设置可信字段。
- 控制动作同时存在 `supports`、方法注解、默认处理器和列表优先级等绑定方式。
- Spring Boot 2 与 Boot 3 Starter 复制了大量与 Servlet 命名空间无关的逻辑。
- 生产内存仓储与 MyBatis 仓储形成两套持久化语义和隐式回退。

本设计将定义权、绑定权、事实所有权、判定权、执行权和持久化权分开，并在启动阶段把可验证的错误一次性暴露出来。

## 2. 目标

1. `api` 保持单一 Maven 制品，按能力分包，不新增细粒度 Maven 模块。
2. action、fact、rule 和 control 都有唯一、冻结的运行时目录。
3. 内置与宿主自定义 action 使用同一种类型约束和同一条调用链。
4. 内置规则只消费经过类型、来源和完整性验证的事实。
5. Spring Bean 的注解绑定在启动阶段完成扫描、校验和预计算。
6. MyBatis 是唯一生产持久化实现，删除生产内存仓储和无数据库回退。
7. Boot 2 与 Boot 3 只保留 `javax.servlet` 与 `jakarta.servlet` 的必要差异。
8. 使用现有框架或成熟第三方能力替换属性路径、IP/CIDR 等容易出错的自研实现。
9. 每个运行阶段都有明确的错误所有者和失败策略。
10. `integration-audit` 以真实宿主方式实施新规范，并成为 Boot 2/3 的统一验收门禁。
11. 文档按项目关联角色组织，每项规范只有一个事实来源，删除历史镜像和生成物重复。

## 3. 非目标

- 不提供旧 `io.github.jasper.monitoring.api` 扁平包的兼容转发类型。
- 不支持运行时动态新增或替换 action、fact、rule 或 control binding。
- 不引入通用 `Utils` 大类、Repository Factory、ORM Provider SPI 或代码生成式双 Starter。
- 不允许浏览器信号成为身份、授权、action 或自动控制的权威来源。
- 不把 Spring、Servlet 或 MyBatis 类型引入 `core` 领域与应用逻辑。

## 4. 模块与包结构

```text
api
└─ io.github.jasper.monitoring.api
   ├─ action
   ├─ event
   ├─ fact
   ├─ identity
   ├─ authorization
   ├─ control
   ├─ rule
   └─ error

core
└─ io.github.jasper.monitoring.core
   ├─ domain
   │  ├─ event
   │  ├─ alert
   │  ├─ control
   │  └─ rule
   ├─ application
   │  ├─ monitoring
   │  ├─ alert
   │  ├─ authorization
   │  └─ control
   └─ port

mybatis
└─ io.github.jasper.monitoring.mybatis
   ├─ repository
   ├─ mapper
   ├─ model
   ├─ converter
   └─ typehandler

spring-support
└─ io.github.jasper.monitoring.spring.support
   ├─ action
   ├─ fact
   ├─ configuration
   ├─ context
   └─ control

spring2-starter / spring3-starter
├─ autoconfigure
└─ web

integration-audit
├─ spring2-web
└─ spring3-web
```

依赖方向固定为：

```text
api <- core <- mybatis
          ^        ^
          └─ spring-support <- spring2-starter
                    └─────── <- spring3-starter

api <- web-contract <- spring-support
```

约束如下：

- `api` 只包含公开契约和不可变输入/输出模型。
- `core` 不依赖 Spring、Servlet、MyBatis 或宿主授权框架。
- `mybatis` 是 `core` 持久化端口的唯一生产实现。
- `spring-support` 不出现 `javax.servlet` 或 `jakarta.servlet`。
- Starter 只处理 Boot 自动配置和对应 Servlet 命名空间。
- `integration-audit` 只能作为公开 Starter 的下游消费者，不能被任何生产模块反向依赖。

## 5. 统一 action 类型

### 5.1 类型令牌

Java 注解不能接收接口实例，但可以接收受接口约束的类令牌。内置与自定义 action 因此统一使用：

```java
public interface ActionType {
}

public @interface MonitorAction {
    Class<? extends ActionType> value();
}
```

内部只保留一个包级私有所有权标识：

```java
interface BuiltInActionType extends ActionType {
}
```

内置 action 的公开类型可以被宿主引用，但外部代码无法直接实现 `BuiltInActionType`。该接口只表达库所有权，不承载事实、规则或失败策略。

action 的业务能力使用独立的契约接口组合：

```java
public interface ActionContract {
}

public interface ExportActionContract extends ActionContract {
}
```

action 类型是不可继承的具体类型；内置与自定义 action 都可以实现同一个公开契约：

```java
public final class ReportExportAction
        implements ActionType, BuiltInActionType, ExportActionContract {
}

public final class ScheduledExportAction
        implements ActionType, ExportActionContract {
}
```

这种结构将“action 身份”和“可继承业务要求”分开。`ActionType` 负责名义身份，`ActionContract` 负责事实、规则和最低失败策略。action 不通过 Java 类继承传播语义。

```java
@MonitorAction(ReportExportAction.class)
public ExportResult export(ExportRequest request) {
    // business implementation
}
```

自定义 action 实现公开接口，并通过贡献者注册：

```java
public final class OrderRefundAction
        implements ActionType, PrivilegedMutationActionContract {
}

public final class OrderActionContributor implements ActionContributor {
    @Override
    public void contribute(ActionRegistry registry) {
        registry.register(OrderRefundAction.class,
            ActionDefinition.builder("order:refund")
                .eventType(SecurityEventType.SENSITIVE_OPERATION)
                .resourceType("order")
                .failurePolicy(ActionFailurePolicy.FAIL_CLOSED)
                .build());
    }
}
```

### 5.2 ActionContractDefinition

`ActionContractDefinition` 是一组可继承业务要求的唯一所有者，至少包含：

- 必需和可选 `FactType`。
- 每项事实允许用于满足契约的 `FactSource`。
- 显式参与的 `RuleType`。
- 最低运行时失败策略。

内置 action 与继承该契约的自定义 action 自动获得同一组规则前置条件。例如：

```java
contracts.register(ExportActionContract.class,
    ActionContractDefinition.builder()
        .require(ResourceIdFact.class,
            FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
        .require(DataCountFact.class,
            FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
        .participateIn(ExportThresholdRule.class)
        .minimumFailurePolicy(ActionFailurePolicy.FAIL_CLOSED)
        .build());
```

契约接口可以继承其他契约，也可以由一个 action 同时组合多个契约。有效要求按以下规则合并：

- 必需和可选事实取并集；任一契约要求必需时，最终为必需。
- 允许来源取交集，避免组合后放宽信任边界。
- 参与规则取并集。
- 失败策略取更严格值。
- 产生空来源集合或不兼容定义时，启动失败。

子 action 可以增加事实、规则和更严格策略，不能删除或降低继承要求。不需要这些语义的 action 不应实现对应契约。

### 5.3 ActionDefinition

`ActionDefinition` 是 action 全局静态语义的唯一所有者，至少包含：

- 稳定持久化编码。
- `SecurityEventType`。
- 逻辑资源类型。
- 规则选择标签。
- action 自身追加的 `RuleType` 集合。
- action 自身追加的必需和可选事实集合。
- action 自身追加的事实来源约束。
- 运行时失败策略。

编译后的有效 action 定义是 `ActionDefinition` 与全部 `ActionContractDefinition` 的严格合并结果。`@MonitorAction` 不再声明 `eventType`、`resourceType`、`ruleTags`、静态属性或 Provider 类型。同一个 action 可以被多个方法引用，但只能注册一份定义。不同贡献者重复注册相同类型或编码，即使内容一致，也属于配置冲突。

持久化编码必须匹配小写 `domain:verb` 结构；domain 和 verb 只允许字母、数字及内部连字符，整体长度不超过 128。目录将其转换为不可变 `ActionId`，类型令牌和 `ActionId` 必须保持一对一关系。

### 5.4 ActionCatalog

启动阶段先加载内置 action 与 contract 定义，再执行宿主贡献者。完成契约展开、扫描和交叉校验后目录冻结。运行期间只能通过 action 类型令牌获取包含有效契约的不可变 `RegisteredAction`，不能隐式创建 action 或按字符串查找未注册定义。

`@MonitorAction` 的值必须是已注册、具体且不可继承的 action 类型。接口、抽象类和 `ActionContract` 不能直接作为方法 action。

## 6. 类型化事实契约

### 6.1 FactType

规则依赖的事实不能继续使用任意字符串名称。所有标准和自定义事实统一使用泛型类型令牌：

```java
public interface FactType<T> {
}

interface BuiltInFactType<T> extends FactType<T> {
}
```

示例：

```java
public final class ResourceIdFact implements BuiltInFactType<String> {
}

public final class DataCountFact implements BuiltInFactType<Long> {
}
```

程序式写入保持编译期值类型约束：

```java
public <T> ActionFacts.Builder put(
    Class<? extends FactType<T>> factType,
    T value
)
```

`ActionFacts.builder().put(DataCountFact.class, "5000")` 必须无法编译。

### 6.2 FactDefinition

`FactDefinition<T>` 是事实持久化和验证语义的唯一所有者，至少包含：

- `Class<? extends FactType<T>>` 类型令牌。
- 稳定数据库/事件键。
- Java 值类型和规范化 codec。
- 允许的 `FactSource`。
- 敏感级别、最大长度和校验规则。
- 存储位置：标准列或扩展事实表。

稳定字符串键只存在于目录和数据库映射中，不由注解、Provider 或规则手写。

### 6.3 事实来源

标准来源固定为：

```text
TRUSTED_REQUEST
TRUSTED_IDENTITY
METHOD_PARAMETER
HOST_PROVIDER
CLIENT_SUPPLEMENTAL
FRAMEWORK_OUTCOME
```

`FactDefinition` 决定某个来源能否提供该事实。低信任来源不能满足要求可信来源的规则前置条件。

### 6.4 参数绑定

删除同时承担静态属性和参数映射的 `MonitorActionAttribute`。参数事实使用单一职责注解：

```java
@Repeatable(ActionFacts.class)
public @interface ActionFact {
    Class<? extends FactType<?>> value();
    String path() default "";
}
```

示例：

```java
public ExportResult export(
    @ActionFact(value = ResourceIdFact.class, path = "report.id")
    @ActionFact(value = OrganizationScopeFact.class, path = "tenant.code")
    ExportRequest request) {
    // business implementation
}
```

Spring 支持层使用 `BeanWrapper` 验证和访问受限 Bean 属性路径。路径在启动阶段验证并编译成绑定描述，不在每次请求时重复解析。禁止 `class`、静态成员、任意方法调用和不受支持的集合访问。

### 6.5 FactBinding 与 ActionFactProvider

动态事实提供器由宿主配置决定，不由业务方法注解引用。Provider 不声明支持哪些 action，也不拥有 action 与事实的关系：

```java
public interface ActionFactProvider {
    ActionFacts provide(ActionExecution execution);
}
```

独立的 `FactBinding` 拥有 action、事实和 Provider 之间的具体提供关系。宿主通过 `ActionFactBindingContributor` 注册绑定：

```java
bindings.forAction(ReportExportAction.class)
    .using(reportExportFactProvider)
    .provides(DataCountFact.class, SensitivityFact.class);
```

只有确实能服务整个契约的 Provider 才允许显式绑定到契约：

```java
bindings.forContract(ExportActionContract.class)
    .using(exportFactProvider)
    .provides(DataCountFact.class);
```

继承规则固定为：

```text
ActionContract 的事实要求自动继承
具体 action 的 Provider 绑定默认不继承
只有显式 contract binding 才应用于全部契约实现 action
```

启动阶段把参数注解、可信上下文、具体 action binding 和 contract binding 统一编译成 `ActionBinding`，并校验 action 存在、事实已声明、来源合法且没有重复所有者。

Provider 只能产生 `FactBinding.provides(...)` 声明范围内的 `ActionFacts`，不能设置 action、身份、请求、执行结果或耗时。参数绑定和 Provider 同时负责同一 fact 时启动失败，不使用执行顺序覆盖。Provider 运行时返回未声明事实时，拒绝该事实并记录稳定的 Provider 契约违规问题。

## 7. 规则契约

### 7.1 规则拥有自己的前置条件

删除 `MonitoringEventPolicy` 和 `DefaultMonitoringEventPolicy`。规则所需事实、允许来源和窗口由 `DetectionRule` 自己声明，避免按规则 ID 在另一个类中重复维护字符串条件。

规则与 action 一样使用统一类型令牌：

```java
public interface RuleType {
}

interface BuiltInRuleType extends RuleType {
}
```

`ActionContractDefinition` 和 `ActionDefinition` 显式列出参与的 `RuleType`。规则引擎不再通过运行时事件 Predicate 猜测 action 是否适用；它只评估当前 action 的有效契约已声明参与且前置事实完整的规则。规则稳定 ID 只由对应 `RuleDefinition` 提供。

`DetectionRule` 至少公开：

```text
definition()
requiredFacts()
acceptedSources()
evaluate(event, history)
```

规则继续保持确定性和无副作用，只返回 `RuleMatch`。

### 7.2 启动期交叉校验

`RuleCatalogValidator` 校验：

```text
ActionContractDefinition 继承的事实
→ ActionDefinition 追加的事实
→ ActionBinding 可提供的事实
→ FactDefinition 允许的来源
→ 有效 action 定义参与的 RuleType
→ DetectionRule 要求的事实与来源
```

以下情况阻止启动：

- action 契约或定义声明会参与某条规则，但有效定义未包含规则必需事实。
- 必需事实没有参数绑定、可信上下文来源或 Provider。
- Provider 来源不满足规则要求。
- 具体 action binding 被错误继承到其他 action。
- 规则 ID、action 编码或 fact 稳定键重复。

运行时必需事实取值为空时，事件标记为 `INCOMPLETE`，只跳过依赖该事实的规则；无关规则继续执行并记录稳定诊断。

## 8. 执行与事件组装

### 8.1 模型职责

```text
ActionDefinition  全局静态语义
ActionContract    可继承、可组合的事实和规则要求
ActionBinding     Spring 方法与 action 的已编译绑定
ActionExecution   一次方法执行的只读视图
ActionFacts       经过来源验证的补充事实
ActionOutcome     结果、原因、异常分类和耗时
SecurityEvent     最终领域事件
```

`MonitorActionInvocation` 重构为 `ActionExecution`。`MonitorActionFacts` 重构为只保存事实的 `ActionFacts`。`result`、`reasonCode` 和 `latencyMs` 移入 `ActionOutcome`，由框架结果解析器负责，Provider 无权覆盖。

### 8.2 唯一事件组装器

删除允许宿主任意设置可信字段的公开 `SecurityEventDraft.Builder`。`SecurityEventAssembler` 是最终事件的唯一组装者：

```text
ActionDefinition
+ TrustedRequestContext
+ IdentityContext
+ ActionFacts
+ ActionOutcome
= SecurityEvent
```

固定所有权和优先级为：

```text
可信请求/身份上下文
> ActionDefinition 静态语义
> 框架生成的 ActionOutcome
> 宿主 ActionFactProvider
> 参数声明式补充事实
> 浏览器补充事实
```

低优先级数据不能覆盖高优先级数据。冲突产生 `ObservationIssue`，不采用最后写入者胜出。

### 8.3 运行时数据流

```text
Spring Method
→ 读取预计算 ActionBinding
→ 解析可信请求与身份上下文
→ 提取声明式参数事实
→ 执行业务方法
→ 生成 ActionOutcome
→ 执行预绑定 ActionFactProvider
→ 验证并合并 ActionFacts
→ SecurityEventAssembler
→ MyBatis 持久化
→ 规则评估
→ 告警与控制计划
```

## 9. 启动期运行时编译

`SpringMonitoringRuntimeCompiler` 是 Spring 宿主装配的唯一入口，执行顺序固定为：

1. 加载内置 action、action contract、fact、rule 和 control 定义。
2. 执行宿主贡献者。
3. 扫描 Spring Bean 的类型、方法和接口注解。
4. 展开 action contract 继承与组合，计算有效 action 定义。
5. 解析最具体方法、参数事实和显式 FactBinding。
6. 校验代理条件、类型、来源、Provider 所有权、规则事实覆盖率和控制覆盖率。
7. 生成不可变 `ActionBinding`。
8. 冻结全部目录。
9. 发布不可变 `MonitoringRuntime`。

非 Spring 对象、消息消费者和定时任务不进行全 classpath 猜测扫描，必须显式注册并通过程序式记录入口使用 action 类型令牌。

扫描应聚合全部 `ConfigurationViolation` 后一次性报告，信息包含 action、Bean、类和方法位置，但不包含参数值或敏感数据。

## 10. MyBatis 唯一持久化

删除 `core.infrastructure.memory` 以及 Starter 中的内存回退。缺少 `SqlSessionFactory`、Mapper、事务能力或 schema 前置条件时，应用启动失败。

现有 `MonitoringRepository` 职责过宽，拆为：

```text
EventRepository
AlertRepository
ControlRepository
WhitelistRepository
NotificationDeliveryRepository
MonitoringTransaction
```

这些是 `core` 的窄持久化端口，不代表 ORM 可切换设计。`MyBatisMonitoringStore` 是唯一生产实现，可以同时实现多个窄接口。

事务边界由应用用例定义：

- 事件、规则匹配、告警状态和告警关联在一个监测事务内提交。
- 外部通知和宿主控制不放入可回滚数据库事务。
- MyBatis 实现负责加入可用的宿主事务或创建明确的监测事务，不允许隐式混用多个 Session。
- 查询历史必须至少按系统和规则最大时间窗口限定，不再无条件读取全局一天事件。

标准事实映射到明确列；自定义事实以稳定 fact key、值类型和规范化值存入扩展事实表。PO、Mapper 和领域转换按聚合拆分，不继续集中在单个大型 Repository 类。

## 11. 控制执行

`RECORD` 只是无控制动作，应从 `ControlActionType` 移除；规则不需要控制时返回空集合。

删除默认 fallback handler、`supports` 列表搜索和多级优先级。启动后 `ControlCatalog` 对每个可执行控制类型最多绑定一个宿主 Handler。

`ENFORCE` 的校验按启用规则实际可能产生的控制动作计算覆盖率，而不是只检查是否存在任意 Handler。需要人工审批的动作进入审批工作流，不伪装成立即执行成功。

幂等状态机为：

```text
不存在
→ 数据库原子创建 PENDING
→ 调用宿主幂等 Handler
→ SUCCEEDED | FAILED | SKIPPED
```

唯一键解决并发占位。宿主 Handler 必须使用同一幂等键抵抗“外部效果已发生但结果尚未回写”的重试场景。执行来源和状态使用明确字段，不再从 control ID 前缀推断。

控制失败不回滚已经提交的事件与告警，但必须形成可查询的 `ControlExecution`。

## 12. 身份、授权与请求上下文

以下接口保持独立，由宿主分别拥有：

- `IdentityContextProvider`：只解析服务端认证身份。
- `ResourceScopeAuthorizer`：只给出宿主资源范围授权结论。
- `TrustedProxyResolver`：只决定可信代理链和客户端 IP。

Maven 插件不再生成一个类同时实现三个接口。`ResourceAccessGuard` 必须引用已注册 `ActionType`，不能从 HTTP method 临时生成 action 绕过目录。

可信请求、身份和授权事实由各自适配器写入专属上下文，FactProvider 和浏览器信号无权覆盖。

## 13. 前端补充信号

浏览器上报只产生 `CLIENT_SUPPLEMENTAL` 来源的类型化事实。客户端不能决定：

- action 类型或持久化 action 编码。
- `SecurityEventType`。
- 用户、角色、会话和来源 IP。
- 成功/失败结果。
- 自动控制结论。

服务端 `FrontendSignalBinding` 将允许的信号类型绑定到已注册 action。客户端 action 字符串不能直接进入 `SecurityEvent`。客户端事实不能满足要求可信服务端来源的规则前置条件。

JSON schema、Java 模型和映射规则必须由同一组契约测试保持一致。

## 14. 运行时失败策略

每个 `ActionDefinition` 必须显式声明：

```text
OBSERVE_ONLY  监测系统故障时业务继续，并输出稳定日志和指标
FAIL_CLOSED   监测系统故障时抛出 MonitoringUnavailableException
```

内置普通查询、浏览和补充遥测使用 `OBSERVE_ONLY`。登录、权限变更、敏感导出和安全配置变更等关键 action 使用 `FAIL_CLOSED`。自定义 action 注册时没有隐式默认值。

参数事实缺失、前端信号非法和单个 Provider 失败属于观测质量问题，不触发 fail-closed。MyBatis、规则引擎、action 目录和关键可信上下文故障应用 action 的失败策略。

核心层只抛出分类异常，Spring 边界负责隔离或传播。`ENFORCE` 模式不能降低关键 action 的失败策略。
`OBSERVE` 模式也必须尊重 action 的 `FAIL_CLOSED` 声明；监测模式只决定是否执行控制，不改变系统故障策略。

## 15. 错误模型

错误分为四类：

```text
ConfigurationViolation      启动期定义和绑定错误
ObservationIssue            运行期事实质量问题
MonitoringSystemException   持久化、规则和关键上下文故障
ControlExecution            宿主控制结果
```

约束如下：

- `ObservationIssue` 只保存 `FactType`、`FactSource` 和稳定问题码。
- MyBatis 原异常只作为 cause 保留，不写入事件、控制原因或公开响应。
- 规则异常是系统故障，不能转换成“规则未命中”。
- Provider 失败只影响其负责的事实，其他 Provider 继续执行。
- 通知失败不回滚事件和告警，但必须形成日志、指标和可查询投递记录。
- 禁止静默 `noop` 回退；可选能力必须通过显式配置关闭。
- 错误码按 `CONFIGURATION`、`OBSERVATION`、`PERSISTENCE`、`RULE` 和 `CONTROL` 分组。

## 16. Spring 2/3 共用内核

移动到 `spring-support` 的逻辑包括：

- 最具体方法和接口注解解析。
- action/fact/provider 扫描与绑定。
- 运行时目录编译和错误聚合。
- 请求执行编排和事件记录。
- 共享属性模型与配置校验。
- 非 Servlet 的上下文、MDC 和控制支持。

Boot 2/3 Starter 只保留：

- 自动配置发现入口。
- 对应 Servlet API 的请求、响应、过滤器和拦截器适配。
- 对应 Boot 版本的配置属性绑定验证。

不得通过模板或生成代码复制完整 Starter。两个 Starter 的公共行为由 `spring-support` 测试覆盖，各 Starter 只验证命名空间和真实 Web 集成。

## 17. 工具与第三方库边界

- 使用 Spring `BeanWrapper` 替换自研 Bean 属性路径反射器。
- IP literal 和 CIDR 采用支持“禁止 DNS 解析”和规范化输出的成熟库，替换自研解析逻辑；具体库和版本在实施计划中根据 Java 8 与依赖树验证后确定。
- 使用 ArchUnit 固化包和模块依赖方向。
- 使用 Maven 校验阶段的重复代码检查，并只排除不可避免的 Servlet 命名空间适配。
- Maven 插件模板移到 `src/main/resources`，生成器只负责参数校验、占位替换和安全写入。
- `SecurityFieldSanitizer` 是领域安全策略，保留其行为但移出公开 API，拆成有明确语义的规范化器。
- 不为简单空白判断和集合复制引入大型通用依赖，不创建万能工具类。
- 移除未使用依赖，包括未实际使用的 Lombok。

## 18. 测试设计

所有行为变更遵循测试先行，并先观察目标测试因缺少新行为而失败。

### 18.1 API 契约

- action/fact 类型令牌和目录唯一性。
- action contract 的继承、组合、严格合并与冲突。
- `FactType<T>` 值类型约束。
- `ActionDefinition` 必需字段和显式失败策略。
- 内置 action、fact 和 rule 的参数化完整性测试。

### 18.2 Core

- 每条规则的事实和来源前置条件。
- 缺少事实时只跳过相关规则。
- `SecurityEventAssembler` 的来源所有权与冲突行为。
- 告警生命周期和控制计划。
- 规则异常分类与 action 失败策略。

### 18.3 MyBatis

- H2 完整仓储集成测试。
- 事务提交、回滚和嵌套参与。
- 并发控制幂等键和状态迁移。
- 标准事实列与扩展事实表映射。
- 事件、告警、处置、白名单、通知和控制恢复。
- schema 与 Mapper 一致性。

生产内存仓储删除后，core 单元测试只能使用测试源码中的最小 fake；持久化语义必须由 MyBatis + H2 验证。

### 18.4 Spring Support 与 Starter

- Bean、接口、继承方法和代理方法扫描。
- 参数路径、具体 action binding、contract binding、Provider 覆盖率和重复所有者。
- 全量启动错误聚合。
- Boot 2/3 各一套自动配置和真实 HTTP 验收测试。

当前两套大型重复 action 测试迁移到 `spring-support`。Starter 中只保留版本差异测试。

### 18.5 架构与完整验证

- ArchUnit 禁止逆向依赖和框架泄漏。
- 重复代码检查对 Boot namespace 适配使用精确排除。
- `mvn clean verify -DskipTests=false` 是最终验收命令。
- 分别运行 Spring 2 与 Spring 3 真实集成应用验收。

## 19. 集成审计实施与评估规范

### 19.1 模块定位

`integration-audit` 不再只是可运行示例，而是新公开规范的正式黑盒验收模块。它同时承担三项职责：

1. 展示宿主系统应如何接入 action contract、typed fact、MyBatis、身份、授权和控制。
2. 通过真实 HTTP、Spring 容器和数据库验证组件行为，而不是调用内部实现模拟成功。
3. 对 Spring Boot 2 与 Spring Boot 3 执行同一份验收矩阵，证明除 Servlet 命名空间外语义一致。

继续保留两个现有子模块，不新增 Maven 模块：

```text
integration-audit
├─ spring2-web
└─ spring3-web
```

父目录提供共享的验收测试源码和测试数据，由两个子模块分别在各自依赖树中编译运行。子模块只保留应用启动类、`javax`/`jakarta` 适配和极薄的测试入口，不复制业务场景断言。

### 19.2 宿主使用规范

两个审计应用必须按真实宿主方式使用新契约：

- 生产源码只依赖正式 Starter 和宿主实际需要的 ORM、Web、Shiro 依赖，不为访问内部类而直接依赖 `core`。
- 使用 Starter 的标准 MyBatis 装配和 schema 前置条件，不手工调用内部 Registrar 或自行复制建表逻辑。
- 每个 HTTP 用例使用具体 `ActionType`；禁止字符串 action。
- 内置和自定义 action 通过 `ActionContract` 继承事实、规则和最低失败策略。
- 参数事实使用 `@ActionFact(FactType.class)`；动态事实通过显式 `FactBinding` 绑定 Provider。
- Provider 不声明 action 支持范围，也不直接出现在 `@MonitorAction` 中。
- Shiro 只提供宿主身份和权限结论；监测组件不替代 Shiro 授权。
- 控制动作通过冻结的 `ControlCatalog` 显式绑定，不使用旧 `@ControlTrigger` 反射适配或 fallback handler。
- 前端信号只能作为 `CLIENT_SUPPLEMENTAL` 事实进入已注册的服务端 action。
- 测试数据只使用保留地址、哈希标识和虚构账号，不包含 cookie、token 或原始敏感值。

### 19.3 必须覆盖的使用场景

两个应用都必须实现以下宿主场景：

```text
内置登录失败 action
内置敏感导出 action
继承导出契约的自定义 action
不继承内置契约的独立自定义 action
资源范围授权拒绝 action
程序式非 MVC action
前端补充信号 action
宿主控制 handler
```

敏感导出场景必须同时展示：

- `ResourceIdFact` 由受限参数路径提供。
- `DataCountFact` 由 action-specific `FactBinding` 提供。
- `SensitivityFact` 由显式 contract binding 或可信宿主 Provider 提供。
- 派生自定义导出 action 自动继承导出规则和 `FAIL_CLOSED` 下限。
- 具体 action Provider 不会被错误继承到另一个导出 action。

程序式非 MVC 场景必须使用 action 类型令牌和同一 `MonitoringRuntime`，不能绕过目录直接构造事件。

### 19.4 共享验收矩阵

每个用例拥有稳定 ID，并由 Boot 2/3 共享测试契约执行。最低矩阵如下：

| ID | 场景 | 必须验证的证据 |
| --- | --- | --- |
| `AUD-BOOT-001` | 合法配置启动 | 所有目录冻结，MyBatis schema 可用 |
| `AUD-BOOT-002` | 未注册 action | 启动失败并定位 Bean 与方法 |
| `AUD-BOOT-003` | 缺少必需 FactBinding | 启动失败并报告 action、contract、fact |
| `AUD-BOOT-004` | 重复事实提供者 | 启动失败，不使用 Provider 顺序覆盖 |
| `AUD-BOOT-005` | 非法事实来源 | 启动失败并报告来源约束 |
| `AUD-BOOT-006` | contract 合并冲突 | 启动失败，不生成部分运行时 |
| `AUD-BOOT-007` | 缺少 MyBatis/schema | 启动失败，不回退内存仓储 |
| `AUD-BOOT-008` | ENFORCE 控制覆盖不足 | 启动失败并列出缺失控制类型 |
| `AUD-ACT-001` | 内置 action HTTP 调用 | 类型、编码、身份、请求和 outcome 正确 |
| `AUD-ACT-002` | 自定义 action 注册 | 自定义类型与稳定编码一对一 |
| `AUD-ACT-003` | 派生 action 契约继承 | 事实、规则和失败策略不可削弱 |
| `AUD-ACT-004` | 程序式 action | 与注解 action 使用同一目录和事件组装器 |
| `AUD-FACT-001` | 参数事实提取 | 类型、值、来源和路径绑定正确 |
| `AUD-FACT-002` | action-specific Provider | 只应用于显式 action binding |
| `AUD-FACT-003` | contract Provider | 应用于全部契约实现且输出范围受限 |
| `AUD-FACT-004` | Provider 返回未声明事实 | 拒绝事实并记录稳定质量问题 |
| `AUD-FACT-005` | 运行时必需事实为空 | 事件为 INCOMPLETE，只跳过相关规则 |
| `AUD-RULE-001` | 五次登录失败 | 产生预期告警和控制计划 |
| `AUD-RULE-002` | 大量敏感导出 | 类型化事实触发预期导出规则 |
| `AUD-RULE-003` | 无关规则 | 缺失某事实时仍正常评估 |
| `AUD-AUTH-001` | Shiro 已认证主体 | 服务端身份进入事件且不可被覆盖 |
| `AUD-AUTH-002` | 资源范围拒绝 | 宿主授权结论权威，拒绝事件可审计 |
| `AUD-OUT-001` | HTTP 成功 | 框架 outcome 优先于 Provider |
| `AUD-OUT-002` | HTTP 拒绝 | 403/异常不能被 Provider 改写为成功 |
| `AUD-CTL-001` | 控制首次执行 | PENDING 原子占位后进入成功终态 |
| `AUD-CTL-002` | 幂等重放 | 相同幂等键不重复产生宿主效果 |
| `AUD-CTL-003` | 控制失败 | 事件与告警保留，失败结果可查询 |
| `AUD-FE-001` | 合法前端信号 | 只追加允许的 CLIENT_SUPPLEMENTAL 事实 |
| `AUD-FE-002` | 前端伪造 action/身份/outcome | 伪造字段被拒绝或忽略 |
| `AUD-FAIL-001` | OBSERVE_ONLY 系统故障 | 业务继续，分类故障可观测 |
| `AUD-FAIL-002` | FAIL_CLOSED 系统故障 | 业务失败并返回稳定不可用分类 |
| `AUD-DB-001` | 事件与事实持久化 | 标准列、扩展事实、类型和来源可回读 |
| `AUD-DB-002` | 事务回滚 | 部分事件、告警或关联记录不残留 |

实现计划可以增加场景，但不能删除或合并上述验收语义。

### 19.5 测试用例标准

共享验收测试必须遵循以下标准：

- 测试名描述可观察行为，不描述内部方法。
- 每个测试关联一个或多个稳定验收 ID；全部 ID 必须在两个子模块中执行。
- 使用 Given/When/Then 结构，但不添加重复叙述性注释。
- 每个用例独立重置数据库和宿主控制状态，禁止依赖执行顺序。
- 时间窗口使用可控 `Clock`，禁止 `Thread.sleep` 和依赖机器当前时间。
- HTTP 用例按场景同时断言适用的响应、数据库证据、事实来源、规则结果和控制结果；只断言 Bean 存在不算验收。
- 启动失败用例断言稳定错误码及位置，不绑定完整异常文案。
- 安全用例必须包含低信任来源无法覆盖高信任事实的负向断言。
- 并发幂等用例使用受控并发屏障，不使用概率性循环。
- 测试不能替换 MyBatis 为 fake，也不能直接调用 Mapper 插入期望结果。
- Boot 2/3 允许不同的启动和 Servlet 适配代码，但共享业务断言必须完全一致。
- 测试失败输出不得包含原始请求体、凭证、session 或敏感字段值。

### 19.6 评估产物与门禁

`integration-audit/README.md` 维护“规范要求 → 宿主实现 → 验收 ID”的可追踪矩阵。共享测试在 `target` 下生成机器可读验收汇总，至少包含版本、验收 ID、通过/失败和耗时，不提交生成结果。

必须执行：

```bash
mvn -pl integration-audit/spring2-web -am verify
mvn -pl integration-audit/spring3-web -am verify
mvn -pl integration-audit -am verify
mvn clean verify -DskipTests=false
```

依赖检查还必须证明：

- Boot 2 运行时不存在 `jakarta.servlet` 适配引用。
- Boot 3 运行时不存在 `javax.servlet` 适配引用。
- 两个审计应用生产源码不导入 `core`、MyBatis 内部实现包或 Starter 内部包。
- 两个应用执行了完全相同的共享验收 ID 集合。

任何共享验收 ID 在任一 Boot 版本缺失或跳过，都视为 reactor 验证失败。

## 20. 文档信息架构与治理

### 20.1 关联角色与权责

文档不再按形成时间或技术名词平铺，而是按使用者完成的任务组织。角色是职责而非具体人员：

| 关联角色 | 负责决定 | 需要的文档 | 不应承担 |
| --- | --- | --- | --- |
| 宿主集成负责人 | 依赖、action、fact、SPI 接入 | 快速接入、Action/Fact 用法 | 内部实现与表字段设计 |
| 安全与审计负责人 | 信任边界、规则、控制、证据要求 | 安全模型、规则与控制、审计矩阵 | Starter 装配细节 |
| DBA/SRE | schema、事务、容量、故障处置 | 数据库与运维手册 | 业务 action 定义 |
| 框架维护者 | 模块边界、扩展契约、测试策略 | 架构、开发与测试规范、ADR | 宿主授权结论 |
| 发布负责人 | 版本范围、兼容性和发布门禁 | 路线图、发布清单、验收报告 | 重复维护技术参考 |

每份文档顶部只保留稳定元数据：

```text
Audience: 主要阅读角色
Owner: 负责维护的角色
Source of truth: 对应代码、schema 或目录
Validated by: 对应测试或验收 ID
```

不填写容易过期的个人姓名和人工“最后更新日期”；变更历史由 Git 提供。实际责任人由仓库权限和后续 CODEOWNERS 配置管理，不在正文复制。

### 20.2 目标目录

最终公开文档控制在以下结构，不再为同一主题维护中英文全文镜像：

```text
README.md                         中文项目入口，保持简短
README.en.md                      精简英文入口，不复制完整手册
docs/README.md                    按角色和任务导航
docs/integrators/
├─ getting-started.md             最短可运行接入
├─ actions-and-facts.md           ActionContract、FactBinding、程序式入口
└─ frontend-signals.md            前端补充证据边界
docs/security/
├─ security-model.md              身份、授权、来源和失败策略
└─ rules-and-controls.md           规则前置事实、控制覆盖与审计
docs/operators/
├─ database.md                    MyBatis schema、事务、索引和迁移
└─ operations.md                  配置、上线、观测和故障处置
docs/maintainers/
├─ architecture.md                模块、运行时编译和事件管线
├─ development.md                 构建、测试、Javadoc 和贡献约束
├─ release.md                     路线图与发布门禁
└─ decisions/                     已批准 ADR
docs/reference/
├─ catalogs.md                    action/fact/rule/control 生成参考
└─ errors.md                      稳定错误码与宿主映射
integration-audit/README.md       规范到验收 ID 的可追踪矩阵
```

`docs/superpowers` 只作为重构期间的内部设计与实施材料，不出现在公开导航。重构完成后，仍有效的架构决定提炼为 ADR；临时计划不复制到公开手册，Git 历史承担归档职责。

### 20.3 现有文档迁移

| 现有文件 | 处理方式 | 唯一去向 |
| --- | --- | --- |
| `README.md` | 重写并压缩 | 项目定位、最短构建命令、角色导航 |
| `README.en.md` | 重写并压缩 | 英文概览和公开入口链接 |
| `docs/集成指南.md` | 拆解后删除 | `integrators/*`、`security/*`、`operators/*` |
| `docs/integration-guide.en.md` | 删除全文镜像 | `README.en.md` 保留精简入口 |
| `docs/集成审计与基础项目验收.md` | 合并后删除 | `integration-audit/README.md` |
| 中英文错误规范 | 合并后删除原文件 | `reference/errors.md` |
| 中英文架构与事务说明 | 合并后删除原文件 | `maintainers/architecture.md`、`operators/database.md` |
| `docs/领域模型与数据设计.md` | 拆解后删除 | 架构、数据库和安全模型对应章节 |
| `docs/MyBatis标准化ORM与架构设计评审稿.md` | 提炼决定后删除评审稿 | `operators/database.md` 和 ADR |
| 中英文路线图与 1.0 排期 | 合并并删除旧版本 | `maintainers/release.md` |
| `docs/Javadoc生成说明.md` | 合并后删除 | `maintainers/development.md` |
| `output/pdf/*.pdf` | 从源码仓库移除 | 发布流程按需生成，不作为事实来源 |

过期文档不移动到仓库内 `archive` 目录。Git 已提供历史恢复能力，保留一份失效副本只会继续参与搜索并误导使用者。

### 20.4 单一事实来源

每类信息只能有一个权威来源：

| 信息 | 权威来源 | 文档处理 |
| --- | --- | --- |
| action/fact/rule/control 定义 | 冻结目录的代码定义 | 生成 `reference/catalogs.md` 并校验差异 |
| Spring 配置项 | 配置属性类和 metadata | 文档只解释策略与示例，不手抄完整字段表 |
| 数据表与索引 | `monitoring-schema.sql` | `database.md` 解释生命周期，不复制完整 DDL |
| 错误码 | 错误码类型 | 生成或测试校验 `reference/errors.md` |
| 接入示例 | `integration-audit` 可编译宿主源码 | 文档链接到示例，仅保留一个最短片段 |
| 验收要求 | integration audit ID 矩阵 | 其他文档引用 ID，不复制验收清单 |
| 架构决定 | ADR | README 和手册只链接，不复述评审过程 |

生成型参考必须在 `verify` 阶段重新生成到 `target` 并与已提交版本比较。代码目录变化但参考未更新时构建失败。

### 20.5 精简规则

- `README.md` 目标不超过 120 行，只回答“是什么、如何构建、下一步读什么”。
- 每份角色手册目标不超过 250 行；超出时先删除重复，再判断是否按独立任务拆分。
- 同一个代码示例只完整出现一次；其他位置使用链接和一行说明。
- 不在 README 复制 action 矩阵、表字段、错误码或发布清单。
- 不保留已经由类型系统、schema 或配置 metadata 精确表达的手工表格。
- 使用面向任务的标题，例如“注册自定义导出 action”，不使用“其他说明”“高级内容”。
- 规范使用 MUST/SHOULD/MAY 或明确中文等价词，背景说明与强制要求分开。
- 删除“当前”“未来”“暂时”等没有版本边界的措辞；版本差异进入 release 或 ADR。
- 公共 Java API 的 Javadoc 保持简洁英文技术说明；叙述性项目手册以中文为唯一正式版本。
- 英文 README 只维护稳定概览，不承诺与全部中文手册逐段镜像。

### 20.6 文档测试与门禁

文档作为代码进入 `verify`：

- 校验全部相对链接、标题锚点和本地文件引用。
- 校验公开文档不存在已删除类型、旧包名、字符串 action、内存回退和 `@ControlTrigger` 示例。
- 编译或直接复用 `integration-audit` 中被引用的示例代码，禁止不可运行的伪代码充当接入规范。
- 校验所有公开 action、fact、rule、control 和错误码都出现在生成参考中，且没有多余条目。
- 校验 `integration-audit/README.md` 中的验收 ID 与两个 Boot 测试实际执行集合一致。
- 校验生成 PDF、临时评审稿和 `target` 产物未被提交。
- Markdown 格式和链接检查优先采用成熟 Maven/跨平台工具；只有领域一致性检查使用小型 JUnit 测试。

文档验收不以“文件存在”为标准，而以目标角色能否沿导航完成任务、示例是否编译、参考是否与代码一致为标准。

## 21. 实施分解

本次重构分四个阶段实施，最终一次性交付，不保留兼容层。

### 阶段一：类型化契约与启动编译

- 重构 `api` 包。
- 建立 action、action contract、fact 类型、定义、目录和贡献接口。
- 建立具体 action binding、contract binding 与纯取值 Provider 契约。
- 建立 `SpringMonitoringRuntimeCompiler`。
- 实现严格扫描、交叉校验和错误聚合。

### 阶段二：事件与规则管线

- 引入 `ActionExecution`、`ActionOutcome` 和 `SecurityEventAssembler`。
- 移除万能 Draft、字符串规则事实和 EventPolicy。
- 让规则声明事实依赖和来源要求。

### 阶段三：MyBatis 与控制状态机

- 删除内存仓储。
- 拆分持久化职责并由 MyBatis 唯一实现。
- 更新基线 schema；由于项目未投入使用，不保留旧结构兼容层。
- 实现控制覆盖率、PENDING 幂等占位和明确终态。

### 阶段四：Spring 适配、集成审计、文档与外围收敛

- 抽取 Boot 2/3 公共执行内核。
- 收紧前端信号。
- 将 `integration-audit` 改造为新规范的真实宿主，并实现共享验收矩阵。
- 删除审计应用对旧字符串 action、旧属性注解、反射控制和手工内部 MyBatis 初始化的使用。
- 重写 Maven 资源模板。
- 迁移重复测试并加入架构、重复代码检查。
- 按关联角色重组并精简公开文档，删除旧镜像、评审稿和已提交 PDF。
- 从运行时目录、错误码和集成审计生成可校验参考。

每个阶段结束时运行 focused tests 和完整 reactor。临时迁移代码只能存在于阶段内部，阶段四结束时不存在旧公开类型、过渡适配器或双轨数据模型。

## 22. 验收标准

1. 所有 `@MonitorAction` 都引用已注册的具体 `ActionType`，不存在运行时隐式 action。
2. 内置和自定义 action 通过 `ActionContract` 继承事实、规则和最低失败策略，子 action 无法削弱约束。
3. 所有内置规则事实都使用 `FactType<T>`，规则源码不直接读取字符串属性名。
4. action、action contract、fact、rule 和 control 目录在应用启动前完成校验并冻结。
5. 同一事实只有一个允许的运行时提供者，来源冲突无法通过启动。
6. Provider 不决定 action 语义；事实要求属于 action contract，提供关系属于 FactBinding。
7. `SecurityEvent` 只能由 `SecurityEventAssembler` 从明确来源构建。
8. 生产代码不存在内存仓储或无数据库回退。
9. ENFORCE 启动校验覆盖所有启用规则可能产生的控制动作。
10. 前端信号不能决定服务端 action、身份、结果或自动控制。
11. Boot 2 只使用 `javax.servlet`，Boot 3 只使用 `jakarta.servlet`，公共行为无复制实现。
12. Maven 插件生成的新模板符合新的分离式宿主 SPI。
13. ArchUnit 和重复代码检查进入 `verify` 生命周期。
14. `mvn clean verify -DskipTests=false` 通过，两个真实 Web 集成模块通过验收。
15. `integration-audit` 的全部共享验收 ID 在 Boot 2 与 Boot 3 中均执行且通过。
16. 集成审计应用只使用公开规范，不依赖旧 API 或内部实现捷径。
17. 公开文档按关联角色导航，每项规范只有一个事实来源。
18. 公开文档不存在旧 API、内存回退、字符串 action 或反射控制示例。
19. 目录与错误码参考由代码生成或校验，文档漂移会使 `verify` 失败。
20. 仓库不跟踪生成 PDF、临时评审稿或过期文档副本。

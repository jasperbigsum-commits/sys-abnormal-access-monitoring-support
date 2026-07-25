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

内置 action 的公开类型可以被宿主引用，但外部代码无法实现 `BuiltInActionType`。业务分类不使用接口继承表达；事件类型、资源类型和规则标签属于 `ActionDefinition`。

```java
@MonitorAction(BuiltInActions.ReportExport.class)
public ExportResult export(ExportRequest request) {
    // business implementation
}
```

自定义 action 实现公开接口，并通过贡献者注册：

```java
public final class OrderRefundAction implements ActionType {
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

### 5.2 ActionDefinition

`ActionDefinition` 是 action 全局静态语义的唯一所有者，至少包含：

- 稳定持久化编码。
- `SecurityEventType`。
- 逻辑资源类型。
- 规则选择标签。
- 显式参与的 `RuleType` 集合。
- 必需和可选事实集合。
- 允许的事实来源。
- 运行时失败策略。

`@MonitorAction` 不再声明 `eventType`、`resourceType`、`ruleTags`、静态属性或 Provider 类型。同一个 action 可以被多个方法引用，但只能注册一份定义。不同贡献者重复注册相同类型或编码，即使内容一致，也属于配置冲突。

持久化编码必须匹配小写 `domain:verb` 结构；domain 和 verb 只允许字母、数字及内部连字符，整体长度不超过 128。目录将其转换为不可变 `ActionId`，类型令牌和 `ActionId` 必须保持一对一关系。

### 5.3 ActionCatalog

启动阶段先加载内置定义，再执行宿主 `ActionContributor`。完成扫描和交叉校验后目录冻结。运行期间只能通过 action 类型令牌获取不可变的 `RegisteredAction`，不能隐式创建 action 或按字符串查找未注册定义。

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

### 6.5 ActionFactProvider

动态事实提供器由宿主配置决定，不由业务方法注解引用。每个 Provider 声明支持的 action 类型和负责的 fact 类型。启动阶段预绑定 Provider，校验 action 存在、事实已声明、来源合法且没有重复所有者。

Provider 只能产生 `ActionFacts`，不能设置 action、身份、请求、执行结果或耗时。参数绑定和 Provider 同时负责同一 fact 时启动失败，不使用执行顺序覆盖。

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

`ActionDefinition` 显式列出参与的 `RuleType`。规则引擎不再通过运行时事件 Predicate 猜测 action 是否适用；它只评估当前 action 已声明参与且前置事实完整的规则。规则稳定 ID 只由对应 `RuleDefinition` 提供。

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
ActionDefinition 声明的事实
→ ActionBinding 可提供的事实
→ FactDefinition 允许的来源
→ ActionDefinition 显式参与的 RuleType
→ DetectionRule 要求的事实与来源
```

以下情况阻止启动：

- action 声明会参与某条规则，但未声明规则必需事实。
- 必需事实没有参数绑定、可信上下文来源或 Provider。
- Provider 来源不满足规则要求。
- 规则 ID、action 编码或 fact 稳定键重复。

运行时必需事实取值为空时，事件标记为 `INCOMPLETE`，只跳过依赖该事实的规则；无关规则继续执行并记录稳定诊断。

## 8. 执行与事件组装

### 8.1 模型职责

```text
ActionDefinition  全局静态语义
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

1. 加载内置 action、fact、rule 和 control 定义。
2. 执行宿主贡献者。
3. 扫描 Spring Bean 的类型、方法和接口注解。
4. 解析最具体方法、参数事实和 Provider。
5. 校验代理条件、类型、来源、规则事实覆盖率和控制覆盖率。
6. 生成不可变 `ActionBinding`。
7. 冻结全部目录。
8. 发布不可变 `MonitoringRuntime`。

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
- 参数路径、Provider 覆盖率和重复所有者。
- 全量启动错误聚合。
- Boot 2/3 各一套自动配置和真实 HTTP 验收测试。

当前两套大型重复 action 测试迁移到 `spring-support`。Starter 中只保留版本差异测试。

### 18.5 架构与完整验证

- ArchUnit 禁止逆向依赖和框架泄漏。
- 重复代码检查对 Boot namespace 适配使用精确排除。
- `mvn clean verify -DskipTests=false` 是最终验收命令。
- 分别运行 Spring 2 与 Spring 3 真实集成应用验收。

## 19. 实施分解

本次重构分四个阶段实施，最终一次性交付，不保留兼容层。

### 阶段一：类型化契约与启动编译

- 重构 `api` 包。
- 建立 action/fact 类型、定义、目录和贡献接口。
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

### 阶段四：Spring 适配与外围收敛

- 抽取 Boot 2/3 公共执行内核。
- 收紧前端信号。
- 重写 Maven 资源模板。
- 迁移重复测试并加入架构、重复代码检查。
- 更新中英文文档和集成审计应用。

每个阶段结束时运行 focused tests 和完整 reactor。临时迁移代码只能存在于阶段内部，阶段四结束时不存在旧公开类型、过渡适配器或双轨数据模型。

## 20. 验收标准

1. 所有 `@MonitorAction` 都引用已注册 `ActionType`，不存在运行时隐式 action。
2. 所有内置规则事实都使用 `FactType<T>`，规则源码不直接读取字符串属性名。
3. action、fact、rule 和 control 目录在应用启动前完成校验并冻结。
4. 同一事实只有一个允许的运行时提供者，来源冲突无法通过启动。
5. `SecurityEvent` 只能由 `SecurityEventAssembler` 从明确来源构建。
6. 生产代码不存在内存仓储或无数据库回退。
7. ENFORCE 启动校验覆盖所有启用规则可能产生的控制动作。
8. 前端信号不能决定服务端 action、身份、结果或自动控制。
9. Boot 2 只使用 `javax.servlet`，Boot 3 只使用 `jakarta.servlet`，公共行为无复制实现。
10. Maven 插件生成的新模板符合新的分离式宿主 SPI。
11. ArchUnit 和重复代码检查进入 `verify` 生命周期。
12. `mvn clean verify -DskipTests=false` 通过，两个真实 Web 集成模块通过验收。

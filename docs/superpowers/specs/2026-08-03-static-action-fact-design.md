# 方法级静态 Action Fact 设计

## 背景与目标

当前 `@MonitorAction` 方法只能通过参数上的 `@ActionFact`、方法体内的
`MonitoringFacts.put(...)` 或宿主注册的 `FactBinding` 提供事实。对于资源类型、固定敏感级别等
与方法声明稳定绑定的事实，这些方式会产生没有必要的参数或运行时代码。

本设计增加一个方法级、可重复的静态 Fact 注解。静态值必须进入现有类型化 Fact 契约，参与
checkpoint 和无 checkpoint 的完整性、来源及有效性校验，不能绕过 `ActionDefinition` 或
`FactDefinition`。

## 公共 API

在 `api.fact` 包新增 `@StaticActionFact`：

```java
@MonitorAction(BuiltInActions.ReportExport.class)
@StaticActionFact(fact = BuiltInFacts.ResourceId.class, value = "fixed-report")
public void export() {
    MonitoringFacts.put(BuiltInFacts.DataCount.class, 100L);
    MonitoringGate.checkpoint();
}
```

注解仅允许标注方法，运行期保留并支持重复声明。属性如下：

- `fact`：目标 `FactType` token；
- `value`：Fact codec 的稳定字符串表示。

重复能力通过注解内部的公开容器 `StaticActionFact.List` 提供，避免增加只有容器用途的顶层类型。
该 API 保持 Java 8 兼容。

不扩展 `@MonitorAction`：嵌套数组会让单个动作声明过重。不扩展参数 `@ActionFact`：参数路径提取与
方法常量是不同的数据来源和失败阶段，混用会使注解语义含糊。

## 编译与类型校验

`MonitorActionContractValidator` 在编译方法绑定时读取静态注解，并通过 `FactCatalog` 找到对应的
`FactDefinition`。字符串值调用 `FactDefinition.decode(...)`，因此字符串、Long、Boolean、枚举和
其他自定义 codec 使用相同的归一化与 validator 规则。

方法绑定缓存归一化后的不可变 `ActionFacts`，调用期间不再重复解析常量。下列配置在方法契约编译
阶段失败：

- Fact 未由 Action 声明为必填或可选；
- Action 或 FactDefinition 不允许 `HOST_PROVIDER` 来源；
- 静态字符串不能被 codec 解码或未通过 Fact validator；
- 同一方法重复声明同一个静态 Fact；
- 静态 Fact 与参数 `@ActionFact` 或适用于该 Action 的 `FactBinding` 生产相同 Fact。

配置错误统一包装为现有 `MonitoringConfigurationException`，避免泄漏 codec 的实现异常作为启动期
错误类型。

## 来源与执行流程

方法级静态 Fact 是由宿主服务端代码声明的可信常量，来源记为现有 `HOST_PROVIDER`。本次不新增
`FactSource`，避免要求所有 Action、Fact 和规则目录迁移来源矩阵。

三套 `TypedMonitorActionAspect` 按以下顺序合并事实：

1. 方法级静态 Fact，来源 `HOST_PROVIDER`；
2. 参数 `@ActionFact`，来源 `METHOD_PARAMETER`；
3. `MonitoringFacts.put(...)` 运行时 Fact，来源 `HOST_PROVIDER`。

合并后继续调用统一的 `ActionFactExtractor.validate(...)`。因此静态 Fact 会在
`MonitoringGate.checkpoint()` 决策前参与必填与有效性校验；没有 checkpoint 时，也会在方法正常
返回后的监控收尾阶段参与同一校验。

静态 Fact 与运行时 `MonitoringFacts.put(...)` 的冲突只能在调用期发现，合并时抛出
`IllegalStateException`，不允许后写覆盖。静态 Fact 与参数或注册 Provider 的冲突可在编译期发现。

## 失败与安全语义

- 注解值只表达 Fact codec 的稳定文本，不支持 SpEL、属性路径、类名、方法调用或占位符解析。
- 注解不能声明密码、令牌、Cookie、原始请求体或其他敏感载荷；现有 FactCatalog 的敏感性与存储
  策略仍然生效。
- codec 解码、长度、类型和业务 validator 校验均在方法契约编译时执行。
- checkpoint 之前发现的静态配置错误不能被 action failure policy 转换为放行。
- 无 checkpoint 的方法只能在业务方法返回后完成最终事实校验，因此不能用静态注解替代需要在副
  作用前执行的 `MonitoringGate.checkpoint()`。

## 测试范围

- API：验证注解运行期可见、仅支持方法目标且可重复读取。
- Spring Support：验证静态值解码和归一化，以及未声明 Fact、非法值、非法来源、重复生产者冲突。
- Spring 2 与 Spring 3：验证静态必填 Fact 可通过 checkpoint，并以 `HOST_PROVIDER` 来源持久化。
- Spring 2 legacy：验证无 checkpoint 收尾路径合并并校验静态 Fact。
- 回归：参数 Fact、运行时 Fact、FactBinding 和既有 checkpoint 行为保持不变。

## 兼容性与非目标

该能力是向后兼容的 API 新增，不修改数据库 schema，不改变已有注解签名，也不引入 Spring、
`javax.*` 或 `jakarta.*` 到公共模块。

本次不支持动态配置替换、环境变量、国际化文本、类型级继承、组合注解或异步上下文传播。需要动态
值时继续使用参数 `@ActionFact`、`MonitoringFacts.put(...)` 或 `FactBinding`。

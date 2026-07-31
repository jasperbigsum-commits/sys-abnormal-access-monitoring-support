# 认证监测与稳定代码治理设计

日期：2026-08-01  
状态：已确认，待实施计划

## 1. 背景

当前认证失败接入同时存在代码缺陷和领域模型缺陷：

- Spring 3 验收服务同时通过 `MonitoringRecorder` 和 `MonitoringService` 提交同一次登录失败，导致重复事件。
- 登录失败时把请求中的账号写入 `IdentityContext.userId`，将“尝试登录的账户”误认为“已认证行为人”。
- `LoginFailure` 同时作为 Action、事件类型和 `FAILURE` Outcome，失败语义重复。
- 正常的凭据或验证码拒绝被表示成异常失败，并被错误归类为授权异常。
- 未知账号和登录成功路径没有形成完整事件，AUTH-02 的跨账号失败率分母不可靠。
- AUTH-01/02 已读取字符串属性 `attempted_account_hash`，但 typed Fact 目录和认证接入没有提供该事实。
- `ActionOutcome`、授权、控制、通知和规则诊断散落使用任意字符串代码，缺少命名、唯一性、适用范围和生命周期约束。

本设计同时修正认证领域边界和全组件稳定代码治理。项目尚未发布，因此直接完成强类型迁移，不保留任意字符串兼容入口。

## 2. 目标

1. 明确区分已认证行为人、尝试登录主体、被监测操作、最终结果、原因和技术失败分类。
2. 为认证提供一个简洁的程序式门面；普通调用者只传登录信息和认证结论，不生成或接触内部主体 key。
3. 支持多租户/账号域，以及用户名、邮箱、手机号等登录别名归并。
4. 让账户和 IP 维度规则能够稳定聚合，并让下一次认证请求消费带 TTL 的控制。
5. 禁止公共 API 接受任意原因字符串；允许宿主通过受控目录注册自定义代码。
6. 对认证、授权、动作执行、输入诊断、规则评估、控制、通知和管理 API 采用一致的稳定代码治理。
7. 保持 `api` 与 `core` 框架无关，保持 Spring Boot 2/3 语义一致，并避免新增 Maven 模块。

## 3. 非目标

- 监测组件不验证密码、验证码、MFA、Token 或会话凭据。
- 监测组件不取代宿主认证、授权、账号状态或会话管理结论。
- 不为每个原因建立一个 marker class。
- 不把稳定代码目录建设成文案、国际化、严重度或运营工作流平台。
- 不根据单纯用户名失败自动永久锁号。
- 首期不隐式支持主体保护密钥轮换；密钥轮换需要独立的数据和控制迁移设计。

## 4. 核心领域模型

### 4.1 已认证行为人与尝试登录主体

`IdentityContext` 只表示宿主已经确认的行为人：

- 登录失败时使用 `IdentityContext.anonymous()`，`user_id` 必须为 `null`。
- 登录成功时由宿主显式提供认证后的 `IdentityContext`。
- 请求进入时 Starter 建立的身份快照可能仍然匿名，因此登录成功事件不能继续复用该快照。

`LoginSubjectInput` 表示一次登录尝试的瞬时输入：

```java
LoginSubjectInput.of(loginUser, realm)
```

- `loginUser` 是宿主登录接口接收到的用户名、邮箱、手机号或其他登录标识。
- `realm` 是租户、账号域或身份目录边界，不能为空。
- 两者只允许在认证门面和主体解析链的内存中短暂存在。
- 原值不得进入事件、Fact、日志、异常消息、告警或控制记录。

组件内部从输入生成 `LoginSubjectKey`，用于规则聚合和控制查询。普通接入者不生成、不读取也不持久化该 key。

### 4.2 操作、结果、事件和原因

登录被建模为一个操作：

```text
Action  = auth:login
Outcome = SUCCESS | DENIED | FAILURE
```

`ActionDefinition` 支持按 Outcome 选择事件类型：

```text
SUCCESS -> LOGIN_SUCCESS
DENIED  -> LOGIN_FAILURE
FAILURE -> LOGIN_FAILURE
```

普通 Action 继续使用单一默认事件类型，只有确实需要时才配置 Outcome 映射。删除内置 `LoginFailure` Action，新增 `BuiltInActions.Login`。

结果语义固定为：

| Outcome | 含义 | ReasonCode | FailureClass |
|---|---|---|---|
| `SUCCESS` | 操作成功完成 | 禁止 | 禁止 |
| `DENIED` | 业务、认证、授权或安全策略明确拒绝操作 | 必须 | 禁止 |
| `FAILURE` | 操作因业务异常或技术异常未完成 | 必须 | 必须 |

密码错误、验证码错误、MFA 错误、账号停用和限流都是 `DENIED`。认证目录不可用、数据库异常或认证服务异常才是 `FAILURE`。

## 5. 稳定代码治理

### 5.1 统一治理不等于统一语义

组件使用一个薄的治理内核，但保留不同代码家族：

| CodeFamily | 解释对象 | 示例存储位置 |
|---|---|---|
| `OUTCOME_REASON` | 动作为什么得到 DENIED 或 FAILURE | `monitoring_security_event.reason_code` |
| `INPUT_DIAGNOSTIC` | 事件或规则为什么无法完整评估 | input issue / evaluation |
| `OPERATIONAL_FAILURE` | 控制或通知为什么执行失败 | control attempt / delivery |
| `API_ERROR` | 组件 API 为什么拒绝调用 | `MonitoringException` |

规则 ID、Action code、Fact key、Control type 和状态枚举是领域标识，不属于原因码，不能为了“统一”而塞进 CodeFamily。

### 5.2 最小公共契约

`api` 提供：

```java
public interface GovernedCode {
    String getCode();
    CodeFamily getFamily();
}

public interface ReasonCode extends GovernedCode {
}
```

以及四个基础类型：

- `CodeFamily`
- `CodeDefinition`
- `StableCodeCatalog`
- `StableCodeContributor`

`CodeDefinition` 只保存治理所需元数据：代码、家族、允许的 Outcome，以及适用的 Action type 或 Action contract。它不保存展示文案、国际化、严重度或处置建议。

`ActionOutcome.denied(...)` 和 `ActionOutcome.failure(...)` 改为接受 `ReasonCode`，删除 `String` 重载。现有领域枚举（例如输入问题码）可以实现 `GovernedCode`；控制和通知的字符串失败类别分别收敛为各自领域枚举。

### 5.3 命名规范

稳定代码格式为：

```text
OWNER.DOMAIN.CAUSE
```

示例：

```text
MON.AUTH.INVALID_CREDENTIAL
MON.AUTH.CAPTCHA_INVALID
MON.AUTHZ.RESOURCE_SCOPE_DENIED
MON.ACTION.INVOCATION_FAILED
ACME.ORDER.CREDIT_REJECTED
```

约束如下：

- 只允许大写 ASCII 字母、数字和下划线，段之间使用点分隔。
- 总长度不超过数据库 `reason_code` 的 128 字符限制。
- `MON` 只归组件内置代码所有。
- 宿主必须配置自己的 OWNER 前缀，且只能注册该前缀下的代码。
- 代码发布后不得改义、复用或静默删除；废弃只能停止新写入并保留历史解析能力。
- 代码不得包含用户名、资源 ID、异常消息或其他动态/敏感内容。

由于项目尚未发布，现有 `MON-xxx` API 错误码迁移到相同的语义命名规范，不保留数字代码别名。

### 5.4 目录生命周期和校验

Starter 的初始化顺序为：

1. 注册组件内置代码。
2. 收集并执行宿主 `StableCodeContributor`。
3. 校验 OWNER、格式、全局唯一性、家族、Outcome 和 Action/Contract 适用性。
4. 冻结 `StableCodeCatalog`。
5. 冻结后禁止新增、替换或改变定义。

下列情况使应用上下文启动失败：

- 重复代码或同一代码不同定义。
- 宿主注册 `MON.*` 或其他 OWNER 的代码。
- ReasonCode 没有允许的 DENIED/FAILURE Outcome。
- ReasonCode 声明 SUCCESS。
- 内置 Action 契约引用未注册代码。

实际记录时，运行时再次校验 ReasonCode 是否适用于当前 Action 和 Outcome，防止宿主在错误位置使用已注册代码。

### 5.5 内置原因组织

使用一个 `BuiltInReasonCodes` 容器和按领域分组的嵌套枚举，不为每个原因创建类型。认证首批原因包括：

| 原因 | Outcome | 说明 |
|---|---|---|
| `MON.AUTH.INVALID_CREDENTIAL` | DENIED | 用户名不存在和凭据错误使用同一内部/外部原因，避免账号枚举 |
| `MON.AUTH.CAPTCHA_REQUIRED` | DENIED | 当前请求缺少已要求的验证码 |
| `MON.AUTH.CAPTCHA_INVALID` | DENIED | 验证码内容不正确 |
| `MON.AUTH.CAPTCHA_EXPIRED` | DENIED | 验证码已过期 |
| `MON.AUTH.MFA_INVALID` | DENIED | 第二认证因子校验失败 |
| `MON.AUTH.ACCOUNT_DISABLED` | DENIED | 宿主确认账号已停用 |
| `MON.AUTH.ACCOUNT_LOCKED` | DENIED | 宿主确认账号已锁定 |
| `MON.AUTH.RATE_LIMITED` | DENIED | 请求被有效限流控制拒绝 |
| `MON.AUTH.AUTHENTICATION_UNAVAILABLE` | FAILURE | 认证依赖不可用 |

验证码不是 MFA；CAPTCHA 和 MFA 必须使用不同 `AuthenticationStage` 与原因码。

### 5.6 内部原因与外部响应

事件 `ReasonCode` 是内部观测和规则输入，不是宿主 HTTP/API 错误码。宿主响应必须经过自己的安全映射，不能直接回显内部原因。

- 未知账号与密码错误对外统一为凭据错误。
- 账号停用、锁定和内部账号状态不得向匿名调用者证明账号存在。
- 认证依赖异常对外只暴露宿主定义的通用服务错误，不返回异常类型、目录信息或内部代码目录。
- 管理端在经过认证和授权后可以查询内部 ReasonCode，但展示文案仍由管理应用负责。

## 6. 认证专用门面

### 6.1 公共 API

`api` 提供框架无关接口：

```java
public interface AuthenticationMonitor {
    ActionDecision preCheck(LoginSubjectInput subject);

    void recordDenied(LoginSubjectInput subject,
                      AuthenticationStage stage,
                      ReasonCode reason);

    void recordFailure(LoginSubjectInput subject,
                       AuthenticationStage stage,
                       ReasonCode reason,
                       FailureClass failureClass);

    void recordSuccess(LoginSubjectInput subject,
                       IdentityContext authenticatedIdentity);
}
```

`AuthenticationStage` 只包含当前需要的三个值：

```text
CREDENTIAL, CAPTCHA, MFA
```

该门面内部选择 Action、Outcome、事件类型和 typed Facts。调用者不能传事件类型、内部主体 key、原始 attribute 或任意原因字符串。

### 6.2 主体规范化与保护

只提供一个宿主扩展点：

```java
public interface LoginSubjectCanonicalizer {
    String canonicalize(LoginSubjectInput input);
}
```

职责：

- 合并同一账号的用户名、邮箱和手机号等别名。
- 在 `realm` 内返回稳定的规范账户材料。
- 对未知账号返回稳定、规范化的输入材料，使用户名扫描仍可按尝试主体统计。
- 不执行密码验证，也不把“能解析账号”当成认证成功。

组件内部的 `LoginSubjectKeyFactory` 使用以下输入生成 key：

```text
algorithm-version + realm + canonical-material
```

保护算法使用 HMAC-SHA-256 和部署时强制配置的稳定密钥，输出包含算法版本但不包含密钥 ID或原始材料。密钥必须在实例、重启和滚动发布之间一致；缺失或过短时 Starter 启动失败。

首期改变密钥会重置历史聚合和未过期主体控制，因此只能通过显式运维迁移执行，不能作为普通配置热更新。

### 6.3 认证数据流

```text
宿主收到 loginUser + realm
  -> AuthenticationMonitor.preCheck(subject)
  -> 内部派生账户 key，并结合可信 source IP 查询有效控制
  -> 返回 ActionDecision
  -> 宿主执行 CAPTCHA/MFA/限流要求并完成认证判断
  -> recordDenied / recordFailure / recordSuccess
  -> 组装 auth:login 事件和 typed Facts
  -> AUTH-01/02/03 评估
  -> 持久化带 TTL 的账户或 IP 控制
  -> 下一次 preCheck 消费控制
```

`preCheck` 复用现有 `ActionDecision`：

- 无控制时返回 `ALLOW`。
- `REQUIRE_CAPTCHA` / `REQUIRE_MFA` 进入 requirements。
- `RATE_LIMIT`、`DENY` 等进入 controls/disposition。
- matched rule IDs 仅作可信诊断，不返回给匿名 HTTP 客户端。

宿主仍负责真正展示和验证 CAPTCHA/MFA，并把决定映射为自身 HTTP 响应。组件不提供登录 Controller 或响应模型。

### 6.4 失败隔离

- `preCheck` 是宿主显式接入的补充控制边界；存储不可用时遵循认证控制专用的可配置失败策略，默认只失去补充控制，不能绕过宿主原有密码、账号状态或授权校验。
- `recordDenied`、`recordFailure` 和 `recordSuccess` 在宿主认证结论确定后调用；监测写入失败不能把拒绝改成允许，也不能把成功改成失败。
- 目录配置错误在启动期失败；运行期管线错误进入组件健康、日志和失败报告器，日志只包含稳定代码、request ID 和 trace ID。

## 7. Fact、规则和控制

### 7.1 Typed Facts

新增两个内置 Fact：

- `LoginSubjectKey`：组件内部生成的受保护规则主体。
- `AuthenticationStageFact`：`CREDENTIAL`、`CAPTCHA` 或 `MFA`。

两者来源固定为 `HOST_PROVIDER`，普通 `MonitoringRecorder` 调用者不能从方法参数或客户端补充它们。认证门面是内置登录 Action 的标准提供者。

规则通过 typed Fact 读取，不再调用：

```java
event.getAttribute("attempted_account_hash")
```

`SecurityEventAssembler` 不再为每个扩展 Fact 手工增加 `putAttribute`。标准列继续显式组装；扩展 Fact 通过通用 Fact 持久化和规则访问机制流转。

### 7.2 认证规则

AUTH-01：

- 作用于 `BuiltInActions.Login`。
- 当前事件必须为 `DENIED` 登录结果。
- 按 `LoginSubjectKey` 聚合指定窗口内的凭据/CAPTCHA 失败。
- 默认生成短 TTL 的 `REQUIRE_CAPTCHA` 和必要的渐进延迟，不永久锁号。

AUTH-02：

- 作用于 `BuiltInActions.Login`。
- 按可信 source IP 统计不同 `LoginSubjectKey`。
- 使用完整的 LOGIN_SUCCESS 与 LOGIN_FAILURE 事件计算失败比例。
- 命中后只限制来源 IP，不对被尝试的账号集合执行锁定。

AUTH-03：

- 作用于 `BuiltInActions.Login`。
- 使用 `MON.AUTH.ACCOUNT_DISABLED` 或可信账号状态 Fact 判断。
- 宿主账号状态检查本身必须同步拒绝；规则负责事件、告警和补充控制。

### 7.3 控制闭环

账户控制 subject 使用内部 `LoginSubjectKey`，IP 控制 subject 使用规范的可信 IP subject。`AuthenticationMonitor.preCheck` 同时查询两者，并只消费未过期、执行有效的控制。

单纯账号失败不能触发长期 `LOCK` 或永久 `DENY`，防止攻击者通过反复提交受害者用户名造成拒绝服务。长期账号控制需要宿主账号策略或更强的可信证据。

## 8. 模块组织

### 8.1 api

- 稳定代码契约、目录、定义和 Contributor。
- `BuiltInReasonCodes`。
- `AuthenticationMonitor`、`LoginSubjectInput`、`AuthenticationStage`、`LoginSubjectCanonicalizer`。
- `BuiltInActions.Login` 和 Action Outcome 事件映射契约。
- `ActionOutcome` 强类型原因码签名。

### 8.2 core

- `DefaultAuthenticationMonitor`。
- 内部 `LoginSubjectKeyFactory`。
- 稳定代码定义与 Action/Outcome 适用性校验。
- Outcome 到事件类型映射。
- AUTH-01/02/03 typed Fact 评估和认证控制查询端口。

### 8.3 spring-support、spring2-starter、spring3-starter

- 使用 `MonitoringContextAccessor` 取得当前可信请求。
- 收集宿主 `StableCodeContributor` 和唯一 `LoginSubjectCanonicalizer`。
- 装配主体保护密钥、认证门面和控制查询实现。
- 对缺失、重复或无效配置执行 Boot 2/3 相同的启动校验。
- `@MonitorAction` 继续负责通用方法动作；认证使用专用门面，不要求注解识别业务返回对象。

### 8.4 mybatis

- `reason_code` 继续保存稳定字符串，无需增加文案或枚举表。
- typed Fact 表继续保存 `LoginSubjectKey` 和 `AuthenticationStageFact`。
- 增加适合按 `fact_key + value_text` 查询历史的索引。
- 增加按 system、subject、control type、status 和 expiry 查询有效认证控制的仓储能力。

### 8.5 integration-audit

- Spring 2/3 认证服务只调用 `AuthenticationMonitor`。
- 删除手工 `ActionExecution`、伪造 `IdentityContext`、重复提交和手工内部 key。
- 验收账号别名、realm、未知账号、成功登录、密码失败、CAPTCHA 失败、账号停用和控制消费。

## 9. 迁移顺序

### 阶段一：稳定代码治理

1. 增加治理契约和目录。
2. 迁移 `ActionOutcome` 与 `AuthorizationDecision` 到强类型原因。
3. 将输入诊断、规则跳过、控制失败、通知失败和 API 错误迁移到各自受控枚举。
4. 删除公共 String 原因入口和散落常量。

### 阶段二：认证领域

1. 增加 `BuiltInActions.Login` 与 Outcome 事件映射。
2. 增加认证门面、主体输入、规范化扩展点和内部 key。
3. 登录失败保持匿名；成功显式使用认证后身份。
4. 未知账号与凭据错误统一记录 `INVALID_CREDENTIAL`。

### 阶段三：规则和控制

1. 增加认证 typed Facts。
2. AUTH-01/02/03 改用 Login Action、Outcome 和 typed Facts。
3. 补齐 LOGIN_SUCCESS 并修正 AUTH-02 分母。
4. `preCheck` 消费账户/IP 有效控制。

### 阶段四：集成和持久化

1. 增加 MyBatis 索引和控制查询。
2. Spring 2/3 验收应用迁移到相同门面。
3. 更新集成指南、领域模型、架构运维说明和 schema 迁移注释。
4. 删除旧 `LoginFailure` Action 和 `attempted_account_hash` 字符串属性兼容逻辑。

## 10. 测试与验收

### 10.1 API 和目录

- 裸字符串原因 API 不再编译存在。
- 重复代码、非法格式、错误 OWNER、ReasonCode 允许 SUCCESS 时启动失败。
- ReasonCode 用于错误 Action 或 Outcome 时被拒绝。
- Catalog 冻结后不可修改。
- 宿主 enum 可以通过 Contributor 注册并被正常使用。

### 10.2 主体安全

- 同一 realm 内的用户名、邮箱和手机号别名得到同一内部主体。
- 相同 loginUser 在不同 realm 得到不同主体。
- 未知账号输入稳定归并，但原始值不出现在事件、Fact、日志和异常中。
- 主体 key 在实例和重启之间稳定。
- 缺少主体保护密钥时 Starter 启动失败。

### 10.3 认证事件

- 五次登录请求只保存五条事件。
- 密码、CAPTCHA 和 MFA 失败分别记录正确 Stage 和 ReasonCode。
- 正常拒绝为 DENIED，依赖异常为 FAILURE。
- 失败事件 `user_id=null`；成功事件携带认证后 user ID。
- 未知账号产生事件，对外仍使用统一凭据错误响应。

### 10.4 规则和控制

- AUTH-01 按账户主体命中，别名不能绕过阈值。
- AUTH-02 按 IP 统计不同账户，并使用完整成功/失败分母。
- CAPTCHA 控制在下一次 preCheck 可见，TTL 到期后失效。
- IP 限制不误锁被尝试账号。
- 单纯失败不能产生长期 LOCK。
- 数据库或监测故障不能改变宿主已有认证结论。

### 10.5 模块与版本

- `api`、`core` 和 `spring-support` 保持 Java 8 兼容。
- Spring 2 不引入 `jakarta.*`，Spring 3 不引入 `javax.*`。
- Spring 2/3 执行同一验收矩阵。
- MyBatis H2 集成测试覆盖 Fact 查询索引语义和有效控制查询。
- 全量 `mvn clean verify -DskipTests=false` 通过。

## 11. 最终决策摘要

- 采用“薄治理内核 + 领域目录 + 专用认证门面”。
- 全组件统一代码治理，但不同代码家族不互相冒充。
- 登录建模为一个 Action，由 Outcome 映射登录成功/失败事件。
- 登录失败主体不是 `user_id`；内部主体由组件从 `loginUser + realm` 规范化并保护生成。
- 宿主允许扩展 ReasonCode，但必须经过 OWNER 命名空间和冻结目录。
- 定义只保留治理必需元数据，避免运营和展示职责侵入核心领域。

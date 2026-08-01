# Spring Boot 3 集成审计宿主

本模块是可运行的 Spring Boot 3.2.12 验收夹具，使用 jakarta.servlet、Shiro Jakarta、MyBatis、H2 和嵌入式 HTTP 容器验证监测组件的组合行为。

它不是 Jeecg-Boot 适配器，也不是生产脚手架。X-Audit-Principal、固定账号、audit_* 表、H2、模拟控制处理器和模拟通知渠道只服务本模块测试。

完整验收矩阵见 ../../docs/集成审计与基础项目验收.md。

## 运行

从仓库根目录执行：

    mvn -pl integration-audit/spring3-web -am test
    mvn -pl integration-audit/spring3-web -am spring-boot:run

验收报告位于 integration-audit/spring3-web/target/surefire-reports/。两个 Boot 宿主的编号集合可通过以下命令校验：

    mvn -pl integration-audit verify

每个宿主都必须唯一覆盖 TC-01 至 TC-18 和 IA-01 至 IA-12。

## 从哪里开始看

按一次报告导出请求阅读代码。这个包不是普通的报表 CRUD 示例，而是把“宿主业务授权/预检/副作用”
与“监测组件 Action/Fact/Rule/Control”串起来的接入边界：

1. [AuditPrincipalFilter.java](src/main/java/io/github/jasper/monitoring/audit/spring3/security/AuditPrincipalFilter.java)：测试身份如何进入 Shiro Subject。
2. [AuditReportAuthorizationInterceptor.java](src/main/java/io/github/jasper/monitoring/audit/spring3/security/AuditReportAuthorizationInterceptor.java)：资源加载和组织范围授权发生在哪里。
3. [ReportExportController.java](src/main/java/io/github/jasper/monitoring/audit/spring3/report/ReportExportController.java)：HTTP DTO 如何进入业务 Service。
4. [ReportExportService.java](src/main/java/io/github/jasper/monitoring/audit/spring3/report/ReportExportService.java)：服务端重新计算行数和字段，执行当前请求的风险中断；风险阻断时不生成 XLSX。
5. [Spring3AuditWebAcceptanceTest.java](src/test/java/io/github/jasper/monitoring/audit/spring3/Spring3AuditWebAcceptanceTest.java)：HTTP 响应、组件表、宿主表和副作用计数如何共同验收。

其他场景按同样方式定位：登录和会话在 security，控制处理器在 control，通知在 notification，组件管理服务的 HTTP 适配在 management，注解采集示例在 monitoring，测试夹具仓储只在 persistence。

## 包边界

| 包 | 责任 | 不负责 |
| --- | --- | --- |
| security | 从测试 Subject 派生身份；在报告访问前授权 | 不替业务 Service 生成资源事实 |
| report | 受保护查询、导出预检、业务副作用和导出监测埋点 | 不接受客户端身份、组织或最终行数作为事实；不直接执行控制处理器 |
| privilege | 演示权限变更前的控制边界 | 不绕过组件管理服务写监测表 |
| control | 实现 ControlTrigger 对应的宿主副作用 | 不扩大 ControlCommand.subject 的作用范围 |
| notification | 实现可控失败的 NotificationChannel | 不回滚已提交告警 |
| management | 将 HTTP DTO 映射到组件管理服务 | 不从请求字段构造 ManagementActor |
| monitoring | 展示 MonitorAction 和显式监测入口 | 不把请求上下文单独当作业务事件 |
| persistence | 访问 audit_* 宿主测试表 | 不访问 Controller 或直接维护 monitoring_* 状态机 |

## 夹具配置

src/main/resources/application.yml 使用 system-id=audit-spring3-web、mode=ENFORCE、frontend.enabled=false、instrumentation.enabled=true，并只信任本地测试代理地址。它还显式启用 `monitoring.authentication`；生产必须从密钥管理设施注入至少 32 字节随机密钥的 Base64 编码，集群实例保持一致，认证 Service 只向 `AuthenticationMonitor` 传入 `login_user` 和 realm。

Spring3AuditApplication 启动时执行组件 Schema 和 db/audit-fixture-schema.sql，然后构造 MyBatis SqlSessionFactory。这是测试初始化路径，不是 Jeecg-Boot 的数据库迁移方案。

## Jeecg-Boot 对照

当前代码不引入 Jeecg-Boot 类，也不模拟 LoginUser、SysUser、角色菜单或租户实现。接入 Jeecg-Boot 时，将真实对象映射到组件边界：

| Jeecg-Boot 位置 | 组件接入点 | 关键要求 |
| --- | --- | --- |
| 登录过滤器或 LoginUser 上下文 | IdentityContextProvider | 只从已经认证的服务端上下文取用户、账号类型、角色和会话信息 |
| SysUser、角色、部门或租户授权 Service | ResourceScopeAuthorizer、ManagementAuthorizer | 资源范围由服务端查询，不能使用请求体中的组织字段 |
| 同步业务 Controller/Service 的查询、导出和登录结果 | MonitoringRecorder.record(...) 或 MonitorAction | 在业务结果已确定的位置提交 Action 和可信 Fact |
| 验证码、限流、踢人、MFA、审批服务 | ControlTrigger 对应的 ControlHandler | 按 idempotencyKey 去重，严格限制在 subject 范围内 |
| 通知队列或消息服务 | NotificationChannel | 支持有限重试和幂等，失败不回滚已提交告警 |
| Jeecg 导出前的业务校验 | ReportExportService 类似的 Service 边界 | 先授权和预检，再生成文件；拒绝时不得生成文件 |

不要把 X-Audit-Principal、accepted 登录字段或客户端组织字段直接映射为 Jeecg 的安全身份。它们只是验收输入，用来驱动夹具分支。

## 关键调用链

报告导出的成功路径：

    HTTP request
      -> AuditPrincipalFilter
      -> Shiro Subject / IdentityContext
      -> AuditReportAuthorizationInterceptor
      -> ReportExportController
      -> ReportExportService
      -> AuditFixtureRepository.countReportRows
      -> MonitoringFacts / MonitoringGate.checkpoint
      -> XLSX generation
      -> @MonitorAction final outcome
      -> audit_export_ledger

阻断路径在 `MonitoringGate.checkpoint()` 结束：切面同步返回 `BLOCK` 决策并记录 `DENIED` 结果，
但不写成功台账，也不调用 XLSX 生成。跨组织访问则在拦截器阶段提交 `AccessDenied` 事件并返回 404，
导出 Service 不会被调用。审批、MFA、验证码要求与当前动作的阻断结论独立，宿主控制处理器负责持久化后续重试所需的工作流状态。

### 监测接入对照

| 业务阶段 | report 包做什么 | 监测组件做什么 | 集成者必须替换/补充 |
| --- | --- | --- | --- |
| 认证和资源授权 | 读取已授权报告对象，拒绝时停止 Controller 链 | 记录 `AccessDenied` 或授权结果 | 接入真实身份、租户/组织资源查询和授权 Service |
| 查询执行前 | 检查已有会话、拒绝和限流状态 | 不自动把普通 HTTP 请求转成 Query | 在真实查询 Service 计算 `ResourceId`、`SequentialAccess` |
| 查询执行后 | 调用 `monitor(Query)` | 持久化事件，评估 AUTHZ/DATA 规则并编排告警/控制 | 保证事件调用位于真实查询决策点，并处理控制的后续请求效果 |
| 导出预检 | 重新统计行数、识别敏感列、检查 UTC 日累计；风险命中则不生成文件 | 接收 DENIED `ReportExport`，评估 EXPT 规则并形成告警/控制 | 将固定仓储替换为真实数据权限、行数和导出台账 |
| 文件生成成功 | 生成文件后调用 `monitor(ReportExport)` | 保存 SUCCESS 事件和事实 | 保证事实使用实际生成结果，不使用请求体自报行数 |

## 表归属

audit_* 是宿主测试夹具表；monitoring_* 是组件内部表。两者在测试中共用 H2，但用途和所有权不同。

| 表组 | 用途 |
| --- | --- |
| audit_account、audit_session、audit_user_role | 账号、会话和角色测试状态 |
| audit_report、audit_report_row | 组织范围报告和导出数据 |
| audit_control_state | 控制处理器的幂等和 TTL 测试状态 |
| audit_export_ledger、audit_notification_attempt | 宿主导出和通知测试记录 |
| monitoring_* | 组件事件、告警、控制、通知和管理审计 |

验收代码通过宿主夹具表产生输入，再调用组件服务；不要通过直接写 monitoring_* 表伪造验收结果。

## 验收边界

注解监听的总体架构、普通 Service 用例、运行时事实作用域与并发边界见[注解监听与运行时事实作用域](../../docs/注解监听与运行时事实作用域.md)。

- IA-01：生产仓储使用 MyBatis，不使用内存监测仓储。
- IA-02：只有请求上下文而没有业务动作时，不产生业务事件。
- IA-03、IA-04、IA-05：注解结果、Service 运行时 Fact 和服务端事实覆盖。
- IA-07：跨组织报告在导出 Service 执行前被拒绝，返回 404 且不生成文件。
- IA-08 至 IA-10：控制触发器声明合法、唯一，并覆盖 ENFORCE 所需动作。
- IA-11：管理操作者从服务端身份派生，跨系统调用被拒绝并记录审计。
- IA-12：Boot 2/3 验收编号集合一致。
- TC-08、TC-09：导出阻断发生在 XLSX 生成前，客户端行数和组织字段不能篡改服务端事实。
- TC-11、TC-13：控制重放不重复执行，也不延长已有 TTL。
- TC-14：通知失败不回滚已保存告警，有限重试不会创建重复告警。
- TC-15：密码、Token、Cookie 和原始敏感载荷不出现在响应、事件、控制失败原因或日志中。

Spring2 只承担 javax.servlet 与 Boot 2 兼容性验证；Spring3 是本模块的主阅读示例。

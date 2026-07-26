# 集成审计职责边界与真实导出设计

## 目标

重构 `integration-audit/spring2-web` 与 `integration-audit/spring3-web`，使两个宿主以相同语义证明认证、资源授权、真实报表导出、强类型监测、MyBatis 持久化和管理服务能够完整协作。

本次不新增 Maven 模块，不引入异步导出任务、临时文件存储或宿主前端。Boot 2 与 Boot 3 保持对称实现，公共模块继续遵守 Java 8、Servlet 命名空间和框架隔离边界。

## 决策

采用同步 XLSX 下载。导出场景参考 Jeecg 的查询条件复用、勾选导出、字段权限和大数据分页思想，但不引入 Jeecg 或 AutoPOI 运行依赖。集成宿主直接使用 Apache POI 生成并校验 XLSX。

资源授权采用 MVC `HandlerInterceptor`，不放入 Controller、AOP 切面或认证 Filter。Shiro Filter 只认证；资源拦截器是报表资源访问的唯一决策入口。

## 包与职责

每个 Boot 宿主只增加必要的职责包，不继续细分层级：

```text
io.github.jasper.monitoring.audit.spring2|spring3
  security/    认证、Realm、资源授权拦截器及其配置
  report/      报表目录、请求模型、XLSX 服务、报表 Controller
  monitoring/  登录失败、注解埋点和控制动作验收入口
  management/  管理服务 HTTP 适配
  Spring*AuditApplication
```

### 认证

`AuditPrincipalFilter` 只把验收身份交给 Shiro 认证。缺少或未知身份时返回 401 并终止链路。`AuditRbacRealm` 只提供测试身份、角色和权限数据。

### 资源授权

`AuditReportAuthorizationInterceptor` 读取 Handler 方法上的内部资源策略声明、URI 中的 `reportId` 和服务端资源目录，然后严格调用一次 `ResourceAccessGuard.authorize(...)`。

- 资源不存在：返回 404，返回 `false`。
- 授权拒绝、异常或空决策：失败关闭，返回 403，返回 `false`。
- 授权允许：将已解析的可信报表写入 request attribute，继续调用 Controller。

拦截器不会先调用 `Subject.isPermitted` 再调用 Guard。宿主的 `ResourceScopeAuthorizer` 负责组织范围和 Shiro 权限的单次组合判断，Guard 负责统一调用、失败关闭和允许/拒绝审计。

`ReportController` 不注入 Guard 或 Shiro，不重新查询资源，也不执行第二次权限判断。

### HTTP 适配

```text
GET  /audit/reports/{reportId}
POST /audit/reports/{reportId}/exports
```

`ReportController` 只解析 HTTP 请求、取得拦截器附加的 `AuthorizedReport`、调用应用服务并构造响应。

`MonitoringFixtureController` 承载登录失败和注解埋点验收入口。`ManagementController` 只依赖公开管理服务接口，将服务结果适配为 HTTP 响应。

## 同步 XLSX 导出

导出请求支持：

- 与列表查询一致的服务端可识别查询条件；
- 可选的勾选记录 ID；
- 可选的前端选择字段。

有勾选 ID 时，最终数据为“勾选项、查询结果和数据权限”的交集；没有勾选 ID 时，导出当前查询结果。客户端不能提供或覆盖导出行数。

最终字段为“前端选择字段、服务端允许字段和当前身份字段权限”的交集。密码、密钥、内部权限标识等敏感字段永不进入允许集合。未知字段被视为无效请求并返回 400，避免静默掩盖错误调用。

`ReportExportService` 的处理顺序：

```text
规范化查询条件
  -> 约束勾选 ID
  -> 计算允许字段
  -> 分页读取服务端数据
  -> Apache POI 生成 XLSX
  -> ExportResult(fileName, bytes, rowCount, exportedFields)
```

响应使用 XLSX Content-Type 和 RFC 兼容的 `Content-Disposition` 附件文件名。生成失败时返回 500，不发送半成品文件。

成功生成后，`ReportExportAuditService` 使用服务端 `ExportResult` 记录强类型 `BuiltInActions.ReportExport`，绑定资源 ID 和实际导出行数。失败导出不得记录为成功。监测系统自身失败遵循现有可观测性策略，但不得把已完成的文件生成改写为权限决策。

## 请求链

```text
AuditPrincipalFilter
  -> 认证失败：401，终止
RequestMetadataInterceptor
  -> 建立服务端监测上下文
AuditReportAuthorizationInterceptor
  -> 资源不存在：404，终止
  -> 授权拒绝/异常：403，审计并终止
  -> 授权允许：附加 AuthorizedReport
ReportController
  -> ReportExportService
  -> ReportExportAuditService
  -> XLSX 附件响应
```

管理查询继续由管理服务自身执行系统边界授权和管理审计，不复用报表资源拦截器。

## 错误契约

| 情况 | HTTP | 副作用 |
| --- | --- | --- |
| 身份缺失或未知 | 401 | 不进入资源拦截器或 Controller |
| 报表不存在 | 404 | 不进入 Controller 或导出服务 |
| 权限拒绝、授权异常或空决策 | 403 | 记录拒绝证据；不进入 Controller 或导出服务 |
| 查询、选择项或字段请求非法 | 400 | 不生成文件；不记录成功导出 |
| XLSX 生成失败 | 500 | 不发送半成品；不记录成功导出 |
| 成功 | 200 | 返回 XLSX；按实际结果记录导出事件 |

## 编号验收矩阵

Boot 2 与 Boot 3 必须逐项实现相同编号和语义：

| 编号 | 要求 |
| --- | --- |
| IA-01 | 宿主只装配 `MyBatisMonitoringStore`，审计证据实际写入数据库 |
| IA-02 | 缺失或未知身份由认证 Filter 返回 401，后续链路不执行 |
| IA-03 | 同组织且具有读取权限时允许访问，资源授权严格调用一次 |
| IA-04 | 跨组织读取或导出由资源拦截器返回 403，Controller 和导出服务不执行 |
| IA-05 | 缺少导出权限时返回 403，并持久化拒绝审计 |
| IA-06 | 资源不存在时由资源拦截器返回 404，业务链路不执行 |
| IA-07 | 勾选导出遵循选择项、查询条件和数据权限交集，返回有效 XLSX |
| IA-08 | 未勾选时分页导出当前查询结果，并由服务端计算行数 |
| IA-09 | 导出字段按三方交集裁剪，非法或敏感字段不进入工作簿 |
| IA-10 | 响应包含规范的 XLSX Content-Type、文件名和附件头 |
| IA-11 | 成功导出记录强类型 `ReportExport`，事实来自实际结果并触发规则 |
| IA-12 | 登录失败窗口产生 `AUTH-01`、验证码与限流控制证据 |
| IA-13 | `@MonitorAction` MVC 路径产生强类型事件，HTTP 失败不记录为成功 |
| IA-14 | 管理服务成功查询并写入 `SUCCEEDED` 管理审计 |
| IA-15 | 跨系统管理查询被服务拒绝并写入 `DENIED` 管理审计 |
| IA-16 | Boot 2/3 用例编号集合完全一致且连续 |

每个用例方法以 `iaXX_` 开头，并使用 `@DisplayName("IA-XX ...")`。元测试反射检查编号唯一、连续、完整。文档逐项关联 HTTP 入口、测试方法、持久化证据和预期结果。

XLSX 验收必须用 Apache POI 重新读取响应字节，核对工作表、列、行、查询/选择语义以及敏感字段缺失。授权用例同时验证单次授权调用和拒绝时零业务调用。

## 验证

按 TDD 逐项先增加失败用例，再实现最小行为并重构。完成后依次执行：

```bash
mvn -pl integration-audit/spring2-web -am test
mvn -pl integration-audit/spring3-web -am test
mvn clean verify -DskipTests=false
```

验收文档记录每个编号对应的测试证据。两个宿主的 Surefire 报告作为自动化证据，但不替代生产身份系统、目标数据库性能、数据留存、真实控制副作用和灾备验证。

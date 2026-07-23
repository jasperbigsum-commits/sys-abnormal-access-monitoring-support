# Frontend Supplemental Signal Ingress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Document a unified, secure backend API ingestion flow for frontend supplemental signals while leaving endpoint paths and response bodies to each host system.

**Architecture:** `web-contract` remains the canonical `FrontendSignal v1` request contract. A host-owned HTTP adapter validates and maps native input to that model, derives `FrontendServerContext` from trusted backend facts, then invokes `FrontendSignalRecorder.record(...)`. The host retains routing, authentication, native DTOs, and its response envelope.

**Tech Stack:** Java 8 API contracts, JSON Schema draft 2020-12, JUnit 5, Maven, Markdown.

---

## File Structure

- Modify: `docs/集成指南.md` - replace section 6 with the complete uniform-ingress rule, mapping table, safe sample, error semantics, and acceptance matrix.
- Modify: `README.md` - link the existing frontend boundary statement to the expanded guide.
- Verify: `web-contract/src/main/resources/frontend-signal.schema.json` - remains the sole standard request payload definition.
- Verify: `web-contract/src/test/java/io/github/jasper/monitoring/web/FrontendSignalTest.java` and `web-contract/src/test/java/io/github/jasper/monitoring/web/FrontendSignalValidatorTest.java` - guard documented payload and timestamp constraints.

### Task 1: Document the Uniform Request Contract

**Files:**
- Modify: `docs/集成指南.md: section 6, 前端信号契约`

- [ ] **Step 1: Replace the section title and introductory paragraphs**

Insert this exact opening, before the existing schema field list:

```markdown
## 6. 前端补充信息统一接入

前端补充信息必须通过宿主系统的后端 HTTP 接口进入监测组件。组件统一的是“接收、校验、归一化并记录补充证据”的功能和 `FrontendSignal v1` 请求语义；**不**提供通用 Controller、不固定 URL、不规定认证实现，也**不**定义成功或失败响应 JSON。宿主端点应继续使用自身网关、鉴权和统一返回模型。

建议端点使用 `POST` 和 `application/json`，但路径由宿主系统决定，例如 `/api/monitoring/frontend-signals`。该示例路径不是组件 API。宿主将自己的字段名称、枚举和值域显式映射为标准信号，再调用 `FrontendSignalRecorder.record(signal, serverContext)`；该方法是组件的统一接入入口。

不同系统可以有不同的页面码、操作码和资源对象：`pageCode` 映射为 `route`，`operationCode` 映射为 `action`，已做单向摘要的订单号映射为 `resource_id_hash`。无法映射的业务字段不能放入 `attributes` 或任意扩展对象；新增字段必须同时更新 JSON Schema、`FrontendSignal` 白名单和测试。
```

- [ ] **Step 2: Insert a contract table and a non-sensitive request example**

Add a `### 6.1 标准请求契约与字段映射` subsection. Its table must cover `contract_version`, `client_event_id`, `occurred_at`, `request_id`, `trace_id`, `route`, `action`, `device_id_hash`, `resource_type`, `resource_id_hash`, and `attributes`; label every browser-supplied value as non-authoritative evidence. State that `device_id_hash` and `resource_id_hash` require a `sha256:` prefix, that `attributes` contains only the existing allowlist, and that `additionalProperties=false` rejects unknown fields.

Use this exact safe example:

```json
{
  "contract_version": "1.0",
  "client_event_id": "fd584fda-3d98-4e73-857f-a19e3be1e456",
  "occurred_at": "2026-07-23T09:30:00Z",
  "request_id": "req-5af1f1f0",
  "trace_id": "trace-41c92bd9",
  "route": "/orders/detail",
  "action": "order:view",
  "resource_type": "order",
  "resource_id_hash": "sha256:6a440d8653c80e2176c0f59ec1d4c572e0ebd4b4667c491505c9067cfde2a8c4",
  "attributes": {
    "feature": "order-center",
    "page_type": "detail",
    "ui_version": "2026.07"
  }
}
```

End the subsection by forbidding browser assertions of user ID, roles, account type, session ID, source IP, risk level, alert ID, control action, authorization decision, and final business result.

- [ ] **Step 3: Add a fully annotated host-adapter pseudocode example**

Add `### 6.2 宿主接口适配流程` and state that `HostFrontendSignalRequest`, the schema validator, authentication context, and response factory are host types. Include the following pseudocode, preserving every Chinese security comment:

```java
@PostMapping(path = "/api/monitoring/frontend-signals", consumes = "application/json")
public Object receiveFrontendSignal(@RequestBody HostFrontendSignalRequest request,
                                    HttpServletRequest servletRequest) {
    // 认证、端点授权、CSRF 和 CORS 已由宿主安全链完成；接口不是匿名遥测入口。
    HostAuthenticatedContext authenticatedContext = authenticatedContext(servletRequest);
    // Schema 先拒绝未知字段和不兼容版本，原始请求体、Cookie、令牌和业务对象绝不入事件库。
    frontendSignalSchemaValidator.validate(request);
    // 显式转换宿主 DTO；构造 FrontendSignal 时继续校验长度、哈希前缀和属性白名单。
    FrontendSignal signal = toFrontendSignal(request);
    // 收件时间只取后端时钟；超出允许偏差的浏览器时间映射为宿主 400 参数错误。
    frontendSignalValidator.validate(signal, clock.instant());
    // 身份、角色、会话哈希和来源 IP 只从认证上下文及可信代理配置得到。
    FrontendServerContext serverContext = frontendServerContextFactory.create(authenticatedContext, servletRequest);
    // 统一入口合并补充证据与可信上下文，再记录事件和评估规则。
    frontendSignalRecorder.record(signal, serverContext);
    // 组件不定义响应体；此处返回宿主自己的成功模型。
    return hostSuccessResponse();
}
```

List the mandatory ordering after the sample: authentication and endpoint authorization; CSRF or CORS; JSON Content-Type; body-size limit; trusted-user and trusted-IP rate limit; Schema; `FrontendSignal` constraints; clock skew; trusted server context; recorder. State that only `TrustedProxyResolver` may interpret forwarding headers.

- [ ] **Step 4: Add response semantics, retries, and acceptance tests**

Add `### 6.3 响应语义、重试与验收`. State that the component owns no response JSON, error code, or exception wrapper, while hosts use `400` for malformed/unknown/invalid/skewed signals, `401` for unauthenticated callers, `403` for forbidden callers, `413` for oversized bodies, `429` for rate limits, and `5xx` for unrecoverable server errors. Prohibit errors from echoing events, cookies, session values, rules, risk scores, alert details, or controls.

Document short-term de-duplication as `trusted user or session scope + client_event_id`, explicitly noting that the component does not provide end-to-end duplicate suppression. Add an acceptance matrix covering standard submission, native DTO mapping, unknown fields, clock skew, identity/IP/role forgery, duplicate IDs, and rate limiting.

- [ ] **Step 5: Check the documentation against the schema**

Run `rg -n 'client_event_id|occurred_at|request_id|route|action|device_id_hash|resource_id_hash|attributes|additionalProperties' docs/集成指南.md web-contract/src/main/resources/frontend-signal.schema.json`.

Expected: every required schema property is documented, both hash fields state `sha256:`, and the guide states unknown fields are rejected.

### Task 2: Link the Expanded Contract from the README

**Files:**
- Modify: `README.md: 宿主接入边界 and 文档与验证`

- [ ] **Step 1: Replace the short frontend sentence**

Replace the sentence beginning `组件不自动建表，也不发布业务端点。` with:

```markdown
组件不自动建表，也不发布业务端点。前端补充信息由宿主后端 API 接收：组件统一 `FrontendSignal v1` 的请求语义和 `FrontendSignalRecorder` 记录入口，但不固定 URL、认证方式或响应 JSON。宿主必须完成认证、请求大小限制、频率限制、Schema 校验和可信上下文构造；字段适配、安全顺序和 HTTP 状态语义见[前端补充信息统一接入](docs/集成指南.md#6-前端补充信息统一接入)。
```

- [ ] **Step 2: Expand the integration-guide entry in the document index**

Replace the existing `集成指南` index bullet with:

```markdown
- [集成指南](docs/集成指南.md)：依赖、SPI、注解/方法埋点、内部/持久化规则边界、MDC、控制触发、MyBatis、前端契约和最小验收矩阵；其中[前端补充信息统一接入](docs/集成指南.md#6-前端补充信息统一接入)说明统一请求契约、宿主适配、安全校验和响应边界。
```

- [ ] **Step 3: Check anchors and response ownership**

Run `rg -n 'frontend-signals|前端补充信息统一接入|响应 JSON|统一返回模型' README.md docs/集成指南.md`.

Expected: README has the relative section anchor and neither document specifies a component-owned response payload.

### Task 3: Verify the Existing Contract and Documentation Scope

**Files:**
- Verify: `web-contract/src/test/java/io/github/jasper/monitoring/web/FrontendSignalTest.java`
- Verify: `web-contract/src/test/java/io/github/jasper/monitoring/web/FrontendSignalValidatorTest.java`
- Verify: `web-contract/src/main/resources/frontend-signal.schema.json`
- Verify: `README.md`
- Verify: `docs/集成指南.md`

- [ ] **Step 1: Run the focused contract tests**

Run `mvn -pl web-contract -am test`.

Expected: Maven exits `0`; the published signal allowlist, hash-prefix constraints, required fields, and clock-skew validation continue to pass.

- [ ] **Step 2: Check the documentation diff**

Run `git diff --check -- README.md docs/集成指南.md` followed by `git diff -- README.md docs/集成指南.md`.

Expected: no whitespace errors and no changes outside frontend ingress rules, commented pseudocode, and README discoverability.

- [ ] **Step 3: Run the full reactor verification**

Run `mvn clean verify -DskipTests=false`.

Expected: Maven exits `0`. If an unrelated pre-existing change fails verification, report the module and error without altering unrelated files.

- [ ] **Step 4: Commit only this documentation implementation**

Run `git add -- README.md docs/集成指南.md` and then `git commit -m "docs: define frontend signal ingress"`.

Expected: one Conventional Commit containing only these two documentation files; do not stage or revert pre-existing worktree changes.

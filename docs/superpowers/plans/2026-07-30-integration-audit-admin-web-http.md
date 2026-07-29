# Integration Audit Admin Web HTTP Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the accepted Mock management console to complete, scope-safe Spring 3 and Spring 2 management APIs through Jeecg-style `defHttp` and a shared response contract.

**Architecture:** The frontend swaps `MockMonitoringRepository` for `HttpMonitoringRepository` without view changes. Framework-neutral API services remain the authorization and transaction boundary; MyBatis supplies safe projections, while Boot host controllers only map HTTP DTOs, derive the trusted actor, and wrap results.

**Tech Stack:** Existing Java 8-compatible `api/core/mybatis/spring-support`, Spring Boot 3.5.13, Spring Boot 2.7.18, MyBatis, H2, JUnit 5, Vue 3, Axios `defHttp`, Vitest, Playwright

---

## File Map

- `integration-audit/admin-web/src/api/http/`: Jeecg-compatible Axios instance, `Result<T>`, response transform, and error normalization.
- `integration-audit/admin-web/src/repositories/httpMonitoringRepository.ts`: HTTP implementation of the phase-one repository contract.
- `api/.../management/model/`: safe detailed projections, dashboard projection, and management audit view.
- `api/.../management/query/ManagementAuditQuery.java`: audit pagination filters.
- `api/.../management/ManagementAuditQueryService.java`: authorized read boundary.
- `core/.../management/DefaultManagementAuditQueryService.java`: scope authorization and repository delegation.
- `core/.../port/ManagementQueryRepository.java`: adds scope-constrained dashboard/audit queries.
- `mybatis/.../mapper/ManagementQueryMapper.java`: fixed SQL for new projections.
- `integration-audit/spring{2,3}-web/.../management/`: equal HTTP paths, `Result<T>` envelope, exception mapping, and request DTOs.
- `integration-audit/admin-web/e2e/http-workflows.spec.ts`: browser acceptance against Spring 3.

### Task 1: Add Jeecg-Compatible `defHttp` and Result Transformation

**Files:**
- Create: `integration-audit/admin-web/src/api/http/types.ts`
- Create: `integration-audit/admin-web/src/api/http/axios.ts`
- Create: `integration-audit/admin-web/src/api/http/normalizeError.ts`
- Test: `integration-audit/admin-web/src/api/http/axios.test.ts`

- [ ] **Step 1: Write failing transform tests**

```ts
it('returns result for a successful Jeecg envelope', async () => {
  mockAdapter.onGet('/ok').reply(200, { success: true, code: 200, message: '操作成功', result: { id: 'A' }, timestamp: 1 });
  await expect(defHttp.get<{ id: string }>({ url: '/ok' })).resolves.toEqual({ id: 'A' });
});

it('preserves an unknown backend error type', async () => {
  mockAdapter.onGet('/failure').reply(422, { success: false, code: 422, message: '拒绝', result: { errorType: 'POLICY_LOCKED', errorCode: 'MON-999', requestId: 'req-1' }, timestamp: 1 });
  await expect(defHttp.get({ url: '/failure' })).rejects.toMatchObject({ category: 'UNKNOWN', originalType: 'POLICY_LOCKED', errorCode: 'MON-999', requestId: 'req-1' });
});
```

- [ ] **Step 2: Run and verify failure**

Run: `cd integration-audit/admin-web; npm run test:run -- src/api/http/axios.test.ts`

Expected: FAIL because HTTP modules are absent.

- [ ] **Step 3: Implement the open response and error types**

```ts
export interface Result<T> { success: boolean; code: number; message: string; result: T; timestamp: number; }
export interface ErrorResult { errorType?: string; errorCode?: string; requestId?: string; details?: Record<string, unknown>; }
export interface RequestOptions { isTransformResponse?: boolean; errorMessageMode?: 'none' | 'message'; }
```

Create an Axios instance with base URL `VITE_API_BASE_URL || '/audit/management'`, 15-second timeout, credentials disabled by default, request cancellation, and an interceptor that returns `result` only when `success && code === 200`. In this integration fixture only, add `X-Audit-Principal` and `X-Audit-Approver` from explicit `VITE_AUDIT_*` variables; document that production hosts replace these headers with their authenticated server session. Map known statuses/types and preserve unknown values.

- [ ] **Step 4: Run tests and commit**

Run: `npm run test:run -- src/api/http/axios.test.ts; npm run typecheck`

Expected: PASS for success, 401, 403, 404, 409, 422, 429, 503, cancellation, network, and unknown errors.

```bash
git add integration-audit/admin-web/src/api/http
git commit -m "feat(audit-web): add Jeecg defHttp client"
```

### Task 2: Implement the HTTP Monitoring Repository

**Files:**
- Create: `integration-audit/admin-web/src/api/management.ts`
- Create: `integration-audit/admin-web/src/repositories/httpMonitoringRepository.ts`
- Modify: `integration-audit/admin-web/src/repositories/monitoringRepository.ts`
- Modify: `integration-audit/admin-web/src/main.ts`
- Test: `integration-audit/admin-web/src/repositories/httpMonitoringRepository.test.ts`

- [ ] **Step 1: Write failing path and page-mapping tests**

```ts
it('maps one-based UI pagination to zero-based management pagination', async () => {
  defHttp.get = vi.fn().mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 });
  await repository.searchAlerts({ page: 1, pageSize: 20, status: ['NEW'] });
  expect(defHttp.get).toHaveBeenCalledWith(expect.objectContaining({
    url: '/alerts', params: expect.objectContaining({ page: 0, size: 20, status: 'NEW' }),
  }));
});

it('posts the expected version and reason for alert transitions', async () => {
  await repository.transitionAlert({ id: 'ALT-1', action: 'CLOSE', expectedVersion: 3, reason: '调查完成' });
  expect(defHttp.post).toHaveBeenCalledWith(expect.objectContaining({ url: '/alerts/ALT-1/close', params: expect.objectContaining({ expectedVersion: 3, reason: '调查完成' }) }));
});
```

- [ ] **Step 2: Run and verify failure**

Run: `npm run test:run -- src/repositories/httpMonitoringRepository.test.ts`

Expected: FAIL because the HTTP repository is absent.

- [ ] **Step 3: Implement every repository method with explicit endpoints**

```ts
export const managementApi = {
  dashboard: () => defHttp.get({ url: '/dashboard' }),
  alerts: (params: object) => defHttp.get({ url: '/alerts', params }),
  alert: (id: string) => defHttp.get({ url: `/alerts/${encodeURIComponent(id)}` }),
  events: (params: object) => defHttp.get({ url: '/events', params }),
  controls: (params: object) => defHttp.get({ url: '/controls', params }),
  rules: (params: object) => defHttp.get({ url: '/rules', params }),
  whitelists: (params: object) => defHttp.get({ url: '/whitelists', params }),
  managementAudit: (params: object) => defHttp.get({ url: '/audit-log', params }),
};
```

Map `items/page/size/totalElements` to frontend `items/page+1/pageSize/total`. Generate idempotency keys with `crypto.randomUUID()` for commands that require them. The rule body contains mode, threshold, reason, expected version, and idempotency key but no trusted approver identity; the fixture server resolves the approver from its independent header context. Select the repository in `main.ts` from `VITE_DATA_MODE`, defaulting to `mock`.

- [ ] **Step 4: Run the shared repository contract against HTTP mocks**

Run: `npm run test:run -- src/repositories/httpMonitoringRepository.test.ts src/repositories/monitoringRepository.test.ts`

Expected: PASS for all 14 contract methods.

- [ ] **Step 5: Commit**

```bash
git add integration-audit/admin-web/src/api/management.ts integration-audit/admin-web/src/repositories integration-audit/admin-web/src/main.ts
git commit -m "feat(audit-web): add HTTP repository adapter"
```

### Task 3: Add Safe Dashboard and Management-Audit API Contracts

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/ManagementDashboardView.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/ManagementAuditView.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/query/ManagementAuditQuery.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/ManagementAuditQueryService.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/ManagementOperation.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/model/AlertView.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/model/SecurityEventView.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/model/ControlView.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/model/RuleView.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/model/WhitelistView.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/query/AlertQuery.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/query/ControlQuery.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/query/RuleQuery.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/query/WhitelistQuery.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/management/ManagementDashboardContractsTest.java`

- [ ] **Step 1: Write failing API invariant tests**

```java
@Test void managementAuditViewRejectsBlankScopeAndActor() {
    assertThrows(IllegalArgumentException.class, () -> ManagementAuditView.of(
        "audit-1", " ", "operator", "ALERT_READ", "alert", "a-1", "SUCCEEDED", Instant.EPOCH));
}

@Test void dashboardCountsMustBeNonNegative() {
    assertThrows(IllegalArgumentException.class, () -> ManagementDashboardView.of(-1, 0, 0, 0, 0));
}
```

- [ ] **Step 2: Run and verify failure**

Run: `mvn -pl api -Dtest=ManagementDashboardContractsTest test`

Expected: FAIL with missing types.

- [ ] **Step 3: Implement immutable Java 8-compatible contracts**

```java
public interface ManagementAuditQueryService {
    ManagementPage<ManagementAuditView> search(ManagementActor actor, ManagementAuditQuery query);
}
```

`ManagementDashboardView` exposes `openAlerts`, `highRiskAlerts`, `eventsToday`, `pendingControls`, and `closureRateBasisPoints`. `ManagementAuditView` exposes only ID, system scope, actor, operation, target type/ID, outcome, and occurrence time. Add `MANAGEMENT_AUDIT_READ` to `ManagementOperation`.

Extend the existing management projections only with persisted, non-sensitive fields required by the UI:

```text
AlertView: ruleId, riskLevel, subject, firstSeenAt, lastSeenAt, occurrenceCount
SecurityEventView: actionCode, subject, resourceId, result, occurredAt, safe facts
ControlView: alertId, ruleId, subject, actionType, expiresAt, safe failureReason, attempts
RuleView: name, riskLevel, enabled, createdAt, createdBy, changeReason, approvedBy
WhitelistView: subject, ruleId, expiresAt, approvedBy, reason
```

Extend query objects with bounded optional keyword/status/risk/mode fields. Validate keyword length <= 128, status values against their domain set, and time windows <= 31 days. Keep all classes Java 8 compatible.

- [ ] **Step 4: Run API tests and commit**

Run: `mvn -pl api test`

Expected: PASS.

```bash
git add api
git commit -m "feat(api): add management dashboard and audit contracts"
```

### Task 4: Implement Authorized Audit and Dashboard Queries

**Files:**
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/management/DefaultManagementAuditQueryService.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/port/ManagementQueryRepository.java`
- Modify: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/management/ManagementServices.java`
- Modify: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/management/ManagementServiceFactory.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/ManagementQueryMapper.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/repository/MyBatisManagementRepository.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/po/ManagementAuditPo.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/management/DefaultManagementAuditQueryServiceTest.java`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/repository/MyBatisManagementDashboardTest.java`

- [ ] **Step 1: Write failing authorization and scope tests**

```java
@Test void deniesAuditReadBeforeRepositoryAccess() {
    Fixture fixture = fixture(false);
    ManagementAuditQueryService service = fixture.service;
    assertThrows(ManagementAccessDeniedException.class,
        () -> service.search(ManagementActor.of("viewer", "system-a"), query()));
    assertEquals(0, fixture.repositoryCalls());
}

@Test void myBatisAuditQueryCannotCrossSystemScope() {
    ManagementPage<ManagementAuditView> page = repository.searchManagementAudit(
        "system-a", ManagementAuditQuery.of(ManagementPageRequest.of(0, 20, ManagementAuditQuery.Sort.OCCURRED_AT)));
    assertTrue(page.getItems().stream().allMatch(item -> "system-a".equals(item.getSystemScope())));
}
```

- [ ] **Step 2: Run and verify failure**

Run: `mvn -pl core,mybatis -am -Dtest=DefaultManagementAuditQueryServiceTest,MyBatisManagementDashboardTest test`

Expected: FAIL with missing repository methods.

- [ ] **Step 3: Implement authorization and fixed scope-constrained SQL**

Add repository methods `searchManagementAudit(scope, query)` and `dashboard(scope, from, to)`. Extend existing event, alert, control, rule, and whitelist SQL projections and filters to populate every field introduced in Task 3. The service calls `ManagementAccessGuard.require(actor, MANAGEMENT_AUDIT_READ, "management-audit", "search")` before repository access. SQL includes `WHERE system_id=#{scope}` for every query and binds sort columns through MyBatis `<choose>`, never interpolated strings.

- [ ] **Step 4: Wire the sixth service into `ManagementServices`**

Add `ManagementAuditQueryService audits()` and construct it in `ManagementServiceFactory`. Keep constructor ordering explicit and update factory tests.

- [ ] **Step 5: Run focused modules and commit**

Run: `mvn -pl spring-support,mybatis -am test`

Expected: PASS, including H2 scope and count assertions.

```bash
git add core spring-support mybatis
git commit -m "feat(mybatis): query management dashboard and audit"
```

### Task 5: Introduce the Jeecg `Result<T>` Envelope in Both Hosts

**Files:**
- Create: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/management/ManagementResult.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/management/ManagementResult.java`
- Modify: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/management/ManagementExceptionHandler.java`
- Modify: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/management/ManagementExceptionHandler.java`
- Test: `integration-audit/spring3-web/src/test/java/io/github/jasper/monitoring/audit/spring3/ManagementResultContractTest.java`
- Test: `integration-audit/spring2-web/src/test/java/io/github/jasper/monitoring/audit/spring2/ManagementResultContractTest.java`

- [ ] **Step 1: Write identical host contract tests**

```java
@Test void conflictUsesHttp409AndJeecgBody() {
    ResponseEntity<?> response = handler.conflict(new ManagementConflictException("stale"));
    assertEquals(409, response.getStatusCodeValue());
    ManagementResult<?> body = (ManagementResult<?>) response.getBody();
    assertFalse(body.isSuccess());
    assertEquals(409, body.getCode());
    assertEquals("CONFLICT", ((ManagementResult.ErrorDetails) body.getResult()).getErrorType());
}
```

- [ ] **Step 2: Run and verify failure**

Run: `mvn -pl integration-audit/spring3-web,integration-audit/spring2-web -am -Dtest=ManagementResultContractTest test`

Expected: FAIL because the envelope does not exist.

- [ ] **Step 3: Implement the Java 8-compatible result type and mappings**

```java
public final class ManagementResult<T> {
    private final boolean success;
    private final int code;
    private final String message;
    private final T result;
    private final long timestamp;
    public static <T> ManagementResult<T> ok(T result) { return new ManagementResult<T>(true, 200, "操作成功", result, System.currentTimeMillis()); }
}
```

Add safe error details `errorType`, `errorCode`, `requestId`, and empty/safe `details`. Map access denied 403, not found 404, conflict 409, management validation 422, monitoring failure by stable `MON-*` code, and unexpected failure 500 without returning exception text or cause.

- [ ] **Step 4: Run parity tests and commit**

Run: `mvn -pl integration-audit/spring3-web,integration-audit/spring2-web -am -Dtest=ManagementResultContractTest test`

Expected: PASS in both hosts with identical JSON fields.

```bash
git add integration-audit/spring2-web integration-audit/spring3-web
git commit -m "feat(integration-audit): standardize management results"
```

### Task 6: Complete Spring 3 Management Endpoints

**Files:**
- Modify: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/management/MonitoringManagementController.java`
- Modify: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/management/AlertManagementController.java`
- Modify: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/management/RuleManagementController.java`
- Modify: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/management/WhitelistManagementController.java`
- Create: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/management/ControlManagementController.java`
- Create: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/management/ManagementAuditController.java`
- Test: `integration-audit/spring3-web/src/test/java/io/github/jasper/monitoring/audit/spring3/Spring3ManagementApiContractTest.java`

- [ ] **Step 1: Write failing MockMvc endpoint tests**

```java
mockMvc.perform(get("/audit/management/alerts").header("X-Audit-Principal", "audit-admin")
        .param("page", "0").param("size", "20"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.success").value(true))
    .andExpect(jsonPath("$.result.items").isArray())
    .andExpect(jsonPath("$.result.page").value(0));

mockMvc.perform(post("/audit/management/alerts/{id}/false-positive", "alert-a")
        .header("X-Audit-Principal", "audit-admin").contentType(APPLICATION_JSON)
        .content("{\"expectedVersion\":0,\"reason\":\"confirmed test alert\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("FALSE_POSITIVE"));
```

- [ ] **Step 2: Run and verify missing routes fail**

Run: `mvn -pl integration-audit/spring3-web -am -Dtest=Spring3ManagementApiContractTest test`

Expected: FAIL with 404 or unwrapped bodies.

- [ ] **Step 3: Add complete list/detail/action mappings**

Expose:

```text
GET  /dashboard
GET  /events, /events/{id}
GET  /alerts, /alerts/{id}, /alerts/{id}/assignments
POST /alerts/{id}/assign|acknowledge|investigate|close|false-positive
GET  /controls, /controls/{id}
POST /controls/{id}/approve|reject|retry
POST /sessions/{subject}/revoke
GET  /rules, /rules/{id}; POST /rules/{id}/versions
GET  /whitelists, /whitelists/{id}; POST /whitelists/{id}/grant|revoke
GET  /audit-log
```

All methods return `ManagementResult.ok(value)`. Parse page/size/status/risk/time into API query objects. Construct `ManagementActor` only from `MonitoringContextAccessor`; never accept actor/system scope from query or body.

- [ ] **Step 4: Run Spring 3 tests and commit**

Run: `mvn -pl integration-audit/spring3-web -am test`

Expected: PASS including existing TC/IA acceptance tests.

```bash
git add integration-audit/spring3-web
git commit -m "feat(integration-audit): complete Spring 3 management API"
```

### Task 7: Mirror the API in Spring 2 and Enforce Parity

**Files:**
- Modify: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/management/MonitoringManagementController.java`
- Modify: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/management/AlertManagementController.java`
- Modify: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/management/RuleManagementController.java`
- Modify: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/management/WhitelistManagementController.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/management/ControlManagementController.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/management/ManagementAuditController.java`
- Test: `integration-audit/spring2-web/src/test/java/io/github/jasper/monitoring/audit/spring2/Spring2ManagementApiContractTest.java`
- Modify: `integration-audit/src/verify/java/AcceptanceIdVerifier.java`

- [ ] **Step 1: Copy the HTTP contract assertions with Boot 2 imports**

Use the same paths, request JSON, expected status, and JSONPath assertions as `Spring3ManagementApiContractTest`. Keep `javax.*` only in Boot 2 and `jakarta.*` only in Boot 3.

- [ ] **Step 2: Run and verify Boot 2 failure**

Run: `mvn -pl integration-audit/spring2-web -am -Dtest=Spring2ManagementApiContractTest test`

Expected: FAIL with missing routes.

- [ ] **Step 3: Implement the equal Boot 2 adapters**

Mirror endpoint behavior without sharing servlet-specific code. Extend the verifier to compare normalized `METHOD path` sets from both management packages and fail on any mismatch.

- [ ] **Step 4: Run both hosts and parity verification**

Run: `mvn -pl integration-audit verify`

Expected: PASS with equal management endpoint sets and existing TC-01..TC-18/IA-01..IA-12 IDs.

- [ ] **Step 5: Commit**

```bash
git add integration-audit/spring2-web integration-audit/src/verify
git commit -m "feat(integration-audit): mirror management API in Spring 2"
```

### Task 8: Configure Dev Proxy and Run Real HTTP Acceptance

**Files:**
- Modify: `integration-audit/admin-web/vite.config.ts`
- Create: `integration-audit/admin-web/.env.mock`
- Create: `integration-audit/admin-web/.env.http`
- Create: `integration-audit/admin-web/e2e/http-workflows.spec.ts`
- Modify: `integration-audit/admin-web/playwright.config.ts`
- Modify: `integration-audit/admin-web/README.md`

- [ ] **Step 1: Write the real-host Playwright workflow**

```ts
test('HTTP mode lists and acknowledges a persisted alert', async ({ page }) => {
  await page.goto('/alerts');
  await expect(page.getByTestId('data-mode')).toHaveText('HTTP');
  const row = page.getByRole('row').filter({ hasText: 'AUTH-01' }).first();
  await row.click();
  await page.getByRole('button', { name: '确认' }).click();
  await page.getByLabel('处置原因').fill('Spring 3 HTTP integration acceptance');
  await page.getByRole('button', { name: '提交' }).click();
  await expect(page.getByText('已确认')).toBeVisible();
});
```

- [ ] **Step 2: Add explicit environment files and proxy**

```env
# .env.mock
VITE_DATA_MODE=mock
```

```env
# .env.http
VITE_DATA_MODE=http
VITE_API_BASE_URL=/audit/management
VITE_AUDIT_PRINCIPAL=audit-admin
VITE_AUDIT_APPROVER=audit-approver
```

Proxy `/audit` to `http://127.0.0.1:8080`. Add scripts `dev:http` and `e2e:http`; do not enable permissive production CORS in either Java host. The two `VITE_AUDIT_*` values are acceptance-fixture inputs only and must not appear in a production deployment example.

- [ ] **Step 3: Start Spring 3 and frontend servers**

Terminal A: `mvn -pl integration-audit/spring3-web -am spring-boot:run`

Terminal B: `cd integration-audit/admin-web; npm run dev:http -- --port 4173`

Expected: Spring health log shows the host on 8080; Vite prints `http://127.0.0.1:4173`.

- [ ] **Step 4: Run HTTP browser acceptance**

Run: `npm run e2e:http`

Expected: PASS for list/detail, alert transition, control approval/rejection, rule conflict, whitelist transition, 403, and management audit visibility.

- [ ] **Step 5: Commit**

```bash
git add integration-audit/admin-web
git commit -m "test(audit-web): verify Spring management integration"
```

### Task 9: Run Full Regression and Security Checks

**Files:**
- Modify: `integration-audit/admin-web/README.md`
- Modify: `integration-audit/spring2-web/README.md`
- Modify: `integration-audit/spring3-web/README.md`

- [ ] **Step 1: Run frontend gates in both modes**

Run: `cd integration-audit/admin-web; npm run test:run; npm run typecheck; npm run build; npm run e2e`

Expected: all commands exit 0.

- [ ] **Step 2: Run focused backend gates**

Run: `mvn -pl api,core,mybatis,spring-support,integration-audit -am test`

Expected: PASS.

- [ ] **Step 3: Run the repository gate**

Run: `mvn clean verify -DskipTests=false`

Expected: BUILD SUCCESS with Boot 2/3 parity and all acceptance IDs.

- [ ] **Step 4: Scan generated responses and logs for sensitive fields**

Run: `rg -n -i "password|authorization|cookie|token|rawPayload|jdbc:" integration-audit/admin-web/test-results integration-audit/spring2-web/target/surefire-reports integration-audit/spring3-web/target/surefire-reports`

Expected: no leaked credential values, request headers, raw payloads, SQL, or database exception causes. Test names mentioning the words are acceptable only after manual inspection.

- [ ] **Step 5: Update runbooks and commit**

Document Mock start, HTTP start, required admin fixture identity, ports, environment variables, test commands, and the rule that the demo header identity is not a production authentication pattern.

```bash
git add integration-audit/admin-web/README.md integration-audit/spring2-web/README.md integration-audit/spring3-web/README.md
git commit -m "docs(integration-audit): document admin web integration"
```

## Final Completion Check

The work is complete only when:

1. No business view imports Axios or checks `VITE_DATA_MODE`.
2. Mock and HTTP implementations pass the same `MonitoringRepository` contract.
3. Every management response uses `ManagementResult<T>` and preserves correct HTTP status.
4. Every backend query constrains `system_id` before returning data.
5. Actor, approver, and system scope are derived from trusted server context.
6. Boot 2 and Boot 3 expose equal management paths.
7. Mock and HTTP Playwright suites pass without visual overlap or sensitive output.
8. `mvn clean verify -DskipTests=false` reports BUILD SUCCESS.

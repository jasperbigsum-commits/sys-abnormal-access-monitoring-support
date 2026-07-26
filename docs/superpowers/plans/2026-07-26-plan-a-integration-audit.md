# Plan A Integration Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Boot 2 and Boot 3 stateful reference hosts that pass Plan A `TC-01..TC-18` and framework `IA-01..IA-12` with HTTP, MyBatis, and real side-effect evidence.

**Architecture:** Each host uses the same feature packages and fixture schema, with only Servlet namespace differences. Authentication, resource authorization, business services, monitoring adapters, controls, and management HTTP adapters are separate; all fixture state is persisted through MyBatis/H2.

**Tech Stack:** Spring Boot 2.7/3.x, Shiro, MyBatis, H2, Apache POI, JUnit 5, TestRestTemplate.

---

### Task 1: Repackage Controllers and Add the Single Authorization Interceptor

**Files:**
- Delete: both flat `AuditController.java` files
- Move: both Shiro classes into `security/`
- Create: both `security/AuditReportAuthorizationInterceptor.java`
- Create: both `security/AuditWebSecurityConfiguration.java`
- Create: both `report/ReportController.java`
- Create: both `monitoring/MonitoringFixtureController.java`
- Create: both `management/MonitoringManagementController.java`
- Test: both `Spring*AuditWebAcceptanceTest.java`

- [ ] **Step 1: Add failing `IA-07`, `TC-04`, and `TC-05` HTTP tests**

Assert one Guard invocation, 403 for functional denial, identical 404 for missing/cross-org resources, denied audit reasons, and unchanged MyBatis business state.

- [ ] **Step 2: Verify RED in both hosts**

Run: `mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am -Dtest=Spring2AuditWebAcceptanceTest,Spring3AuditWebAcceptanceTest test`
Expected: FAIL because Controller performs authorization directly.

- [ ] **Step 3: Implement interceptor fail-closed flow**

```java
AuthorizationDecision decision = guard.authorize(identity, scopeRequest);
if (decision == null || !decision.isAllowed()) {
    response.sendError(resourceHidden ? 404 : 403);
    return false;
}
request.setAttribute(AUTHORIZED_REPORT, report);
return true;
```

- [ ] **Step 4: Remove Guard/Shiro dependencies from Controllers**

Controllers consume only the trusted request attribute and public management/business services.

- [ ] **Step 5: Verify and commit**

Run both host tests; expected PASS for the new cases.

```bash
git add integration-audit
git commit -m "refactor(audit): isolate web authorization"
```

### Task 2: Add MyBatis Fixture State

**Files:**
- Create: both `src/main/resources/db/audit-fixture-schema.sql`
- Create: both `persistence/AuditFixtureMapper.java`
- Create: both `persistence/AuditFixtureRepository.java`
- Create: both `persistence/AuditFixtureDataInitializer.java`
- Modify: both `application.yml` files
- Test: both acceptance tests (`IA-01`)

- [ ] **Step 1: Write failing persistence assertions**

Assert account, session, report row, user role, control state, export ledger, and notification delivery are queried through registered MyBatis mappers; assert no memory repository bean exists.

- [ ] **Step 2: Verify RED**

Run both host tests; expected FAIL because fixture state is currently Java objects and counters.

- [ ] **Step 3: Add fixture-only tables**

Create `audit_account`, `audit_session`, `audit_report`, `audit_report_row`, `audit_user_role`, `audit_control_state`, `audit_export_ledger`, and `audit_notification_attempt`. Keep them out of public `monitoring-schema.sql`.

- [ ] **Step 4: Implement parameterized mapper operations and deterministic seed data**

Use fictional identities only; never seed passwords, tokens, cookies, or real personal data.

- [ ] **Step 5: Verify and commit**

```bash
mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am test
git add integration-audit
git commit -m "feat(audit): persist reference host state"
```

### Task 3: Implement Identity and Session Scenarios

**Files:**
- Create: both `security/AuditAuthenticationService.java`
- Create: both `security/AuditSessionService.java`
- Create: both `security/AuthenticationController.java`
- Modify: both `monitoring/AuditControlActions.java`
- Test: both acceptance tests (`TC-01`, `TC-03`, `TC-11`)

- [ ] **Step 1: Write failing tests for the three numbered cases**

Use a controlled clock. Verify fifth failure activates challenge/delay, disabled account creates no session, session revoke deletes all target sessions, and replay does not execute twice.

- [ ] **Step 2: Verify RED**

Run both host tests; expected FAIL because controls do not change authentication/session state.

- [ ] **Step 3: Implement service-side actions and real control triggers**

```java
@ControlTrigger(ControlActionType.REQUIRE_CAPTCHA)
public ControlExecution requireCaptcha(ControlCommand command) {
    return controls.activateOnce(command, "CAPTCHA");
}
```

Implement rate delay and session revocation through MyBatis state with the command idempotency key.

- [ ] **Step 4: Verify and commit**

Run both host tests; expected PASS for `TC-01`, `TC-03`, `TC-11`.

```bash
git add integration-audit
git commit -m "feat(audit): simulate identity controls"
```

### Task 4: Implement IP, Traversal, and Query Controls

**Files:**
- Create: both `report/ReportQueryService.java`
- Modify: both `application.yml` IP-control settings
- Modify: both resource authorization interceptors
- Test: both acceptance tests (`TC-02`, `TC-06`, `TC-07`)

- [ ] **Step 1: Write failing boundary tests**

Verify ten distinct accounts at 80% failure activates only the source IP; one hundred sequential resources revokes the active session; 120 queries limit only the target user/interface.

- [ ] **Step 2: Verify RED**

Expected: FAIL because current fixture has no stateful IP/query/session effects.

- [ ] **Step 3: Wire built-in rules to real handlers and filters**

Explicitly configure protected paths, trusted proxy CIDRs, rule IDs, TTL, and capacity. Do not accept arbitrary forwarded headers.

- [ ] **Step 4: Verify and commit**

```bash
mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am test
git add integration-audit
git commit -m "feat(audit): enforce access-rate controls"
```

### Task 5: Implement Jeecg-Style Synchronous XLSX Export

**Files:**
- Modify: both audit POMs to add `org.apache.poi:poi-ooxml`
- Create: both `report/ReportExportRequest.java`
- Create: both `report/ExportRiskGuard.java`
- Create: both `report/ReportExportService.java`
- Create: both `report/ReportExportAuditService.java`
- Test: both acceptance tests (`TC-08`, `TC-09`, `IA-05`)

- [ ] **Step 1: Write failing XLSX and preflight tests**

Parse response bytes using `XSSFWorkbook`. Assert 4999 rows download, selected IDs and filters intersect, forbidden fields are absent, 5000/high-sensitive exports create approval without generating a file, and daily cumulative exports trigger `EXPT-02`.

- [ ] **Step 2: Verify RED**

Expected: FAIL because current export only increments a counter and returns JSON.

- [ ] **Step 3: Implement server-side selection and field policy**

```java
Set<String> fields = intersection(requestedFields, report.allowedFields(), actor.allowedFields());
long candidateCount = repository.count(report.id(), normalizedFilter, selectedIds);
```

Reject unknown fields with 400; never trust a client row count.

- [ ] **Step 4: Implement risk guard before XLSX generation**

Persist DENIED action facts and approval controls at 5000 rows/high sensitivity or daily cumulative threshold; successful export records actual generated rows after workbook completion.

- [ ] **Step 5: Verify and commit**

```bash
mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am test
git add integration-audit
git commit -m "feat(audit): add policy-aware xlsx export"
```

### Task 6: Implement Privilege, Whitelist, Rule, and Alert Management

**Files:**
- Create: both `management/RoleManagementController.java`
- Expand: both `management/MonitoringManagementController.java`
- Create: both `management/AuditManagementExceptionHandler.java`
- Test: both acceptance tests (`TC-10`, `TC-12`, `TC-16`, `TC-18`, `IA-11`)

- [ ] **Step 1: Write failing management HTTP tests**

Verify self-grant leaves roles unchanged, active/expired whitelist behavior, rule-version conflict 409, and ACK/assign/investigate/close append-only timeline with denied management audit.

- [ ] **Step 2: Verify RED**

Expected: FAIL because only event search has an HTTP adapter.

- [ ] **Step 3: Implement thin Controllers over public management services**

Construct `ManagementActor` only from `MonitoringContextAccessor`; map validation/access/not-found/conflict exceptions to 400/403/404/409.

- [ ] **Step 4: Add the host role transaction guard**

Reject actor-equals-target privilege elevation before MyBatis role update, then emit the typed denied action.

- [ ] **Step 5: Verify and commit**

```bash
mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am test
git add integration-audit
git commit -m "feat(audit): expose managed security workflows"
```

### Task 7: Implement Notification Failure, Control Idempotency, Modes, and Sanitization

**Files:**
- Create: both `monitoring/AuditNotificationChannel.java`
- Modify: both control action classes
- Test: both acceptance tests (`TC-13`, `TC-14`, `TC-15`, `TC-17`, `IA-02..IA-10`)

- [ ] **Step 1: Write failing tests**

Cover one side effect per idempotency key, finite notification retry without duplicate alert, OBSERVE versus ENFORCE contexts, unannotated versus annotated endpoints, typed Action Fact extraction, and scanning HTTP/database/captured logs for sentinels.

- [ ] **Step 2: Verify RED**

Expected: FAIL for missing durable notification retry and incomplete IA evidence.

- [ ] **Step 3: Implement finite delivery state**

Use the core `NotificationDeliveryService` with a controlled clock to claim and retry persisted deliveries. The host channel fails the first two calls and succeeds on the third; store only stable error categories.

- [ ] **Step 4: Add sensitive sentinel scanning**

Use unique test values for password/token/cookie/card fields and assert none appear in response, `security_event`, fact/input-issue tables, control failure reason, notification state, or captured logging output.

- [ ] **Step 5: Verify and commit**

```bash
mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am test
git add integration-audit
git commit -m "test(audit): complete plan A control evidence"
```

### Task 8: Enforce Acceptance Number Completeness and Full Parity

**Files:**
- Modify: both acceptance test classes
- Create: `integration-audit/verify-acceptance-ids.ps1`
- Modify: `integration-audit/pom.xml`

- [ ] **Step 1: Add failing ID completeness checks**

Every method uses `tcXX_`/`iaXX_` and matching `@DisplayName`. The verifier extracts both modules and compares exact sets `TC-01..18` and `IA-01..12`.

- [ ] **Step 2: Verify RED**

Run: `mvn -pl integration-audit verify -DskipTests=false`
Expected: FAIL until both suites contain the complete sets.

- [ ] **Step 3: Complete missing IDs and wire verifier to `verify`**

The script exits non-zero on missing, duplicate, malformed, or asymmetric IDs.

- [ ] **Step 4: Run full audit and commit**

Run: `mvn -pl integration-audit -am verify -DskipTests=false`
Expected: PASS with identical Boot 2/3 case sets.

```bash
git add integration-audit
git commit -m "test(audit): enforce acceptance parity"
```

# Public Error Contract and Bilingual Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a framework-neutral, machine-readable monitoring failure contract and complete the bilingual integration, architecture, and roadmap documentation that makes the component safe to adopt.

**Architecture:** `api.error` owns stable codes and typed runtime failures without transport semantics. `api`, `web-contract`, `core`, MyBatis, and both Boot starters emit those types only at known public contract boundaries; unexpected application defects remain untouched. Documentation keeps `README` as navigation and moves operational detail into paired Chinese and English guides.

**Tech Stack:** Java 8, Maven reactor, JUnit 5, H2, MyBatis 3.5.x, Spring Boot 2.7/3.x test contexts, Markdown and Mermaid.

---

## File Structure

- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringErrorCode.java` — stable error-code enum.
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringFailure.java` — common inspection interface.
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringValidationException.java` — coded `IllegalArgumentException` subtype.
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringConfigurationException.java` — coded `IllegalStateException` subtype.
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringStateException.java` — coded `IllegalStateException` subtype.
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringPersistenceException.java` — coded persistence-boundary failure.
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/package-info.java` — framework-neutral error contract Javadoc.
- Create: `api/src/test/java/io/github/jasper/monitoring/api/MonitoringErrorContractTest.java` — code stability, cause, and legacy-base-class tests.
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/{MonitoringRequestContext,MonitorActionDefinition,SecurityEventDraft,SecurityFieldSanitizer,package-info}.java` — emit coded validation failures.
- Modify: `api/src/test/java/io/github/jasper/monitoring/api/SecurityEventDraftTest.java` — assert the credential-material code.
- Modify: `web-contract/src/main/java/io/github/jasper/monitoring/web/{FrontendSignal,FrontendServerContext,FrontendSignalValidator}.java` — reuse API validation errors.
- Modify: `web-contract/src/test/java/io/github/jasper/monitoring/web/{FrontendSignalTest,FrontendSignalValidatorTest}.java` — assert coded frontend validation.
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/{domain/RuleMatch,domain/AlertDisposition,domain/rule/WindowAggregateRule,application/DefaultSecurityMonitor,application/AlertLifecycleService,application/MonitoringActionRegistry,application/rule/InternalRuleRegistry,application/control/AnnotatedControlHandler}.java` — map known domain, configuration, registration, and lifecycle failures.
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/{AlertLifecycleServiceTest,MonitoringActionRegistryTest,InternalRuleRegistryTest,AnnotatedControlHandlerTest}.java` — assert exact codes while retaining legacy catch compatibility.
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepository.java` — translate MyBatis persistence errors and invalid persisted control data.
- Modify: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepositoryTest.java` — verify `MON-401` and retained MyBatis cause against an unmigrated H2 schema.
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java` and `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java` — produce one configuration failure type for duplicate annotated control bindings.
- Modify: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java` and `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java` — assert the common code through startup failure causes.
- Modify: `README.md`, `README.en.md`, `docs/集成指南.md`, `docs/架构与运维说明.md`, and `docs/architecture-and-transaction-boundaries.en.md` — add navigable quick-start, errors, diagrams, and operational boundaries.
- Create: `docs/integration-guide.en.md`, `docs/错误规范.md`, `docs/error-contract.en.md`, `docs/路线图.md`, and `docs/roadmap.en.md` — paired public guides, error contract, and milestone backlog.

### Task 1: Add the Public Error Contract

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringErrorCode.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringFailure.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringValidationException.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringConfigurationException.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringStateException.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringPersistenceException.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/package-info.java`
- Create: `api/src/test/java/io/github/jasper/monitoring/api/MonitoringErrorContractTest.java`

- [ ] **Step 1: Write the failing API contract test**

```java
package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringFailure;
import io.github.jasper.monitoring.api.error.MonitoringPersistenceException;
import io.github.jasper.monitoring.api.error.MonitoringStateException;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MonitoringErrorContractTest {
    @Test
    void exposesStableCodesAndPreservesLegacyValidationAndStateCatchTypes() {
        RuntimeException cause = new RuntimeException("database unavailable");
        MonitoringValidationException validation = new MonitoringValidationException(
            MonitoringErrorCode.REQUIRED_FIELD_MISSING, "action is required");
        MonitoringConfigurationException configuration = new MonitoringConfigurationException(
            MonitoringErrorCode.ENFORCEMENT_HANDLER_REQUIRED, "ENFORCE requires a handler");
        MonitoringStateException state = new MonitoringStateException(
            MonitoringErrorCode.INVALID_ALERT_TRANSITION, "alert is already closed");
        MonitoringPersistenceException persistence = new MonitoringPersistenceException(
            MonitoringErrorCode.PERSISTENCE_OPERATION_FAILED, "Monitoring persistence failed", cause);

        assertEquals("MON-001", validation.getErrorCode().getCode());
        assertTrue(validation instanceof IllegalArgumentException);
        assertTrue(configuration instanceof IllegalStateException);
        assertTrue(state instanceof IllegalStateException);
        assertEquals(MonitoringErrorCode.PERSISTENCE_OPERATION_FAILED,
            ((MonitoringFailure) persistence).getErrorCode());
        assertSame(cause, persistence.getCause());
    }
}
```

- [ ] **Step 2: Run the test to verify the public types do not yet exist**

Run: `mvn -pl api -Dtest=MonitoringErrorContractTest test`

Expected: compilation fails because `io.github.jasper.monitoring.api.error` is absent.

- [ ] **Step 3: Implement the enum, interface, and exceptions without framework dependencies**

```java
public enum MonitoringErrorCode {
    REQUIRED_FIELD_MISSING("MON-001"),
    INVALID_FIELD_VALUE("MON-002"),
    UNSAFE_EVENT_ATTRIBUTE("MON-003"),
    ACTION_NOT_REGISTERED("MON-101"),
    CONFLICTING_ACTION_DEFINITION("MON-102"),
    RULE_REGISTRY_FROZEN("MON-103"),
    DUPLICATE_CONTROL_BINDING("MON-104"),
    INVALID_CONTROL_TRIGGER("MON-105"),
    DUPLICATE_INTERNAL_RULE_ID("MON-106"),
    ALERT_NOT_FOUND("MON-201"),
    INVALID_ALERT_TRANSITION("MON-202"),
    ENFORCEMENT_HANDLER_REQUIRED("MON-301"),
    PERSISTENCE_OPERATION_FAILED("MON-401");

    private final String code;

    MonitoringErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
```

```java
public interface MonitoringFailure {
    MonitoringErrorCode getErrorCode();
}
```

Implement each exception with a final `MonitoringErrorCode errorCode`, a null-checking constructor, and `getErrorCode()`. `MonitoringValidationException` extends `IllegalArgumentException`; `MonitoringConfigurationException` and `MonitoringStateException` extend `IllegalStateException`; `MonitoringPersistenceException` extends `RuntimeException`. Supply `(code, message)` constructors for all four types and `(code, message, cause)` constructors for the persistence type. Keep messages safe and do not add Spring, Servlet, MyBatis, or Lombok dependencies to `api`.

- [ ] **Step 4: Add package documentation and run the API test**

Write `api/error/package-info.java` to state that codes are stable machine-readable values, hosts own transport mapping, and messages must not be parsed or contain sensitive fields.

Run: `mvn -pl api -Dtest=MonitoringErrorContractTest test`

Expected: `BUILD SUCCESS` with one passing test.

- [ ] **Step 5: Commit only the new API error files and their test**

```powershell
git add -- api/src/main/java/io/github/jasper/monitoring/api/error api/src/test/java/io/github/jasper/monitoring/api/MonitoringErrorContractTest.java
git commit -m "feat(api): add monitoring error contract"
```

### Task 2: Emit Coded Validation, Lifecycle, and Registration Failures

**Files:**
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/{MonitoringRequestContext,MonitorActionDefinition,SecurityEventDraft,SecurityFieldSanitizer,package-info}.java`
- Modify: `api/src/test/java/io/github/jasper/monitoring/api/SecurityEventDraftTest.java`
- Modify: `web-contract/src/main/java/io/github/jasper/monitoring/web/{FrontendSignal,FrontendServerContext,FrontendSignalValidator}.java`
- Modify: `web-contract/src/test/java/io/github/jasper/monitoring/web/{FrontendSignalTest,FrontendSignalValidatorTest}.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/{domain/RuleMatch,domain/AlertDisposition,domain/rule/WindowAggregateRule,application/DefaultSecurityMonitor,application/AlertLifecycleService,application/MonitoringActionRegistry,application/rule/InternalRuleRegistry,application/control/AnnotatedControlHandler}.java`
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/{AlertLifecycleServiceTest,MonitoringActionRegistryTest,InternalRuleRegistryTest,AnnotatedControlHandlerTest}.java`

- [ ] **Step 1: Turn the existing tests into code assertions**

Replace broad assertions in the existing tests with `assertThrows` assignments and code checks. For example:

```java
MonitoringValidationException exception = assertThrows(MonitoringValidationException.class,
    () -> SecurityEventDraft.builder()
        .eventType(SecurityEventType.LOGIN_FAILURE)
        .action("LOGIN")
        .result(SecurityEventResult.FAILURE)
        .sourceIp("203.0.113.8")
        .requestId("req-1")
        .occurredAt(Instant.parse("2026-07-22T00:00:00Z"))
        .attribute("password", "not-allowed")
        .build());

assertEquals(MonitoringErrorCode.UNSAFE_EVENT_ATTRIBUTE, exception.getErrorCode());
assertTrue(exception instanceof IllegalArgumentException);
```

Add matching assertions for: an unknown action (`ACTION_NOT_REGISTERED`), a conflicting action registration (`CONFLICTING_ACTION_DEFINITION`), registration after rule freeze (`RULE_REGISTRY_FROZEN`), a completed-alert transition (`INVALID_ALERT_TRANSITION`), an `ENFORCE` monitor with no handler (`ENFORCEMENT_HANDLER_REQUIRED`), an invalid annotated control binding (`DUPLICATE_CONTROL_BINDING`), and frontend clock-skew rejection (`INVALID_FIELD_VALUE`).

- [ ] **Step 2: Run focused tests and confirm they fail against raw Java exceptions**

Run: `mvn -pl core -am -Dtest=SecurityEventDraftTest,MonitoringActionRegistryTest,InternalRuleRegistryTest,AlertLifecycleServiceTest,AnnotatedControlHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: test failures because the current code throws raw `IllegalArgumentException` and `IllegalStateException` rather than the new types.

- [ ] **Step 3: Replace only known semantic failures with the mapped error code**

Use the following mapping; retain `Objects.requireNonNull` and unexpected runtime failures as standard Java failures because they are not component-specific semantic outcomes.

| Location | Condition | Error code | Exception type |
| --- | --- | --- | --- |
| `MonitoringRequestContext`, `SecurityEventDraft`, `AlertDisposition` | required safe field missing | `REQUIRED_FIELD_MISSING` | `MonitoringValidationException` |
| `MonitorActionDefinition`, `RuleMatch`, `WindowAggregateRule`, frontend hash/clock-skew validation | malformed or out-of-range value | `INVALID_FIELD_VALUE` | `MonitoringValidationException` |
| `SecurityFieldSanitizer` and unsupported frontend attributes | credential-like or unsupported event attribute | `UNSAFE_EVENT_ATTRIBUTE` | `MonitoringValidationException` |
| `MonitoringActionRegistry.require` | action was never registered | `ACTION_NOT_REGISTERED` | `MonitoringValidationException` |
| `MonitoringActionRegistry.register` | same action has different definitions | `CONFLICTING_ACTION_DEFINITION` | `MonitoringConfigurationException` |
| `InternalRuleRegistry.register` after `freeze()` | runtime registration is closed | `RULE_REGISTRY_FROZEN` | `MonitoringStateException` |
| `InternalRuleRegistry.register` before `freeze()` | duplicate rule ID | `DUPLICATE_INTERNAL_RULE_ID` | `MonitoringConfigurationException` |
| `AlertLifecycleService.requireAlert` | alert ID cannot be found | `ALERT_NOT_FOUND` | `MonitoringValidationException` |
| `AlertLifecycleService.assertTransition` | requested transition is not permitted | `INVALID_ALERT_TRANSITION` | `MonitoringStateException` |
| `DefaultSecurityMonitor` | `ENFORCE` has no host handler | `ENFORCEMENT_HANDLER_REQUIRED` | `MonitoringConfigurationException` |
| `AnnotatedControlHandler` | duplicate action binding | `DUPLICATE_CONTROL_BINDING` | `MonitoringConfigurationException` |
| `AnnotatedControlHandler` | `RECORD`, non-public, or invalid method signature | `INVALID_CONTROL_TRIGGER` | `MonitoringValidationException` |

Use messages that name only stable identifiers such as `action`, `ruleId`, or `alertId`; never interpolate a comment, event attribute value, source payload, or exception text.

- [ ] **Step 4: Document the API package and run API, web-contract, and core tests**

Update `api/package-info.java` to link `api.error` as the public failure contract. Run:

```powershell
mvn -pl api -Dtest=MonitoringErrorContractTest,SecurityEventDraftTest test
mvn -pl web-contract -am -Dtest=FrontendSignalTest,FrontendSignalValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl core -am -Dtest=MonitoringActionRegistryTest,InternalRuleRegistryTest,AlertLifecycleServiceTest,AnnotatedControlHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: every selected test passes and existing `IllegalArgumentException` / `IllegalStateException` compatibility assertions remain true where the previous behavior had that base type.

- [ ] **Step 5: Commit the mapped API, web, and core behavior with its tests**

```powershell
git add -- api/src/main/java/io/github/jasper/monitoring/api api/src/test/java/io/github/jasper/monitoring/api web-contract/src/main/java/io/github/jasper/monitoring/web web-contract/src/test/java/io/github/jasper/monitoring/web core/src/main/java/io/github/jasper/monitoring/core core/src/test/java/io/github/jasper/monitoring/core
git commit -m "feat(core): expose coded monitoring failures"
```

### Task 3: Preserve MyBatis Causes and Align Boot Starter Configuration Failures

**Files:**
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepository.java`
- Modify: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepositoryTest.java`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`

- [ ] **Step 1: Write failures for an unmigrated schema and duplicate annotated controls**

In `MyBatisMonitoringRepositoryTest`, create an H2 `UnpooledDataSource` without calling `executeSchema`, create the repository with `MyBatisMonitoringRepositoryRegistrar.create(configuration)`, and attempt `repository.saveEvent(...)` using the complete `SecurityEvent` builder already used by `retrievesAlertsAndAppendsDispositionHistoryWithoutChangingEvents`.

```java
MonitoringPersistenceException exception = assertThrows(MonitoringPersistenceException.class,
    () -> repository.saveEvent(event));

assertEquals(MonitoringErrorCode.PERSISTENCE_OPERATION_FAILED, exception.getErrorCode());
assertNotNull(exception.getCause());
```

In each starter test, obtain the startup failure in `rejectsDuplicateAnnotatedControlTriggers` and assert that its cause chain contains a `MonitoringConfigurationException` whose code is `DUPLICATE_CONTROL_BINDING`.

- [ ] **Step 2: Run the MyBatis and starter tests to verify failure before implementation**

```powershell
mvn -pl mybatis -am -Dtest=MyBatisMonitoringRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl spring2-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl spring3-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the new persistence assertion receives a raw MyBatis exception and at least one starter does not expose the common configuration type.

- [ ] **Step 3: Translate only MyBatis persistence exceptions at the repository boundary**

Import `org.apache.ibatis.exceptions.PersistenceException` and add this helper to `MyBatisMonitoringRepository`:

```java
private static MonitoringPersistenceException persistenceFailure(PersistenceException exception) {
    return new MonitoringPersistenceException(MonitoringErrorCode.PERSISTENCE_OPERATION_FAILED,
        "Monitoring persistence operation failed", exception);
}
```

In `inTransaction`, catch `PersistenceException` before the existing broad `RuntimeException`, roll back, and throw `persistenceFailure(exception)`. In non-managed `read`, add the same specific catch before `finally`; managed reads remain covered by the surrounding transaction. Change the missing whitelist expiry to `MonitoringValidationException(REQUIRED_FIELD_MISSING, "Whitelist entries require expiresAt")`, and convert an unknown persisted control status to `MonitoringPersistenceException(PERSISTENCE_OPERATION_FAILED, "Persisted control status is invalid")` without including the database value in the message.

- [ ] **Step 4: Use one coded configuration failure in both starters and run the focused suite**

Replace both duplicate annotated-control `throw new Illegal...` statements with:

```java
throw new MonitoringConfigurationException(MonitoringErrorCode.DUPLICATE_CONTROL_BINDING,
    "Duplicate annotated ControlTrigger binding for " + action);
```

The Boot 3 starter intentionally changes from its current `IllegalArgumentException` to the same configuration failure used by Boot 2. This aligns the public contract while the project remains `1.0.0-SNAPSHOT`.

Re-run the three commands from Step 2.

Expected: MyBatis exposes `MON-401` with a MyBatis cause; Boot 2 and 3 expose `MON-104` through failed application contexts.

- [ ] **Step 5: Commit persistence and starter parity changes**

```powershell
git add -- mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepository.java mybatis/src/test/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepositoryTest.java spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java
git commit -m "fix(mybatis): code persistence failures"
```

### Task 4: Publish Bilingual Integration, Error, Architecture, and Roadmap Documentation

**Files:**
- Modify: `README.md`
- Modify: `README.en.md`
- Modify: `docs/集成指南.md`
- Create: `docs/integration-guide.en.md`
- Create: `docs/错误规范.md`
- Create: `docs/error-contract.en.md`
- Modify: `docs/架构与运维说明.md`
- Modify: `docs/architecture-and-transaction-boundaries.en.md`
- Create: `docs/路线图.md`
- Create: `docs/roadmap.en.md`

- [ ] **Step 1: Define the documentation-link acceptance check before changing guides**

Verify these exact targets after writing the documents: the Chinese and English README, both integration guides, both error-contract documents, both architecture documents, and both roadmap documents. Each README must link to every document in its own language, and every document must link back to its language-matched README.

- [ ] **Step 2: Write the minimal quick-start and common-mistakes sections in both languages**

At the beginning of each integration guide, add this seven-step path in the same order: import exactly one matching Boot starter; execute `monitoring-schema.sql` through controlled migration; implement identity, authorization, and trusted-proxy SPI; configure `system-id`, `OBSERVE`, and trusted proxies; verify a safe test event reaches storage; register and test a real `ControlHandler`; switch to `ENFORCE` only after the preceding evidence exists.

Add an explicit common-mistakes table covering these exact rows: schema not applied, in-memory fallback in production, two starters on the classpath, anonymous identity in production, forwarding headers from untrusted peers, `ENFORCE` without a tested handler, unregistered manual action, sensitive event property, frontend evidence treated as authoritative, and database rule version mistaken for an active runtime rule.

- [ ] **Step 3: Write the error contract and advanced-integration sections from the implementation**

Publish all thirteen `MON-xxx` codes with exception type, retry guidance, and host responsibility. Include this host-side inspection example in both language versions:

```java
try {
    monitor.record(draft);
} catch (MonitoringFailure failure) {
    MonitoringErrorCode code = failure.getErrorCode();
    // The host maps code to its own HTTP, RPC, or message response contract.
    auditLogger.warn("monitoring_failure_code={}", code.getCode());
}
```

State that hosts must not parse exception messages, expose causes to external clients, or assume component error codes are HTTP status codes. Document advanced use of `@MonitorAction`, `MonitoringActionRegistry`, `ActionEventRecorder`, `@ControlTrigger`, `ResourceAccessGuard`, frontend schema validation, MDC trace propagation, `NotificationChannel`, `DetectionRule`, MyBatis transaction boundaries, and the approved dynamic-rule loading boundary.

- [ ] **Step 4: Add architecture and runtime call diagrams to both architecture documents**

Insert the approved Mermaid module diagram and the committed-before-side-effect sequence diagram. Explain that `mybatis.po` is private persistence mapping, repository conversion is the sole domain/PO boundary, monitoring writes are one `MonitoringRepository.inTransaction(...)` unit, and notification/control execute after commit. Ensure diagrams name only existing modules and types.

- [ ] **Step 5: Create milestone roadmaps and update README navigation**

Create Chinese and English roadmaps with the exact milestones below, each with priority, scope, and completion signal:

| Milestone | Priority | Scope | Completion signal |
| --- | --- | --- | --- |
| M0 | Now | Error contract and bilingual integration documentation | Full reactor passes; both READMEs link to all public guides. |
| M1 | Next | Metrics SPI, health guidance, retention, and archival policy | Operators can measure persistence, notification, controls, and data age. |
| M2 | Later | Approved transactional-outbox adapter and idempotent notification delivery | External delivery can recover without joining host transactions. |
| M3 | Later | Validated, versioned, staged dynamic-rule loader | Rule activation requires validation, approval, rollout, and rollback evidence. |

List automatic schema migration, automatic `EventEnricher` discovery, automatic online database-rule activation, and built-in HTTP error envelopes under explicitly deferred work. Add language-matched navigation links from both READMEs and avoid duplicating full tutorials in either README.

- [ ] **Step 6: Validate documentation and commit only documentation files**

```powershell
rg -n "T[O]DO|T[B]D|待定|占位" README.md README.en.md docs
git diff --check
mvn -Pjavadoc -pl api -am verify
```

Expected: the search returns no placeholder markers in changed public documents, the diff check passes, and API Javadocs build under Java 8.

```powershell
git add -- README.md README.en.md docs/集成指南.md docs/integration-guide.en.md docs/错误规范.md docs/error-contract.en.md docs/架构与运维说明.md docs/architecture-and-transaction-boundaries.en.md docs/路线图.md docs/roadmap.en.md
git commit -m "docs: expand bilingual integration guidance"
```

### Task 5: Run the Full Reactor and Perform Contract Regression Checks

**Files:**
- Verify: all files changed in Tasks 1-4.

- [ ] **Step 1: Build and test every Maven module**

Run: `mvn clean verify -DskipTests=false`

Expected: all reactor modules pass, including H2 MyBatis integration tests, Boot 2/3 context tests, integration-audit applications, and Maven-plugin tests.

- [ ] **Step 2: Scan for raw public contract exceptions and code/doc drift**

```powershell
rg -n "throw new (IllegalArgumentException|IllegalStateException)" api/src/main/java core/src/main/java mybatis/src/main/java web-contract/src/main/java spring2-starter/src/main/java spring3-starter/src/main/java
rg -n "MON-(001|002|003|101|102|103|104|105|106|201|202|301|401)" docs README.md README.en.md
git diff --check
```

Expected: remaining raw exceptions are either explicitly documented Java null checks or unrelated Maven-plugin validation; every public `MON-xxx` code appears in both error-contract documents; the whitespace check passes.

- [ ] **Step 3: Review final commit scope**

Run: `git status --short`

Expected: no files from unrelated pre-existing worktree changes are staged or committed by this plan. Report any remaining user-owned modifications without reverting them.

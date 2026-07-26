# Runtime Monitoring Boundaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make rule modes, annotated facts, HTTP outcomes, rule catalogs, and control-handler startup validation match the strict host/framework boundary.

**Architecture:** The frozen typed rule catalog becomes the only source of executable rules and required controls. Spring support owns bounded annotation extraction; Boot starters only adapt Servlet/Spring outcomes and scan host beans. MyBatis persists observe-only rule matches so every mode has auditable behavior.

**Tech Stack:** Java 8, Maven, JUnit 5, Spring AOP, Spring Boot 2/3, MyBatis, H2.

---

### Task 1: Make Rule Modes Executable Semantics

**Files:**
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/RuleMode.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/RuleObservation.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/RuleObservationRepository.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/TypedRuleEvaluationService.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/TypedRuleEvaluationServiceTest.java`

- [ ] **Step 1: Write failing mode tests**

Add tests asserting `DISABLED` evaluates nothing, `OBSERVE` saves one `RuleObservation` without alert, `ALERT_ONLY` saves an alert without control, global `OBSERVE` suppresses rule-level `ENFORCE` controls, and global plus rule `ENFORCE` executes controls.

```java
assertEquals(1, observations.saved().size());
assertTrue(alerts.saved().isEmpty());
assertEquals(0, handler.calls());
```

- [ ] **Step 2: Verify RED**

Run: `mvn -pl core -am -Dtest=TypedRuleEvaluationServiceTest test`
Expected: FAIL because rule mode is ignored and `RuleObservationRepository` does not exist.

- [ ] **Step 3: Implement the mode decision once**

In `TypedRuleEvaluationService`, branch on `rule.definition().getMode()` before raising alerts and execute controls only for `RuleMode.ENFORCE` when global mode is `MonitoringMode.ENFORCE`.

```java
if (mode == RuleMode.DISABLED) continue;
if (mode == RuleMode.OBSERVE) {
    observations.save(RuleObservation.of(match, event.getEventId(), now));
    continue;
}
raise(match, event);
```

- [ ] **Step 4: Verify GREEN and remove the duplicate enum**

Run: `mvn -pl api,core -am test`
Expected: PASS and no production reference to `io.github.jasper.monitoring.api.RuleMode`.

- [ ] **Step 5: Commit**

```bash
git add api core
git commit -m "fix(core): enforce typed rule modes"
```

### Task 2: Persist Observe-Only Rule Matches with MyBatis

**Files:**
- Modify: `mybatis/src/main/resources/db/monitoring-schema.sql`
- Create: `mybatis/src/main/resources/db/upgrade/monitoring-rule-observation-v5.sql`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/RuleObservationMapper.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/po/RuleObservationPo.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringStoreRegistrar.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/repository/MyBatisMonitoringStore.java`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/repository/MyBatisMonitoringStoreTest.java`

- [ ] **Step 1: Write a failing round-trip test**

Persist `RuleObservation.of(observationId, "AUTH-01", eventId, subject, occurredAt)` and assert mapper retrieval preserves rule, event, subject, and time.

- [ ] **Step 2: Verify RED**

Run: `mvn -pl mybatis -am -Dtest=MyBatisMonitoringStoreTest test`
Expected: FAIL because observation schema and mapper are absent.

- [ ] **Step 3: Add append-only schema and mapper**

```sql
CREATE TABLE rule_observation (
  observation_id VARCHAR(64) PRIMARY KEY,
  rule_id VARCHAR(128) NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  subject VARCHAR(256) NOT NULL,
  observed_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_rule_observation_event FOREIGN KEY (event_id) REFERENCES security_event(event_id)
);
```

- [ ] **Step 4: Register the mapper and implement the repository port**

Make `MyBatisMonitoringStore` implement `RuleObservationRepository`; inserts are append-only and parameterized.

- [ ] **Step 5: Verify and commit**

Run: `mvn -pl mybatis -am test`
Expected: PASS.

```bash
git add mybatis
git commit -m "feat(mybatis): persist rule observations"
```

### Task 3: Make the Frozen Rule Catalog the Single Control Authority

**Files:**
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/rule/RuleCatalog.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/DefaultRuleCatalog.java`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/DefaultRuleCatalogCoverageTest.java`
- Test: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedRuntimeAutoConfigurationTest.java`
- Test: `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/TypedRuntimeAutoConfigurationTest.java`

- [ ] **Step 1: Write failing catalog authority tests**

Assert `catalog.requiredControlTypes()` is derived from non-disabled, rule-level `ENFORCE` definitions and starter validation uses the injected frozen catalog rather than `DefaultRuleCatalog.requiredControlTypes()`.

- [ ] **Step 2: Verify RED**

Run: `mvn -pl core,spring2-starter,spring3-starter -am -Dtest=DefaultRuleCatalogCoverageTest,TypedRuntimeAutoConfigurationTest test`
Expected: FAIL because starters use a separate hard-coded boundary.

- [ ] **Step 3: Expose derived controls on frozen `RuleCatalog`**

```java
public Set<ControlActionType> requiredControlTypes() {
    requireFrozen();
    return requiredControls;
}
```

- [ ] **Step 4: Inject that catalog into evaluator and startup validation**

Remove direct default-catalog lookups from both starters.

- [ ] **Step 5: Verify and commit**

Run: `mvn -pl core,spring2-starter,spring3-starter -am test`
Expected: PASS.

```bash
git add api core spring2-starter spring3-starter
git commit -m "refactor(runtime): unify rule and control catalogs"
```

### Task 4: Reject Ambiguous Control Handlers at Startup

**Files:**
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/control/ControlHandlerRegistry.java`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/ControlHandlerRegistryTest.java`
- Test: both starter `TypedRuntimeAutoConfigurationTest.java` files

- [ ] **Step 1: Write failing duplicate-binding tests**

Cover two explicit handlers, explicit plus annotated handler, generic plus explicit handler for the same action, invalid annotated signature, and fallback-only ENFORCE.

- [ ] **Step 2: Verify RED**

Run: `mvn -pl core,spring2-starter,spring3-starter -am -Dtest=ControlHandlerRegistryTest,TypedRuntimeAutoConfigurationTest test`
Expected: FAIL because the first Spring bean currently wins.

- [ ] **Step 3: Build a unique `ControlCatalog` by action**

```java
if (bindings.put(type, handler) != null) {
    throw configuration("DUPLICATE_CONTROL_HANDLER", type.name());
}
```

Fallback handlers remain outside executable coverage and never satisfy ENFORCE.

- [ ] **Step 4: Verify Boot parity and commit**

Run: `mvn -pl core,spring2-starter,spring3-starter -am test`
Expected: PASS.

```bash
git add core spring2-starter spring3-starter
git commit -m "fix(starter): reject ambiguous control handlers"
```

### Task 5: Implement Bounded `@ActionFact` Extraction

**Files:**
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/ActionFactExtractor.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/MonitorActionContractValidator.java`
- Modify: both `TypedMonitorActionAspect.java` files
- Modify: both `AbnormalAccessMonitorAutoConfiguration.java` files
- Test: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/ActionFactExtractorTest.java`
- Test: both starter `TypedRuntimeAutoConfigurationTest.java` files

- [ ] **Step 1: Write failing extractor and validation tests**

Test public getter/field, array/List index, null path, type mismatch, `class`, method calls, Map access, negative index, duplicate producer, and Fact not accepted by the selected Action.

- [ ] **Step 2: Verify RED**

Run: `mvn -pl spring-support,spring2-starter,spring3-starter -am -Dtest=ActionFactExtractorTest,TypedRuntimeAutoConfigurationTest test`
Expected: FAIL because no production extractor exists.

- [ ] **Step 3: Implement the restricted grammar**

```java
private static final Pattern PATH = Pattern.compile(
    "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*|\\[[0-9]+\\])*"
);
```

Resolve only public bean getters/public fields and array/List indexes. Convert through the registered `FactDefinition`, not `toString()`.

- [ ] **Step 4: Validate all annotated bean methods before runtime creation**

The validator resolves `@MonitorAction`, inspects each `@ActionFact`, rejects conflicts with `FactBinding`, and returns immutable method bindings consumed by the aspect.

- [ ] **Step 5: Merge extracted facts into `ActionExecution` and verify**

Run: `mvn -pl spring-support,spring2-starter,spring3-starter -am test`
Expected: PASS on Boot 2 and Boot 3.

- [ ] **Step 6: Commit**

```bash
git add spring-support spring2-starter spring3-starter
git commit -m "feat(starter): bind typed action facts"
```

### Task 6: Resolve HTTP Failure Outcomes Correctly

**Files:**
- Modify: both `TypedMonitorActionAspect.java` files
- Test: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/TypedMonitorActionAspectTest.java`
- Test: `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/TypedMonitorActionAspectTest.java`

- [ ] **Step 1: Write failing response tests**

Assert normal values are success, thrown exceptions are failure, 401/403 `ResponseEntity` are denied, and other 4xx/5xx responses are failure with stable reason codes.

- [ ] **Step 2: Verify RED**

Run: `mvn -pl spring2-starter,spring3-starter -am -Dtest=TypedMonitorActionAspectTest test`
Expected: FAIL because all normal returns are currently success.

- [ ] **Step 3: Implement Boot-specific response resolution**

```java
if (value instanceof ResponseEntity) {
    int status = ((ResponseEntity<?>) value).getStatusCodeValue();
    if (status == 401 || status == 403) return denied("HTTP_ACCESS_DENIED", elapsed);
    if (status >= 400) return failure("HTTP_REQUEST_FAILED", elapsed);
}
```

- [ ] **Step 4: Verify all runtime modules and commit**

Run: `mvn -pl api,core,mybatis,spring-support,spring2-starter,spring3-starter -am test`
Expected: PASS.

```bash
git add spring2-starter spring3-starter
git commit -m "fix(starter): preserve HTTP action outcomes"
```

### Task 7: Complete Versioned Rule Changes and Alert Assignment

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/RuleChangeCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/AlertAssignmentCommand.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/RuleCatalogService.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/AlertManagementService.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/management/ManagementOperation.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/management/DefaultRuleCatalogService.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/management/DefaultAlertManagementService.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/port/ManagementQueryRepository.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/ManagementQueryMapper.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/repository/MyBatisManagementRepository.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/management/ManagementServiceAuthorizationTest.java`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/repository/MyBatisManagementRepositoryTest.java`

- [ ] **Step 1: Write failing public-contract and service tests**

Assert rule change requires expected version, mode, threshold, reason, approver, and actor; a successful change appends a new version and audit record, stale version throws `ManagementConflictException`, and alert assignment appends an assignment disposition without overwriting earlier history.

- [ ] **Step 2: Verify RED**

Run: `mvn -pl api,core,mybatis -am -Dtest=ManagementContractsTest,ManagementServiceAuthorizationTest,MyBatisManagementRepositoryTest test`
Expected: FAIL because rule write and alert assignment contracts are absent.

- [ ] **Step 3: Add immutable commands and service methods**

```java
RuleView change(ManagementActor actor, RuleChangeCommand command);
AlertDetails assign(ManagementActor actor, AlertAssignmentCommand command);
```

Commands validate nonblank reason/assignee/approver, positive threshold, expected version, and stable idempotency key.

- [ ] **Step 4: Implement append-only version changes in one transaction**

Insert `rule_version + 1` with previous/new mode and threshold metadata; never update historical rows. Append `management_audit` in the same transaction. Assignment appends a disposition record and advances alert version with optimistic locking.

- [ ] **Step 5: Verify and commit**

Run: `mvn -pl api,core,mybatis -am test`
Expected: PASS.

```bash
git add api core mybatis
git commit -m "feat(management): add versioned rule and assignment commands"
```

### Task 8: Add Durable Finite Notification Retry

**Files:**
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/notification/NotificationDeliveryService.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/port/NotificationDeliveryRepository.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/TypedRuleEvaluationService.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/NotificationDeliveryMapper.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/repository/MyBatisMonitoringStore.java`
- Modify: `mybatis/src/main/resources/db/monitoring-schema.sql`
- Create: `mybatis/src/main/resources/db/upgrade/monitoring-notification-retry-v6.sql`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/notification/NotificationDeliveryServiceTest.java`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/repository/MyBatisMonitoringStoreTest.java`

- [ ] **Step 1: Write failing retry-state tests**

Configure maximum three attempts. Assert a channel failure records `RETRY_PENDING` without throwing into the monitoring transaction, retry preserves the delivery ID, success records `DELIVERED`, and the fourth invocation is not attempted after terminal failure.

- [ ] **Step 2: Verify RED**

Run: `mvn -pl core,mybatis -am -Dtest=NotificationDeliveryServiceTest,MyBatisMonitoringStoreTest test`
Expected: FAIL because delivery persistence only supports blind inserts.

- [ ] **Step 3: Implement a versioned delivery state**

```text
PENDING -> DELIVERED
       -> RETRY_PENDING -> DELIVERED
                        -> FAILED
```

Store delivery ID, channel, aggregate ID, attempt count, next-attempt time, status, stable failure category, and version. Do not store exception messages or payloads.

- [ ] **Step 4: Invoke delivery after alert transaction commit**

Replace direct `NotificationChannel.notify` calls with `NotificationDeliveryService.deliver`. A delivery failure must not remove or roll back the alert.

- [ ] **Step 5: Verify and commit**

Run: `mvn -pl core,mybatis,spring2-starter,spring3-starter -am test`
Expected: PASS.

```bash
git add core mybatis spring2-starter spring3-starter
git commit -m "feat(core): persist finite notification retries"
```

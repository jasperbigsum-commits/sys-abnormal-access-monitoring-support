# Strict Monitoring Runtime Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the initial string-based, memory-capable implementation with a strictly typed, startup-compiled, MyBatis-only monitoring runtime and complete management application services.

**Architecture:** Public contracts remain in one `api` artifact but move into responsibility packages. Core owns immutable runtime compilation results, event/rule/control orchestration, and management use cases; MyBatis is the only production persistence implementation. Spring Support owns shared scanning and assembly, while Boot 2/3 keep only namespace adapters and integration-audit proves both variants through the same black-box acceptance IDs.

**Tech Stack:** Java 8 common modules, Java 17 Boot 3 adapter, Maven reactor, JUnit 5, Spring Framework `BeanWrapper`, MyBatis 3.5.19, H2 2.2.224, IPAddress 5.5.1, ArchUnit 1.3.0, Maven PMD Plugin 3.24.0.

---

## Execution Rules

- Execute tasks in order. Do not keep a compatibility facade for the old flat API.
- Use TDD for every behavior: observe the named test fail before production code is added.
- Common modules must compile with `--release 8`; only `spring3-starter` and its audit host use Java 17.
- Never commit generated `target/`, PDF output, cookies, tokens, raw request bodies, or sensitive event values.
- Run the focused command in every task and commit only after it passes.
- Run `mvn clean verify -DskipTests=false` at Tasks 5, 10, and 15 to expose reactor drift early.

## Target File Map

| Responsibility | Files |
| --- | --- |
| Typed action contracts | `api/src/main/java/io/github/jasper/monitoring/api/action/*` |
| Typed facts | `api/src/main/java/io/github/jasper/monitoring/api/fact/*` |
| Rule/control/event contracts | `api/src/main/java/io/github/jasper/monitoring/api/{rule,control,event}/*` |
| Management contracts | `api/src/main/java/io/github/jasper/monitoring/api/management/*` |
| Monitoring application flow | `core/src/main/java/io/github/jasper/monitoring/core/application/monitoring/*` |
| Management use cases | `core/src/main/java/io/github/jasper/monitoring/core/application/management/*` |
| Narrow persistence ports | `core/src/main/java/io/github/jasper/monitoring/core/port/*Repository.java` |
| MyBatis production store | `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/{repository,mapper,model,converter,typehandler}/*` |
| Shared Spring compiler | `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/{action,fact,configuration,context,control,management}/*` |
| Boot namespace adapters | `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/{autoconfigure,web}/*`, `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/{autoconfigure,web}/*` |
| Black-box hosts | `integration-audit/spring2-web/*`, `integration-audit/spring3-web/*`, `integration-audit/src/testFixtures/*` |
| Public documentation | `docs/{integrators,security,operators,maintainers,reference}/*`, `integration-audit/README.md` |

### Task 1: Introduce Typed Action and Fact Contracts

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/action/ActionType.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/action/ActionContract.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/action/MonitorAction.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/fact/FactType.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/fact/FactSource.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/fact/ActionFact.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/fact/ActionFacts.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/action/TypedActionContractTest.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/fact/ActionFactsTest.java`

- [ ] **Step 1: Write failing compile-time and runtime contract tests**

```java
@MonitorAction(ReportExportAction.class)
void exportReport() {}

ActionFacts.builder().put(DataCountFact.class, 5000L).build();
// ActionFacts.builder().put(DataCountFact.class, "5000") must not compile.
```

- [ ] **Step 2: Run the API tests and observe missing typed contracts**

Run: `mvn -pl api -Dtest=TypedActionContractTest,ActionFactsTest test`

Expected: compilation fails because `api.action` and `api.fact` do not exist.

- [ ] **Step 3: Add minimal Java 8 contracts**

```java
public interface FactType<T> {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MonitorAction {
    Class<? extends ActionType> value();
}
```

Implement `ActionFacts` as an immutable class-keyed map. `Builder.put(Class<? extends FactType<T>>, T)` preserves the compile-time association and rejects null keys or values. Runtime validation of framework-supplied raw values belongs to `FactDefinition<T>` in Task 2, the specification's single owner of value type and validation metadata.

- [ ] **Step 4: Run focused tests**

Run: `mvn -pl api test`

Expected: all API tests pass on Java 8.

- [ ] **Step 5: Commit**

```bash
git add api
git commit -m "feat(api): add typed action and fact contracts"
```

### Task 2: Define Action Contracts, Catalogs, and Built-ins

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/action/ActionDefinition.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/action/ActionContractDefinition.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/action/ActionCatalog.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/action/ActionFailurePolicy.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/action/BuiltInActions.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/fact/FactDefinition.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/fact/BuiltInFacts.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/fact/ActionFactProvider.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/fact/FactBinding.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/action/ActionCatalogTest.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/fact/FactBindingTest.java`

- [ ] **Step 1: Write tests for ownership and strict contract merging**

```java
@Test
void derivedExportCannotWeakenContractFailurePolicy() {
    assertThrows(MonitoringConfigurationException.class,
        () -> catalog.register(customExport(ActionFailurePolicy.OBSERVE_ONLY)));
}

@Test
void actionSpecificProviderDoesNotApplyToSiblingActions() {
    assertFalse(binding.forAction(FirstExport.class).appliesTo(SecondExport.class));
}

@Test
void factDefinitionRejectsARawValueOfTheWrongRuntimeType() {
    assertThrows(IllegalArgumentException.class,
        () -> dataCountDefinition.validateRaw("5000"));
}
```

- [ ] **Step 2: Verify tests fail**

Run: `mvn -pl api -Dtest=ActionCatalogTest,FactBindingTest test`

Expected: compilation fails on missing definitions.

- [ ] **Step 3: Implement frozen catalogs and package-private built-in markers**

```java
interface BuiltInActionType extends ActionType {}
interface BuiltInFactType<T> extends FactType<T> {}

public final class ActionCatalog {
    public void freeze() { this.frozen = true; }
    public ActionDefinition require(Class<? extends ActionType> type) {
        ActionDefinition definition = definitions.get(type);
        if (definition == null) {
            throw new MonitoringConfigurationException(ACTION_NOT_REGISTERED, "Action type is not registered");
        }
        return definition;
    }
}
```

`ActionContractDefinition` owns required facts, allowed sources, rule participation, and minimum failure policy. `ActionDefinition` owns stable code, event/resource type, tags, contracts, additions, and explicit failure policy. Merge by set union plus strict source intersection; reject empty intersections and weaker policies. `ActionFactProvider` only returns values; `FactBinding` alone owns provider-to-action or provider-to-contract applicability.

- [ ] **Step 4: Run API tests**

Run: `mvn -pl api test`

Expected: catalog duplicate, freeze, merge, and provider ownership tests pass.

- [ ] **Step 5: Commit**

```bash
git add api
git commit -m "feat(api): define frozen action and fact catalogs"
```

### Task 3: Replace Draft-Based Recording with One Event Assembly Pipeline

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/ActionExecution.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/ActionOutcome.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/ObservationIssue.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/MonitoringMode.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/MonitoringRequestContext.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/MonitoringContextAccessor.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/RiskLevel.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/SecurityEventResult.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/SecurityEventType.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/identity/AccountType.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/identity/IdentityContext.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/identity/IdentityContextProvider.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/authorization/AuthorizationDecision.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/authorization/ResourceScopeAuthorizer.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/authorization/ResourceScopeRequest.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/identity/TrustedProxyResolver.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/monitoring/SecurityEventAssembler.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/monitoring/MonitoringService.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/domain/event/SecurityValueNormalizer.java`
- Move: `core/src/main/java/io/github/jasper/monitoring/core/domain/SecurityEvent.java` to `core/src/main/java/io/github/jasper/monitoring/core/domain/event/SecurityEvent.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/monitoring/SecurityEventAssemblerTest.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/monitoring/MonitoringServiceTest.java`

- [ ] **Step 1: Write source ownership and outcome precedence tests**

```java
@Test
void frameworkOutcomeCannotBeOverriddenByAProvider() {
    ActionExecution execution = executionWithProviderOutcome(SUCCESS);
    SecurityEvent event = assembler.assemble(execution, ActionOutcome.denied("FORBIDDEN"));
    assertEquals(FAILURE, event.getResult());
}
```

- [ ] **Step 2: Observe failure**

Run: `mvn -pl core -am -Dtest=SecurityEventAssemblerTest,MonitoringServiceTest test`

Expected: tests fail because there is no typed execution pipeline.

- [ ] **Step 3: Implement the only event construction path**

```java
public SecurityEvent assemble(ActionExecution execution, ActionOutcome outcome) {
    ActionDefinition action = runtime.actions().require(execution.actionType());
    ActionFacts facts = factCollector.collect(action, execution);
    return SecurityEvent.create(action.code(), trustedIdentity(execution), trustedRequest(execution),
        outcome, facts, observationIssues(facts));
}
```

Only `SecurityEventAssembler` may call the domain event constructor. Missing provider facts create `ObservationIssue`; trusted identity/request/outcome collisions are rejected. `SecurityValueNormalizer` preserves the current sanitizer's tested redaction behavior without exposing a public utility. `MonitoringService` persists the event before evaluating eligible rules.

- [ ] **Step 4: Run focused tests**

Run: `mvn -pl core -am test`

Expected: core tests pass with the new pipeline; old recorder tests may remain until Task 14 deletion.

- [ ] **Step 5: Commit**

```bash
git add api core
git commit -m "refactor(core): centralize security event assembly"
```

### Task 4: Make Rules Typed and Explicitly Action-Bound

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/rule/RuleType.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/rule/RuleDefinition.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/rule/RuleCatalog.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/rule/RuleMode.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/rule/RuleSource.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/DetectionRule.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/RuleEvaluationContext.java`
- Rewrite: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/DefaultRuleCatalog.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/domain/rule/TypedRuleCatalogTest.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/domain/rule/RuleFactPrerequisiteTest.java`

- [ ] **Step 1: Write eligibility tests**

```java
@Test
void missingFactSkipsOnlyRulesThatDeclareThatFact() {
    RuleEvaluation result = engine.evaluate(eventWithout(DataCountFact.class));
    assertEquals(SKIPPED_MISSING_FACT, result.forRule(LargeExportRule.class).status());
    assertEquals(EVALUATED, result.forRule(LoginVelocityRule.class).status());
}
```

- [ ] **Step 2: Verify red state**

Run: `mvn -pl core -am -Dtest=TypedRuleCatalogTest,RuleFactPrerequisiteTest test`

Expected: missing `RuleType`, `RuleDefinition`, and typed prerequisites.

- [ ] **Step 3: Implement explicit rule definitions**

```java
public interface DetectionRule<R extends RuleType> {
    Class<R> type();
    Optional<RuleMatch> evaluate(RuleEvaluationContext context);
}
```

Each `RuleDefinition` lists participating action contracts/types, required `FactType` classes, permitted `FactSource` values, history window, risk, and possible controls. Do not infer participation from predicates or string tags. Freeze the catalog before runtime publication.

- [ ] **Step 4: Run rule and core suites**

Run: `mvn -pl core -am test`

Expected: typed eligibility, five-failure, export, whitelist, and unrelated-rule tests pass.

- [ ] **Step 5: Commit**

```bash
git add api core
git commit -m "refactor(core): bind typed rules to declared facts"
```

### Task 5: Split Persistence Ports and Make MyBatis Mandatory

**Files:**
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/EventRepository.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/AlertRepository.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/ControlRepository.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/WhitelistRepository.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/NotificationDeliveryRepository.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/MonitoringTransaction.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/repository/MyBatisMonitoringStore.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/EventMapper.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/AlertMapper.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/ControlMapper.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/WhitelistMapper.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/NotificationDeliveryMapper.java`
- Rewrite: `mybatis/src/main/resources/db/monitoring-schema.sql`
- Delete: `mybatis/src/main/resources/db/upgrade/monitoring-event-input-quality-v2.sql`
- Delete: `mybatis/src/main/resources/db/upgrade/monitoring-control-rule-id-v3.sql`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/repository/MyBatisMonitoringStoreTest.java`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/repository/MyBatisTransactionTest.java`

- [ ] **Step 1: Write H2 round-trip and rollback tests**

```java
@Test
void rollsBackEventAlertAndLinkAsOneUnit() {
    assertThrows(TestFailure.class, () -> transaction.required(() -> {
        events.save(event);
        alerts.save(alert);
        throw new TestFailure();
    }));
    assertFalse(events.find(event.id()).isPresent());
}
```

- [ ] **Step 2: Verify failures against the broad repository**

Run: `mvn -pl mybatis -am -Dtest=MyBatisMonitoringStoreTest,MyBatisTransactionTest test`

Expected: tests fail because narrow ports and the new schema do not exist.

- [ ] **Step 3: Implement aggregate mappers and one production store**

Use explicit mapper methods and converters; do not introduce a repository factory or ORM SPI. Schema must include typed fact value/source columns, optimistic versions, control attempts, management audit, and system-scoped indexes. `MonitoringTransaction.required` joins an active MyBatis transaction and rolls back all writes on runtime failure.

```java
public final class MyBatisMonitoringStore implements EventRepository, AlertRepository,
        ControlRepository, WhitelistRepository, NotificationDeliveryRepository, MonitoringTransaction {
    @Override
    public <T> T required(TransactionWork<T> work) {
        return transactionExecutor.required(work);
    }
}
```

- [ ] **Step 4: Run MyBatis and reactor verification**

Run: `mvn -pl mybatis -am test`

Then: `mvn clean verify -DskipTests=false`

Expected: H2 round trips and the complete reactor pass without using memory persistence.

- [ ] **Step 5: Commit**

```bash
git add core mybatis
git commit -m "refactor(mybatis): provide mandatory aggregate persistence"
```

### Task 6: Implement the Unified Control State Machine

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/control/ControlStatus.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/control/ControlType.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/control/ControlCatalog.java`
- Rewrite: `core/src/main/java/io/github/jasper/monitoring/core/application/DefaultControlService.java` as `core/src/main/java/io/github/jasper/monitoring/core/application/control/ControlExecutionService.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/domain/control/ControlAttempt.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/control/ControlExecutionServiceTest.java`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/repository/ControlConcurrencyTest.java`

- [ ] **Step 1: Write state and concurrency tests**

```java
@Test
void reservesPendingBeforeCallingTheHostHandler() {
    handler.assertion(() -> assertEquals(PENDING, controls.require(command.id()).status()));
    service.execute(command);
}
```

- [ ] **Step 2: Observe failure**

Run: `mvn -pl mybatis -am -Dtest=ControlExecutionServiceTest,ControlConcurrencyTest test`

Expected: current service calls handlers without an atomic PENDING reservation.

- [ ] **Step 3: Implement catalog and transitions**

```text
automatic: absent -> PENDING -> SUCCEEDED | FAILED | SKIPPED
approval:  absent -> AWAITING_APPROVAL -> PENDING | REJECTED
retry:     FAILED -> PENDING -> SUCCEEDED | FAILED
```

Bind exactly one Handler per executable `ControlType`; reject duplicate or missing ENFORCE coverage at startup. Persist every attempt, reuse the idempotency key, and never place the external Handler call inside the database transaction.

- [ ] **Step 4: Run control suites**

Run: `mvn -pl mybatis -am test`

Expected: concurrent calls produce one reservation and one host side effect.

- [ ] **Step 5: Commit**

```bash
git add api core mybatis
git commit -m "refactor(core): enforce durable control transitions"
```

### Task 7: Add Public Management Contracts

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/ManagementActor.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/ManagementOperation.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/ManagementAuthorizer.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/ManagementResource.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/ManagementPageRequest.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/ManagementPage.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/SecurityEventQueryService.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/AlertManagementService.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/RuleCatalogService.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/WhitelistManagementService.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/ControlManagementService.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/SecurityEventSummary.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/SecurityEventDetails.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/AlertSummary.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/AlertDetails.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/AlertStatus.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/DispositionType.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/RuleView.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/WhitelistView.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/model/ControlView.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/AcknowledgeAlertCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/StartInvestigationCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/CloseAlertCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/MarkFalsePositiveCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/GrantWhitelistCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/RevokeWhitelistCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/ApproveControlCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/RejectControlCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/command/RetryControlCommand.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/query/SecurityEventQuery.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/query/AlertQuery.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/query/RuleQuery.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/query/WhitelistQuery.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/management/query/ControlQuery.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/ManagementValidationException.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/ManagementAccessDeniedException.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/ManagementNotFoundException.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/ManagementConflictException.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringSystemException.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/management/ManagementContractTest.java`

- [ ] **Step 1: Write contract and validation tests**

```java
@Test
void pageSizeIsBounded() {
    assertThrows(ManagementValidationException.class, () -> new ManagementPageRequest(0, 201));
}

@Test
void commandsDoNotAcceptAnOperatorIdentifier() {
    assertNoFieldNamed(CloseAlertCommand.class, "operatorId");
}
```

- [ ] **Step 2: Observe missing API**

Run: `mvn -pl api -Dtest=ManagementContractTest test`

Expected: compilation fails on management types.

- [ ] **Step 3: Implement immutable framework-neutral contracts**

Every service method takes `ManagementActor` first. Queries use fixed sort enums and bounded time ranges. Alert, whitelist, and control commands carry `expectedVersion`, reason, and bounded evidence/reference fields. Add complete English Javadocs covering authorization operation, transaction, side effects, return value, stable failures, and sensitive-data restrictions.

```java
public interface AlertManagementService {
    ManagementPage<AlertSummary> search(ManagementActor actor, AlertQuery query);
    AlertDetails get(ManagementActor actor, String alertId);
    AlertDetails close(ManagementActor actor, CloseAlertCommand command);
}
```

- [ ] **Step 4: Run API and Javadoc checks**

Run: `mvn -pl api test javadoc:javadoc`

Expected: contracts compile under Java 8 and public Javadocs complete successfully.

- [ ] **Step 5: Commit**

```bash
git add api
git commit -m "feat(api): define strict management services"
```

### Task 8: Implement Authorized and Audited Management Use Cases

**Files:**
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/management/DefaultSecurityEventQueryService.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/management/DefaultAlertManagementService.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/management/DefaultRuleCatalogService.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/management/DefaultWhitelistManagementService.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/management/DefaultControlManagementService.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/management/ManagementAccessGuard.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/domain/management/ManagementAuditRecord.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/ManagementQueryRepository.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/ManagementAuditRepository.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/management/ManagementServiceAuthorizationTest.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/management/AlertManagementServiceTest.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/management/ControlManagementServiceTest.java`

- [ ] **Step 1: Write authorization-before-read and audit tests**

```java
@Test
void deniedListDoesNotReadPersistence() {
    authorizer.deny(EVENT_READ);
    assertThrows(ManagementAccessDeniedException.class, () -> service.search(actor, query));
    assertEquals(0, queries.invocationCount());
    assertEquals(DENIED, audits.onlyRecord().result());
}
```

- [ ] **Step 2: Verify red state**

Run: `mvn -pl core -am -Dtest=ManagementServiceAuthorizationTest,AlertManagementServiceTest,ControlManagementServiceTest test`

Expected: management implementations and ports are missing.

- [ ] **Step 3: Implement service orchestration**

Use `ManagementAccessGuard` before repository access. Authorized reads append a sanitized audit before returning data. Database-only mutations and success audits share one transaction; denied/validation/conflict attempts use an independent audit transaction. Control approval commits PENDING plus approval audit, calls the Handler, then commits attempt, final state, and completion audit.

```java
public AlertDetails close(ManagementActor actor, CloseAlertCommand command) {
    access.require(actor, ALERT_CLOSE, ManagementResource.alert(command.alertId()));
    return transaction.required(() -> closeAndAudit(actor, command));
}
```

- [ ] **Step 4: Run core tests**

Run: `mvn -pl core -am test`

Expected: authorization order, actor ownership, optimistic conflict, immutable history, and error classification pass.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): implement audited management use cases"
```

### Task 9: Persist Management Queries and Audit with MyBatis

**Files:**
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/ManagementQueryMapper.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/ManagementAuditMapper.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/repository/MyBatisManagementRepository.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/model/ManagementAuditPo.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/converter/ManagementViewConverter.java`
- Modify: `mybatis/src/main/resources/db/monitoring-schema.sql`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/repository/MyBatisManagementRepositoryTest.java`

- [ ] **Step 1: Write H2 tests for pagination, locking, and audit sanitization**

```java
@Test
void staleExpectedVersionDoesNotOverwriteAlert() {
    assertEquals(0, mapper.updateAlertStatus(alertId, 1L, IN_PROGRESS, 0L));
    assertEquals(ACKNOWLEDGED, repository.getAlert(actor, alertId).status());
}
```

- [ ] **Step 2: Observe missing mapper behavior**

Run: `mvn -pl mybatis -am -Dtest=MyBatisManagementRepositoryTest test`

Expected: missing management mapper/schema objects.

- [ ] **Step 3: Implement fixed SQL queries**

Use allow-listed sort branches selected in Java, always append stable ID ordering, apply `LIMIT/OFFSET`, and issue a matching count query. Add `record_version` conditions for alert/whitelist/control updates. Add append-only `management_audit` and `control_attempt` tables; no update/delete Mapper methods may target audit rows.

```sql
UPDATE security_alert
SET status = #{status}, record_version = record_version + 1
WHERE alert_id = #{alertId} AND record_version = #{expectedVersion}
```

- [ ] **Step 4: Run MyBatis tests**

Run: `mvn -pl mybatis -am test`

Expected: deterministic pages, conflict row counts, sanitized audits, and state histories pass.

- [ ] **Step 5: Commit**

```bash
git add mybatis
git commit -m "feat(mybatis): persist management views and audit"
```

### Task 10: Compile the Spring Runtime Once at Startup

**Files:**
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/configuration/SpringMonitoringRuntimeCompiler.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/configuration/ConfigurationViolation.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/action/ActionBinding.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/action/AnnotatedActionScanner.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/fact/BeanWrapperFactExtractor.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/fact/FactBindingCompiler.java`
- Test: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/configuration/SpringMonitoringRuntimeCompilerTest.java`
- Test: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/fact/BeanWrapperFactExtractorTest.java`

- [ ] **Step 1: Write aggregate startup validation tests**

```java
@Test
void reportsAllInvalidBindingsBeforePublishingRuntime() {
    MonitoringConfigurationException failure = assertThrows(MonitoringConfigurationException.class,
        () -> compiler.compile(contextWithUnknownActionAndMissingFact()));
    assertEquals(Arrays.asList(ACTION_NOT_REGISTERED, REQUIRED_FACT_UNBOUND), failure.codes());
}
```

- [ ] **Step 2: Observe failure**

Run: `mvn -pl spring-support -am -Dtest=SpringMonitoringRuntimeCompilerTest,BeanWrapperFactExtractorTest test`

Expected: compiler and BeanWrapper extractor are absent.

- [ ] **Step 3: Implement deterministic compilation**

Load built-ins, apply contributors, scan most-specific methods and interfaces, expand contracts, compile fact bindings, validate source/provider/rule/control coverage, freeze catalogs, then publish one immutable runtime. Use Spring `BeanWrapper` for property paths and report action/bean/class/method locations without parameter values.

```java
public MonitoringRuntime compile(ListableBeanFactory beans) {
    CompilationState state = loadDefinitions(beans);
    scanAnnotatedActions(beans, state);
    validateBindingsAndCoverage(state);
    return state.freezeAndBuild();
}
```

- [ ] **Step 4: Run shared Spring and reactor tests**

Run: `mvn -pl spring-support -am test`

Then: `mvn clean verify -DskipTests=false`

Expected: all startup violations are aggregated and no partially compiled runtime Bean exists.

- [ ] **Step 5: Commit**

```bash
git add spring-support
git commit -m "feat(spring): compile monitoring runtime at startup"
```

### Task 11: Reduce Boot 2 and Boot 3 to Thin Adapters

**Files:**
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/configuration/MonitoringAutoConfigurationSupport.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/management/ManagementServiceFactory.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/management/ManagementServices.java`
- Rewrite: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Rewrite: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Rewrite: both `AbnormalAccessMonitorProperties.java`
- Modify: both Spring configuration metadata files
- Test: both `AbnormalAccessMonitorAutoConfigurationTest.java`

- [ ] **Step 1: Write parity and strict dependency tests**

```java
contextRunner.withPropertyValues("monitoring.management.enabled=true")
    .run(context -> assertThat(context).hasFailed()
        .getFailure().hasMessageContaining("ManagementAuthorizer"));
```

- [ ] **Step 2: Run both starter tests and observe duplicated behavior**

Run: `mvn -pl spring2-starter,spring3-starter -am test`

Expected: new strict management and compiler assertions fail.

- [ ] **Step 3: Move common construction to Spring Support**

Boot auto-configurations should only expose version-specific conditions and servlet adapters. `monitoring.management.enabled` defaults false; true requires authorizer, MyBatis ports, transaction, frozen catalogs, and Clock. Never use `@ConditionalOnMissingBean` to install permissive or memory substitutes.

```java
public ManagementServices managementServices(ManagementAuthorizer authorizer,
        ManagementQueryRepository queries, ManagementAuditRepository audits,
        MonitoringTransaction transaction, Clock clock) {
    return ManagementServiceFactory.create(authorizer, queries, audits, transaction, clock);
}
```

- [ ] **Step 4: Run starter parity tests**

Run: `mvn -pl spring2-starter,spring3-starter -am test`

Expected: identical shared behavior; Boot 2 contains only `javax.servlet`, Boot 3 only `jakarta.servlet`.

- [ ] **Step 5: Commit**

```bash
git add spring-support spring2-starter spring3-starter
git commit -m "refactor(starters): keep only boot namespace adapters"
```

### Task 12: Tighten Frontend Signals and IP/CIDR Handling

**Files:**
- Modify: `pom.xml`
- Rewrite: `web-contract/src/main/java/io/github/jasper/monitoring/web/FrontendSignalMapper.java`
- Create: `web-contract/src/main/java/io/github/jasper/monitoring/web/FrontendSignalBinding.java`
- Rewrite: `web-contract/src/main/resources/frontend-signal.schema.json`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/context/IpNetwork.java`
- Rewrite: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/ConfiguredTrustedProxyResolver.java`
- Rewrite: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/control/GenericIpControlHandler.java`
- Test: `web-contract/src/test/java/io/github/jasper/monitoring/web/FrontendSignalContractTest.java`
- Test: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/context/IpNetworkTest.java`

- [ ] **Step 1: Write trust-boundary and network normalization tests**

```java
@Test
void browserCannotSelectAnActionOrIdentity() {
    assertRejected(jsonWith("action", "admin:disable-user"));
}

@Test
void parserNeverResolvesHostNames() {
    assertThrows(IllegalArgumentException.class, () -> IpNetwork.parse("example.com/24"));
}
```

- [ ] **Step 2: Observe current behavior**

Run: `mvn -pl web-contract,spring-support -am test`

Expected: typed frontend binding and mature network parser assertions fail.

- [ ] **Step 3: Adopt IPAddress and map only supplemental facts**

Add `com.github.seancfoley:ipaddress:5.5.1` to dependency management and Spring Support. Accept only IP literals/CIDR, normalize without DNS, and remove `IpAddressLiteral`. Frontend mapper resolves a server-configured `FrontendSignalBinding`; the payload cannot carry action, identity, result, risk, control, or trusted request fields.

```xml
<dependency>
    <groupId>com.github.seancfoley</groupId>
    <artifactId>ipaddress</artifactId>
    <version>${ipaddress.version}</version>
</dependency>
```

- [ ] **Step 4: Run web and Spring tests**

Run: `mvn -pl web-contract,spring-support -am test`

Expected: schema/Java parity, supplemental-only mapping, IPv4/IPv6/CIDR, and no-DNS tests pass.

- [ ] **Step 5: Commit**

```bash
git add pom.xml web-contract spring-support
git commit -m "refactor(web): constrain supplemental signals and networks"
```

### Task 13: Rebuild Integration Audit as the Shared Black-Box Gate

**Files:**
- Modify: `integration-audit/pom.xml`
- Modify: `integration-audit/spring2-web/pom.xml`
- Modify: `integration-audit/spring3-web/pom.xml`
- Create: `integration-audit/src/testFixtures/java/io/github/jasper/monitoring/audit/acceptance/SharedMonitoringAcceptance.java`
- Create: `integration-audit/src/testFixtures/java/io/github/jasper/monitoring/audit/acceptance/AcceptanceIds.java`
- Create: `integration-audit/src/testFixtures/java/io/github/jasper/monitoring/audit/acceptance/Acceptance.java`
- Create: `integration-audit/src/testFixtures/java/io/github/jasper/monitoring/audit/acceptance/AuditHttpClient.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/ManagementController.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/AuditManagementAuthorizer.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/ManagementHttpMapper.java`
- Create: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/ManagementController.java`
- Create: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/AuditManagementAuthorizer.java`
- Create: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/ManagementHttpMapper.java`
- Rewrite: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/AuditController.java`
- Rewrite: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/AuditController.java`
- Rewrite: `integration-audit/spring2-web/src/test/java/io/github/jasper/monitoring/audit/spring2/Spring2AuditWebAcceptanceTest.java`
- Rewrite: `integration-audit/spring3-web/src/test/java/io/github/jasper/monitoring/audit/spring3/Spring3AuditWebAcceptanceTest.java`
- Create: `integration-audit/README.md`

- [ ] **Step 1: Add a shared test that proves old access is forbidden**

```java
@Test
@Acceptance("AUD-MGMT-HOST-001")
void managementControllerUsesOnlyPublicContracts() {
    assertNoImports("io.github.jasper.monitoring.audit", "..core..", "..mybatis..", "..autoconfigure..");
}
```

- [ ] **Step 2: Run both hosts and observe old dependencies**

Run: `mvn -pl integration-audit -am verify`

Expected: fails because hosts import core/MyBatis and use string actions/old annotations.

- [ ] **Step 3: Convert hosts to real downstream consumers**

Remove direct core/MyBatis dependencies. Use concrete action types, ActionContract, ActionFact, FactBinding, host Shiro identity/resource authorization, strict control Handler, typed frontend binding, and five public management services. Share all 46 acceptance IDs from the design; child tests only provide Boot-specific startup and HTTP clients.

```java
public abstract class SharedMonitoringAcceptance {
    protected abstract AuditHttpClient client();

    @Test
    @Acceptance("AUD-MGMT-ALT-002")
    void staleAlertVersionReturnsConflict() {
        client().assertStaleAlertUpdateReturns409();
    }
}
```

- [ ] **Step 4: Run both audit variants separately and together**

Run: `mvn -pl integration-audit/spring2-web -am verify`

Run: `mvn -pl integration-audit/spring3-web -am verify`

Run: `mvn -pl integration-audit -am verify`

Expected: both variants report the exact same acceptance ID set with all IDs passing.

- [ ] **Step 5: Commit**

```bash
git add integration-audit
git commit -m "test(audit): enforce shared host acceptance matrix"
```

### Task 14: Update Generator, Public Docs, and Build Guardrails

**Files:**
- Create: `maven-plugin/src/main/resources/templates/IdentityContextProvider.java.tpl`
- Create: `maven-plugin/src/main/resources/templates/ResourceScopeAuthorizer.java.tpl`
- Create: `maven-plugin/src/main/resources/templates/TrustedProxyResolver.java.tpl`
- Create: `maven-plugin/src/main/resources/templates/ManagementAuthorizer.java.tpl`
- Rewrite: `maven-plugin/src/main/java/io/github/jasper/monitoring/maven/StarterFilesGenerator.java`
- Rewrite: `maven-plugin/src/test/java/io/github/jasper/monitoring/maven/StarterFilesGeneratorTest.java`
- Modify: `pom.xml`
- Create: `core/src/test/java/io/github/jasper/monitoring/core/architecture/ModuleArchitectureTest.java`
- Create: `README.md`, `README.en.md`, `docs/README.md`
- Create: `docs/integrators/getting-started.md`
- Create: `docs/integrators/actions-and-facts.md`
- Create: `docs/integrators/management-services.md`
- Create: `docs/integrators/frontend-signals.md`
- Create: `docs/security/security-model.md`
- Create: `docs/security/rules-and-controls.md`
- Create: `docs/operators/database.md`
- Create: `docs/operators/operations.md`
- Create: `docs/maintainers/architecture.md`
- Create: `docs/maintainers/development.md`
- Create: `docs/maintainers/release.md`
- Create: `docs/reference/catalogs.md`
- Create: `docs/reference/errors.md`
- Delete: `docs/集成指南.md`
- Delete: `docs/integration-guide.en.md`
- Delete: `docs/集成审计与基础项目验收.md`
- Delete: `docs/错误规范.md`
- Delete: `docs/error-contract.en.md`
- Delete: `docs/架构与运维说明.md`
- Delete: `docs/architecture-and-transaction-boundaries.en.md`
- Delete: `docs/领域模型与数据设计.md`
- Delete: `docs/MyBatis标准化ORM与架构设计评审稿.md`
- Delete: `docs/路线图.md`
- Delete: `docs/roadmap.en.md`
- Delete: `docs/1.0最小上线验收与版本排期.md`
- Delete: `docs/Javadoc生成说明.md`
- Delete: `output/pdf/集成指南-正式版.pdf`
- Delete: `output/pdf/MyBatis标准化ORM与架构设计评审稿-可视化.pdf`
- Delete: `output/pdf/MyBatis标准化ORM与架构设计评审稿-可视化-更新版.pdf`
- Test: `maven-plugin/src/test/java/io/github/jasper/monitoring/maven/StarterFilesGeneratorTest.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/architecture/DocumentationCatalogContractTest.java`

- [ ] **Step 1: Write generator and architecture tests**

```java
@Test
void generatedHostUsesSeparatedPublicSpis() {
    GeneratedFiles files = generator.generate(validRequest());
    assertThat(files.javaSources()).noneMatch(text -> text.contains("implements IdentityContextProvider, ResourceScopeAuthorizer"));
}
```

- [ ] **Step 2: Observe old templates and dependency leaks**

Run: `mvn -pl maven-plugin,core -am test`

Expected: tests fail on inline templates and missing architecture constraints.

- [ ] **Step 3: Add guardrails and role-oriented documentation**

Add ArchUnit 1.3.0 tests for `api <- core <- mybatis`, framework-free core, and audit-host public imports. Configure Maven PMD Plugin 3.24.0 CPD in `verify` with narrow exclusions for Boot servlet adapters, and Maven Linkcheck Plugin 1.2.1 for local Markdown links. `DocumentationCatalogContractTest` renders catalog/error references into `target/generated-docs` and byte-compares them with committed references. Each public document contains Audience, Owner, Source of truth, and Validated by metadata; reuse integration-audit examples instead of copying code.

```xml
<execution>
    <id>cpd-check</id>
    <phase>verify</phase>
    <goals><goal>cpd-check</goal></goals>
</execution>
```

- [ ] **Step 4: Run plugin, docs, architecture, and link verification**

Run: `mvn -pl maven-plugin,core,integration-audit -am verify`

Expected: templates generate strict SPI examples, architecture/duplicate/reference/link checks pass, and no removed API appears in public docs.

- [ ] **Step 5: Commit**

```bash
git add -A -- pom.xml maven-plugin core README.md README.en.md docs integration-audit/README.md output/pdf
git commit -m "docs: publish role-oriented integration contracts"
```

### Task 15: Remove the Legacy Runtime and Complete Verification

**Files:**
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/EventEnricher.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionAttribute.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionAttributes.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionAttributeTarget.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionDefinition.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionEnricher.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionFacts.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionInvocation.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/MonitoringEventPolicy.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/RuleManagementEntry.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/SecurityEventDraft.java`
- Delete: `api/src/main/java/io/github/jasper/monitoring/api/ControlTrigger.java`
- Move then delete flat replacements: `api/src/main/java/io/github/jasper/monitoring/api/MonitorAction.java`, `AccountType.java`, `AlertStatus.java`, `AuthorizationDecision.java`, `ControlActionType.java`, `ControlStatus.java`, `DispositionType.java`, `EventFactSource.java`, `EventInputIssue.java`, `EventInputIssueCode.java`, `EventInputStatus.java`, `EventInputValidation.java`, `IdentityContext.java`, `IdentityContextProvider.java`, `MonitoringContextAccessor.java`, `MonitoringMode.java`, `MonitoringRequestContext.java`, `ResourceScopeAuthorizer.java`, `ResourceScopeRequest.java`, `RiskLevel.java`, `RuleMode.java`, `RuleSource.java`, `SecurityEventResult.java`, `SecurityEventType.java`, `TrustedProxyResolver.java`
- Move then delete: `api/src/main/java/io/github/jasper/monitoring/api/SecurityFieldSanitizer.java` after its internal core replacement exists
- Delete: `core/src/main/java/io/github/jasper/monitoring/core/infrastructure/memory/InMemoryMonitoringRepository.java`
- Delete: `core/src/main/java/io/github/jasper/monitoring/core/infrastructure/memory/package-info.java`
- Delete: `core/src/main/java/io/github/jasper/monitoring/core/application/ActionEventRecorder.java`
- Delete: `core/src/main/java/io/github/jasper/monitoring/core/application/MonitoringActionRegistry.java`
- Delete: `core/src/main/java/io/github/jasper/monitoring/core/application/quality/DefaultMonitoringEventPolicy.java`
- Delete: `core/src/main/java/io/github/jasper/monitoring/core/application/control/AnnotatedControlHandler.java`
- Delete: `core/src/main/java/io/github/jasper/monitoring/core/application/control/DefaultControlActionTrigger.java`
- Delete: `core/src/main/java/io/github/jasper/monitoring/core/port/MonitoringRepository.java`
- Delete: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepository.java`
- Delete: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MonitoringSqlMapper.java`
- Delete: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MonitoringAdministrationMapper.java`
- Delete: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepositoryRegistrar.java`
- Delete: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepositoryTest.java`
- Delete: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AnnotatedActionSourceResolver.java`
- Delete: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AnnotatedMonitoringAspect.java`
- Delete: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AnnotatedMonitoringInterceptor.java`
- Delete: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AnnotatedActionSourceResolver.java`
- Delete: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AnnotatedActionMonitoringAspect.java`
- Delete: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AnnotatedActionMonitoringInterceptor.java`
- Modify: all module POMs and `bom/pom.xml`

- [ ] **Step 1: Add a legacy-symbol absence test**

```java
@Test
void legacyPublicTypesAreAbsent() {
    assertClassNotPresent("io.github.jasper.monitoring.api.SecurityEventDraft");
    assertClassNotPresent("io.github.jasper.monitoring.core.infrastructure.memory.InMemoryMonitoringRepository");
}
```

- [ ] **Step 2: Search and record remaining references**

Run: `rg -n "SecurityEventDraft|EventEnricher|MonitoringEventPolicy|InMemoryMonitoringRepository|@ControlTrigger|action\s*=\s*\"|enrichers\s*=" --glob '!docs/superpowers/**'`

Expected: command lists every remaining production, test, template, or public-doc reference to remove; after this task it returns no matches.

- [ ] **Step 3: Delete legacy code and unused dependencies**

Remove old types only after all callers use the new contracts. Remove Lombok where `rg -n "lombok" --glob '*.java'` has no results. Ensure BOM exposes only supported artifacts and common modules retain Java 8 release settings.

```powershell
rg -n "SecurityEventDraft|EventEnricher|MonitoringEventPolicy|InMemoryMonitoringRepository" api core mybatis spring-support spring2-starter spring3-starter integration-audit maven-plugin
rg -n "lombok" -g "*.java"
mvn -pl api,core,mybatis,spring-support -am package -DskipTests
```

- [ ] **Step 4: Execute complete acceptance**

Run: `mvn clean verify -DskipTests=false`

Run: `mvn -Pjavadoc package -DskipTests=false`

Run: `git status --short`

Expected: both Maven commands exit 0; all 46 audit IDs execute in Boot 2 and Boot 3; legacy search is empty; Git status contains only intended source changes and no generated files.

- [ ] **Step 5: Commit the final removal**

```bash
git add -A -- api core mybatis spring-support spring2-starter spring3-starter integration-audit maven-plugin bom pom.xml
git commit -m "refactor: complete strict monitoring runtime migration"
```

## Final Review Checklist

- [ ] Every design acceptance item 1-24 maps to at least one task and passing test.
- [ ] Action, fact, rule, control, and management types use consistent names across API, core, MyBatis, Spring, docs, and audit hosts.
- [ ] No provider selects actions; only `FactBinding` owns provider applicability.
- [ ] No browser payload establishes trusted action, identity, result, authorization, or control data.
- [ ] No production memory repository, fallback control Handler, permissive management Authorizer, or dynamic fake-rule toggle remains.
- [ ] Management reads and commands authorize before data access and append sanitized audits.
- [ ] Public docs contain one source of truth and no tracked generated PDF.
- [ ] Full reactor, Javadoc profile, Boot 2 audit, and Boot 3 audit pass from a clean checkout.

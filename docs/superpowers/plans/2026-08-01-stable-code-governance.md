# Stable Code Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace free-form reason and failure strings with registered, namespaced stable codes across action outcomes, authorization, rule diagnostics, controls, notifications, and API errors.

**Architecture:** Add a small framework-neutral code catalog in `api`, following the existing mutable-at-startup/frozen-at-runtime catalog pattern. Outcome reasons use the narrower `ReasonCode` type and are validated against Action/Outcome at the monitoring boundary; other persisted failure families use their own enums implementing `GovernedCode`. Spring Boot 2/3 assemble the same frozen catalog from built-ins and host contributors.

**Tech Stack:** Java 8, Maven reactor, JUnit 5, Spring Boot 2/3 ApplicationContextRunner, MyBatis/H2.

---

## File Structure

Create these focused API files:

- `api/src/main/java/io/github/jasper/monitoring/api/code/CodeFamily.java`: four governed code families.
- `api/src/main/java/io/github/jasper/monitoring/api/code/GovernedCode.java`: shared stable-code contract.
- `api/src/main/java/io/github/jasper/monitoring/api/code/ReasonCode.java`: ActionOutcome-compatible marker contract.
- `api/src/main/java/io/github/jasper/monitoring/api/code/CodeDefinition.java`: immutable scope metadata.
- `api/src/main/java/io/github/jasper/monitoring/api/code/StableCodeCatalog.java`: registration, namespace validation, lookup, scope validation, and freeze.
- `api/src/main/java/io/github/jasper/monitoring/api/code/StableCodeContributor.java`: host extension contract returning definitions.
- `api/src/main/java/io/github/jasper/monitoring/api/code/BuiltInReasonCodes.java`: nested built-in reason enums and registration.
- `api/src/main/java/io/github/jasper/monitoring/api/event/FailureClass.java`: failure-only classification, separated from the outcome container.
- `api/src/test/java/io/github/jasper/monitoring/api/code/StableCodeCatalogTest.java`: catalog contract tests.

Create these core-owned operational files:

- `core/src/main/java/io/github/jasper/monitoring/core/domain/control/ControlFailureCode.java`
- `core/src/main/java/io/github/jasper/monitoring/core/domain/notification/NotificationFailureCode.java`
- `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/RuleDiagnosticCode.java`
- `core/src/main/java/io/github/jasper/monitoring/core/domain/CoreStableCodes.java`: registers core operational definitions.

Modify existing models and assemblers rather than adding compatibility facades:

- `api/src/main/java/io/github/jasper/monitoring/api/event/ActionOutcome.java`
- `api/src/main/java/io/github/jasper/monitoring/api/AuthorizationDecision.java`
- `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringErrorCode.java`
- `api/src/main/java/io/github/jasper/monitoring/api/EventInputIssueCode.java`
- `core/src/main/java/io/github/jasper/monitoring/core/application/MonitoringService.java`
- `core/src/main/java/io/github/jasper/monitoring/core/application/SecurityEventAssembler.java`
- `core/src/main/java/io/github/jasper/monitoring/core/domain/ControlExecution.java`
- `core/src/main/java/io/github/jasper/monitoring/core/domain/NotificationDelivery.java`
- `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/RuleEvaluationContext.java`
- both `TypedMonitorActionAspect.java` files and both auto-configuration files.
- MyBatis store/PO mapping only where persisted operational codes must be resolved.

### Task 1: Add the stable-code API and catalog

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/code/CodeFamily.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/code/GovernedCode.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/code/ReasonCode.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/code/CodeDefinition.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/code/StableCodeCatalog.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/code/StableCodeContributor.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/code/StableCodeCatalogTest.java`

- [ ] **Step 1: Write failing catalog tests**

Cover valid built-in/host registration, duplicate definitions, illegal lowercase/dynamic content, host attempts to register `MON.*`, ReasonCode allowing SUCCESS, Action/Outcome mismatch, and freeze:

```java
enum HostReason implements ReasonCode {
    CREDIT_REJECTED;
    @Override public String getCode() { return "ACME.ORDER.CREDIT_REJECTED"; }
    @Override public CodeFamily getFamily() { return CodeFamily.OUTCOME_REASON; }
}

@Test
void validatesRegisteredReasonAgainstActionAndOutcome() {
    StableCodeCatalog catalog = new StableCodeCatalog("ACME");
    catalog.registerHost(CodeDefinition.reason(HostReason.CREDIT_REJECTED)
        .allow(SecurityEventResult.DENIED).appliesTo(OrderAction.class).build());
    catalog.freeze();

    catalog.validateReason(HostReason.CREDIT_REJECTED, OrderAction.class,
        SecurityEventResult.DENIED);
    assertThrows(MonitoringConfigurationException.class, () ->
        catalog.validateReason(HostReason.CREDIT_REJECTED, QueryAction.class,
            SecurityEventResult.DENIED));
}
```

- [ ] **Step 2: Run the API test and verify the missing types fail compilation**

Run:

```bash
mvn -pl api -Dtest=StableCodeCatalogTest test
```

Expected: FAIL because `io.github.jasper.monitoring.api.code` does not exist.

- [ ] **Step 3: Implement the minimal contracts and immutable definition**

Use these exact public signatures:

```java
public enum CodeFamily {
    OUTCOME_REASON, INPUT_DIAGNOSTIC, OPERATIONAL_FAILURE, API_ERROR
}

public interface GovernedCode {
    String getCode();
    CodeFamily getFamily();
}

public interface ReasonCode extends GovernedCode {
}

public interface StableCodeContributor {
    java.util.Collection<CodeDefinition> definitions();
}
```

`CodeDefinition.Builder` must expose `allow(SecurityEventResult...)`, `appliesTo(Class<? extends ActionType>...)`, and `appliesToContract(Class<? extends ActionContract>...)`. Copy every supplied collection and expose immutable views.

- [ ] **Step 4: Implement catalog validation and freeze**

`StableCodeCatalog` must use `LinkedHashMap<String, CodeDefinition>`, the pattern `[A-Z][A-Z0-9_]*(?:\.[A-Z][A-Z0-9_]*){2,}`, and these methods:

```java
public void registerBuiltIn(CodeDefinition definition);
public void registerHost(CodeDefinition definition);
public GovernedCode require(String code);
public void validateReason(ReasonCode reason,
    Class<? extends ActionType> actionType, SecurityEventResult result);
public void freeze();
public boolean isFrozen();
```

`registerBuiltIn` accepts only `MON.*`; `registerHost` accepts only the configured host owner and rejects `MON.*`. Reason definitions must allow at least one of DENIED/FAILURE and must reject SUCCESS. Contract scope matches when `contract.isAssignableFrom(actionType)`.

- [ ] **Step 5: Run the API tests**

Run:

```bash
mvn -pl api -Dtest=StableCodeCatalogTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the foundation**

```bash
git add api/src/main/java/io/github/jasper/monitoring/api/code api/src/test/java/io/github/jasper/monitoring/api/code
git commit -m "feat(api): add stable code catalog"
```

### Task 2: Define built-in outcome reasons and semantic API errors

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/code/BuiltInReasonCodes.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringErrorCode.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/EventInputIssueCode.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/code/BuiltInReasonCodesTest.java`
- Test: existing API exception and input issue tests.

- [ ] **Step 1: Write failing built-in inventory tests**

Assert every built-in is registered, every code starts `MON.`, codes are unique, authentication reject reasons only allow DENIED, and infrastructure reasons only allow FAILURE. Include these nested groups:

```text
Authentication: INVALID_CREDENTIAL, CAPTCHA_REQUIRED, CAPTCHA_INVALID,
  CAPTCHA_EXPIRED, MFA_INVALID, ACCOUNT_DISABLED, ACCOUNT_LOCKED,
  RATE_LIMITED, AUTHENTICATION_UNAVAILABLE
Authorization: RESOURCE_SCOPE_DENIED, NO_DECISION,
  EVALUATION_ERROR, AUTHORIZER_NOT_CONFIGURED
Action: BLOCKED, INVOCATION_FAILED, REQUEST_FAILED
Privilege: SELF_ESCALATION
```

- [ ] **Step 2: Run the inventory test and verify failure**

```bash
mvn -pl api -Dtest=BuiltInReasonCodesTest test
```

Expected: FAIL because `BuiltInReasonCodes` is missing.

- [ ] **Step 3: Implement grouped nested enums and registration**

Each nested enum implements `ReasonCode` and stores the full code, for example:

```java
public enum Authentication implements ReasonCode {
    INVALID_CREDENTIAL("MON.AUTH.INVALID_CREDENTIAL"),
    CAPTCHA_REQUIRED("MON.AUTH.CAPTCHA_REQUIRED"),
    CAPTCHA_INVALID("MON.AUTH.CAPTCHA_INVALID"),
    CAPTCHA_EXPIRED("MON.AUTH.CAPTCHA_EXPIRED"),
    MFA_INVALID("MON.AUTH.MFA_INVALID"),
    ACCOUNT_DISABLED("MON.AUTH.ACCOUNT_DISABLED"),
    ACCOUNT_LOCKED("MON.AUTH.ACCOUNT_LOCKED"),
    RATE_LIMITED("MON.AUTH.RATE_LIMITED"),
    AUTHENTICATION_UNAVAILABLE("MON.AUTH.AUTHENTICATION_UNAVAILABLE");
}
```

`BuiltInReasonCodes.registerInto(StableCodeCatalog)` registers definitions with explicit allowed outcomes and actions/contracts; authentication definitions may initially scope to the existing login action and are updated atomically by the authentication plan when `BuiltInActions.Login` replaces it.

- [ ] **Step 4: Migrate existing API error values without compatibility aliases**

Make `MonitoringErrorCode implements GovernedCode`, return `CodeFamily.API_ERROR`, and replace numeric values with semantic codes such as:

```java
REQUIRED_FIELD_MISSING("MON.API.REQUIRED_FIELD_MISSING"),
ACTION_NOT_REGISTERED("MON.API.ACTION_NOT_REGISTERED"),
PERSISTENCE_OPERATION_FAILED("MON.API.PERSISTENCE_OPERATION_FAILED");
```

Make `EventInputIssueCode implements GovernedCode`, return `MON.INPUT.<ENUM_NAME>` and `CodeFamily.INPUT_DIAGNOSTIC`. Do not rename enum constants in this task; authentication cleanup removes `MISSING_ATTEMPTED_ACCOUNT_HASH` later.

- [ ] **Step 5: Run all API tests**

```bash
mvn -pl api test
```

Expected: PASS with no `MON-[0-9]` assertions remaining.

- [ ] **Step 6: Commit built-ins**

```bash
git add api/src/main/java/io/github/jasper/monitoring/api/code/BuiltInReasonCodes.java api/src/main/java/io/github/jasper/monitoring/api/error/MonitoringErrorCode.java api/src/main/java/io/github/jasper/monitoring/api/EventInputIssueCode.java api/src/test
git commit -m "refactor(api): govern built-in monitoring codes"
```

### Task 3: Make action and authorization reasons strongly typed

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/event/FailureClass.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/event/ActionOutcome.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/AuthorizationDecision.java`
- Modify: `api/src/test/java/io/github/jasper/monitoring/api/event/ActionOutcomeTest.java`
- Modify: authorization tests in `core` and both starters.

- [ ] **Step 1: Change tests to require ReasonCode**

Add assertions for mandatory denied/failure reasons and forbidden success reasons:

```java
@Test
void deniedRequiresTypedReasonAndFailureRequiresClassification() {
    ActionOutcome denied = ActionOutcome.denied(
        BuiltInReasonCodes.Authorization.RESOURCE_SCOPE_DENIED, 3L);
    assertSame(BuiltInReasonCodes.Authorization.RESOURCE_SCOPE_DENIED,
        denied.getReason());
    assertThrows(NullPointerException.class, () -> ActionOutcome.denied(null, 0L));
}
```

- [ ] **Step 2: Run focused API tests and verify signature failures**

```bash
mvn -pl api -Dtest=ActionOutcomeTest test
```

Expected: FAIL because factories still accept String and `getReason()` is absent.

- [ ] **Step 3: Replace String fields and factories**

Use:

```java
private final ReasonCode reason;

public static ActionOutcome success(long latencyMs);
public static ActionOutcome denied(ReasonCode reason, long latencyMs);
public static ActionOutcome failure(ReasonCode reason,
    FailureClass failureClass, long latencyMs);
public ReasonCode getReason();
public FailureClass getFailureClass();
```

Move the existing values (`AUTHORIZATION`, `INFRASTRUCTURE`, `UNKNOWN`) into the top-level `FailureClass` enum. Delete nested `ActionOutcome.ExceptionClassification`, `getExceptionClassification()`, and the String-based factories. For `AuthorizationDecision`, use `ReasonCode reason`, `denied(ReasonCode)`, and `getReason()`. Delete `getReasonCode()` from these transient models; persisted events continue exposing their stored String code.

- [ ] **Step 4: Migrate action/authorization call sites to built-ins**

Apply the same mapping in Spring 2 and Spring 3:

```text
ACTION_BLOCKED -> BuiltInReasonCodes.Action.BLOCKED
ACTION_INVOCATION_FAILED -> BuiltInReasonCodes.Action.INVOCATION_FAILED
HTTP_REQUEST_FAILED -> BuiltInReasonCodes.Action.REQUEST_FAILED
HTTP_ACCESS_DENIED -> BuiltInReasonCodes.Authorization.RESOURCE_SCOPE_DENIED
AUTHORIZATION_NO_DECISION -> BuiltInReasonCodes.Authorization.NO_DECISION
AUTHORIZATION_ERROR -> BuiltInReasonCodes.Authorization.EVALUATION_ERROR
RESOURCE_SCOPE_AUTHORIZER_NOT_CONFIGURED -> BuiltInReasonCodes.Authorization.AUTHORIZER_NOT_CONFIGURED
SELF_PRIVILEGE_ESCALATION -> BuiltInReasonCodes.Privilege.SELF_ESCALATION
```

Remove local String constants made obsolete by the mapping.

- [ ] **Step 5: Run API, core, and starter tests**

```bash
mvn -pl spring2-starter,spring3-starter -am test
```

Expected: PASS.

- [ ] **Step 6: Commit strong outcome types**

```bash
git add api/src/main/java/io/github/jasper/monitoring/api/event api/src/main/java/io/github/jasper/monitoring/api/AuthorizationDecision.java api/src/test core spring2-starter spring3-starter integration-audit
git commit -m "refactor(api): type action outcome reasons"
```

### Task 4: Validate reasons at the monitoring boundary

**Files:**
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/MonitoringService.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/SecurityEventAssembler.java`
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/MonitoringServiceTest.java`
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/SecurityEventAssemblerTest.java`

- [ ] **Step 1: Add failing mismatch tests**

Construct a registered Query action with an authentication-only reason and assert `MonitoringConfigurationException`; assert a valid action reason persists its exact code string.

- [ ] **Step 2: Run focused core tests**

```bash
mvn -pl core -am -Dtest=MonitoringServiceTest,SecurityEventAssemblerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `MonitoringService` has no `StableCodeCatalog` dependency.

- [ ] **Step 3: Inject and enforce the frozen catalog**

Add `StableCodeCatalog codes` to the `MonitoringService` constructor. Before decide/monitor assembly, call:

```java
ActionOutcome outcome = execution.getOutcome();
if (outcome.getReason() != null) {
    codes.validateReason(outcome.getReason(), execution.getActionType(), outcome.getResult());
}
```

Update `SecurityEventAssembler` to persist:

```java
.reasonCode(outcome.getReason() == null ? null : outcome.getReason().getCode())
```

- [ ] **Step 4: Run focused core tests**

Use the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit boundary validation**

```bash
git add core
git commit -m "feat(core): validate action reason scopes"
```

### Task 5: Type control, notification, and rule diagnostic failures

**Files:**
- Create: the four core files listed in File Structure.
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/ControlExecution.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/NotificationDelivery.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/RuleEvaluationContext.java`
- Modify: control/notification services and tests.
- Modify: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/control/GenericIpControlHandler.java`

- [ ] **Step 1: Add failing domain tests for typed operational codes**

Update control and notification tests so factories accept `GovernedCode` and persisted accessors return the code value. Add a rule evaluation assertion using `RuleDiagnosticCode.ACTION_NOT_APPLICABLE` instead of a String.

- [ ] **Step 2: Run focused tests and verify compile failures**

```bash
mvn -pl core -am -Dtest=ControlExecutionServiceTest,NotificationDeliveryServiceTest,DefaultRuleCatalogTypedIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL until typed codes exist.

- [ ] **Step 3: Implement explicit operational enums**

At minimum define:

```text
ControlFailureCode:
  HOST_HANDLER_REQUIRED, UNSUPPORTED_ACTION, HANDLER_RETURNED_NULL,
  HANDLER_FAILED, INVALID_HANDLER_STATUS, ANNOTATED_METHOD_MISSING,
  ANNOTATED_METHOD_INACCESSIBLE, ANNOTATED_INVOCATION_FAILED,
  IP_RULE_NOT_ALLOWED, IP_SUBJECT_REQUIRED, IP_LITERAL_REQUIRED,
  IP_CONTROL_EXPIRED, IP_TTL_EXCEEDED, IP_CAPACITY_REJECTED,
  IP_STATE_FAILED
NotificationFailureCode:
  AGGREGATE_NOT_FOUND, ATTEMPT_LIMIT_REACHED, CHANNEL_FAILURE
RuleDiagnosticCode:
  ACTION_NOT_APPLICABLE, FACT_MISSING, FACT_SOURCE_NOT_ACCEPTED
```

Give each semantic `MON.CONTROL.*`, `MON.NOTIFICATION.*`, or `MON.RULE.*` code and family `OPERATIONAL_FAILURE` or `INPUT_DIAGNOSTIC`.

- [ ] **Step 4: Replace domain String factories and concatenated reasons**

`ControlExecution.failed/skipped/rejected` accept `GovernedCode`; `NotificationDelivery.failedAttempt` accepts `GovernedCode`; `RuleEvaluationContext.Evaluation` holds `GovernedCode`. Dynamic action names remain structured fields and must not be concatenated into code values.

For persistence-facing accessors, expose `getFailureCode()` returning `GovernedCode`; adapters write `getCode()`. Trusted restoration resolves through `StableCodeCatalog.require(storedValue)`.

- [ ] **Step 5: Run core and spring-support tests**

```bash
mvn -pl spring-support -am test
```

Expected: PASS with no uppercase failure literals remaining in the migrated services.

- [ ] **Step 6: Commit operational codes**

```bash
git add core spring-support
git commit -m "refactor(core): type operational failure codes"
```

### Task 6: Assemble and freeze the catalog in both starters

**Files:**
- Modify: both `AbnormalAccessMonitorProperties.java` files.
- Modify: both `AbnormalAccessMonitorAutoConfiguration.java` files.
- Modify: both `AbnormalAccessMonitorAutoConfigurationTest.java` files.
- Modify: both `spring-configuration-metadata.json` files.

- [ ] **Step 1: Add parallel auto-configuration tests**

Assert a single frozen `StableCodeCatalog`, built-in resolution, host contributor registration under configured owner, duplicate rejection, and rejection of a host contributor under `MON.*`.

- [ ] **Step 2: Run both starter tests and verify missing bean failures**

```bash
mvn -pl spring2-starter,spring3-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because no catalog bean exists.

- [ ] **Step 3: Add minimal owner configuration and catalog beans**

Add `String codeOwner` under `abnormal.access.monitor`, normalized to uppercase and validated only when host contributors are present. Build each catalog identically:

```java
StableCodeCatalog catalog = new StableCodeCatalog(properties.getCodeOwner());
BuiltInReasonCodes.registerInto(catalog);
CoreStableCodes.registerInto(catalog);
for (StableCodeContributor contributor : contributors) {
    for (CodeDefinition definition : contributor.definitions()) {
        catalog.registerHost(definition);
    }
}
catalog.freeze();
return catalog;
```

Inject the catalog into `MonitoringService` and MyBatis restoration paths.

- [ ] **Step 4: Run starter and MyBatis tests**

```bash
mvn -pl spring2-starter,spring3-starter,mybatis -am test
```

Expected: PASS with Boot 2/3 parity.

- [ ] **Step 5: Commit starter wiring**

```bash
git add spring2-starter spring3-starter mybatis
git commit -m "feat(starters): configure stable code catalog"
```

### Task 7: Remove legacy strings and document the final contract

**Files:**
- Modify: `docs/集成指南.md`
- Modify: `docs/领域模型与数据设计.md`
- Modify: `docs/架构与运维说明.md`
- Modify: tests/examples containing removed reason strings.

- [ ] **Step 1: Run the legacy scan before cleanup**

```powershell
rg -n 'ActionOutcome\.(denied|failure)\("|AuthorizationDecision\.denied\("|ControlExecution\.(failed|skipped|rejected)\([^,]+,\s*"|failedAttempt\("|MON-[0-9]{3}' api core spring-support spring2-starter spring3-starter integration-audit
```

Expected: matches identify every remaining old production/test call site.

- [ ] **Step 2: Remove remaining String paths and update docs**

Document `OWNER.DOMAIN.CAUSE`, family separation, contributor registration, startup failure cases, and the rule that internal reasons are not anonymous HTTP response codes. Do not add deprecated overloads or aliases.

- [ ] **Step 3: Re-run the legacy scan**

Use Step 1. Expected: no matches.

- [ ] **Step 4: Run the full reactor**

```bash
mvn clean verify -DskipTests=false
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit final cleanup**

```bash
git add api core spring-support spring2-starter spring3-starter mybatis integration-audit docs
git commit -m "docs(api): publish stable code governance"
```

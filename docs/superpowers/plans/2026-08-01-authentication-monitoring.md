# Authentication Monitoring and Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the ambiguous login-failure event path with one governed Login action, protected attempted-account facts, complete success/denied/failure recording, and system-scoped account/IP controls that can be consumed before the next authentication attempt.

**Architecture:** Callers pass only `LoginSubjectInput(loginUser, realm)` to an `AuthenticationMonitor` facade. The core canonicalizes aliases, derives a versioned HMAC subject key, records it as a typed Fact, maps outcome to LOGIN_SUCCESS or LOGIN_FAILURE, and evaluates rules against persisted Facts. MyBatis stores opaque subjects and system-scoped controls; Spring Boot 2/3 expose identical configuration and wiring. This plan depends on `2026-08-01-stable-code-governance.md` and deliberately removes the old login-failure action and free-form attribute path.

**Tech Stack:** Java 8, Maven reactor, JUnit 5, Spring Boot 2/3 ApplicationContextRunner, MyBatis/H2, JCE HmacSHA256.

---

## File Structure

Create the public authentication surface in `api`:

- `api/src/main/java/io/github/jasper/monitoring/api/authentication/AuthenticationMonitor.java`
- `api/src/main/java/io/github/jasper/monitoring/api/authentication/LoginSubjectInput.java`
- `api/src/main/java/io/github/jasper/monitoring/api/authentication/LoginSubjectCanonicalizer.java`
- `api/src/main/java/io/github/jasper/monitoring/api/authentication/AuthenticationStage.java`

Create the implementation boundary in `core`:

- `core/src/main/java/io/github/jasper/monitoring/core/application/authentication/DefaultAuthenticationMonitor.java`
- `core/src/main/java/io/github/jasper/monitoring/core/application/authentication/LoginSubjectKeyFactory.java`
- `core/src/main/java/io/github/jasper/monitoring/core/port/AuthenticationControlRepository.java`

Modify, do not wrap, these existing concepts:

- `BuiltInActions.LoginFailure` becomes `BuiltInActions.Login`.
- `ActionDefinition` resolves an event type from the outcome.
- `BuiltInFacts` owns `LoginSubjectKey` and `AuthenticationStageFact`.
- `SecurityEvent` exposes typed Fact lookup.
- AUTH-01/02/03 consume typed Facts and full success/failure history.
- Control persistence gains `system_id` and an active-control query.
- Both audit applications use only `AuthenticationMonitor` for authentication monitoring.

There is no compatibility adapter, deprecated alias, String reason overload, `attempted_account_hash` fallback, or parallel direct `MonitoringService` submission when this plan is complete.

### Task 1: Map one Login action outcome to the correct event type

**Files:**
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/action/ActionDefinition.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/action/BuiltInActions.java`
- Modify: `api/src/test/java/io/github/jasper/monitoring/api/action/ActionDefinitionTest.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/SecurityEventAssembler.java`
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/SecurityEventAssemblerTest.java`

- [ ] **Step 1: Write failing outcome-mapping tests**

Register one Login definition and assert:

```java
assertEquals(SecurityEventType.LOGIN_SUCCESS,
    definition.resolveEventType(SecurityEventResult.SUCCESS));
assertEquals(SecurityEventType.LOGIN_FAILURE,
    definition.resolveEventType(SecurityEventResult.DENIED));
assertEquals(SecurityEventType.LOGIN_FAILURE,
    definition.resolveEventType(SecurityEventResult.FAILURE));
```

Also assemble one SUCCESS and one DENIED execution and assert the event types differ while both use the same action type.

- [ ] **Step 2: Run focused tests and verify failure**

```bash
mvn -pl core -am -Dtest=ActionDefinitionTest,SecurityEventAssemblerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `ActionDefinition` has only one fixed event type.

- [ ] **Step 3: Add explicit outcome mapping**

Store an immutable `EnumMap<SecurityEventResult, SecurityEventType>`. Preserve `builder.eventType(type)` as the default for ordinary actions and add:

```java
public Builder eventTypeFor(SecurityEventResult result, SecurityEventType eventType);
public SecurityEventType resolveEventType(SecurityEventResult result);
```

The builder must reject a missing default and null mapping entries. `SecurityEventAssembler` calls `resolveEventType(outcome.getResult())`.

- [ ] **Step 4: Replace the built-in login action**

Delete `BuiltInActions.LoginFailure`. Add `BuiltInActions.Login` with one authentication contract and this mapping:

```text
default / DENIED / FAILURE -> LOGIN_FAILURE
SUCCESS                    -> LOGIN_SUCCESS
```

Update the authentication reason definitions from the code-governance plan to apply to `BuiltInActions.Login` only.

- [ ] **Step 5: Run API and core tests**

```bash
mvn -pl core -am test
```

Expected: PASS after updating compile-time references that belong to core tests. Integration references are migrated in Task 9.

- [ ] **Step 6: Commit the action model**

```bash
git add api/src/main/java/io/github/jasper/monitoring/api/action api/src/test/java/io/github/jasper/monitoring/api/action core/src/main/java/io/github/jasper/monitoring/core/application/SecurityEventAssembler.java core/src/test/java/io/github/jasper/monitoring/core/SecurityEventAssemblerTest.java core/src/test/java/io/github/jasper/monitoring/core/DefaultRuleCatalogCoverageTest.java
git commit -m "refactor(api): model login as one outcome-aware action"
```

### Task 2: Add the caller-facing authentication contract and typed facts

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/authentication/AuthenticationMonitor.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/authentication/LoginSubjectInput.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/authentication/LoginSubjectCanonicalizer.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/authentication/AuthenticationStage.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/fact/BuiltInFacts.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/EventInputIssueCode.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/authentication/AuthenticationContractTest.java`
- Test: `api/src/test/java/io/github/jasper/monitoring/api/fact/BuiltInFactsTest.java`

- [ ] **Step 1: Write failing contract tests**

Verify `LoginSubjectInput` rejects blank login user/realm, never renders the login user from `toString()`, and the facade exposes only host-domain inputs:

```java
ActionDecision preCheck(LoginSubjectInput subject);
void recordDenied(LoginSubjectInput subject, AuthenticationStage stage, ReasonCode reason);
void recordFailure(LoginSubjectInput subject, AuthenticationStage stage,
                   ReasonCode reason, FailureClass failureClass);
void recordSuccess(LoginSubjectInput subject, IdentityContext authenticatedIdentity);
```

The public package must not expose a subject-key factory or accept a precomputed key.

- [ ] **Step 2: Run API tests and verify missing types**

```bash
mvn -pl api -Dtest=AuthenticationContractTest,BuiltInFactsTest test
```

Expected: FAIL because the authentication package and facts do not exist.

- [ ] **Step 3: Implement the minimal API**

`LoginSubjectCanonicalizer` has one method:

```java
String canonicalize(LoginSubjectInput subject);
```

`AuthenticationStage` contains `CREDENTIAL`, `CAPTCHA`, and `MFA`. Keep raw login input transient; do not add it to `ActionRequest`, attributes, Facts, logs, or exception messages.

- [ ] **Step 4: Register protected facts**

Add to `BuiltInFacts`:

```text
LoginSubjectKey         key=login_subject_key, type=String, source=EXTENSION
AuthenticationStageFact key=authentication_stage, type=AuthenticationStage, source=EXTENSION
```

Mark the subject key internal/protected using the existing fact metadata model, cap encoded values at 128 characters, and register both definitions in the built-in Fact catalog.

- [ ] **Step 5: Delete the obsolete input diagnostic**

Remove `EventInputIssueCode.MISSING_ATTEMPTED_ACCOUNT_HASH` and all tests/documentation that describe a caller-provided account hash. No replacement input diagnostic is needed because the facade always derives the key.

- [ ] **Step 6: Run and commit**

```bash
mvn -pl api test
git add api/src/main/java/io/github/jasper/monitoring/api/authentication api/src/main/java/io/github/jasper/monitoring/api/fact/BuiltInFacts.java api/src/main/java/io/github/jasper/monitoring/api/EventInputIssueCode.java api/src/test
git commit -m "feat(api): add authentication monitoring contract"
```

### Task 3: Support typed Fact lookup and persistence round trips

**Files:**
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/SecurityEvent.java`
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/SecurityEventTest.java`
- Modify: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringStoreTest.java`
- Modify: `mybatis/src/main/resources/db/monitoring-schema.sql`
- Create: `mybatis/src/main/resources/db/upgrade/monitoring-authentication-subject-v8.sql`

- [ ] **Step 1: Write failing typed lookup tests**

Assert lookup decodes a matching fact, returns empty for an absent fact, and throws `MonitoringStateException` for a stored value type that contradicts the registered definition:

```java
Optional<T> getFact(FactDefinition<T> definition);
```

- [ ] **Step 2: Run the core test and verify failure**

```bash
mvn -pl core -am -Dtest=SecurityEventTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `SecurityEvent` only exposes the raw Fact list.

- [ ] **Step 3: Implement lookup through FactDefinition**

Iterate the immutable event facts, compare stable keys, validate `valueType`, and call `definition.decode(valueText)`. Do not duplicate enum parsing or string conversion in rules.

- [ ] **Step 4: Prove MyBatis round-trip behavior**

Persist an event containing both authentication facts, reload it with `findSince`, and assert typed values are available. Add an index to the baseline schema and v8 upgrade:

```sql
CREATE INDEX idx_monitoring_event_fact_lookup
    ON monitoring_security_event_fact (fact_key, value_text, event_id);
```

- [ ] **Step 5: Run and commit**

```bash
mvn -pl mybatis -am test
git add core/src/main/java/io/github/jasper/monitoring/core/domain/SecurityEvent.java core/src/test/java/io/github/jasper/monitoring/core/SecurityEventTest.java mybatis/src/test mybatis/src/main/resources/db
git commit -m "feat(core): expose persisted authentication facts"
```

### Task 4: Derive opaque account subjects inside core

**Files:**
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/authentication/LoginSubjectKeyFactory.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/authentication/LoginSubjectKeyFactoryTest.java`

- [ ] **Step 1: Write failing cryptographic contract tests**

Cover deterministic output, alias equivalence through the canonicalizer, realm separation, secret separation, invalid short secrets, and absence of raw/canonical input in returned keys and exception messages.

- [ ] **Step 2: Run the focused test**

```bash
mvn -pl core -am -Dtest=LoginSubjectKeyFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the factory is missing.

- [ ] **Step 3: Implement one versioned derivation format**

Use Java 8 JCE `HmacSHA256` over UTF-8 bytes:

```text
realm + NUL + canonicalLoginSubject
```

Return `v1:` plus URL-safe Base64 without padding. Require at least 32 decoded secret bytes and take a defensive copy. The factory accepts `LoginSubjectInput`, invokes the canonicalizer internally, and never exposes canonical material.

- [ ] **Step 4: Run and commit**

```bash
mvn -pl core -am -Dtest=LoginSubjectKeyFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test
git add core/src/main/java/io/github/jasper/monitoring/core/application/authentication/LoginSubjectKeyFactory.java core/src/test/java/io/github/jasper/monitoring/core/application/authentication/LoginSubjectKeyFactoryTest.java
git commit -m "feat(core): derive protected login subject keys"
```

### Task 5: Make persisted controls system-scoped and queryable

**Files:**
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/ControlCommand.java`
- Create: `core/src/main/java/io/github/jasper/monitoring/core/port/AuthenticationControlRepository.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/TypedRuleEvaluationService.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/po/ControlActionPo.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/mapper/ControlMapper.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/repository/MyBatisControlExecutionStore.java`
- Modify: `mybatis/src/main/resources/db/monitoring-schema.sql`
- Modify: `mybatis/src/main/resources/db/upgrade/monitoring-authentication-subject-v8.sql`
- Test: existing core control tests and MyBatis control tests.

- [ ] **Step 1: Write failing isolation and expiry tests**

Save effective controls for the same opaque subject under two system IDs. Assert the authentication query returns only records matching requested system, subject, `SUCCEEDED` status, and `expiresAt > now`; failed, pending, and expired controls must be invisible.

- [ ] **Step 2: Run focused control tests**

```bash
mvn -pl mybatis -am -Dtest=*Control*Test -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because control records have no system ID or active query.

- [ ] **Step 3: Carry system ID from event to command and persistence**

Add mandatory `systemId` to new `ControlCommand` construction and to persisted control state. Delete constructors that permit a new command without system ID. `TypedRuleEvaluationService` copies the triggering event's system ID when it creates each command.

- [ ] **Step 4: Add the narrow read port**

```java
public interface AuthenticationControlRepository {
    List<ControlCommand> findActive(String systemId, String subject, Instant at);
}
```

`MyBatisControlExecutionStore` implements this port in addition to `ControlExecutionStore`. Returning commands is sufficient because the query admits only successfully executed controls. The mapper query includes all four predicates (`system_id`, `subject`, `status`, expiry) and orders deterministically by execution time/control ID.

- [ ] **Step 5: Migrate schema and indexes**

Add `system_id VARCHAR(128) NOT NULL` to the baseline control table and an index beginning with:

```text
(system_id, subject, status, expires_at, action_type)
```

The v8 script contains both the Fact index from Task 3 and the control-table migration for pre-existing unpublished installations. Document the explicit legacy system value used while adding the non-null column; do not infer it from account data.

- [ ] **Step 6: Run and commit**

```bash
mvn -pl mybatis -am test
git add core/src/main/java/io/github/jasper/monitoring/core/domain/ControlCommand.java core/src/main/java/io/github/jasper/monitoring/core/port/AuthenticationControlRepository.java core/src/main/java/io/github/jasper/monitoring/core/application/TypedRuleEvaluationService.java core/src/test mybatis/src/main mybatis/src/test
git commit -m "feat(mybatis): persist system-scoped authentication controls"
```

### Task 6: Implement the authentication facade

**Files:**
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/authentication/DefaultAuthenticationMonitor.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/application/authentication/DefaultAuthenticationMonitorTest.java`

- [ ] **Step 1: Write failing facade behavior tests**

Cover:

- no active control returns ALLOW;
- account `REQUIRE_CAPTCHA`/`REQUIRE_MFA` returns matching requirements;
- account or trusted-IP `RATE_LIMIT`, `DENY`, or `LOCK` returns BLOCK;
- controls from another system or expired controls do not apply;
- denied/failed events use anonymous actor identity and carry key/stage Facts;
- success requires a non-anonymous authenticated identity and emits LOGIN_SUCCESS;
- monitoring write failure does not change the host authentication conclusion;
- control-store failure follows `ActionFailurePolicy`, defaulting to `OBSERVE_ONLY` (loss of supplemental control only).

- [ ] **Step 2: Run focused tests**

```bash
mvn -pl core -am -Dtest=DefaultAuthenticationMonitorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the implementation is missing.

- [ ] **Step 3: Implement one internal execution builder**

The facade derives the key and creates `ActionExecution<BuiltInActions.Login>` internally. It obtains request context from the existing context accessor, sets `IdentityContext.anonymous()` for DENIED/FAILURE, attaches both typed Facts, and passes only governed reasons. `recordSuccess` uses the supplied authenticated identity and rejects anonymous or blank user IDs.

Do not accept `ActionRequest`, `ActionExecution`, attributes, or precomputed subjects in this API.

- [ ] **Step 4: Consume account and IP controls**

Query the opaque account subject and the normalized trusted source-IP subject separately. Merge active controls with this precedence:

```text
BLOCK: LOCK / DENY / RATE_LIMIT
CHALLENGE: REQUIRE_MFA / REQUIRE_CAPTCHA
ALLOW: no effective control
```

Never transform IP-triggered controls into account controls. Preserve matched rule IDs and control metadata in `ActionDecision`.

- [ ] **Step 5: Contain monitoring failures**

After the host has reached an authentication result, catch monitoring pipeline runtime failures at the facade boundary. Log through `java.util.logging.Logger` at warning level, extracting `MonitoringFailure.getErrorCode()` when available and otherwise using `MonitoringErrorCode.MONITORING_SYSTEM_UNAVAILABLE`; include request/trace IDs but never login input, canonical material, or the derived key. Return without changing the host result. For `preCheck`, catch repository failures and return ALLOW under `OBSERVE_ONLY` or BLOCK under `FAIL_CLOSED`.

- [ ] **Step 6: Run and commit**

```bash
mvn -pl core -am test
git add core/src/main/java/io/github/jasper/monitoring/core/application/authentication core/src/test/java/io/github/jasper/monitoring/core/application/authentication
git commit -m "feat(core): add authentication monitoring facade"
```

### Task 7: Rewrite AUTH rules against the typed subject model

**Files:**
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/DefaultRuleCatalog.java`
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/DefaultRuleCatalogAuthenticationTest.java`
- Modify: `core/src/test/java/io/github/jasper/monitoring/core/DefaultRuleCatalogCoverageTest.java`

- [ ] **Step 1: Replace tests with domain-observable scenarios**

Build histories using `BuiltInActions.Login`, typed Facts, and outcomes. Assert:

- AUTH-01 aggregates DENIED credential/CAPTCHA attempts by exact LoginSubjectKey;
- AUTH-02 counts distinct subject keys per trusted IP and uses LOGIN_SUCCESS plus LOGIN_FAILURE as its denominator;
- AUTH-03 never treats one attempted-account failure as authenticated actor activity;
- absent protected Fact causes a governed skipped diagnostic, not a String lookup or exception;
- account-only evidence cannot create a long-lived LOCK/DENY.

- [ ] **Step 2: Run authentication rule tests**

```bash
mvn -pl core -am -Dtest=DefaultRuleCatalogAuthenticationTest,DefaultRuleCatalogCoverageTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL while rules still bind to `LoginFailure` and `attempted_account_hash`.

- [ ] **Step 3: Rewrite rule predicates and aggregation**

Bind all authentication rules to `BuiltInActions.Login`, filter on `SecurityEventResult`, and read `BuiltInFacts.LoginSubjectKey` / `AuthenticationStageFact` through typed lookup. Use `event.getType()` only where the success/failure event distinction is meaningful. Keep source IP server-derived and treat frontend telemetry as supplemental evidence.

- [ ] **Step 4: Verify controls and denial-of-service safeguards**

AUTH-01 may create short TTL CAPTCHA/progressive delay controls for the opaque account subject. AUTH-02 applies rate limiting to the IP subject. No rule based solely on attempted-account failures creates permanent account locking.

- [ ] **Step 5: Run and commit**

```bash
mvn -pl core -am test
git add core/src/main/java/io/github/jasper/monitoring/core/domain/rule/DefaultRuleCatalog.java core/src/test/java/io/github/jasper/monitoring/core/DefaultRuleCatalogAuthenticationTest.java core/src/test/java/io/github/jasper/monitoring/core/DefaultRuleCatalogCoverageTest.java
git commit -m "refactor(core): evaluate authentication rules from protected facts"
```

### Task 8: Wire equivalent Spring Boot 2 and 3 configuration

**Files:**
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorProperties.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorProperties.java`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Modify: each starter's auto-configuration imports metadata only if bean ordering requires it.
- Modify: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`
- Modify: `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/AbnormalAccessMonitorAutoConfigurationTest.java`

- [ ] **Step 1: Add matching context-runner tests**

For both starters assert:

- authentication facade is absent when authentication integration is disabled;
- enabling it with a Base64 secret of at least 32 decoded bytes creates one facade;
- enabled plus missing/short/malformed secret fails startup with a semantic configuration code;
- a host `LoginSubjectCanonicalizer` overrides the conservative default;
- both starters register identical action, fact, reason, and code catalogs.

- [ ] **Step 2: Run both starter suites and verify failure**

```bash
mvn -pl spring2-starter,spring3-starter -am test
```

Expected: FAIL because authentication properties and beans do not exist.

- [ ] **Step 3: Add minimal properties**

Use the same property names in both generations:

```text
monitoring.authentication.enabled=false
monitoring.authentication.subject-key=<Base64 secret>
monitoring.authentication.control-failure-policy=OBSERVE_ONLY
```

The policy reuses existing `ActionFailurePolicy`; do not add an authentication-specific policy enum. The default canonicalizer trims and applies locale-independent lowercase only. Applications with email/phone/tenant aliases override the single canonicalizer SPI. Do not add separate username, email, or phone strategies.

- [ ] **Step 4: Assemble and freeze dependencies**

Create `LoginSubjectKeyFactory`, `DefaultAuthenticationMonitor`, and the MyBatis control port only when enabled. Fail during context creation for invalid catalog or key configuration. Keep `javax.*` out of Boot 3 and `jakarta.*` out of Boot 2.

- [ ] **Step 5: Run and commit**

```bash
mvn -pl spring2-starter,spring3-starter -am test
git add spring2-starter spring3-starter
git commit -m "feat(starters): auto-configure authentication monitoring"
```

### Task 9: Migrate both audit applications to the facade

**Files:**
- Modify: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/security/AuditAuthenticationService.java`
- Modify: `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/security/AuditAuthenticationService.java`
- Modify: matching authentication controllers/requests and application configuration.
- Modify: both `MonitoringFixtureController.java` files.
- Modify: integration acceptance tests and fixtures for Spring 2/3.

- [ ] **Step 1: Preserve and understand overlapping worktree edits**

Before editing, run:

```bash
git diff -- integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/security/AuditAuthenticationService.java spring-support/src/main/java/io/github/jasper/monitoring/spring/support/MonitoringRecorder.java
```

Merge with those user changes. Do not reset, overwrite, or restore them.

- [ ] **Step 2: Add failing parity acceptance tests**

For each application cover unknown account, invalid password, invalid/expired CAPTCHA, disabled account, infrastructure failure, successful login, alias login, realm separation, and next-attempt control consumption. Assert exactly one event per attempt, not merely at least one.

Expected identity semantics:

```text
DENIED/FAILURE: user_id is null/anonymous; LoginSubjectKey Fact is present
SUCCESS:        user_id is authenticated identity; same protected Fact is present
```

- [ ] **Step 3: Replace direct recording with AuthenticationMonitor**

The service flow is:

```text
construct LoginSubjectInput -> preCheck -> host validation -> one record method
```

Password, CAPTCHA, MFA, disabled-account, and rate-limit outcomes use `recordDenied`. Only unavailable dependencies or unexpected technical faults use `recordFailure`. Successful authentication uses `recordSuccess`. Unknown accounts still emit the same invalid-credential reason to avoid account enumeration.

Delete direct dependencies on `MonitoringService`, `MonitoringRecorder`, `ActionExecution`, and manually fabricated `IdentityContext` from authentication services. The request DTO never contains or returns the internal key.

- [ ] **Step 4: Supply audit-specific alias canonicalization**

Use the fixture account directory to resolve configured username/email/phone aliases into one stable canonical account identifier within the supplied realm. Unknown identifiers use conservative normalized input without revealing whether an account exists. Configure a non-production test secret through test configuration, never source a production credential.

- [ ] **Step 5: Update fixture endpoints and assertions**

Fixtures call the new facade or submit the Login action with typed Facts as appropriate; they do not reference deleted types. Acceptance tests inspect event facts and effective control behavior, not raw `loginUser` values in control tables.

- [ ] **Step 6: Run and commit**

```bash
mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am test
git add integration-audit/spring2-web integration-audit/spring3-web
git diff --cached --check
git commit -m "refactor(integration): use authentication monitoring facade"
```

### Task 10: Remove legacy structures and verify the full component

**Files:**
- Delete/update all remaining production/tests/docs found by the searches below.
- Modify: public README and authentication examples where present.

- [ ] **Step 1: Prove legacy names are gone**

```bash
rg -n "LoginFailure|attempted_account_hash|MISSING_ATTEMPTED_ACCOUNT_HASH|ExceptionClassification|getExceptionClassification|getReasonCode\(\)|ActionOutcome\.(denied|failure)\(\s*\"" --glob "*.java" --glob "*.md" --glob "*.sql"
```

Expected: no production, test, schema, or documentation matches. Historical design/plan documents may mention a deleted identifier only in explicit migration acceptance text.

- [ ] **Step 2: Prove authentication has one submission path**

```bash
rg -n "MonitoringService|MonitoringRecorder|ActionExecution|new IdentityContext" integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/security integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/security
```

Expected: no matches.

- [ ] **Step 3: Check persistence and API cleanliness**

Verify no raw login identifier appears in event attributes, Facts, control subjects, logs, fixture database assertions, or serialized responses. Verify no deprecated alias, compatibility overload, unused bean, unused property, unused import, or transitional adapter remains.

- [ ] **Step 4: Run focused security behavior suites**

```bash
mvn -pl api,core,mybatis,spring2-starter,spring3-starter,integration-audit/spring2-web,integration-audit/spring3-web -am test
```

Expected: PASS.

- [ ] **Step 5: Run full reactor verification**

```bash
mvn clean verify -DskipTests=false
git diff --check
git status --short
```

Expected: full reactor PASS; diff check clean; status contains only intended changes.

- [ ] **Step 6: Commit final cleanup and documentation**

```bash
git add README.md api core mybatis spring-support spring2-starter spring3-starter integration-audit docs
git diff --cached --check
git commit -m "refactor(auth): remove legacy login monitoring structures"
```

## Completion Criteria

- A caller supplies `loginUser` and realm only; internal subject generation is invisible.
- Alias identifiers map to one key in the same realm; the same identifier maps differently across realms.
- Every authentication attempt creates exactly one event.
- Host rejection is DENIED, technical interruption is FAILURE, and success is SUCCESS.
- Failed attempts have anonymous actor identity; successful attempts have authenticated identity.
- ReasonCode and FailureClass remain separate concepts and are validated centrally.
- AUTH-02 includes successful attempts in its denominator.
- Persisted account subjects are opaque, indexed typed Facts.
- Effective controls are isolated by system, subject, status, action type, and expiry.
- `preCheck` consumes both account and trusted-IP controls without turning IP abuse into account locking.
- Boot 2 and Boot 3 expose equivalent behavior with the correct servlet namespace.
- All old structures and free-form compatibility paths are physically removed.

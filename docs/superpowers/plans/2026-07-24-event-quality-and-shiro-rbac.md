# Event Quality and Shiro RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and enforce rule-specific monitoring input quality without changing host business outcomes, then prove the authorization boundary through isolated Spring Boot 2/3 Shiro RBAC audit applications.

**Architecture:** Keep input policy framework-neutral in `api` and `core`. A validated event is always persisted with protected quality metadata; only rules whose required facts are valid run. The audit modules alone own Apache Shiro, adapt its authenticated `Subject` to existing framework-neutral identity and scope interfaces, and call `ResourceAccessGuard` before a resource side effect.

**Tech Stack:** Java 8 API/core/Spring support, Maven, JUnit 5, AssertJ, H2/MyBatis, Spring Boot 2.7/3.2, Apache Shiro isolated to `integration-audit`.

---

## File Map

- `api/src/main/java/io/github/jasper/monitoring/api/SecurityEventDraft.java`: preserve count/latency presence independently from their numeric value.
- `api/src/main/java/io/github/jasper/monitoring/api/{EventInputStatus,EventInputIssue,EventInputValidation,MonitoringEventPolicy,MonitoringInputIssueReporter}.java`: public, framework-neutral quality contract.
- `api/src/main/java/io/github/jasper/monitoring/api/{SecurityFieldSanitizer,MonitorActionDefinition,MonitorActionFacts}.java`: canonical attribute keys and protected static facts.
- `core/src/main/java/io/github/jasper/monitoring/core/{domain/SecurityEvent.java,application/DefaultSecurityMonitor.java,application/MonitoringOutcome.java,application/quality/DefaultMonitoringEventPolicy.java}`: quality-aware event creation, rule selection and reporting.
- `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/{DefaultRuleCatalog,WindowAggregateRule}.java`: never aggregate an unknown data count.
- `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/{po/SecurityEventPo.java,po/SecurityEventInputIssuePo.java,MonitoringSqlMapper.java,MyBatisMonitoringRepository.java}` and `mybatis/src/main/resources/db/monitoring-schema.sql`: durable quality fields and issue rows.
- `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/{BoundParameterFactsExtractor,AnnotatedActionFacts}.java`: diagnostics for failed bean-path binding and canonical protected-key merges.
- `spring2-starter` / `spring3-starter`: wire quality policy/reporting through the existing annotation recording path.
- `integration-audit/spring2-web` and `integration-audit/spring3-web`: isolated Shiro Realm, fixture subject filter, identity/scope adapters, guard-backed resource endpoints and HTTP acceptance tests.
- `docs/集成指南.md`: static/dynamic collection matrix, quality result semantics, Shiro fixture boundary and resource guard sequence.

### Task 1: Lock Count Presence and Key Canonicalization

**Files:**
- Test: `api/src/test/java/io/github/jasper/monitoring/api/SecurityEventDraftTest.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/SecurityEventDraft.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/SecurityFieldSanitizer.java`
- Modify: `api/src/main/java/io/github/jasper/monitoring/api/MonitorActionDefinition.java`

- [ ] **Step 1: Write the failing API tests**

```java
@Test
void distinguishesAnUnknownDataCountFromAnExplicitZero() {
    SecurityEventDraft unknown = requiredDraft().build();
    SecurityEventDraft zero = requiredDraft().dataCount(0L).latencyMs(0L).build();

    assertThat(unknown.hasDataCount()).isFalse();
    assertThat(zero.hasDataCount()).isTrue();
    assertThat(zero.getDataCount()).isZero();
    assertThat(zero.hasLatencyMs()).isTrue();
}

@Test
void rejectsAttributeKeysThatCollideAfterCanonicalization() {
    assertThatThrownBy(() -> requiredDraft()
        .attribute("sensitivity", "HIGH")
        .attribute("Sensitivity", "LOW")
        .build()).isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Run the API test and verify RED**

Run: `mvn -pl api -Dtest=SecurityEventDraftTest test`

Expected: compilation failure for `hasDataCount` / `hasLatencyMs`, then a failing collision assertion.

- [ ] **Step 3: Implement the smallest compatible model change**

Keep the existing getters as `long`; add explicit flags rather than changing their public return type:

```java
private final boolean dataCountKnown;
private final boolean latencyMsKnown;

public boolean hasDataCount() { return dataCountKnown; }
public boolean hasLatencyMs() { return latencyMsKnown; }

public Builder dataCount(long value) {
    this.dataCount = value;
    this.dataCountKnown = true;
    return this;
}
```

Make `SecurityFieldSanitizer.normalizeAttributeKey(String)` validate, sanitize and lower-case with `Locale.ROOT`; all definition and draft maps use it and reject a duplicate normalized key.

- [ ] **Step 4: Run the focused API tests and commit**

Run: `mvn -pl api -Dtest=SecurityEventDraftTest test`

Expected: PASS.

Commit: `git add api && git commit -m "feat(api): track monitoring fact presence"`

### Task 2: Add the Durable Quality Contract

**Files:**
- Create: `api/src/main/java/io/github/jasper/monitoring/api/EventInputStatus.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/EventInputIssue.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/EventInputValidation.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/MonitoringEventPolicy.java`
- Create: `api/src/main/java/io/github/jasper/monitoring/api/MonitoringInputIssueReporter.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/SecurityEvent.java`
- Create: `core/src/test/java/io/github/jasper/monitoring/core/SecurityEventTest.java`

- [ ] **Step 1: Write the failing domain round-trip test**

```java
@Test
void preservesInputQualityAndKnownFactFlagsOnAcceptedEvent() {
    EventInputIssue issue = EventInputIssue.missing("EXPT-01", "dataCount", "SERVER_COMPUTED");
    EventInputValidation validation = EventInputValidation.incomplete(Collections.singletonList(issue),
        Collections.singleton("EXPT-01"));

    SecurityEvent event = SecurityEvent.from(explicitZeroDraft(), "test", "event-1", Instant.EPOCH, validation);

    assertThat(event.getInputStatus()).isEqualTo(EventInputStatus.INCOMPLETE);
    assertThat(event.getInputIssues()).containsExactly(issue);
    assertThat(event.hasDataCount()).isTrue();
}
```

- [ ] **Step 2: Run the core test and verify RED**

Run: `mvn -pl core -am -Dtest=SecurityEventTest test`

Expected: compilation failure because quality types and `SecurityEvent.from(..., validation)` do not exist.

- [ ] **Step 3: Implement immutable contract types and domain storage**

`EventInputIssue` contains only stable fields: `ruleId`, `factName`, `issueCode`, `sourceType`; reject blank values and never accept a raw value or exception message. `EventInputValidation` exposes `isEligible(String ruleId)` and makes its collections immutable. `SecurityEvent` receives the validation and presence flags, retaining the existing four-argument `from` overload by delegating to `EventInputValidation.valid()`.

- [ ] **Step 4: Run the focused core tests and commit**

Run: `mvn -pl core -am -Dtest=SecurityEventTest test`

Expected: PASS.

Commit: `git add api core && git commit -m "feat(core): retain event input quality"`

### Task 3: Gate Built-in Rules on Valid Facts

**Files:**
- Create: `core/src/main/java/io/github/jasper/monitoring/core/application/quality/DefaultMonitoringEventPolicy.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/DefaultSecurityMonitor.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/MonitoringOutcome.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/DefaultRuleCatalog.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/rule/WindowAggregateRule.java`
- Create: `core/src/test/java/io/github/jasper/monitoring/core/MonitoringEventPolicyTest.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/DefaultSecurityMonitorTest.java`

- [ ] **Step 1: Write failing policy and monitor tests**

```java
@Test
void excludesOnlyExportRulesWhenExportDataCountIsUnknown() {
    EventInputValidation validation = policy.validate(exportDraftWithoutDataCount(), DEFAULT_RULE_IDS);

    assertThat(validation.isEligible("EXPT-01")).isFalse();
    assertThat(validation.isEligible("EXPT-02")).isFalse();
    assertThat(validation.isEligible("SECU-01")).isTrue();
    assertThat(validation.getStatus()).isEqualTo(EventInputStatus.INCOMPLETE);
}

@Test
void persistsAnIncompleteEventWithoutCreatingAnExportControl() {
    MonitoringOutcome outcome = monitor.record(exportDraftWithoutDataCount());

    assertThat(outcome.getEvent().getInputStatus()).isEqualTo(EventInputStatus.INCOMPLETE);
    assertThat(outcome.getMatches()).extracting(RuleMatch::getRuleId)
        .doesNotContain("EXPT-01", "EXPT-02");
    assertThat(outcome.getControls()).isEmpty();
}
```

- [ ] **Step 2: Run the core tests and verify RED**

Run: `mvn -pl core -am -Dtest=MonitoringEventPolicyTest,DefaultSecurityMonitorTest test`

Expected: missing policy class and/or export rules still match.

- [ ] **Step 3: Implement rule eligibility before persistence evaluation**

`DefaultMonitoringEventPolicy` uses stable rule IDs and validates only the facts each built-in rule consumes: canonical IP for IP rules, required resource ID for distinct-resource rules, strict lowercase boolean values, known count for count/export rules, finite non-negative `baseline_ratio`, and required target user attributes for privilege escalation. Unknown custom rules remain eligible.

In `DefaultSecurityMonitor.record`, compute validation first, create `SecurityEvent` with it, persist it, and skip only a `DetectionRule` whose ID is ineligible. Invoke `MonitoringInputIssueReporter` after the transaction in a best-effort `try/catch`; it must not use `NotificationChannel`.

Make `WindowAggregateRule.Aggregation.DATA_COUNT` ignore candidates where `!candidate.hasDataCount()`. Make `EXPT-02` accumulate only known counts with saturated addition:

```java
private static long addSaturated(long total, long value) {
    return Long.MAX_VALUE - total < value ? Long.MAX_VALUE : total + value;
}
```

- [ ] **Step 4: Run focused tests and commit**

Run: `mvn -pl core -am -Dtest=MonitoringEventPolicyTest,DefaultSecurityMonitorTest test`

Expected: PASS.

Commit: `git add api core && git commit -m "feat(core): skip rules with invalid monitoring facts"`

### Task 4: Persist Quality Metadata Through MyBatis

**Files:**
- Modify: `mybatis/src/main/resources/db/monitoring-schema.sql`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/po/SecurityEventPo.java`
- Create: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/po/SecurityEventInputIssuePo.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MonitoringSqlMapper.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepository.java`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepositoryTest.java`

- [ ] **Step 1: Write the failing H2 round-trip test**

```java
@Test
void roundTripsInputStatusKnownFlagsAndIssueCodes() {
    SecurityEvent saved = incompleteEventWithUnknownDataCount();
    repository.saveEvent(saved);

    SecurityEvent restored = repository.findEventsSince(Instant.EPOCH).get(0);
    assertThat(restored.getInputStatus()).isEqualTo(EventInputStatus.INCOMPLETE);
    assertThat(restored.hasDataCount()).isFalse();
    assertThat(restored.getInputIssues()).extracting(EventInputIssue::getIssueCode)
        .containsExactly("MISSING_DATA_COUNT");
}
```

- [ ] **Step 2: Run the MyBatis test and verify RED**

Run: `mvn -pl mybatis -am -Dtest=MyBatisMonitoringRepositoryTest test`

Expected: schema/mapping does not expose quality fields or issue rows.

- [ ] **Step 3: Add forward-compatible persistence**

Keep `data_count` and `latency_ms` non-null. Add `data_count_known`, `latency_ms_known`, and `input_status` to `security_event`; add `security_event_input_issue(event_id, issue_index, rule_id, fact_name, issue_code, source_type)`. Write and read issue rows in the same repository transaction. Rebuilt historical rows default to `UNKNOWN`, with both known flags false. Add a short `ALTER TABLE` migration note adjacent to the schema change.

- [ ] **Step 4: Run MyBatis tests and commit**

Run: `mvn -pl mybatis -am -Dtest=MyBatisMonitoringRepositoryTest test`

Expected: PASS.

Commit: `git add mybatis && git commit -m "feat(mybatis): persist event input quality"`

### Task 5: Surface Parameter Diagnostics Without Trust Escalation

**Files:**
- Modify: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/BoundParameterFactsExtractor.java`
- Modify: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/AnnotatedActionFacts.java`
- Test: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/BoundParameterFactsExtractorTest.java`
- Modify: `spring2-starter/src/test/java/io/github/jasper/monitoring/spring2/autoconfigure/DynamicActionFactsTest.java`
- Modify: `spring3-starter/src/test/java/io/github/jasper/monitoring/spring3/autoconfigure/DynamicActionFactsTest.java`

- [ ] **Step 1: Write a failing extractor diagnostic test**

```java
@Test
void reportsInvalidBeanPathWithoutExposingTheValueOrException() {
    ExtractionResult result = extractor.extractWithDiagnostics(method("missing.path"), new Object[] { request });

    assertThat(result.getFacts().getResourceId()).isNull();
    assertThat(result.getIssues()).extracting(EventInputIssue::getIssueCode)
        .containsExactly("UNRESOLVED_PARAMETER_PATH");
}
```

- [ ] **Step 2: Run the support test and verify RED**

Run: `mvn -pl spring-support -am -Dtest=BoundParameterFactsExtractorTest test`

Expected: `ExtractionResult` does not exist and unresolved paths are silently ignored.

- [ ] **Step 3: Implement a compatible diagnostic result**

Keep `extract(Method,Object[])` as a delegating compatibility method. Add `extractWithDiagnostics` returning immutable facts and issues. `AnnotatedActionFacts` retains those issues and passes them to the recorder/quality policy. Normalize every attribute key before static-key comparison; a dynamic key that resolves to a static or `monitor.*` reserved key is ignored and records `PROTECTED_FACT_OVERRIDE`.

- [ ] **Step 4: Run support and Starter dynamic-fact tests and commit**

Run: `mvn -pl spring-support,spring2-starter,spring3-starter -am -Dtest=BoundParameterFactsExtractorTest,DynamicActionFactsTest test`

Expected: PASS.

Commit: `git add spring-support spring2-starter spring3-starter && git commit -m "feat(spring): diagnose dynamic monitoring fact gaps"`

### Task 6: Add Isolated Shiro RBAC Audit Fixtures

**Files:**
- Modify: `integration-audit/spring2-web/pom.xml`
- Modify: `integration-audit/spring3-web/pom.xml`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/AuditShiroRbacConfiguration.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/AuditRbacRealm.java`
- Create: `integration-audit/spring2-web/src/main/java/io/github/jasper/monitoring/audit/spring2/AuditPrincipalFilter.java`
- Create equivalent `spring3` classes under `integration-audit/spring3-web/src/main/java/io/github/jasper/monitoring/audit/spring3/`
- Modify: both `Spring*AuditApplication.java` and `AuditController.java`
- Modify: both `Spring*AuditWebAcceptanceTest.java`

- [ ] **Step 1: Write the failing Boot 2 and Boot 3 authorization acceptance tests**

```java
@Test
void deniesCrossOrganizationExportBeforeTheExportServiceRuns() {
    ResponseEntity<String> response = request("audit-viewer").postForEntity("/audit/reports/report-b/export", null, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(exportService.getInvocationCount()).isZero();
    assertThat(repository.findEventsSince(Instant.EPOCH)).extracting(SecurityEvent::getEventType)
        .contains(SecurityEventType.RESOURCE_SCOPE_DENIED);
}
```

- [ ] **Step 2: Run each failing acceptance test**

Run: `mvn -pl integration-audit/spring2-web -am -Dtest=Spring2AuditWebAcceptanceTest test`

Run: `mvn -pl integration-audit/spring3-web -am -Dtest=Spring3AuditWebAcceptanceTest test`

Expected: current fixed identity setup allows the endpoint or lacks a rejected-resource event.

- [ ] **Step 3: Implement a fixture-only RBAC boundary**

Use an in-memory Shiro `Realm` and an `AuditPrincipalFilter` that accepts only a fixed set of non-secret fixture principal names from `X-Audit-Principal`; missing/unknown names return 401. The `IdentityContextProvider` reads only the authenticated Shiro `Subject`, not the header. `AuditResourceScopeAuthorizer` combines `Subject.isPermitted("report:read"/"report:export")` with a server-side report catalog mapping. The controller resolves the report from that catalog, calls `ResourceAccessGuard.authorize` before invoking the export service, and maps a denied `AuthorizationDecision` to 403.

For Boot 2, set only the audit module compiler release to 11 and use the maintained Shiro 2.2.1 `javax` starter. For Boot 3, use the same artifact with its `jakarta` classifier after verifying Maven resolution. Do not add Shiro to parent dependency management, core, API or Starter modules.

- [ ] **Step 4: Run both audit modules and commit**

Run: `mvn -pl integration-audit/spring2-web,integration-audit/spring3-web -am test`

Expected: unauthenticated 401, authorized same-scope read/export 200, denied cross-scope/no-export 403, and a persisted `RESOURCE_SCOPE_DENIED` event.

Commit: `git add integration-audit && git commit -m "feat(audit): add Shiro RBAC acceptance fixtures"`

### Task 7: Update Guide and Run the First Full Verification

**Files:**
- Modify: `docs/集成指南.md`
- Modify: `docs/superpowers/specs/2026-07-24-generic-control-enforcement-design.md`

- [ ] **Step 1: Document verified behavior**

Add the exact static/dynamic source matrix, the distinction between unknown and zero counts, stable issue-code semantics, protected-key rules, and the fact that missing monitoring facts skip only dependent rules. Add the Shiro RBAC audit fixture sequence and explicitly state that `X-Audit-Principal` is test-only and never a production authentication approach.

- [ ] **Step 2: Run full verification**

Run: `mvn clean verify -DskipTests=false`

Expected: PASS for every reactor module.

- [ ] **Step 3: Commit documentation**

Commit: `git add docs && git commit -m "docs: describe event quality and audit RBAC integration"`

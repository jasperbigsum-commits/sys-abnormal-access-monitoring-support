# Generic IP Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit, opt-in generic control path that can apply `RATE_LIMIT` and IP-scoped `DENY` to subsequent protected Servlet requests, while preserving host-handler precedence and audit-only fallback behavior.

**Architecture:** Rule evaluation remains in `core`; it carries the stable rule ID into a command. A Servlet-free IP control state port is implemented by a bounded local store in `spring-support`. Boot 2/3 Starter adapters create an explicitly configured generic handler and pre-MVC filter. Redis is intentionally deferred so there is no hidden distributed-state claim.

**Tech Stack:** Java 8 core/shared code, Spring Boot 2 `javax.servlet`, Spring Boot 3 `jakarta.servlet`, `OncePerRequestFilter`, JUnit 5/AssertJ.

---

## File Map

- `core/src/main/java/io/github/jasper/monitoring/core/domain/ControlCommand.java`: compatible rule ID propagation.
- `core/src/main/java/io/github/jasper/monitoring/core/application/{DefaultSecurityMonitor.java,control/ControlHandlerRegistry.java}`: command creation and host/generic/fallback order.
- `mybatis` control action PO/mapper/schema/repository: persist nullable rule ID for idempotent replay.
- `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/control/*`: framework-neutral IP state, bounded local store and generic control handler.
- `spring2-starter` / `spring3-starter`: properties, auto-configuration and version-specific filters.
- `docs/集成指南.md`: operational control matrix and local-store limitations.

### Task 1: Propagate Rule Identity to a Control Command

**Files:**
- Test: `core/src/test/java/io/github/jasper/monitoring/core/DefaultSecurityMonitorTest.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/domain/ControlCommand.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/DefaultSecurityMonitor.java`
- Modify: `mybatis/src/main/java/io/github/jasper/monitoring/mybatis/{po/ControlActionPo.java,MonitoringSqlMapper.java,MyBatisMonitoringRepository.java}`
- Modify: `mybatis/src/main/resources/db/monitoring-schema.sql`
- Test: `mybatis/src/test/java/io/github/jasper/monitoring/mybatis/MyBatisMonitoringRepositoryTest.java`

- [ ] **Step 1: Write failing propagation and persistence tests**

```java
@Test
void passesTheMatchedRuleIdToEachControlCommand() {
    MonitoringOutcome outcome = enforceMonitor.record(authTwoDraft());

    assertThat(handler.getCommands()).extracting(ControlCommand::getRuleId)
        .contains("AUTH-02");
}

@Test
void roundTripsAControlRuleId() {
    repository.saveControl(record("AUTH-02"));

    assertThat(repository.findControl("idempotency").get().getCommand().getRuleId())
        .isEqualTo("AUTH-02");
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl core,mybatis -am -Dtest=DefaultSecurityMonitorTest,MyBatisMonitoringRepositoryTest test`

Expected: missing getter/constructor or restored command has no rule ID.

- [ ] **Step 3: Implement compatible command and schema change**

Keep the current five-argument constructor and delegate it to a new six-argument constructor with a nullable rule ID:

```java
public ControlCommand(String idempotencyKey, String alertId, String subject,
                      ControlActionType action, Instant expiresAt) {
    this(idempotencyKey, alertId, subject, action, expiresAt, null);
}
```

`DefaultSecurityMonitor.executeControls` passes `match.getRuleId()`. Add nullable `rule_id` to `control_action`, map it both ways, and place the corresponding manual `ALTER TABLE` note beside the initial schema.

- [ ] **Step 4: Run focused tests and commit**

Run: `mvn -pl core,mybatis -am -Dtest=DefaultSecurityMonitorTest,MyBatisMonitoringRepositoryTest test`

Expected: PASS.

Commit: `git add core mybatis && git commit -m "feat(control): retain rule identity on commands"`

### Task 2: Define and Test a Bounded Local IP Control State

**Files:**
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/control/IpControlState.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/control/IpControlDecision.java`
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/control/LocalIpControlState.java`
- Test: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/control/LocalIpControlStateTest.java`

- [ ] **Step 1: Write failing local-state tests**

```java
@Test
void denyWinsOverRateLimitAndExpiresAtTheConfiguredTime() {
    state.activate(deny("ip:203.0.113.10", clock.instant().plusSeconds(60)));
    state.activate(rateLimit("ip:203.0.113.10", clock.instant().plusSeconds(60)));

    assertThat(state.check("203.0.113.10", clock.instant())).isEqualTo(IpControlDecision.denied());
    clock.advance(Duration.ofSeconds(61));
    assertThat(state.check("203.0.113.10", clock.instant())).isEqualTo(IpControlDecision.allowed());
}
```

- [ ] **Step 2: Run the shared-module test and verify RED**

Run: `mvn -pl spring-support -am -Dtest=LocalIpControlStateTest test`

Expected: missing state classes.

- [ ] **Step 3: Implement bounded state semantics**

The state API accepts only canonical IP values supplied by its caller. Activation stores the original idempotency key and never extends an existing matching control. `check` lazily removes expired entries, checks `DENY` first, then atomically advances the configured fixed-window counter for active rate-limit state. At capacity, activation returns a stable `CAPACITY_REJECTED` result and never evicts active controls.

- [ ] **Step 4: Run the state tests and commit**

Run: `mvn -pl spring-support -am -Dtest=LocalIpControlStateTest test`

Expected: PASS for TTL, idempotency, capacity, deny precedence and rate-window retry time.

Commit: `git add spring-support && git commit -m "feat(control): add bounded local IP control state"`

### Task 3: Add a Rule-Allowlisted Generic IP Handler

**Files:**
- Create: `spring-support/src/main/java/io/github/jasper/monitoring/spring/support/control/GenericIpControlHandler.java`
- Test: `spring-support/src/test/java/io/github/jasper/monitoring/spring/support/control/GenericIpControlHandlerTest.java`
- Modify: `core/src/main/java/io/github/jasper/monitoring/core/application/control/ControlHandlerRegistry.java`
- Test: `core/src/test/java/io/github/jasper/monitoring/core/ControlHandlerRegistryTest.java`

- [ ] **Step 1: Write failing handler and precedence tests**

```java
@Test
void activatesRateLimitOnlyForAnAllowedIpRule() {
    ControlExecution execution = handler.execute(command("AUTH-02", "ip:203.0.113.10", ControlActionType.RATE_LIMIT));

    assertThat(execution.getStatus()).isEqualTo(ControlStatus.SUCCEEDED);
    assertThat(state.check("203.0.113.10", clock.instant()).isRateLimited()).isTrue();
}

@Test
void hostHandlerWinsBeforeGenericAndFallbackHandlers() {
    assertThat(registry.find(ControlActionType.RATE_LIMIT).get()).isSameAs(hostHandler);
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl core,spring-support -am -Dtest=ControlHandlerRegistryTest,GenericIpControlHandlerTest test`

Expected: missing generic tier and handler.

- [ ] **Step 3: Implement safe generic dispatch**

Extend `ControlHandlerRegistry` to resolve host, then generic Starter, then `DefaultControlActionTrigger` fallback. The generic tier makes `isEmpty()` false only when explicitly enabled and valid. `GenericIpControlHandler` supports only `RATE_LIMIT` and `DENY`; it returns an audited `SKIPPED` result for absent/unallowlisted rule ID, non-`ip:` subject, non-canonical IP, expired command or TTL above configuration. It never maps user, session or account subjects to an IP.

- [ ] **Step 4: Run tests and commit**

Run: `mvn -pl core,spring-support -am -Dtest=ControlHandlerRegistryTest,GenericIpControlHandlerTest test`

Expected: PASS.

Commit: `git add core spring-support && git commit -m "feat(control): add allowlisted generic IP handler"`

### Task 4: Wire Boot 2 and Boot 3 Filters Behind Explicit Properties

**Files:**
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorProperties.java`
- Modify: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/AbnormalAccessMonitorAutoConfiguration.java`
- Create: `spring2-starter/src/main/java/io/github/jasper/monitoring/spring2/autoconfigure/IpControlFilter.java`
- Modify equivalent Spring 3 files under `spring3-starter/src/main/java/io/github/jasper/monitoring/spring3/autoconfigure/`
- Modify: `spring2-starter/src/main/resources/META-INF/spring-configuration-metadata.json`
- Modify: `spring3-starter/src/main/resources/META-INF/spring-configuration-metadata.json`
- Test: each Starter's `AbnormalAccessMonitorAutoConfigurationTest.java` and new `IpControlFilterTest.java`

- [ ] **Step 1: Write failing disabled/default and filter behavior tests**

```java
@Test
void isDisabledByDefault() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(IpControlFilter.class));
}

@Test
void returns429AndRetryAfterForAProtectedRateLimitedIp() throws Exception {
    state.activate(rateLimit("ip:203.0.113.10", expiry));
    filter.doFilter(request("/api/report", "203.0.113.10"), response, chain);

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isNotBlank();
    verifyNoInteractions(chain);
}
```

- [ ] **Step 2: Run focused Starter tests and verify RED**

Run: `mvn -pl spring2-starter,spring3-starter -am -Dtest=AbnormalAccessMonitorAutoConfigurationTest,IpControlFilterTest test`

Expected: property type/filter bean unavailable.

- [ ] **Step 3: Implement version-specific adapters only**

Add `ip-control.enabled=false`, nonempty protected paths, rule ID allowlists, excluded paths, positive permits/window/TTL/capacity. Fail startup when enabled configuration is incomplete. Register `OncePerRequestFilter` before MVC only in `ENFORCE` mode. Reuse a shared trusted-proxy source-IP resolver from `spring-support`; do not trust arbitrary forwarded headers. Unmatched/excluded paths and `OBSERVE` pass through. For a matching decision, return bare 403 or 429 plus `Retry-After`, without rule/alert/body details.

- [ ] **Step 4: Run both Starter test suites and commit**

Run: `mvn -pl spring2-starter,spring3-starter -am test`

Expected: PASS for disabled default, valid enablement, path excludes, forged forwarding headers, expire behavior, 403/429 and observe pass-through.

Commit: `git add spring2-starter spring3-starter spring-support && git commit -m "feat(starter): enforce configured IP controls"`

### Task 5: Document Limits and Verify the Reactor

**Files:**
- Modify: `docs/集成指南.md`
- Modify: `docs/superpowers/specs/2026-07-24-generic-control-enforcement-design.md`

- [ ] **Step 1: Update the control matrix**

Document host handler precedence, the optional generic `RATE_LIMIT` and IP `DENY` capabilities, required rule/path configuration, first-match-versus-subsequent-request behavior, HTTP 403/429 semantics, `OBSERVE` behavior, and local-memory per-JVM/restart limitations. State that Redis is deferred and must not silently replace an unavailable distributed backend.

- [ ] **Step 2: Run final verification**

Run: `mvn clean verify -DskipTests=false`

Expected: PASS.

- [ ] **Step 3: Commit documentation**

Commit: `git add docs && git commit -m "docs: document generic IP control behavior"`

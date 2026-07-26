# Plan A Documentation Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make public documentation accurately describe the implemented listener, instrumentation, trigger, MyBatis, management, and Plan A acceptance boundaries without duplication.

**Architecture:** Each document has one audience and one responsibility. The acceptance document owns TC/IA traceability; integration and operations documents link to it rather than copying the matrix.

**Tech Stack:** Markdown, Maven/Surefire evidence, PowerShell/Ripgrep documentation checks.

---

### Task 1: Rewrite the Host Integration Boundary

**Files:**
- Modify: `README.md`
- Modify: `docs/集成指南.md`
- Modify: `docs/integration-guide.en.md`

- [ ] **Step 1: Add a failing documentation scan**

Extend the existing documentation test or add `docs/verify-docs.ps1` to require the headings “默认全局能力”, “宿主必须埋点”, “Action Fact”, “控制触发器”, and “ENFORCE 准入” in the Chinese guide and equivalent headings in English.

- [ ] **Step 2: Verify RED**

Run: `pwsh -File docs/verify-docs.ps1`
Expected: FAIL because the ownership matrix is incomplete.

- [ ] **Step 3: Rewrite the guide around the implemented matrix**

Document that request interception does not emit business events, `@MonitorAction` is opt-in, `@ActionFact` is bounded, actual result facts are programmatic, `FactBinding` owns provider scope, and default fallback controls do not satisfy ENFORCE.

- [ ] **Step 4: Keep README short and verify**

README contains only install order, five hard boundaries, and links. Run the documentation scan; expected PASS.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/集成指南.md docs/integration-guide.en.md docs/verify-docs.ps1
git commit -m "docs: define host monitoring boundaries"
```

### Task 2: Publish Exact TC/IA Traceability

**Files:**
- Rewrite: `docs/集成审计与基础项目验收.md`
- Modify: `docs/领域模型与数据设计.md`

- [ ] **Step 1: Make the documentation scan require all IDs once**

Require exact unique sets `TC-01..TC-18` and `IA-01..IA-12` in the acceptance matrix.

- [ ] **Step 2: Verify RED**

Run: `pwsh -File docs/verify-docs.ps1`
Expected: FAIL because the current document has no Plan A matrix.

- [ ] **Step 3: Write one evidence row per case**

Each row includes owner, HTTP/service entry, fixture preparation, expected response, event/rule/alert/control evidence, business-state evidence, and Boot 2/3 test method.

- [ ] **Step 4: Document production versus fixture schema**

Add `rule_observation` to the production model and explicitly mark `audit_*` tables as integration-only host data.

- [ ] **Step 5: Verify and commit**

```bash
pwsh -File docs/verify-docs.ps1
git add docs/集成审计与基础项目验收.md docs/领域模型与数据设计.md docs/verify-docs.ps1
git commit -m "docs(audit): trace plan A acceptance cases"
```

### Task 3: Align Operations and Responsibility Documents

**Files:**
- Modify: `docs/架构与运维说明.md`
- Modify: `docs/architecture-and-transaction-boundaries.en.md`
- Modify: `docs/组织角色与文档管理.md`
- Modify: `docs/错误规范.md`

- [ ] **Step 1: Add failing required-boundary checks**

Require explicit sections for rule modes, notification retry, authorization fail-closed behavior, control recovery, rollback-to-alert-only, and evidence ownership.

- [ ] **Step 2: Verify RED**

Run the documentation scan; expected FAIL.

- [ ] **Step 3: Update runtime and error semantics**

Describe DISABLED/OBSERVE/ALERT_ONLY/ENFORCE, global safety ceiling, append-only observations, 401/403/hidden-404 mapping, notification failure isolation, and control FAILED/retry/recover transitions.

- [ ] **Step 4: Update role deliverables**

Business owns thresholds/sensitivity/approval; security owns rule modes and ENFORCE approval; development owns instrumentation/authorization/controls; testing owns TC/IA evidence; operations owns migration/notification/recovery.

- [ ] **Step 5: Verify and commit**

```bash
pwsh -File docs/verify-docs.ps1
git add docs
git commit -m "docs: align plan A operational ownership"
```

### Task 4: Run Final Documentation and Reactor Verification

**Files:**
- Modify only files with verification defects found in this task

- [ ] **Step 1: Run link, ID, and placeholder scans**

Run: `pwsh -File docs/verify-docs.ps1`
Expected: PASS with no missing links, duplicate IDs, unfinished markers, or stale claims about memory persistence and automatic business listeners.

- [ ] **Step 2: Run focused acceptance verification**

Run: `mvn -pl integration-audit -am verify -DskipTests=false`
Expected: PASS with all TC/IA cases.

- [ ] **Step 3: Run the complete reactor**

Run: `mvn clean verify -DskipTests=false`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit verification-only corrections**

```bash
git add docs README.md
git commit -m "docs: finalize plan A acceptance evidence"
```

# Integration Audit Admin Web Mock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable Vite + Vue 3 abnormal-access management console whose complete workflows operate against deterministic in-memory Mock data.

**Architecture:** Business views call a framework-neutral `MonitoringRepository`; phase one binds it to `MockMonitoringRepository`. Jeecg-style components own table pagination, schema forms, modals, drawers, dictionaries, and permission visibility so pages remain migration-friendly.

**Tech Stack:** Vue 3.5.40, TypeScript 7.0.2, Vite 8.1.5, Ant Design Vue 4.2.6, Pinia 4.0.2, Vue Router 5.2.0, ECharts 6.1.0, Axios 1.19.0, Vitest 4.1.10, Vue Test Utils 2.4.11, Playwright 1.62.0, npm, Node.js 20+

---

## File Map

- `integration-audit/admin-web/package.json`: scripts and locked direct dependencies.
- `integration-audit/admin-web/vite.config.ts`: Vue, aliases, tests, coverage, and dev server.
- `integration-audit/admin-web/src/domain/monitoring.ts`: all frontend domain records, queries, commands, pages, dictionaries, and dashboard projection.
- `integration-audit/admin-web/src/repositories/monitoringRepository.ts`: data-source-neutral contract and injection key.
- `integration-audit/admin-web/src/mocks/fixtures.ts`: deterministic fixture factory.
- `integration-audit/admin-web/src/mocks/mockMonitoringRepository.ts`: filtering, pagination, versions, transitions, and scenario failures.
- `integration-audit/admin-web/src/components/jeecg/`: `BasicTable`, `BasicForm`, `BasicModal`, `BasicDrawer`, `DictTag`, and their registration hooks.
- `integration-audit/admin-web/src/layouts/RiskControlLayout.vue`: responsive bank risk-control shell.
- `integration-audit/admin-web/src/views/`: seven business areas split into focused view, schema, columns, and overlay files.
- `integration-audit/admin-web/e2e/`: Playwright workflows and visual baselines.

### Task 1: Scaffold the Vite Application

**Files:**
- Create: `integration-audit/admin-web/package.json`
- Create: `integration-audit/admin-web/tsconfig.json`
- Create: `integration-audit/admin-web/vite.config.ts`
- Create: `integration-audit/admin-web/index.html`
- Create: `integration-audit/admin-web/src/main.ts`
- Create: `integration-audit/admin-web/src/App.vue`
- Create: `integration-audit/admin-web/src/router/index.ts`
- Create: `integration-audit/admin-web/src/styles/theme.css`
- Create: `integration-audit/admin-web/src/tests/setup.ts`
- Create: `integration-audit/admin-web/src/App.test.ts`

- [ ] **Step 1: Write the failing application smoke test**

```ts
// src/App.test.ts
import { mount } from '@vue/test-utils';
import App from './App.vue';

it('renders the monitoring application root', () => {
  expect(mount(App).get('[data-testid="app-root"]').exists()).toBe(true);
});
```

- [ ] **Step 2: Create the package and tool configuration**

Use exact direct versions from the plan header. Define scripts `dev`, `build`, `typecheck`, `test`, `test:run`, `e2e`, and `e2e:update`. Configure alias `@` to `src`, Vitest `jsdom`, and setup file `src/tests/setup.ts`.

```json
{
  "name": "integration-audit-admin-web",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 127.0.0.1",
    "build": "vue-tsc --noEmit && vite build",
    "typecheck": "vue-tsc --noEmit",
    "test": "vitest",
    "test:run": "vitest run",
    "e2e": "playwright test",
    "e2e:update": "playwright test --update-snapshots"
  },
  "dependencies": {
    "@ant-design/icons-vue": "7.0.1",
    "ant-design-vue": "4.2.6",
    "axios": "1.19.0",
    "echarts": "6.1.0",
    "pinia": "4.0.2",
    "vue": "3.5.40",
    "vue-router": "5.2.0"
  },
  "devDependencies": {
    "@playwright/test": "1.62.0",
    "@types/node": "26.1.2",
    "@vitejs/plugin-vue": "6.0.8",
    "@vue/test-utils": "2.4.11",
    "axios-mock-adapter": "2.1.0",
    "jsdom": "30.0.1",
    "typescript": "7.0.2",
    "vite": "8.1.5",
    "vitest": "4.1.10",
    "vue-tsc": "3.3.8"
  }
}
```

- [ ] **Step 3: Install dependencies and verify the test fails**

Run: `cd integration-audit/admin-web; npm install; npm run test:run -- src/App.test.ts`

Expected: FAIL because `App.vue` does not yet expose `data-testid="app-root"`.

- [ ] **Step 4: Add the minimal Vue entry point**

```vue
<!-- src/App.vue -->
<template><div data-testid="app-root"><RouterView /></div></template>
```

```ts
// src/main.ts
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { router } from './router';
import 'ant-design-vue/dist/reset.css';
import './styles/theme.css';
createApp(App).use(createPinia()).use(router).mount('#app');
```

Create an empty `theme.css` and this temporary router; Task 5 replaces its route table without changing the import boundary:

```ts
import { createRouter, createWebHistory } from 'vue-router';
export const router = createRouter({ history: createWebHistory(), routes: [] });
```

- [ ] **Step 5: Run the smoke test and commit**

Run: `npm run test:run -- src/App.test.ts`

Expected: PASS.

```bash
git add integration-audit/admin-web
git commit -m "feat(audit-web): scaffold Vue management console"
```

### Task 2: Define Domain Types and the Repository Contract

**Files:**
- Create: `integration-audit/admin-web/src/domain/monitoring.ts`
- Create: `integration-audit/admin-web/src/domain/errors.ts`
- Create: `integration-audit/admin-web/src/repositories/monitoringRepository.ts`
- Test: `integration-audit/admin-web/src/repositories/monitoringRepository.test.ts`

- [ ] **Step 1: Write a contract-shape test**

```ts
import type { MonitoringRepository } from './monitoringRepository';

it('requires every management capability', () => {
  const names: (keyof MonitoringRepository)[] = [
    'dashboard', 'searchAlerts', 'getAlert', 'transitionAlert',
    'searchEvents', 'getEvent', 'searchControls', 'transitionControl',
    'executeControl', 'searchRules', 'changeRule', 'searchWhitelists',
    'transitionWhitelist', 'searchManagementAudit',
  ];
  expect(names).toHaveLength(14);
});
```

- [ ] **Step 2: Run the test to verify missing modules fail**

Run: `npm run test:run -- src/repositories/monitoringRepository.test.ts`

Expected: FAIL with module resolution errors.

- [ ] **Step 3: Create the complete public contract**

```ts
export interface PageQuery { page: number; pageSize: number; keyword?: string; }
export interface PageResult<T> { items: T[]; page: number; pageSize: number; total: number; }
export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type AlertStatus = 'NEW' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'CLOSED' | 'FALSE_POSITIVE';
export interface VersionedCommand { id: string; expectedVersion: number; reason: string; }
export interface AlertRecord { id: string; title: string; ruleId: string; risk: RiskLevel; subject: string; status: AlertStatus; assigneeId?: string; version: number; lastSeenAt: string; evidence: Record<string, string | number>; }
export interface EventRecord { id: string; actionCode: string; subject: string; resourceId: string; result: string; occurredAt: string; facts: Record<string, string | number | boolean>; alertIds: string[]; }
export interface ControlRecord { id: string; alertId: string; ruleId: string; subject: string; action: string; status: string; expiresAt?: string; version: number; attempts: { attempt: number; status: string }[]; }
export interface RuleRecord { id: string; name: string; risk: RiskLevel; mode: 'OBSERVE' | 'ALERT_ONLY' | 'ENFORCE' | 'DISABLED'; threshold: number; version: number; }
export interface WhitelistRecord { id: string; subject: string; scope: string; status: 'ACTIVE' | 'REVOKED'; expiresAt?: string; version: number; }
export interface ManagementAuditRecord { id: string; actorId: string; operation: string; targetType: string; targetId: string; outcome: 'SUCCEEDED' | 'DENIED' | 'FAILED'; occurredAt: string; requestId: string; }
```

Define `MonitoringRepository` with typed queries and commands for the 14 methods asserted above. Define `ManagementError` with `category`, `originalType`, `errorCode`, `status`, `requestId`, and safe `details`.

- [ ] **Step 4: Run type checking and the contract test**

Run: `npm run typecheck; npm run test:run -- src/repositories/monitoringRepository.test.ts`

Expected: both PASS.

- [ ] **Step 5: Commit**

```bash
git add integration-audit/admin-web/src/domain integration-audit/admin-web/src/repositories
git commit -m "feat(audit-web): define monitoring repository contract"
```

### Task 3: Implement the Deterministic Mock Repository

**Files:**
- Create: `integration-audit/admin-web/src/mocks/fixtures.ts`
- Create: `integration-audit/admin-web/src/mocks/mockMonitoringRepository.ts`
- Create: `integration-audit/admin-web/src/mocks/scenario.ts`
- Test: `integration-audit/admin-web/src/mocks/mockMonitoringRepository.test.ts`

- [ ] **Step 1: Write failing pagination and state-machine tests**

```ts
it('filters then paginates alerts', async () => {
  const result = await repository.searchAlerts({ page: 1, pageSize: 2, risk: ['HIGH'] });
  expect(result.items).toHaveLength(2);
  expect(result.items.every((item) => item.risk === 'HIGH')).toBe(true);
});

it('increments the version on a valid alert transition', async () => {
  const updated = await repository.transitionAlert({ id: 'ALT-001', expectedVersion: 1, action: 'ACKNOWLEDGE', reason: '已核实异常来源' });
  expect(updated).toMatchObject({ status: 'ACKNOWLEDGED', version: 2 });
});

it('rejects stale versions without changing state', async () => {
  await expect(repository.transitionAlert({ id: 'ALT-001', expectedVersion: 0, action: 'CLOSE', reason: '过期操作' }))
    .rejects.toMatchObject({ category: 'CONFLICT' });
  expect((await repository.getAlert('ALT-001')).version).toBe(1);
});
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `npm run test:run -- src/mocks/mockMonitoringRepository.test.ts`

Expected: FAIL because the repository is absent.

- [ ] **Step 3: Implement fixture cloning, pagination, transitions, and reset**

```ts
const alertTransitions = {
  ACKNOWLEDGE: { from: ['NEW'], to: 'ACKNOWLEDGED' },
  INVESTIGATE: { from: ['NEW', 'ACKNOWLEDGED'], to: 'IN_PROGRESS' },
  CLOSE: { from: ['ACKNOWLEDGED', 'IN_PROGRESS'], to: 'CLOSED' },
  FALSE_POSITIVE: { from: ['NEW', 'ACKNOWLEDGED', 'IN_PROGRESS'], to: 'FALSE_POSITIVE' },
} as const;

function assertVersion(actual: number, expected: number): void {
  if (actual !== expected) throw managementError('CONFLICT', 'MOCK-409', 409, '数据已更新');
}
```

Implement immutable fixture cloning on construction, filter-before-pagination, ISO timestamps, assignment history, dashboard aggregates, control transitions, rule change, whitelist transition, management audit append, scenario failure injection, and `reset()`.

- [ ] **Step 4: Run repository tests**

Run: `npm run test:run -- src/mocks/mockMonitoringRepository.test.ts`

Expected: PASS for query, pagination, each valid transition, invalid transition, stale version, permission denial, and reset.

- [ ] **Step 5: Commit**

```bash
git add integration-audit/admin-web/src/mocks
git commit -m "feat(audit-web): add deterministic monitoring mock"
```

### Task 4: Build the Jeecg-Compatible Component Layer

**Files:**
- Create: `integration-audit/admin-web/src/components/jeecg/BasicTable.vue`
- Create: `integration-audit/admin-web/src/components/jeecg/useTable.ts`
- Create: `integration-audit/admin-web/src/components/jeecg/BasicForm.vue`
- Create: `integration-audit/admin-web/src/components/jeecg/useForm.ts`
- Create: `integration-audit/admin-web/src/components/jeecg/BasicModal.vue`
- Create: `integration-audit/admin-web/src/components/jeecg/BasicDrawer.vue`
- Create: `integration-audit/admin-web/src/components/jeecg/useOverlay.ts`
- Create: `integration-audit/admin-web/src/components/jeecg/DictTag.vue`
- Create: `integration-audit/admin-web/src/components/jeecg/index.ts`
- Test: `integration-audit/admin-web/src/components/jeecg/jeecgComponents.test.ts`

- [ ] **Step 1: Write failing behavior tests**

```ts
it('translates Ant pagination to one-based list queries', async () => {
  const api = vi.fn().mockResolvedValue({ items: [], total: 0, page: 2, pageSize: 20 });
  const { reload, pagination } = useTable({ api, pageSize: 20 });
  pagination.current = 3;
  await reload();
  expect(api).toHaveBeenCalledWith(expect.objectContaining({ page: 3, pageSize: 20 }));
});

it('validates required BasicForm schema fields', async () => {
  const wrapper = mount(BasicForm, { props: { schemas: [{ field: 'reason', label: '原因', required: true, component: 'Input' }] } });
  await expect(wrapper.vm.validate()).rejects.toBeDefined();
});
```

- [ ] **Step 2: Run tests and observe missing exports**

Run: `npm run test:run -- src/components/jeecg/jeecgComponents.test.ts`

Expected: FAIL with missing component and hook modules.

- [ ] **Step 3: Implement stable component contracts**

`useTable` owns query state, pagination, selection, loading, error, `reload`, `reset`, and stale-request protection. `BasicForm` renders `Input`, `Select`, `DatePicker`, `RangePicker`, `InputNumber`, and slots from `FormSchema`. `useOverlay` returns `[register, { open, close, setProps }]`. `DictTag` maps code to text and semantic class; unknown values display the original code.

```ts
export interface FormSchema {
  field: string;
  label: string;
  component: 'Input' | 'Select' | 'DatePicker' | 'RangePicker' | 'InputNumber';
  required?: boolean;
  componentProps?: Record<string, unknown>;
  options?: { label: string; value: string | number }[];
}
```

- [ ] **Step 4: Run component tests and type checking**

Run: `npm run test:run -- src/components/jeecg/jeecgComponents.test.ts; npm run typecheck`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add integration-audit/admin-web/src/components/jeecg
git commit -m "feat(audit-web): add Jeecg compatible UI layer"
```

### Task 5: Add the Risk-Control Layout, Router, and Theme

**Files:**
- Modify: `integration-audit/admin-web/src/router/index.ts`
- Create: `integration-audit/admin-web/src/router/routes.ts`
- Create: `integration-audit/admin-web/src/layouts/RiskControlLayout.vue`
- Modify: `integration-audit/admin-web/src/styles/theme.css`
- Create: `integration-audit/admin-web/src/styles/components.css`
- Test: `integration-audit/admin-web/src/layouts/RiskControlLayout.test.ts`

- [ ] **Step 1: Write the navigation test**

```ts
it('renders all seven management destinations', () => {
  const wrapper = mount(RiskControlLayout, { global: { plugins: [router] } });
  for (const label of ['风险态势', '告警中心', '事件审计', '控制中心', '检测策略', '白名单', '管理审计']) {
    expect(wrapper.text()).toContain(label);
  }
});
```

- [ ] **Step 2: Run it and verify failure**

Run: `npm run test:run -- src/layouts/RiskControlLayout.test.ts`

Expected: FAIL because routes and layout are absent.

- [ ] **Step 3: Implement layout and tokens**

Use CSS variables `--nav:#153a45`, `--nav-active:#2a6266`, `--approval:#efba50`, `--danger:#c94643`, `--success:#208078`, `--canvas:#edf2f1`, `--surface:#ffffff`. Use Lucide-compatible Ant icons for menu items, a compact header with `ENFORCE` status, and a responsive drawer navigation below 900px.

- [ ] **Step 4: Run tests and commit**

Run: `npm run test:run -- src/layouts/RiskControlLayout.test.ts; npm run build`

Expected: PASS and Vite emits `dist/` without type errors.

```bash
git add integration-audit/admin-web/src/router integration-audit/admin-web/src/layouts integration-audit/admin-web/src/styles
git commit -m "feat(audit-web): add bank risk control shell"
```

### Task 6: Implement Dashboard, Event Audit, and Management Audit

**Files:**
- Create: `integration-audit/admin-web/src/views/dashboard/DashboardView.vue`
- Create: `integration-audit/admin-web/src/views/dashboard/RiskTrendChart.vue`
- Create: `integration-audit/admin-web/src/views/events/EventListView.vue`
- Create: `integration-audit/admin-web/src/views/events/EventDetailDrawer.vue`
- Create: `integration-audit/admin-web/src/views/events/eventTable.ts`
- Create: `integration-audit/admin-web/src/views/audit/ManagementAuditView.vue`
- Test: `integration-audit/admin-web/src/views/readOnlyViews.test.ts`

- [ ] **Step 1: Write failing read-only page tests**

```ts
it('drills the high-risk dashboard metric into alerts', async () => {
  const wrapper = mount(DashboardView, { global: testGlobal(repository) });
  await flushPromises();
  await wrapper.get('[data-testid="high-risk-alerts"]').trigger('click');
  expect(router.currentRoute.value.query.risk).toBe('HIGH');
});

it('never renders edit or delete actions on audit tables', async () => {
  const wrapper = mount(ManagementAuditView, { global: testGlobal(repository) });
  await flushPromises();
  expect(wrapper.text()).not.toMatch(/编辑|删除/);
});
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `npm run test:run -- src/views/readOnlyViews.test.ts`

Expected: FAIL because the views are absent.

- [ ] **Step 3: Implement dashboard charts and read-only tables**

Dashboard renders four stable metric tiles, risk trend, level distribution, rule contribution, workload, and a priority queue. Event details render trusted facts as key/value rows and associated alert links. Management audit exposes actor, operation, resource, outcome, time, and request ID only.

- [ ] **Step 4: Run tests and commit**

Run: `npm run test:run -- src/views/readOnlyViews.test.ts; npm run typecheck`

Expected: PASS.

```bash
git add integration-audit/admin-web/src/views/dashboard integration-audit/admin-web/src/views/events integration-audit/admin-web/src/views/audit
git commit -m "feat(audit-web): add risk and audit views"
```

### Task 7: Implement the Alert Workbench

**Files:**
- Create: `integration-audit/admin-web/src/views/alerts/AlertListView.vue`
- Create: `integration-audit/admin-web/src/views/alerts/AlertDetailDrawer.vue`
- Create: `integration-audit/admin-web/src/views/alerts/AlertActionModal.vue`
- Create: `integration-audit/admin-web/src/views/alerts/alertSchemas.ts`
- Test: `integration-audit/admin-web/src/views/alerts/AlertListView.test.ts`

- [ ] **Step 1: Write failing workflow tests**

```ts
it('preserves filters while completing an alert action in the drawer', async () => {
  const wrapper = mount(AlertListView, { global: testGlobal(repository) });
  await wrapper.get('[data-testid="risk-filter"]').setValue('HIGH');
  await wrapper.get('[data-testid="alert-ALT-001"]').trigger('click');
  await wrapper.get('[data-testid="acknowledge"]').trigger('click');
  await wrapper.get('[name="reason"]').setValue('确认异常登录');
  await wrapper.get('[data-testid="submit-action"]').trigger('click');
  await flushPromises();
  expect(wrapper.get('[data-testid="risk-filter"]').element.value).toBe('HIGH');
  expect(wrapper.text()).toContain('已确认');
});
```

- [ ] **Step 2: Run it and verify failure**

Run: `npm run test:run -- src/views/alerts/AlertListView.test.ts`

Expected: FAIL because alert components are absent.

- [ ] **Step 3: Implement the table, detail tabs, and action schema**

Use `BasicTable` for status/risk/assignee filters and pagination. Drawer tabs are evidence, timeline, assignment history, and associated events. `AlertActionModal` derives allowed actions from status, validates a 5-500 character reason, requires assignee for assignment, submits `expectedVersion`, and reloads both row and detail.

- [ ] **Step 4: Add the stale-version recovery test and implementation**

```ts
it('keeps the reason and reloads details after conflict', async () => {
  repository.transitionAlert = vi.fn().mockRejectedValue(managementError('CONFLICT', 'MON-202', 409, '版本冲突'));
  const wrapper = mount(AlertListView, { global: testGlobal(repository) });
  await flushPromises();
  await wrapper.get('[data-testid="alert-ALT-001"]').trigger('click');
  await wrapper.get('[data-testid="acknowledge"]').trigger('click');
  await wrapper.get('[name="reason"]').setValue('确认异常登录');
  await wrapper.get('[data-testid="submit-action"]').trigger('click');
  await flushPromises();
  expect(wrapper.get('[name="reason"]').element.value).toBe('确认异常登录');
  expect(repository.getAlert).toHaveBeenCalledWith('ALT-001');
});
```

- [ ] **Step 5: Run and commit**

Run: `npm run test:run -- src/views/alerts/AlertListView.test.ts`

Expected: PASS for assign, acknowledge, investigate, close, false positive, validation, and conflict.

```bash
git add integration-audit/admin-web/src/views/alerts
git commit -m "feat(audit-web): add alert response workbench"
```

### Task 8: Implement Control Operations

**Files:**
- Create: `integration-audit/admin-web/src/views/controls/ControlListView.vue`
- Create: `integration-audit/admin-web/src/views/controls/ControlDetailDrawer.vue`
- Create: `integration-audit/admin-web/src/views/controls/ControlActionModal.vue`
- Test: `integration-audit/admin-web/src/views/controls/ControlListView.test.ts`

- [ ] **Step 1: Write failing approval and retry tests**

```ts
it.each([
  ['AWAITING_APPROVAL', '批准'],
  ['AWAITING_APPROVAL', '驳回'],
  ['FAILED', '重试'],
])('offers %s controls the %s action', async (status, label) => {
  repository.searchControls = vi.fn().mockResolvedValue(controlPage(status));
  const wrapper = mount(ControlListView, { global: testGlobal(repository) });
  await flushPromises();
  expect(wrapper.text()).toContain(label);
});
```

- [ ] **Step 2: Run and verify failure**

Run: `npm run test:run -- src/views/controls/ControlListView.test.ts`

Expected: FAIL because control views are absent.

- [ ] **Step 3: Implement control list and confirmation flows**

Show action, subject, rule, alert, status, TTL, attempts, and safe failure reason. Approval, rejection, and retry submit versioned reasons. Manual session revocation requires subject, TTL minutes, reason, and generated idempotency key.

- [ ] **Step 4: Run and commit**

Run: `npm run test:run -- src/views/controls/ControlListView.test.ts; npm run typecheck`

Expected: PASS.

```bash
git add integration-audit/admin-web/src/views/controls
git commit -m "feat(audit-web): add control approval console"
```

### Task 9: Implement Rule Governance and Whitelist Lifecycle

**Files:**
- Create: `integration-audit/admin-web/src/views/rules/RuleListView.vue`
- Create: `integration-audit/admin-web/src/views/rules/RuleChangeModal.vue`
- Create: `integration-audit/admin-web/src/views/whitelists/WhitelistListView.vue`
- Create: `integration-audit/admin-web/src/views/whitelists/WhitelistActionModal.vue`
- Test: `integration-audit/admin-web/src/views/governanceViews.test.ts`

- [ ] **Step 1: Write failing governance tests**

```ts
it('requires a distinct rule approver and reason', async () => {
  const wrapper = mount(RuleChangeModal, { props: { rule, requesterId: 'audit-admin' }, global: testGlobal(repository) });
  await wrapper.vm.submit({ approverId: 'audit-admin', reason: '调整阈值', threshold: 8, mode: 'ENFORCE' });
  expect(wrapper.text()).toContain('审批人不能与提交人相同');
});

it('does not expose whitelist creation', async () => {
  const wrapper = mount(WhitelistListView, { global: testGlobal(repository) });
  await flushPromises();
  expect(wrapper.text()).not.toContain('新增');
});
```

- [ ] **Step 2: Run and verify failure**

Run: `npm run test:run -- src/views/governanceViews.test.ts`

Expected: FAIL because governance views are absent.

- [ ] **Step 3: Implement rule and whitelist workflows**

Rule changes require mode, threshold >= 1, 5-500 character reason, distinct approver, expected version, and generated idempotency key. Always show the frozen-runtime activation warning. Whitelist exposes only grant/revoke and validates expected version plus reason.

- [ ] **Step 4: Run and commit**

Run: `npm run test:run -- src/views/governanceViews.test.ts; npm run build`

Expected: PASS.

```bash
git add integration-audit/admin-web/src/views/rules integration-audit/admin-web/src/views/whitelists
git commit -m "feat(audit-web): add governed rule and whitelist flows"
```

### Task 10: Add Error Scenarios, Playwright Acceptance, and Run Documentation

**Files:**
- Create: `integration-audit/admin-web/playwright.config.ts`
- Create: `integration-audit/admin-web/e2e/mock-workflows.spec.ts`
- Create: `integration-audit/admin-web/e2e/visual.spec.ts`
- Create: `integration-audit/admin-web/src/mocks/MockScenarioPanel.vue`
- Create: `integration-audit/admin-web/README.md`
- Modify: `.gitignore`

- [ ] **Step 1: Write end-to-end acceptance tests**

```ts
test('operator resolves an alert and retains the audit trail', async ({ page }) => {
  await page.goto('/alerts');
  await page.getByText('异地高频认证失败').click();
  await page.getByRole('button', { name: '确认' }).click();
  await page.getByLabel('处置原因').fill('核验登录来源后确认异常');
  await page.getByRole('button', { name: '提交' }).click();
  await expect(page.getByText('已确认')).toBeVisible();
});

test('desktop dashboard matches the approved visual direction', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/dashboard');
  await expect(page).toHaveScreenshot('dashboard-1440.png', { animations: 'disabled' });
});
```

- [ ] **Step 2: Run tests and verify missing configuration fails**

Run: `npx playwright install chromium; npm run e2e`

Expected: FAIL because Playwright configuration and complete scenario hooks are absent.

- [ ] **Step 3: Configure web server, viewports, and scenario panel**

Configure Playwright to start `npm run dev -- --port 4173`, use `http://127.0.0.1:4173`, capture traces on retry, and run 1440x1000 plus 390x844 projects. Scenario panel is development-only and supports reset, conflict, forbidden, service unavailable, empty, and unknown error.

- [ ] **Step 4: Document exact run modes**

README commands:

```bash
npm install
npm run dev
npm run test:run
npm run build
npm run e2e
```

Document that phase one supports `VITE_DATA_MODE=mock`; the HTTP mode is delivered by the follow-on plan.

- [ ] **Step 5: Run the complete frontend gate**

Run: `npm run test:run; npm run typecheck; npm run build; npm run e2e`

Expected: all commands exit 0; screenshots are nonblank and show no overlap at both viewports.

- [ ] **Step 6: Commit**

```bash
git add .gitignore integration-audit/admin-web
git commit -m "test(audit-web): verify mock management workflows"
```

## Phase-One Completion Check

Run from `integration-audit/admin-web`:

```bash
npm run test:run
npm run typecheck
npm run build
npm run e2e
```

Start `npm run dev`, open the printed local URL, and review dashboard, all six management lists, every drawer/modal, empty state, 403, conflict, and unknown error. Do not start HTTP integration until this Mock visual review is accepted.

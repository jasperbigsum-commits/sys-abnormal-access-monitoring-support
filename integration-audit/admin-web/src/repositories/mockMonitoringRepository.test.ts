import { describe, expect, it } from 'vitest';

import { ManagementError } from '@/domain/errors';
import { createMockMonitoringRepository } from '@/repositories/mockMonitoringRepository';

function expectManagementError(category: string) {
    return expect.objectContaining({ category });
}

describe('MockMonitoringRepository', () => {
    it('paginates alerts and combines keyword, status, risk and time filters', async () => {
        const repository = createMockMonitoringRepository();

        const firstPage = await repository.searchAlerts({ page: 1, pageSize: 2 });
        const filtered = await repository.searchAlerts({
            page: 1,
            pageSize: 10,
            keyword: '批量查询',
            statuses: ['NEW'],
            risks: ['CRITICAL'],
            from: '2026-07-30T08:00:00+08:00',
            to: '2026-07-30T12:00:00+08:00'
        });

        expect(firstPage.items).toHaveLength(2);
        expect(firstPage.total).toBeGreaterThan(2);
        expect(firstPage.page).toBe(1);
        expect(filtered.items).toHaveLength(1);
        expect(filtered.items[0]).toMatchObject({ status: 'NEW', risk: 'CRITICAL' });
    });

    it('filters events, controls, rules, whitelists and management audit records', async () => {
        const repository = createMockMonitoringRepository();

        const events = await repository.searchEvents({
            page: 1,
            pageSize: 10,
            keyword: '客户',
            actionCode: 'CUSTOMER_EXPORT',
            result: 'DENIED',
            from: '2026-07-30T08:00:00+08:00',
            to: '2026-07-30T12:00:00+08:00'
        });
        const controls = await repository.searchControls({
            page: 1,
            pageSize: 10,
            keyword: 'acct-',
            statuses: ['PENDING_APPROVAL'],
            actions: ['LOCK_ACCOUNT'],
            from: '2026-07-30T08:00:00+08:00',
            to: '2026-07-31T00:00:00+08:00'
        });
        const rules = await repository.searchRules({ page: 1, pageSize: 10, modes: ['ALERT_ONLY'], risks: ['CRITICAL'] });
        const whitelists = await repository.searchWhitelists({ page: 1, pageSize: 10, keyword: 'ops-', statuses: ['ACTIVE'] });
        const audit = await repository.searchManagementAudit({
            page: 1,
            pageSize: 10,
            actorId: 'risk-admin-01',
            outcomes: ['SUCCEEDED'],
            operations: ['RULE_CHANGE'],
            from: '2026-07-30T00:00:00+08:00',
            to: '2026-07-31T00:00:00+08:00'
        });

        expect(events.items).toHaveLength(1);
        expect(controls.items).toHaveLength(1);
        expect(rules.items).toHaveLength(1);
        expect(whitelists.items).toHaveLength(1);
        expect(audit.items).toHaveLength(1);
    });

    it('assigns and advances an alert while appending timeline and audit entries', async () => {
        const repository = createMockMonitoringRepository();
        const before = await repository.getAlert('ALT-20260730-001');

        const assigned = await repository.transitionAlert({
            id: before.id,
            action: 'ASSIGN',
            expectedVersion: before.version,
            reason: '转交值班分析师复核',
            assigneeId: 'analyst-02'
        });
        const acknowledged = await repository.transitionAlert({
            id: assigned.id,
            action: 'ACKNOWLEDGE',
            expectedVersion: assigned.version,
            reason: '已核验事件证据'
        });
        const audit = await repository.searchManagementAudit({ page: 1, pageSize: 100, operations: ['ALERT_ASSIGN', 'ALERT_ACKNOWLEDGE'] });

        expect(assigned).toMatchObject({ assigneeId: 'analyst-02', version: before.version + 1 });
        expect(assigned.assignments.at(-1)?.reason).toBe('转交值班分析师复核');
        expect(acknowledged.status).toBe('ACKNOWLEDGED');
        expect(acknowledged.timeline.at(-1)?.action).toBe('ACKNOWLEDGE');
        expect(audit.items).toHaveLength(2);
    });

    it('rejects stale and invalid alert transitions with typed errors', async () => {
        const repository = createMockMonitoringRepository();
        const alert = await repository.getAlert('ALT-20260730-001');
        await repository.transitionAlert({
            id: alert.id,
            action: 'ACKNOWLEDGE',
            expectedVersion: alert.version,
            reason: '进入研判'
        });

        await expect(repository.transitionAlert({
            id: alert.id,
            action: 'CLOSE',
            expectedVersion: alert.version,
            reason: '使用陈旧版本关闭'
        })).rejects.toEqual(expectManagementError('CONFLICT'));
        await expect(repository.transitionAlert({
            id: 'ALT-20260730-003',
            action: 'ACKNOWLEDGE',
            expectedVersion: 2,
            reason: '关闭态不可确认'
        })).rejects.toEqual(expectManagementError('INVALID_TRANSITION'));
    });

    it('moves an acknowledged alert through investigation to closure', async () => {
        const repository = createMockMonitoringRepository();
        const investigating = await repository.transitionAlert({
            id: 'ALT-20260730-004',
            action: 'INVESTIGATE',
            expectedVersion: 2,
            reason: '进入终端使用情况研判'
        });
        const closed = await repository.transitionAlert({
            id: investigating.id,
            action: 'CLOSE',
            expectedVersion: investigating.version,
            reason: '确认培训终端共享，已完成整改'
        });

        expect(investigating.status).toBe('IN_PROGRESS');
        expect(closed).toMatchObject({ status: 'CLOSED', version: 4 });
    });

    it('approves, retries and executes controls with version validation', async () => {
        const repository = createMockMonitoringRepository();
        const approved = await repository.transitionControl({
            id: 'CTL-20260730-001',
            action: 'APPROVE',
            expectedVersion: 1,
            reason: '双人复核通过'
        });
        const retried = await repository.transitionControl({
            id: 'CTL-20260730-002',
            action: 'RETRY',
            expectedVersion: 3,
            reason: '下游服务恢复'
        });
        const executed = await repository.executeControl({
            subject: 'acct-demo-9001',
            action: 'STEP_UP_AUTH',
            ttlMinutes: 30,
            reason: '高风险操作二次认证',
            idempotencyKey: 'control-demo-9001'
        });

        expect(approved).toMatchObject({ status: 'APPROVED', version: 2 });
        expect(retried).toMatchObject({ status: 'SUCCEEDED', version: 4 });
        expect(retried.attempts.at(-1)?.status).toBe('SUCCEEDED');
        expect(executed).toMatchObject({ subject: 'acct-demo-9001', action: 'STEP_UP_AUTH', status: 'SUCCEEDED', version: 1 });
        await expect(repository.transitionControl({
            id: approved.id,
            action: 'REJECT',
            expectedVersion: 1,
            reason: '陈旧审批'
        })).rejects.toEqual(expectManagementError('CONFLICT'));
    });

    it('rejects a pending control and records the decision', async () => {
        const repository = createMockMonitoringRepository();
        const rejected = await repository.transitionControl({
            id: 'CTL-20260730-001',
            action: 'REJECT',
            expectedVersion: 1,
            reason: '证据不足，退回补充研判材料'
        });
        const audit = await repository.searchManagementAudit({ page: 1, pageSize: 10, operations: ['CONTROL_REJECT'] });

        expect(rejected).toMatchObject({ status: 'REJECTED', version: 2 });
        expect(audit.items.filter((item) => item.targetId === rejected.id)).toHaveLength(1);
    });

    it('changes rules and grants or revokes whitelists while auditing mutations', async () => {
        const repository = createMockMonitoringRepository();
        const changed = await repository.changeRule({
            id: 'RULE-BULK-QUERY',
            mode: 'ENFORCE',
            threshold: 80,
            expectedVersion: 4,
            reason: '完成灰度观察后提升处置强度',
            approverId: 'risk-approver-01',
            idempotencyKey: 'rule-change-demo-01'
        });
        const revoked = await repository.transitionWhitelist({
            id: 'WL-20260730-001',
            action: 'REVOKE',
            expectedVersion: 1,
            reason: '临时运维窗口结束'
        });
        const granted = await repository.transitionWhitelist({
            id: 'WL-20260730-002',
            action: 'GRANT',
            expectedVersion: 2,
            reason: '审批后恢复临时访问'
        });
        const audit = await repository.searchManagementAudit({
            page: 1,
            pageSize: 100,
            operations: ['RULE_CHANGE', 'WHITELIST_REVOKE', 'WHITELIST_GRANT']
        });

        expect(changed).toMatchObject({ mode: 'ENFORCE', threshold: 80, approvedBy: 'risk-approver-01', version: 5 });
        expect(revoked).toMatchObject({ status: 'REVOKED', version: 2 });
        expect(granted).toMatchObject({ status: 'ACTIVE', version: 3 });
        expect(audit.items).toHaveLength(4);
    });

    it('returns not found and invalid transition errors for missing or terminal resources', async () => {
        const repository = createMockMonitoringRepository();

        await expect(repository.getEvent('EVT-MISSING')).rejects.toEqual(expectManagementError('NOT_FOUND'));
        await expect(repository.transitionControl({
            id: 'CTL-20260730-003',
            action: 'APPROVE',
            expectedVersion: 2,
            reason: '完成态不可审批'
        })).rejects.toEqual(expectManagementError('INVALID_TRANSITION'));
        await expect(repository.transitionWhitelist({
            id: 'WL-20260730-001',
            action: 'GRANT',
            expectedVersion: 1,
            reason: '已生效记录不可重复授予'
        })).rejects.toEqual(expectManagementError('INVALID_TRANSITION'));
    });

    it('resets all mutable records and generated audit entries', async () => {
        const repository = createMockMonitoringRepository();
        await repository.transitionAlert({
            id: 'ALT-20260730-001',
            action: 'ACKNOWLEDGE',
            expectedVersion: 1,
            reason: '测试重置'
        });

        repository.reset();

        const alert = await repository.getAlert('ALT-20260730-001');
        const audit = await repository.searchManagementAudit({ page: 1, pageSize: 100, operations: ['ALERT_ACKNOWLEDGE'] });
        expect(alert).toMatchObject({ status: 'NEW', version: 1 });
        expect(audit.items).toHaveLength(0);
    });

    it('applies a fixed delay and injects configured typed failures by operation', async () => {
        const repository = createMockMonitoringRepository({
            delayMs: 20,
            failures: {
                searchAlerts: {
                    category: 'UNAVAILABLE',
                    message: '模拟审计服务暂不可用',
                    errorCode: 'MOCK-503'
                }
            }
        });
        const startedAt = Date.now();

        await expect(repository.searchAlerts({ page: 1, pageSize: 10 })).rejects.toMatchObject({
            category: 'UNAVAILABLE',
            errorCode: 'MOCK-503'
        } satisfies Partial<ManagementError>);
        expect(Date.now() - startedAt).toBeGreaterThanOrEqual(15);
        const dashboard = await repository.dashboard();
        expect(dashboard.metrics.eventsToday).toBeGreaterThan(0);
    });
});

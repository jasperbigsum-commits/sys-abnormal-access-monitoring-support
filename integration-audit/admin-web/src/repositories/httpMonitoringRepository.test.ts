import { beforeEach, describe, expect, it, vi } from 'vitest';

import { managementApi } from '@/api/management';
import { createHttpMonitoringRepository } from './httpMonitoringRepository';

vi.mock('@/api/management', () => ({
    managementApi: {
        dashboard: vi.fn(), alerts: vi.fn(), alert: vi.fn(), alertTransition: vi.fn(), events: vi.fn(), event: vi.fn(),
        controls: vi.fn(), controlTransition: vi.fn(), executeControl: vi.fn(), rules: vi.fn(), changeRule: vi.fn(),
        whitelists: vi.fn(), whitelistTransition: vi.fn(), managementAudit: vi.fn()
    }
}));

const emptyPage = { items: [], page: 0, size: 20, totalElements: 0 };

describe('HttpMonitoringRepository', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        for (const name of ['alerts', 'events', 'controls', 'rules', 'whitelists', 'managementAudit'] as const) {
            vi.mocked(managementApi[name]).mockResolvedValue(emptyPage);
        }
    });

    it('maps UI pagination and array filters to the management API', async () => {
        const result = await createHttpMonitoringRepository().searchAlerts({ page: 2, pageSize: 20, statuses: ['NEW', 'ACKNOWLEDGED'], risks: ['HIGH'] });
        expect(managementApi.alerts).toHaveBeenCalledWith({ page: 1, size: 20, status: 'NEW,ACKNOWLEDGED', risk: 'HIGH' });
        expect(result).toEqual({ items: [], page: 1, pageSize: 20, total: 0 });
    });

    it('posts versioned alert actions to an explicit path', async () => {
        vi.mocked(managementApi.alertTransition).mockResolvedValue({ id: 'ALT-1' } as never);
        await createHttpMonitoringRepository().transitionAlert({ id: 'ALT-1', action: 'FALSE_POSITIVE', expectedVersion: 3, reason: '误报' });
        expect(managementApi.alertTransition).toHaveBeenCalledWith('ALT-1', 'false-positive', { expectedVersion: 3, reason: '误报' });
    });

    it('never sends the independently trusted rule approver in the body', async () => {
        vi.mocked(managementApi.changeRule).mockResolvedValue({ id: 'RULE-1' } as never);
        await createHttpMonitoringRepository().changeRule({
            id: 'RULE-1', mode: 'ALERT_ONLY', threshold: 8, expectedVersion: 2, reason: '审慎调整', approverId: 'approver-2', idempotencyKey: 'idem-1'
        });
        expect(managementApi.changeRule).toHaveBeenCalledWith('RULE-1', {
            mode: 'ALERT_ONLY', threshold: 8, expectedVersion: 2, reason: '审慎调整', idempotencyKey: 'idem-1'
        });
    });

    it('implements all repository reads through the management API', async () => {
        const repository = createHttpMonitoringRepository();
        vi.mocked(managementApi.dashboard).mockResolvedValue({ metrics: {} } as never);
        vi.mocked(managementApi.alert).mockResolvedValue({ id: 'A' } as never);
        vi.mocked(managementApi.event).mockResolvedValue({ id: 'E' } as never);
        await Promise.all([
            repository.dashboard(), repository.getAlert('A'), repository.searchEvents({ page: 1, pageSize: 20 }), repository.getEvent('E'),
            repository.searchControls({ page: 1, pageSize: 20 }), repository.searchRules({ page: 1, pageSize: 20 }),
            repository.searchWhitelists({ page: 1, pageSize: 20 }), repository.searchManagementAudit({ page: 1, pageSize: 20 })
        ]);
        expect(managementApi.dashboard).toHaveBeenCalledOnce();
        expect(managementApi.event).toHaveBeenCalledWith('E');
        expect(managementApi.managementAudit).toHaveBeenCalledOnce();
    });
});

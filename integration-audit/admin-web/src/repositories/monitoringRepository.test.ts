import { describe, expect, it } from 'vitest';
import { ManagementError, managementError } from '@/domain/errors';
import type { MonitoringRepository } from './monitoringRepository';

describe('MonitoringRepository contract', () => {
    it('requires every management capability', () => {
        const names: (keyof MonitoringRepository)[] = [
            'dashboard',
            'searchAlerts',
            'getAlert',
            'transitionAlert',
            'searchEvents',
            'getEvent',
            'searchControls',
            'transitionControl',
            'executeControl',
            'searchRules',
            'changeRule',
            'searchWhitelists',
            'transitionWhitelist',
            'searchManagementAudit'
        ];

        expect(names).toHaveLength(14);
        expect(new Set(names).size).toBe(14);
    });

    it('preserves an unknown backend error type safely', () => {
        const error = managementError({
            category: 'UNKNOWN',
            originalType: 'POLICY_LOCKED',
            errorCode: 'MON-999',
            status: 422,
            requestId: 'req-1',
            message: '策略当前不可变更'
        });

        expect(error).toBeInstanceOf(ManagementError);
        expect(error).toMatchObject({
            category: 'UNKNOWN',
            originalType: 'POLICY_LOCKED',
            errorCode: 'MON-999',
            status: 422,
            requestId: 'req-1'
        });
    });
});

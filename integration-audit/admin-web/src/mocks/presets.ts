import type { ManagementErrorInput } from '@/domain/errors';
import type { MockMonitoringRepositoryOptions, MockOperation } from './scenario';

export type MockPreset = 'default' | 'empty' | 'slow' | 'forbidden' | 'unavailable' | 'conflict' | 'unknown';

const readOperations: MockOperation[] = [
    'dashboard', 'searchAlerts', 'getAlert', 'searchEvents', 'getEvent', 'searchControls',
    'searchRules', 'searchWhitelists', 'searchManagementAudit'
];

function failures(error: ManagementErrorInput, operations: MockOperation[] = readOperations): MockMonitoringRepositoryOptions {
    return { failures: Object.fromEntries(operations.map((operation) => [operation, error])) };
}

export function optionsForPreset(preset: MockPreset): MockMonitoringRepositoryOptions {
    if (preset === 'slow') return { delayMs: 900 };
    if (preset === 'forbidden') return failures({ category: 'FORBIDDEN', message: '当前账号无权读取该管理资源', errorCode: 'MOCK-403' });
    if (preset === 'unavailable') return failures({ category: 'UNAVAILABLE', message: '审计查询服务暂不可用', errorCode: 'MOCK-503' });
    if (preset === 'conflict') return failures({ category: 'CONFLICT', message: '记录已被其他管理员更新', errorCode: 'MOCK-409-VERSION' }, ['transitionAlert', 'transitionControl', 'changeRule', 'transitionWhitelist']);
    if (preset === 'unknown') return failures({ category: 'UNKNOWN', message: '策略引擎返回未定义错误', originalType: 'POLICY_LOCKED', errorCode: 'MON-999', requestId: 'req-mock-unknown' });
    return {};
}

export function readMockPreset(): MockPreset {
    const value = sessionStorage.getItem('audit-mock-preset');
    return ['default', 'empty', 'slow', 'forbidden', 'unavailable', 'conflict', 'unknown'].includes(value ?? '') ? value as MockPreset : 'default';
}

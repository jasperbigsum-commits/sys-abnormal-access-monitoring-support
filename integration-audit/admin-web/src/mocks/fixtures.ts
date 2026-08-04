import type { AlertRecord, ControlRecord, EventRecord, ManagementAuditRecord, RuleRecord, TrendPoint, WhitelistRecord } from '@/domain/monitoring';

export const FIXED_NOW = '2026-07-30T11:30:00+08:00';
export const MOCK_ACTOR = 'risk-admin-01';

const ALERTS: AlertRecord[] = [
    {
        id: 'ALT-20260730-001',
        title: '客户信息批量查询频率异常',
        ruleId: 'RULE-BULK-QUERY',
        risk: 'CRITICAL',
        subject: 'ops-user-demo-01',
        status: 'NEW',
        version: 1,
        firstSeenAt: '2026-07-30T09:18:00+08:00',
        lastSeenAt: '2026-07-30T10:42:00+08:00',
        occurrenceCount: 37,
        evidence: { windowMinutes: 10, queryCount: 37, baseline: 6, sourceZone: '办公网演示区' },
        eventIds: ['EVT-20260730-001', 'EVT-20260730-002'],
        assignments: [],
        timeline: [{ id: 'TL-001', action: 'CREATED', operatorId: 'monitor-engine', reason: '规则命中自动创建', occurredAt: '2026-07-30T09:18:00+08:00' }]
    },
    {
        id: 'ALT-20260730-002',
        title: '非工作时段敏感报表导出',
        ruleId: 'RULE-OFFHOURS-EXPORT',
        risk: 'HIGH',
        subject: 'report-user-demo-03',
        status: 'IN_PROGRESS',
        assigneeId: 'analyst-01',
        version: 3,
        firstSeenAt: '2026-07-30T07:35:00+08:00',
        lastSeenAt: '2026-07-30T07:35:00+08:00',
        occurrenceCount: 1,
        evidence: { exportRows: 5600, businessHour: false, reportType: '经营分析演示报表' },
        eventIds: ['EVT-20260730-003'],
        assignments: [{ id: 'ASN-001', operatorId: MOCK_ACTOR, assigneeId: 'analyst-01', reason: '交由数据安全岗研判', createdAt: '2026-07-30T08:05:00+08:00' }],
        timeline: [
            { id: 'TL-002', action: 'ACKNOWLEDGE', operatorId: 'analyst-01', reason: '已接收告警', occurredAt: '2026-07-30T08:10:00+08:00' },
            { id: 'TL-003', action: 'INVESTIGATE', operatorId: 'analyst-01', reason: '核验审批单与导出范围', occurredAt: '2026-07-30T08:20:00+08:00' }
        ]
    },
    {
        id: 'ALT-20260730-003',
        title: '连续认证失败后访问核心交易',
        ruleId: 'RULE-AUTH-FAILURE',
        risk: 'MEDIUM',
        subject: 'branch-user-demo-08',
        status: 'CLOSED',
        assigneeId: 'analyst-02',
        version: 2,
        firstSeenAt: '2026-07-29T16:02:00+08:00',
        lastSeenAt: '2026-07-29T16:15:00+08:00',
        occurrenceCount: 4,
        evidence: { failedAttempts: 4, verifiedTicket: true },
        eventIds: ['EVT-20260729-011'],
        assignments: [],
        timeline: [{ id: 'TL-004', action: 'CLOSE', operatorId: 'analyst-02', reason: '经核实为密码重置后的正常操作', occurredAt: '2026-07-29T17:10:00+08:00' }]
    },
    {
        id: 'ALT-20260730-004',
        title: '同一终端跨岗位资源探测',
        ruleId: 'RULE-CROSS-ROLE',
        risk: 'LOW',
        subject: 'terminal-demo-12',
        status: 'ACKNOWLEDGED',
        assigneeId: 'analyst-03',
        version: 2,
        firstSeenAt: '2026-07-30T10:55:00+08:00',
        lastSeenAt: '2026-07-30T11:03:00+08:00',
        occurrenceCount: 8,
        evidence: { roleCount: 3, resourceCount: 8 },
        eventIds: ['EVT-20260730-004'],
        assignments: [],
        timeline: [{ id: 'TL-005', action: 'ACKNOWLEDGE', operatorId: 'analyst-03', reason: '开始核查终端共享情况', occurredAt: '2026-07-30T11:08:00+08:00' }]
    }
];

const EVENTS: EventRecord[] = [
    { id: 'EVT-20260730-001', actionCode: 'CUSTOMER_EXPORT', subject: 'ops-user-demo-01', resourceId: '客户信息演示数据集', result: 'DENIED', occurredAt: '2026-07-30T10:42:00+08:00', facts: { rows: 1200, channel: '管理端' }, alertIds: ['ALT-20260730-001'] },
    { id: 'EVT-20260730-002', actionCode: 'CUSTOMER_QUERY', subject: 'ops-user-demo-01', resourceId: '客户画像演示数据集', result: 'SUCCEEDED', occurredAt: '2026-07-30T09:18:00+08:00', facts: { queries: 37 }, alertIds: ['ALT-20260730-001'] },
    { id: 'EVT-20260730-003', actionCode: 'REPORT_EXPORT', subject: 'report-user-demo-03', resourceId: '经营分析演示报表', result: 'SUCCEEDED', occurredAt: '2026-07-30T07:35:00+08:00', facts: { rows: 5600 }, alertIds: ['ALT-20260730-002'] },
    { id: 'EVT-20260730-004', actionCode: 'RESOURCE_PROBE', subject: 'terminal-demo-12', resourceId: '岗位权限演示资源', result: 'DENIED', occurredAt: '2026-07-30T11:03:00+08:00', facts: { resources: 8 }, alertIds: ['ALT-20260730-004'] }
];

const CONTROLS: ControlRecord[] = [
    { id: 'CTL-20260730-001', alertId: 'ALT-20260730-001', ruleId: 'RULE-BULK-QUERY', subject: 'acct-demo-1001', action: 'LOCK_ACCOUNT', status: 'PENDING_APPROVAL', expiresAt: '2026-07-30T13:30:00+08:00', version: 1, attempts: [{ attempt: 1, status: 'PENDING_APPROVAL', occurredAt: '2026-07-30T10:45:00+08:00' }] },
    { id: 'CTL-20260730-002', alertId: 'ALT-20260730-002', ruleId: 'RULE-OFFHOURS-EXPORT', subject: 'acct-demo-1003', action: 'REVOKE_SESSION', status: 'FAILED', version: 3, attempts: [{ attempt: 1, status: 'FAILED', occurredAt: '2026-07-30T08:12:00+08:00', failureReason: '演示下游服务短暂不可用' }], failureReason: '演示下游服务短暂不可用' },
    { id: 'CTL-20260730-003', alertId: 'ALT-20260730-003', ruleId: 'RULE-AUTH-FAILURE', subject: 'acct-demo-1008', action: 'STEP_UP_AUTH', status: 'SUCCEEDED', expiresAt: '2026-07-29T18:15:00+08:00', version: 2, attempts: [{ attempt: 1, status: 'SUCCEEDED', occurredAt: '2026-07-29T16:16:00+08:00' }] }
];

const RULES: RuleRecord[] = [
    { id: 'RULE-BULK-QUERY', name: '客户信息批量查询异常', risk: 'CRITICAL', mode: 'ALERT_ONLY', threshold: 60, enabled: true, version: 4, createdAt: '2026-06-01T09:00:00+08:00', createdBy: 'risk-admin-01', approvedBy: 'risk-approver-01', changeReason: '完成观察期参数调整' },
    { id: 'RULE-OFFHOURS-EXPORT', name: '非工作时段敏感导出', risk: 'HIGH', mode: 'ENFORCE', threshold: 1, enabled: true, version: 3, createdAt: '2026-06-08T09:00:00+08:00', createdBy: 'risk-admin-02', approvedBy: 'risk-approver-02' },
    { id: 'RULE-AUTH-FAILURE', name: '连续认证失败', risk: 'MEDIUM', mode: 'OBSERVE', threshold: 5, enabled: true, version: 2, createdAt: '2026-06-15T09:00:00+08:00', createdBy: 'risk-admin-01' },
    { id: 'RULE-CROSS-ROLE', name: '跨岗位资源探测', risk: 'LOW', mode: 'DISABLED', threshold: 10, enabled: false, version: 1, createdAt: '2026-07-01T09:00:00+08:00', createdBy: 'risk-admin-03' }
];

const WHITELISTS: WhitelistRecord[] = [
    { id: 'WL-20260730-001', subject: 'ops-demo-batch-01', systemScope: 'integration-audit', ruleId: 'RULE-OFFHOURS-EXPORT', status: 'ACTIVE', expiresAt: '2026-07-31T02:00:00+08:00', approvedBy: 'risk-approver-01', reason: '月末演示批处理', version: 1 },
    { id: 'WL-20260730-002', subject: 'ops-demo-maint-02', systemScope: 'integration-audit', status: 'REVOKED', approvedBy: 'risk-approver-02', reason: '维护工作已结束', version: 2 },
    { id: 'WL-20260730-003', subject: 'branch-demo-training', systemScope: 'integration-audit', ruleId: 'RULE-AUTH-FAILURE', status: 'EXPIRED', expiresAt: '2026-08-02T18:00:00+08:00', approvedBy: 'risk-approver-03', reason: '培训演练', version: 1 }
];

const AUDIT_RECORDS: ManagementAuditRecord[] = [
    { id: 'AUD-20260730-001', actorId: 'risk-admin-01', operation: 'RULE_CHANGE', targetType: 'RULE', targetId: 'RULE-BULK-QUERY', outcome: 'SUCCEEDED', occurredAt: '2026-07-30T08:30:00+08:00', requestId: 'req-demo-rule-001' },
    { id: 'AUD-20260730-002', actorId: 'risk-admin-02', operation: 'CONTROL_REJECT', targetType: 'CONTROL', targetId: 'CTL-20260729-009', outcome: 'DENIED', occurredAt: '2026-07-30T09:05:00+08:00', requestId: 'req-demo-control-002' },
    { id: 'AUD-20260730-003', actorId: 'analyst-01', operation: 'ALERT_INVESTIGATE', targetType: 'ALERT', targetId: 'ALT-20260730-002', outcome: 'SUCCEEDED', occurredAt: '2026-07-30T08:20:00+08:00', requestId: 'req-demo-alert-003' }
];

const RISK_TREND: TrendPoint[] = [
    { occurredAt: '2026-07-30T08:00:00+08:00', value: 4, risk: 'MEDIUM' },
    { occurredAt: '2026-07-30T09:00:00+08:00', value: 9, risk: 'HIGH' },
    { occurredAt: '2026-07-30T10:00:00+08:00', value: 14, risk: 'CRITICAL' },
    { occurredAt: '2026-07-30T11:00:00+08:00', value: 8, risk: 'HIGH' }
];

export interface MockFixtures {
    alerts: AlertRecord[];
    events: EventRecord[];
    controls: ControlRecord[];
    rules: RuleRecord[];
    whitelists: WhitelistRecord[];
    auditRecords: ManagementAuditRecord[];
    riskTrend: TrendPoint[];
}

export function createMockFixtures(): MockFixtures {
    return structuredClone({
        alerts: ALERTS,
        events: EVENTS,
        controls: CONTROLS,
        rules: RULES,
        whitelists: WHITELISTS,
        auditRecords: AUDIT_RECORDS,
        riskTrend: RISK_TREND
    });
}

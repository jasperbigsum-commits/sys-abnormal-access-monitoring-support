import { managementError, type ManagementErrorInput } from '@/domain/errors';
import type {
    AlertQuery,
    AlertRecord,
    AlertStatus,
    AlertTransitionCommand,
    ControlQuery,
    ControlRecord,
    ControlTransitionCommand,
    DashboardSummary,
    EventQuery,
    EventRecord,
    ExecuteControlCommand,
    ManagementAuditQuery,
    ManagementAuditRecord,
    PageQuery,
    PageResult,
    RiskLevel,
    RuleChangeCommand,
    RuleQuery,
    RuleRecord,
    TimeRangeQuery,
    WhitelistQuery,
    WhitelistRecord,
    WhitelistTransitionCommand
} from '@/domain/monitoring';
import type { MonitoringRepository } from '@/repositories/monitoringRepository';

export type MockOperation = keyof MonitoringRepository;

export interface MockMonitoringRepositoryOptions {
    delayMs?: number;
    failures?: Partial<Record<MockOperation, ManagementErrorInput>>;
}

export interface MockMonitoringRepository extends MonitoringRepository {
    reset(): void;
}

const FIXED_NOW = '2026-07-30T11:30:00+08:00';
const MOCK_ACTOR = 'risk-admin-01';

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
    { id: 'WL-20260730-001', subject: 'ops-demo-batch-01', scope: '夜间批处理演示窗口', ruleId: 'RULE-OFFHOURS-EXPORT', status: 'ACTIVE', expiresAt: '2026-07-31T02:00:00+08:00', approvedBy: 'risk-approver-01', reason: '月末演示批处理', version: 1 },
    { id: 'WL-20260730-002', subject: 'ops-demo-maint-02', scope: '系统维护演示窗口', status: 'REVOKED', approvedBy: 'risk-approver-02', reason: '维护工作已结束', version: 2 },
    { id: 'WL-20260730-003', subject: 'branch-demo-training', scope: '培训环境演示访问', ruleId: 'RULE-AUTH-FAILURE', status: 'ACTIVE', expiresAt: '2026-08-02T18:00:00+08:00', approvedBy: 'risk-approver-03', reason: '培训演练', version: 1 }
];

const AUDIT_RECORDS: ManagementAuditRecord[] = [
    { id: 'AUD-20260730-001', actorId: 'risk-admin-01', operation: 'RULE_CHANGE', targetType: 'RULE', targetId: 'RULE-BULK-QUERY', outcome: 'SUCCEEDED', occurredAt: '2026-07-30T08:30:00+08:00', requestId: 'req-demo-rule-001' },
    { id: 'AUD-20260730-002', actorId: 'risk-admin-02', operation: 'CONTROL_REJECT', targetType: 'CONTROL', targetId: 'CTL-20260729-009', outcome: 'DENIED', occurredAt: '2026-07-30T09:05:00+08:00', requestId: 'req-demo-control-002' },
    { id: 'AUD-20260730-003', actorId: 'analyst-01', operation: 'ALERT_INVESTIGATE', targetType: 'ALERT', targetId: 'ALT-20260730-002', outcome: 'SUCCEEDED', occurredAt: '2026-07-30T08:20:00+08:00', requestId: 'req-demo-alert-003' }
];

function clone<T>(value: T): T {
    return structuredClone(value);
}

function includesText(values: Array<string | undefined>, keyword?: string): boolean {
    if (!keyword?.trim()) {
        return true;
    }
    const normalized = keyword.trim().toLocaleLowerCase();
    return values.some((value) => value?.toLocaleLowerCase().includes(normalized));
}

function inTimeRange(value: string, query: TimeRangeQuery): boolean {
    const timestamp = Date.parse(value);
    return (!query.from || timestamp >= Date.parse(query.from)) && (!query.to || timestamp <= Date.parse(query.to));
}

function paginate<T>(items: T[], query: PageQuery): PageResult<T> {
    if (!Number.isInteger(query.page) || query.page < 1 || !Number.isInteger(query.pageSize) || query.pageSize < 1) {
        throw managementError({ category: 'VALIDATION', message: '页码和每页数量必须为正整数', errorCode: 'MOCK-400-PAGE' });
    }
    const start = (query.page - 1) * query.pageSize;
    return { items: clone(items.slice(start, start + query.pageSize)), page: query.page, pageSize: query.pageSize, total: items.length };
}

function requireVersion(current: number, expected: number, targetId: string): void {
    if (current !== expected) {
        throw managementError({
            category: 'CONFLICT',
            message: `记录 ${targetId} 已被其他操作更新，请刷新后重试`,
            errorCode: 'MOCK-409-VERSION',
            details: { expectedVersion: expected, currentVersion: current }
        });
    }
}

function notFound(type: string, id: string): never {
    throw managementError({ category: 'NOT_FOUND', message: `${type} ${id} 不存在`, errorCode: 'MOCK-404' });
}

function invalidTransition(type: string, id: string, status: string, action: string): never {
    throw managementError({
        category: 'INVALID_TRANSITION',
        message: `${type} ${id} 当前状态 ${status} 不允许执行 ${action}`,
        errorCode: 'MOCK-409-STATE',
        details: { status, action }
    });
}

class DeterministicMockMonitoringRepository implements MockMonitoringRepository {
    private alerts: AlertRecord[] = [];
    private controls: ControlRecord[] = [];
    private rules: RuleRecord[] = [];
    private whitelists: WhitelistRecord[] = [];
    private auditRecords: ManagementAuditRecord[] = [];
    private auditSequence = 100;
    private controlSequence = 100;

    constructor(private readonly options: MockMonitoringRepositoryOptions) {
        this.reset();
    }

    reset(): void {
        this.alerts = clone(ALERTS);
        this.controls = clone(CONTROLS);
        this.rules = clone(RULES);
        this.whitelists = clone(WHITELISTS);
        this.auditRecords = clone(AUDIT_RECORDS);
        this.auditSequence = 100;
        this.controlSequence = 100;
    }

    private async prepare(operation: MockOperation): Promise<void> {
        const delayMs = Math.max(0, this.options.delayMs ?? 0);
        if (delayMs > 0) {
            await new Promise((resolve) => setTimeout(resolve, delayMs));
        }
        const failure = this.options.failures?.[operation];
        if (failure) {
            throw managementError(failure);
        }
    }

    private appendAudit(operation: string, targetType: string, targetId: string, actorId = MOCK_ACTOR): void {
        const sequence = this.auditSequence++;
        this.auditRecords.unshift({
            id: `AUD-MOCK-${sequence}`,
            actorId,
            operation,
            targetType,
            targetId,
            outcome: 'SUCCEEDED',
            occurredAt: FIXED_NOW,
            requestId: `req-mock-${sequence}`
        });
    }

    async dashboard(query: TimeRangeQuery = {}): Promise<DashboardSummary> {
        await this.prepare('dashboard');
        const alerts = this.alerts.filter((item) => inTimeRange(item.lastSeenAt, query));
        const events = EVENTS.filter((item) => inTimeRange(item.occurredAt, query));
        const riskDistribution = alerts.reduce<Record<RiskLevel, number>>((result, item) => {
            result[item.risk] += 1;
            return result;
        }, { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 });
        const successfulControls = this.controls.filter((item) => item.status === 'SUCCEEDED').length;
        return clone({
            metrics: {
                openAlerts: alerts.filter((item) => !['CLOSED', 'FALSE_POSITIVE'].includes(item.status)).length,
                eventsToday: events.length,
                highRiskSubjects: new Set(alerts.filter((item) => ['CRITICAL', 'HIGH'].includes(item.risk)).map((item) => item.subject)).size,
                controlSuccessRate: this.controls.length === 0 ? 0 : Math.round((successfulControls / this.controls.length) * 100)
            },
            riskTrend: [
                { occurredAt: '2026-07-30T08:00:00+08:00', value: 4, risk: 'MEDIUM' },
                { occurredAt: '2026-07-30T09:00:00+08:00', value: 9, risk: 'HIGH' },
                { occurredAt: '2026-07-30T10:00:00+08:00', value: 14, risk: 'CRITICAL' },
                { occurredAt: '2026-07-30T11:00:00+08:00', value: 8, risk: 'HIGH' }
            ],
            riskDistribution,
            ruleContribution: this.rules.map((rule) => ({ ruleId: rule.id, count: alerts.filter((alert) => alert.ruleId === rule.id).length })).sort((a, b) => b.count - a.count),
            priorityAlertIds: alerts.filter((item) => ['CRITICAL', 'HIGH'].includes(item.risk) && !['CLOSED', 'FALSE_POSITIVE'].includes(item.status)).map((item) => item.id)
        });
    }

    async searchAlerts(query: AlertQuery): Promise<PageResult<AlertRecord>> {
        await this.prepare('searchAlerts');
        const items = this.alerts.filter((item) =>
            includesText([item.id, item.title, item.ruleId, item.subject, item.assigneeId], query.keyword)
            && (!query.risks?.length || query.risks.includes(item.risk))
            && (!query.statuses?.length || query.statuses.includes(item.status))
            && (!query.assigneeId || item.assigneeId === query.assigneeId)
            && inTimeRange(item.lastSeenAt, query)
        );
        return paginate(items, query);
    }

    async getAlert(id: string): Promise<AlertRecord> {
        await this.prepare('getAlert');
        const item = this.alerts.find((record) => record.id === id);
        return item ? clone(item) : notFound('告警', id);
    }

    async transitionAlert(command: AlertTransitionCommand): Promise<AlertRecord> {
        await this.prepare('transitionAlert');
        const item = this.alerts.find((record) => record.id === command.id);
        if (!item) notFound('告警', command.id);
        requireVersion(item.version, command.expectedVersion, item.id);
        const terminal = ['CLOSED', 'FALSE_POSITIVE'].includes(item.status);
        let nextStatus: AlertStatus = item.status;
        if (command.action === 'ASSIGN') {
            if (terminal || !command.assigneeId) invalidTransition('告警', item.id, item.status, command.action);
            item.assigneeId = command.assigneeId;
            item.assignments.push({ id: `ASN-MOCK-${item.assignments.length + 1}`, operatorId: MOCK_ACTOR, assigneeId: command.assigneeId, reason: command.reason, createdAt: FIXED_NOW });
        } else {
            const transitions: Partial<Record<AlertStatus, Partial<Record<AlertTransitionCommand['action'], AlertStatus>>>> = {
                NEW: { ACKNOWLEDGE: 'ACKNOWLEDGED', FALSE_POSITIVE: 'FALSE_POSITIVE' },
                ACKNOWLEDGED: { INVESTIGATE: 'IN_PROGRESS', FALSE_POSITIVE: 'FALSE_POSITIVE' },
                IN_PROGRESS: { CLOSE: 'CLOSED', FALSE_POSITIVE: 'FALSE_POSITIVE' }
            };
            const target = transitions[item.status]?.[command.action];
            if (!target) invalidTransition('告警', item.id, item.status, command.action);
            nextStatus = target;
        }
        item.status = nextStatus;
        item.version += 1;
        item.timeline.push({ id: `TL-MOCK-${item.timeline.length + 1}`, action: command.action, operatorId: MOCK_ACTOR, reason: command.reason, occurredAt: FIXED_NOW });
        this.appendAudit(`ALERT_${command.action}`, 'ALERT', item.id);
        return clone(item);
    }

    async searchEvents(query: EventQuery): Promise<PageResult<EventRecord>> {
        await this.prepare('searchEvents');
        const items = EVENTS.filter((item) =>
            includesText([item.id, item.actionCode, item.subject, item.resourceId, item.result], query.keyword)
            && (!query.actionCode || item.actionCode === query.actionCode)
            && (!query.result || item.result === query.result)
            && inTimeRange(item.occurredAt, query)
        );
        return paginate(items, query);
    }

    async getEvent(id: string): Promise<EventRecord> {
        await this.prepare('getEvent');
        const item = EVENTS.find((record) => record.id === id);
        return item ? clone(item) : notFound('事件', id);
    }

    async searchControls(query: ControlQuery): Promise<PageResult<ControlRecord>> {
        await this.prepare('searchControls');
        const items = this.controls.filter((item) => {
            const occurredAt = item.attempts.at(-1)?.occurredAt ?? item.expiresAt ?? FIXED_NOW;
            return includesText([item.id, item.alertId, item.ruleId, item.subject, item.action, item.status], query.keyword)
                && (!query.statuses?.length || query.statuses.includes(item.status))
                && (!query.actions?.length || query.actions.includes(item.action))
                && inTimeRange(occurredAt, query);
        });
        return paginate(items, query);
    }

    async transitionControl(command: ControlTransitionCommand): Promise<ControlRecord> {
        await this.prepare('transitionControl');
        const item = this.controls.find((record) => record.id === command.id);
        if (!item) notFound('控制任务', command.id);
        requireVersion(item.version, command.expectedVersion, item.id);
        if (command.action === 'RETRY') {
            if (item.status !== 'FAILED') invalidTransition('控制任务', item.id, item.status, command.action);
            item.status = 'SUCCEEDED';
            item.failureReason = undefined;
            item.attempts.push({ attempt: item.attempts.length + 1, status: 'SUCCEEDED', occurredAt: FIXED_NOW });
        } else {
            if (item.status !== 'PENDING_APPROVAL') invalidTransition('控制任务', item.id, item.status, command.action);
            item.status = command.action === 'APPROVE' ? 'APPROVED' : 'REJECTED';
            item.attempts.push({ attempt: item.attempts.length + 1, status: item.status, occurredAt: FIXED_NOW });
        }
        item.version += 1;
        this.appendAudit(`CONTROL_${command.action}`, 'CONTROL', item.id);
        return clone(item);
    }

    async executeControl(command: ExecuteControlCommand): Promise<ControlRecord> {
        await this.prepare('executeControl');
        const sequence = this.controlSequence++;
        const item: ControlRecord = {
            id: `CTL-MOCK-${sequence}`,
            alertId: '',
            ruleId: 'MANUAL',
            subject: command.subject,
            action: command.action,
            status: 'SUCCEEDED',
            expiresAt: new Date(Date.parse(FIXED_NOW) + command.ttlMinutes * 60_000).toISOString(),
            version: 1,
            attempts: [{ attempt: 1, status: 'SUCCEEDED', occurredAt: FIXED_NOW }]
        };
        this.controls.unshift(item);
        this.appendAudit('CONTROL_EXECUTE', 'CONTROL', item.id);
        return clone(item);
    }

    async searchRules(query: RuleQuery): Promise<PageResult<RuleRecord>> {
        await this.prepare('searchRules');
        const items = this.rules.filter((item) =>
            includesText([item.id, item.name, item.createdBy, item.approvedBy], query.keyword)
            && (!query.modes?.length || query.modes.includes(item.mode))
            && (!query.risks?.length || query.risks.includes(item.risk))
        );
        return paginate(items, query);
    }

    async changeRule(command: RuleChangeCommand): Promise<RuleRecord> {
        await this.prepare('changeRule');
        const item = this.rules.find((record) => record.id === command.id);
        if (!item) notFound('规则', command.id);
        requireVersion(item.version, command.expectedVersion, item.id);
        if (!Number.isFinite(command.threshold) || command.threshold <= 0) {
            throw managementError({ category: 'VALIDATION', message: '规则阈值必须大于零', errorCode: 'MOCK-400-THRESHOLD' });
        }
        item.mode = command.mode;
        item.threshold = command.threshold;
        item.enabled = command.mode !== 'DISABLED';
        item.approvedBy = command.approverId;
        item.changeReason = command.reason;
        item.version += 1;
        this.appendAudit('RULE_CHANGE', 'RULE', item.id, command.approverId);
        return clone(item);
    }

    async searchWhitelists(query: WhitelistQuery): Promise<PageResult<WhitelistRecord>> {
        await this.prepare('searchWhitelists');
        const items = this.whitelists.filter((item) =>
            includesText([item.id, item.subject, item.scope, item.ruleId, item.approvedBy, item.reason], query.keyword)
            && (!query.statuses?.length || query.statuses.includes(item.status))
        );
        return paginate(items, query);
    }

    async transitionWhitelist(command: WhitelistTransitionCommand): Promise<WhitelistRecord> {
        await this.prepare('transitionWhitelist');
        const item = this.whitelists.find((record) => record.id === command.id);
        if (!item) notFound('白名单', command.id);
        requireVersion(item.version, command.expectedVersion, item.id);
        const targetStatus = command.action === 'GRANT' ? 'ACTIVE' : 'REVOKED';
        if (item.status === targetStatus) invalidTransition('白名单', item.id, item.status, command.action);
        item.status = targetStatus;
        item.reason = command.reason;
        item.version += 1;
        this.appendAudit(`WHITELIST_${command.action}`, 'WHITELIST', item.id);
        return clone(item);
    }

    async searchManagementAudit(query: ManagementAuditQuery): Promise<PageResult<ManagementAuditRecord>> {
        await this.prepare('searchManagementAudit');
        const items = this.auditRecords.filter((item) =>
            includesText([item.id, item.actorId, item.operation, item.targetType, item.targetId, item.requestId], query.keyword)
            && (!query.outcomes?.length || query.outcomes.includes(item.outcome))
            && (!query.operations?.length || query.operations.includes(item.operation))
            && (!query.actorId || item.actorId === query.actorId)
            && inTimeRange(item.occurredAt, query)
        );
        return paginate(items, query);
    }
}

export function createMockMonitoringRepository(options: MockMonitoringRepositoryOptions = {}): MockMonitoringRepository {
    return new DeterministicMockMonitoringRepository(options);
}

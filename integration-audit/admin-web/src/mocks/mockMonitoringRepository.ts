import { managementError } from '@/domain/errors';
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
import { createMockFixtures, FIXED_NOW, MOCK_ACTOR } from '@/mocks/fixtures';
import { MockScenario, type MockMonitoringRepositoryOptions, type MockOperation } from '@/mocks/scenario';
import type { MonitoringRepository } from '@/repositories/monitoringRepository';

export interface MockMonitoringRepository extends MonitoringRepository {
    reset(): void;
}

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

interface IdempotencyEntry<T> {
    signature: string;
    result: T;
}

function readIdempotent<T>(entries: Map<string, IdempotencyEntry<T>>, key: string, payload: object): T | undefined {
    const entry = entries.get(key);
    if (!entry) return undefined;
    if (entry.signature !== JSON.stringify(payload)) {
        throw managementError({
            category: 'CONFLICT',
            message: `幂等键 ${key} 已用于其他请求载荷`,
            errorCode: 'MOCK-409-IDEMPOTENCY'
        });
    }
    return clone(entry.result);
}

function writeIdempotent<T>(entries: Map<string, IdempotencyEntry<T>>, key: string, payload: object, result: T): void {
    entries.set(key, { signature: JSON.stringify(payload), result: clone(result) });
}

class DeterministicMockMonitoringRepository implements MockMonitoringRepository {
    private alerts: AlertRecord[] = [];
    private events: EventRecord[] = [];
    private controls: ControlRecord[] = [];
    private rules: RuleRecord[] = [];
    private whitelists: WhitelistRecord[] = [];
    private auditRecords: ManagementAuditRecord[] = [];
    private riskTrend: DashboardSummary['riskTrend'] = [];
    private auditSequence = 100;
    private controlSequence = 100;
    private alertIdempotency = new Map<string, IdempotencyEntry<AlertRecord>>();
    private controlIdempotency = new Map<string, IdempotencyEntry<ControlRecord>>();
    private ruleIdempotency = new Map<string, IdempotencyEntry<RuleRecord>>();

    private readonly scenario: MockScenario;

    constructor(options: MockMonitoringRepositoryOptions) {
        this.scenario = new MockScenario(options);
        this.reset();
    }

    reset(): void {
        const fixtures = createMockFixtures();
        this.alerts = fixtures.alerts;
        this.events = fixtures.events;
        this.controls = fixtures.controls;
        this.rules = fixtures.rules;
        this.whitelists = fixtures.whitelists;
        this.auditRecords = fixtures.auditRecords;
        this.riskTrend = fixtures.riskTrend;
        this.auditSequence = 100;
        this.controlSequence = 100;
        this.alertIdempotency.clear();
        this.controlIdempotency.clear();
        this.ruleIdempotency.clear();
    }

    private async prepare(operation: MockOperation): Promise<void> {
        await this.scenario.prepare(operation);
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
        const events = this.events.filter((item) => inTimeRange(item.occurredAt, query));
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
            riskTrend: this.riskTrend.filter((item) => inTimeRange(item.occurredAt, query)),
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
        const cached = command.idempotencyKey ? readIdempotent(this.alertIdempotency, command.idempotencyKey, command) : undefined;
        if (cached) return cached;
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
                NEW: { ACKNOWLEDGE: 'ACKNOWLEDGED', INVESTIGATE: 'IN_PROGRESS', FALSE_POSITIVE: 'FALSE_POSITIVE' },
                ACKNOWLEDGED: { INVESTIGATE: 'IN_PROGRESS', CLOSE: 'CLOSED', FALSE_POSITIVE: 'FALSE_POSITIVE' },
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
        if (command.idempotencyKey) writeIdempotent(this.alertIdempotency, command.idempotencyKey, command, item);
        return clone(item);
    }

    async searchEvents(query: EventQuery): Promise<PageResult<EventRecord>> {
        await this.prepare('searchEvents');
        const items = this.events.filter((item) =>
            includesText([item.id, item.actionCode, item.subject, item.resourceId, item.result], query.keyword)
            && (!query.actionCode || item.actionCode === query.actionCode)
            && (!query.result || item.result === query.result)
            && inTimeRange(item.occurredAt, query)
        );
        return paginate(items, query);
    }

    async getEvent(id: string): Promise<EventRecord> {
        await this.prepare('getEvent');
        const item = this.events.find((record) => record.id === id);
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
        const cached = readIdempotent(this.controlIdempotency, command.idempotencyKey, command);
        if (cached) return cached;
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
        writeIdempotent(this.controlIdempotency, command.idempotencyKey, command, item);
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
        const cached = readIdempotent(this.ruleIdempotency, command.idempotencyKey, command);
        if (cached) return cached;
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
        this.appendAudit('RULE_CHANGE', 'RULE', item.id);
        writeIdempotent(this.ruleIdempotency, command.idempotencyKey, command, item);
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

export interface PageQuery {
    page: number;
    pageSize: number;
    keyword?: string;
}

export interface PageResult<T> {
    items: T[];
    page: number;
    pageSize: number;
    total: number;
}

export interface TimeRangeQuery {
    from?: string;
    to?: string;
}

export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type AlertStatus = 'NEW' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'CLOSED' | 'FALSE_POSITIVE';
export type RuleMode = 'OBSERVE' | 'ALERT_ONLY' | 'ENFORCE' | 'DISABLED';
export type AuditOutcome = 'SUCCEEDED' | 'DENIED' | 'FAILED';

export interface DashboardMetric {
    label: string;
    value: number;
    trend?: number;
}

export interface TrendPoint {
    occurredAt: string;
    value: number;
    risk?: RiskLevel;
}

export interface DashboardSummary {
    metrics: {
        openAlerts: number;
        eventsToday: number;
        highRiskSubjects: number;
        controlSuccessRate: number;
    };
    riskTrend: TrendPoint[];
    riskDistribution: Record<RiskLevel, number>;
    ruleContribution: { ruleId: string; count: number }[];
    priorityAlertIds: string[];
}

export interface AlertAssignment {
    id: string;
    operatorId: string;
    assigneeId: string;
    reason: string;
    createdAt: string;
}

export interface AlertTimelineEntry {
    id: string;
    action: string;
    operatorId: string;
    reason: string;
    occurredAt: string;
}

export interface AlertRecord {
    id: string;
    title: string;
    ruleId: string;
    risk: RiskLevel;
    subject: string;
    status: AlertStatus;
    assigneeId?: string;
    version: number;
    firstSeenAt: string;
    lastSeenAt: string;
    occurrenceCount: number;
    evidence: Record<string, string | number | boolean>;
    eventIds: string[];
    assignments: AlertAssignment[];
    timeline: AlertTimelineEntry[];
}

export interface AlertQuery extends PageQuery, TimeRangeQuery {
    risks?: RiskLevel[];
    statuses?: AlertStatus[];
    assigneeId?: string;
}

export type AlertAction = 'ASSIGN' | 'ACKNOWLEDGE' | 'INVESTIGATE' | 'CLOSE' | 'FALSE_POSITIVE';

export interface AlertTransitionCommand {
    id: string;
    action: AlertAction;
    expectedVersion: number;
    reason: string;
    assigneeId?: string;
    idempotencyKey?: string;
}

export interface EventRecord {
    id: string;
    actionCode: string;
    subject: string;
    resourceId: string;
    result: string;
    occurredAt: string;
    facts: Record<string, string | number | boolean>;
    alertIds: string[];
}

export interface EventQuery extends PageQuery, TimeRangeQuery {
    actionCode?: string;
    result?: string;
}

export interface ControlAttempt {
    attempt: number;
    status: string;
    occurredAt: string;
    failureReason?: string;
}

export interface ControlRecord {
    id: string;
    alertId: string;
    ruleId: string;
    subject: string;
    action: string;
    status: string;
    expiresAt?: string;
    version: number;
    attempts: ControlAttempt[];
    failureReason?: string;
}

export interface ControlQuery extends PageQuery, TimeRangeQuery {
    statuses?: string[];
    actions?: string[];
}

export type ControlAction = 'APPROVE' | 'REJECT' | 'RETRY';

export interface ControlTransitionCommand {
    id: string;
    action: ControlAction;
    expectedVersion: number;
    reason: string;
    passExpiresAt?: string;
}

export interface ExecuteControlCommand {
    subject: string;
    action: string;
    ttlMinutes: number;
    reason: string;
    idempotencyKey: string;
}

export interface RuleRecord {
    id: string;
    name: string;
    risk: RiskLevel;
    mode: RuleMode;
    threshold: number;
    enabled: boolean;
    version: number;
    createdAt: string;
    createdBy: string;
    approvedBy?: string;
    changeReason?: string;
}

export interface RuleQuery extends PageQuery {
    modes?: RuleMode[];
    risks?: RiskLevel[];
}

export interface RuleChangeCommand {
    id: string;
    mode: RuleMode;
    threshold: number;
    expectedVersion: number;
    reason: string;
    approverId: string;
    idempotencyKey: string;
}

export interface WhitelistRecord {
    id: string;
    subject: string;
    systemScope: string;
    ruleId?: string;
    status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
    expiresAt?: string;
    approvedBy?: string;
    reason?: string;
    version: number;
}

export interface WhitelistQuery extends PageQuery {
    statuses?: WhitelistRecord['status'][];
}

export interface WhitelistTransitionCommand {
    id: string;
    action: 'GRANT' | 'REVOKE';
    expectedVersion: number;
    reason: string;
}

export interface ManagementAuditRecord {
    id: string;
    actorId: string;
    operation: string;
    targetType: string;
    targetId: string;
    outcome: AuditOutcome;
    occurredAt: string;
    requestId: string;
}

export interface ManagementAuditQuery extends PageQuery, TimeRangeQuery {
    outcomes?: AuditOutcome[];
    operations?: string[];
    actorId?: string;
}

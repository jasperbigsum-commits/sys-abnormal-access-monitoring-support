import type {
    AlertQuery,
    AlertRecord,
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
    PageResult,
    RuleChangeCommand,
    RuleQuery,
    RuleRecord,
    TimeRangeQuery,
    WhitelistQuery,
    WhitelistRecord,
    WhitelistTransitionCommand
} from '@/domain/monitoring';

export interface MonitoringRepository {
    dashboard(query?: TimeRangeQuery): Promise<DashboardSummary>;
    searchAlerts(query: AlertQuery): Promise<PageResult<AlertRecord>>;
    getAlert(id: string): Promise<AlertRecord>;
    transitionAlert(command: AlertTransitionCommand): Promise<AlertRecord>;
    searchEvents(query: EventQuery): Promise<PageResult<EventRecord>>;
    getEvent(id: string): Promise<EventRecord>;
    searchControls(query: ControlQuery): Promise<PageResult<ControlRecord>>;
    transitionControl(command: ControlTransitionCommand): Promise<ControlRecord>;
    executeControl(command: ExecuteControlCommand): Promise<ControlRecord>;
    searchRules(query: RuleQuery): Promise<PageResult<RuleRecord>>;
    changeRule(command: RuleChangeCommand): Promise<RuleRecord>;
    searchWhitelists(query: WhitelistQuery): Promise<PageResult<WhitelistRecord>>;
    transitionWhitelist(command: WhitelistTransitionCommand): Promise<WhitelistRecord>;
    searchManagementAudit(query: ManagementAuditQuery): Promise<PageResult<ManagementAuditRecord>>;
}

export const monitoringRepositoryKey: InjectionKey<MonitoringRepository> = Symbol('MonitoringRepository');

export function useMonitoringRepository(): MonitoringRepository {
    const repository = inject(monitoringRepositoryKey);
    if (!repository) throw new Error('MonitoringRepository is not provided');
    return repository;
}
import { inject, type InjectionKey } from 'vue';

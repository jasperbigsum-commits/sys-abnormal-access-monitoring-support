import { managementApi, type ManagementPage } from '@/api/management';
import type {
    AlertQuery, AlertRecord, AlertTransitionCommand, ControlQuery, ControlRecord, ControlTransitionCommand,
    DashboardSummary, EventQuery, EventRecord, ExecuteControlCommand, ManagementAuditQuery, ManagementAuditRecord,
    PageQuery, PageResult, RuleChangeCommand, RuleQuery, RuleRecord, TimeRangeQuery, WhitelistQuery,
    WhitelistRecord, WhitelistTransitionCommand
} from '@/domain/monitoring';
import type { MonitoringRepository } from './monitoringRepository';

function compact(values: Readonly<Record<string, unknown>>): Record<string, unknown> {
    return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== undefined && value !== '' && (!Array.isArray(value) || value.length > 0)));
}

function queryParams(query: PageQuery & Record<string, unknown>): Record<string, unknown> {
    const { page, pageSize, ...filters } = query;
    const backendNames: Readonly<Record<string, string>> = {
        statuses: 'status',
        risks: 'risk',
        actions: 'action',
        modes: 'mode',
        outcomes: 'outcome',
        operations: 'operation'
    };
    return compact({
        page: Math.max(0, page - 1),
        size: pageSize,
        ...Object.fromEntries(Object.entries(filters).map(([key, value]) => [backendNames[key] ?? key, Array.isArray(value) ? value.join(',') : value]))
    });
}

function pageResult<T>(page: ManagementPage<T>): PageResult<T> {
    return { items: page.items, page: page.page + 1, pageSize: page.size, total: page.totalElements };
}

function actionPath(action: string): string {
    return action.toLocaleLowerCase().replaceAll('_', '-');
}

function commandBody(command: object, omitted: string[] = []): Record<string, unknown> {
    return Object.fromEntries(Object.entries(command).filter(([key]) => key !== 'id' && key !== 'action' && !omitted.includes(key)));
}

export function createHttpMonitoringRepository(): MonitoringRepository {
    return {
        dashboard: (query: TimeRangeQuery = {}) => managementApi.dashboard<DashboardSummary>(compact(query as Record<string, unknown>)),
        async searchAlerts(query: AlertQuery) {
            return pageResult(await managementApi.alerts<AlertRecord>(queryParams(query as AlertQuery & Record<string, unknown>)));
        },
        getAlert: (id: string) => managementApi.alert<AlertRecord>(id),
        transitionAlert: (command: AlertTransitionCommand) => managementApi.alertTransition<AlertRecord>(command.id, actionPath(command.action), commandBody(command)),
        async searchEvents(query: EventQuery) {
            return pageResult(await managementApi.events<EventRecord>(queryParams(query as EventQuery & Record<string, unknown>)));
        },
        getEvent: (id: string) => managementApi.event<EventRecord>(id),
        async searchControls(query: ControlQuery) {
            return pageResult(await managementApi.controls<ControlRecord>(queryParams(query as ControlQuery & Record<string, unknown>)));
        },
        transitionControl: (command: ControlTransitionCommand) => managementApi.controlTransition<ControlRecord>(command.id, actionPath(command.action), commandBody(command)),
        executeControl: (command: ExecuteControlCommand) => managementApi.executeControl<ControlRecord>(command),
        async searchRules(query: RuleQuery) {
            return pageResult(await managementApi.rules<RuleRecord>(queryParams(query as RuleQuery & Record<string, unknown>)));
        },
        changeRule: (command: RuleChangeCommand) => managementApi.changeRule<RuleRecord>(command.id, commandBody(command, ['approverId'])),
        async searchWhitelists(query: WhitelistQuery) {
            return pageResult(await managementApi.whitelists<WhitelistRecord>(queryParams(query as WhitelistQuery & Record<string, unknown>)));
        },
        transitionWhitelist: (command: WhitelistTransitionCommand) => managementApi.whitelistTransition<WhitelistRecord>(command.id, actionPath(command.action), commandBody(command)),
        async searchManagementAudit(query: ManagementAuditQuery) {
            return pageResult(await managementApi.managementAudit<ManagementAuditRecord>(queryParams(query as ManagementAuditQuery & Record<string, unknown>)));
        }
    };
}

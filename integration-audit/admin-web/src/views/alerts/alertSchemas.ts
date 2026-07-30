import type { TableColumnType } from 'ant-design-vue';

import type { FormSchema } from '@/components/jeecg/useForm';
import type { AlertAction, AlertStatus, RiskLevel } from '@/domain/monitoring';

export const riskOptions = [
    { value: 'CRITICAL', label: '严重', color: '#b72f2d' },
    { value: 'HIGH', label: '高危', color: '#d15a3c' },
    { value: 'MEDIUM', label: '中危', color: '#d4942f' },
    { value: 'LOW', label: '低危', color: '#27887e' }
];

export const alertStatusOptions = [
    { value: 'NEW', label: '待确认', color: 'error' },
    { value: 'ACKNOWLEDGED', label: '已确认', color: 'warning' },
    { value: 'IN_PROGRESS', label: '研判中', color: 'processing' },
    { value: 'CLOSED', label: '已关闭', color: 'success' },
    { value: 'FALSE_POSITIVE', label: '误报', color: 'default' }
];

export const alertSearchSchemas: FormSchema[] = [
    { field: 'keyword', label: '关键词', component: 'Input', componentProps: { allowClear: true, placeholder: '告警编号 / 标题 / 主体' } },
    { field: 'risks', label: '风险等级', component: 'Select', options: riskOptions, componentProps: { mode: 'multiple', allowClear: true, placeholder: '全部等级', 'data-testid': 'risk-filter' } },
    { field: 'statuses', label: '告警状态', component: 'Select', options: alertStatusOptions, componentProps: { mode: 'multiple', allowClear: true, placeholder: '全部状态' } }
];

export const alertColumns: TableColumnType[] = [
    { title: '告警编号', dataIndex: 'id', key: 'id', width: 190 },
    { title: '告警名称', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: '风险', dataIndex: 'risk', key: 'risk', width: 90 },
    { title: '访问主体', dataIndex: 'subject', key: 'subject', width: 180 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
    { title: '负责人', dataIndex: 'assigneeId', key: 'assigneeId', width: 130 },
    { title: '最近发生', dataIndex: 'lastSeenAt', key: 'lastSeenAt', width: 190 },
    { title: '次数', dataIndex: 'occurrenceCount', key: 'occurrenceCount', width: 76, align: 'right' },
    { title: '操作', key: 'action', fixed: 'right', width: 90 }
];

export interface AlertActionOption {
    action: AlertAction;
    label: string;
    danger?: boolean;
}

const assign: AlertActionOption = { action: 'ASSIGN', label: '分派' };
const actionsByStatus: Record<AlertStatus, AlertActionOption[]> = {
    NEW: [assign, { action: 'ACKNOWLEDGE', label: '确认' }, { action: 'INVESTIGATE', label: '研判' }, { action: 'FALSE_POSITIVE', label: '标记误报' }],
    ACKNOWLEDGED: [assign, { action: 'INVESTIGATE', label: '研判' }, { action: 'CLOSE', label: '关闭' }, { action: 'FALSE_POSITIVE', label: '标记误报' }],
    IN_PROGRESS: [assign, { action: 'CLOSE', label: '关闭' }, { action: 'FALSE_POSITIVE', label: '标记误报' }],
    CLOSED: [],
    FALSE_POSITIVE: []
};

export function availableAlertActions(status: AlertStatus): AlertActionOption[] {
    return actionsByStatus[status];
}

export function asRiskLevels(values: string[]): RiskLevel[] {
    return values as RiskLevel[];
}

import type { TableColumnType } from 'ant-design-vue';
import type { FormSchema } from '@/components/jeecg/useForm';
import type { ControlAction } from '@/domain/monitoring';

export const controlStatusOptions = [
    { value: 'PENDING_APPROVAL', label: '待审批', color: 'warning' },
    { value: 'APPROVED', label: '已批准', color: 'processing' },
    { value: 'REJECTED', label: '已驳回', color: 'default' },
    { value: 'SUCCEEDED', label: '执行成功', color: 'success' },
    { value: 'FAILED', label: '执行失败', color: 'error' }
];

export const controlSearchSchemas: FormSchema[] = [
    { field: 'keyword', label: '关键词', component: 'Input', componentProps: { allowClear: true, placeholder: '任务 / 主体 / 告警 / 规则' } },
    { field: 'statuses', label: '任务状态', component: 'Select', options: controlStatusOptions, componentProps: { mode: 'multiple', allowClear: true, placeholder: '全部状态' } },
    { field: 'actions', label: '控制动作', component: 'Select', options: [{ label: '锁定账户', value: 'LOCK_ACCOUNT' }, { label: '撤销会话', value: 'REVOKE_SESSION' }, { label: '增强认证', value: 'STEP_UP_AUTH' }], componentProps: { mode: 'multiple', allowClear: true, placeholder: '全部动作' } }
];

export const controlColumns: TableColumnType[] = [
    { title: '任务编号', dataIndex: 'id', key: 'id', width: 180 },
    { title: '控制动作', dataIndex: 'action', key: 'controlAction', width: 150 },
    { title: '目标主体', dataIndex: 'subject', key: 'subject', width: 170 },
    { title: '来源告警', dataIndex: 'alertId', key: 'alertId', width: 180 },
    { title: '规则', dataIndex: 'ruleId', key: 'ruleId', width: 180 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 120 },
    { title: 'TTL', dataIndex: 'expiresAt', key: 'expiresAt', width: 190 },
    { title: '安全失败原因', dataIndex: 'failureReason', key: 'failureReason', ellipsis: true },
    { title: '操作', key: 'action', fixed: 'right', width: 90 }
];

export interface ControlActionOption { action: ControlAction; label: string; danger?: boolean }

export function availableControlActions(status: string): ControlActionOption[] {
    if (status === 'PENDING_APPROVAL') return [{ action: 'APPROVE', label: '批准' }, { action: 'REJECT', label: '驳回', danger: true }];
    if (status === 'FAILED') return [{ action: 'RETRY', label: '重试' }];
    return [];
}

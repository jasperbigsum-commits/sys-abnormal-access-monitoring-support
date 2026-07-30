import type { TableColumnType } from 'ant-design-vue';
import type { FormSchema } from '@/components/jeecg/useForm';

export const ruleModeOptions = [
    { value: 'OBSERVE', label: '观察', color: 'default' },
    { value: 'ALERT_ONLY', label: '仅告警', color: 'warning' },
    { value: 'ENFORCE', label: '强制处置', color: 'error' },
    { value: 'DISABLED', label: '已停用', color: 'default' },
];
export const whitelistStatusOptions = [
    { value: 'ACTIVE', label: '生效', color: 'success' },
    { value: 'REVOKED', label: '已撤销', color: 'default' },
];
export const ruleSearchSchemas: FormSchema[] = [
    {
        field: 'keyword',
        label: '关键词',
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '规则编号 / 名称' },
    },
    {
        field: 'modes',
        label: '运行模式',
        component: 'Select',
        options: ruleModeOptions,
        componentProps: { mode: 'multiple', allowClear: true },
    },
    {
        field: 'risks',
        label: '风险等级',
        component: 'Select',
        options: [
            { label: '严重', value: 'CRITICAL' },
            { label: '高危', value: 'HIGH' },
            { label: '中危', value: 'MEDIUM' },
            { label: '低危', value: 'LOW' },
        ],
        componentProps: { mode: 'multiple', allowClear: true },
    },
];
export const whitelistSearchSchemas: FormSchema[] = [
    {
        field: 'keyword',
        label: '关键词',
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '主体 / 范围 / 规则' },
        colSpan: 12,
    },
    {
        field: 'statuses',
        label: '状态',
        component: 'Select',
        options: whitelistStatusOptions,
        componentProps: { mode: 'multiple', allowClear: true },
        colSpan: 6,
    },
];
export const ruleColumns: TableColumnType[] = [
    { title: '规则编号', dataIndex: 'id', key: 'id', width: 190 },
    { title: '规则名称', dataIndex: 'name', key: 'name', ellipsis: true },
    { title: '风险', dataIndex: 'risk', key: 'risk', width: 90 },
    { title: '模式', dataIndex: 'mode', key: 'mode', width: 110 },
    { title: '阈值', dataIndex: 'threshold', key: 'threshold', width: 90, align: 'right' },
    { title: '持久化版本', dataIndex: 'version', key: 'version', width: 110 },
    { title: '创建人', dataIndex: 'createdBy', key: 'createdBy', width: 130 },
    { title: '审批人', dataIndex: 'approvedBy', key: 'approvedBy', width: 130 },
    { title: '操作', key: 'action', width: 100 },
];
export const whitelistColumns: TableColumnType[] = [
    { title: '记录编号', dataIndex: 'id', key: 'id', width: 180 },
    { title: '主体', dataIndex: 'subject', key: 'subject', width: 190 },
    { title: '适用范围', dataIndex: 'scope', key: 'scope', ellipsis: true },
    { title: '规则', dataIndex: 'ruleId', key: 'ruleId', width: 190 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
    { title: '到期时间', dataIndex: 'expiresAt', key: 'expiresAt', width: 190 },
    { title: '审批人', dataIndex: 'approvedBy', key: 'approvedBy', width: 140 },
    { title: '操作', key: 'action', width: 100 },
];

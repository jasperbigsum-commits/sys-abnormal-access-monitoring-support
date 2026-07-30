import type { TableColumnType } from 'ant-design-vue';

export const eventColumns: TableColumnType[] = [
    { title: '事件编号', dataIndex: 'id', key: 'id', width: 180 },
    { title: '动作类型', dataIndex: 'actionCode', key: 'actionCode', width: 170 },
    { title: '访问主体', dataIndex: 'subject', key: 'subject', width: 180 },
    { title: '资源', dataIndex: 'resourceId', key: 'resourceId', ellipsis: true },
    { title: '结果', dataIndex: 'result', key: 'result', width: 110 },
    { title: '发生时间', dataIndex: 'occurredAt', key: 'occurredAt', width: 190 },
    { title: '操作', key: 'action', fixed: 'right', width: 90 }
];

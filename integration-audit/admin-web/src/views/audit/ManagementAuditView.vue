<script setup lang="ts">
import { Button as AButton, Space as ASpace } from 'ant-design-vue';
import type { TableColumnType } from 'ant-design-vue';

import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicTable from '@/components/jeecg/BasicTable.vue';
import DictTag from '@/components/jeecg/DictTag.vue';
import type { FormSchema } from '@/components/jeecg/useForm';
import { useTable } from '@/components/jeecg/useTable';
import type { ManagementAuditQuery, ManagementAuditRecord } from '@/domain/monitoring';
import { useMonitoringRepository } from '@/repositories/monitoringRepository';

const repository = useMonitoringRepository();
interface AuditTableQuery extends Record<string, unknown> {
    keyword: string;
    actorId: string;
    outcomes: string[];
}
const outcomeOptions = [{ value: 'SUCCEEDED', label: '成功', color: 'success' }, { value: 'DENIED', label: '拒绝', color: 'warning' }, { value: 'FAILED', label: '失败', color: 'error' }];
const searchSchemas: FormSchema[] = [
    { field: 'keyword', label: '关键词', component: 'Input', componentProps: { allowClear: true, placeholder: '资源 / 请求编号' } },
    { field: 'actorId', label: '操作人', component: 'Input', componentProps: { allowClear: true, placeholder: '操作人编号' } },
    { field: 'outcomes', label: '操作结果', component: 'Select', options: outcomeOptions.map(({ value, label }) => ({ value, label })), componentProps: { mode: 'multiple', allowClear: true, placeholder: '全部结果' } }
];
const columns: TableColumnType[] = [
    { title: '操作人', dataIndex: 'actorId', key: 'actorId', width: 150 },
    { title: '操作类型', dataIndex: 'operation', key: 'operation', width: 190 },
    { title: '资源类型', dataIndex: 'targetType', key: 'targetType', width: 130 },
    { title: '资源编号', dataIndex: 'targetId', key: 'targetId', width: 190 },
    { title: '结果', dataIndex: 'outcome', key: 'outcome', width: 100 },
    { title: '时间', dataIndex: 'occurredAt', key: 'occurredAt', width: 190 },
    { title: '请求编号', dataIndex: 'requestId', key: 'requestId', ellipsis: true }
];
const table = useTable<ManagementAuditRecord, AuditTableQuery>({
    api: (query) => repository.searchManagementAudit(query as unknown as ManagementAuditQuery),
    query: { keyword: '', actorId: '', outcomes: [] }
});
</script>

<template>
    <section class="page-section list-view">
        <header class="page-heading"><div><h1>管理审计</h1><p>管理操作不可变更，仅提供筛选、核验与追踪</p></div><span class="immutable-badge">只读审计账本</span></header>
        <BasicForm class="query-form" :schemas="searchSchemas" :model-value="table.query" :columns="4" :label-width="72" @update:model-value="Object.assign(table.query, $event)">
            <template #action><ASpace><AButton type="primary" @click="table.reload">查询</AButton><AButton @click="table.reset">重置</AButton></ASpace></template>
        </BasicForm>
        <div class="surface-panel table-panel">
            <BasicTable :columns="columns" :data-source="table.dataSource.value" :loading="table.loading.value" :pagination="table.pagination" @change="table.handleTableChange">
                <template #bodyCell="{ column, record }"><DictTag v-if="column.key === 'outcome'" :value="record.outcome" :options="outcomeOptions" /></template>
            </BasicTable>
        </div>
    </section>
</template>

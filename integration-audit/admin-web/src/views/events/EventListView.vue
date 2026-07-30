<script setup lang="ts">
import { ref } from 'vue';
import { Button as AButton, Space as ASpace } from 'ant-design-vue';

import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicTable from '@/components/jeecg/BasicTable.vue';
import DictTag from '@/components/jeecg/DictTag.vue';
import type { FormSchema } from '@/components/jeecg/useForm';
import { useTable } from '@/components/jeecg/useTable';
import type { EventQuery, EventRecord } from '@/domain/monitoring';
import { useMonitoringRepository } from '@/repositories/monitoringRepository';
import EventDetailDrawer from './EventDetailDrawer.vue';
import { eventColumns } from './eventTable';

const repository = useMonitoringRepository();
interface EventTableQuery extends Record<string, unknown> {
    keyword: string;
    actionCode: string;
    result: string | undefined;
}
const detailOpen = ref(false);
const currentRecord = ref<EventRecord>();
const resultOptions = [{ value: 'SUCCEEDED', label: '成功', color: 'success' }, { value: 'DENIED', label: '拒绝', color: 'error' }, { value: 'FAILED', label: '失败', color: 'error' }];
const searchSchemas: FormSchema[] = [
    { field: 'keyword', label: '关键词', component: 'Input', componentProps: { allowClear: true, placeholder: '事件编号 / 主体 / 资源' } },
    { field: 'actionCode', label: '动作类型', component: 'Input', componentProps: { allowClear: true, placeholder: '动作类型' } },
    { field: 'result', label: '执行结果', component: 'Select', options: resultOptions.map(({ value, label }) => ({ value, label })), componentProps: { allowClear: true, placeholder: '全部结果' } }
];
const table = useTable<EventRecord, EventTableQuery>({
    api: (query) => repository.searchEvents(query as unknown as EventQuery),
    query: { keyword: '', actionCode: '', result: undefined }
});

function openDetail(record: EventRecord): void {
    currentRecord.value = record;
    detailOpen.value = true;
}
</script>

<template>
    <section class="page-section list-view">
        <header class="page-heading"><div><h1>事件审计</h1><p>检索服务器端权威访问证据，前端信号仅作为补充信息</p></div></header>
        <BasicForm class="query-form" :schemas="searchSchemas" :model-value="table.query" :columns="4" :label-width="72" @update:model-value="Object.assign(table.query, $event)">
            <template #action><ASpace><AButton type="primary" @click="table.reload">查询</AButton><AButton @click="table.reset">重置</AButton></ASpace></template>
        </BasicForm>
        <div class="surface-panel table-panel">
            <BasicTable :columns="eventColumns" :data-source="table.dataSource.value" :loading="table.loading.value" :pagination="table.pagination" @change="table.handleTableChange">
                <template #bodyCell="{ column, record }">
                    <DictTag v-if="column.key === 'result'" :value="record.result" :options="resultOptions" />
                    <AButton v-else-if="column.key === 'action'" type="link" size="small" @click="openDetail(record as EventRecord)">查看</AButton>
                </template>
            </BasicTable>
        </div>
        <EventDetailDrawer v-model:open="detailOpen" :record="currentRecord" />
    </section>
</template>

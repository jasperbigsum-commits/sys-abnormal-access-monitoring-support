<script setup lang="ts">
import { ref } from 'vue';
import { Button as AButton, message, Space as ASpace } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicTable from '@/components/jeecg/BasicTable.vue';
import DictTag from '@/components/jeecg/DictTag.vue';
import { useTable } from '@/components/jeecg/useTable';
import type { ControlAction, ControlQuery, ControlRecord } from '@/domain/monitoring';
import { ManagementError } from '@/domain/errors';
import { useMonitoringRepository } from '@/repositories/monitoringRepository';
import ControlActionModal from './ControlActionModal.vue';
import ControlDetailDrawer from './ControlDetailDrawer.vue';
import { controlColumns, controlSearchSchemas, controlStatusOptions } from './controlSchemas';

interface ControlTableQuery extends Record<string, unknown> { keyword: string; statuses: string[]; actions: string[] }
type ModalAction = ControlAction | 'EXECUTE';
const repository = useMonitoringRepository();
const table = useTable<ControlRecord, ControlTableQuery>({ api: (query) => repository.searchControls(query as unknown as ControlQuery), query: { keyword: '', statuses: [], actions: [] } });
const detailOpen = ref(false);
const modalOpen = ref(false);
const loading = ref(false);
const currentRecord = ref<ControlRecord>();
const currentAction = ref<ModalAction>();

function openDetail(record: ControlRecord): void { currentRecord.value = record; detailOpen.value = true; }
function startAction(action: ModalAction): void { currentAction.value = action; modalOpen.value = true; }
async function submit(values: Record<string, unknown>): Promise<void> {
    if (!currentAction.value) return;
    loading.value = true;
    try {
        if (currentAction.value === 'EXECUTE') {
            currentRecord.value = await repository.executeControl({ subject: String(values.subject), action: String(values.action), ttlMinutes: Number(values.ttlMinutes), reason: String(values.reason), idempotencyKey: `manual-${String(values.subject)}-${Date.now()}` });
            detailOpen.value = true;
        } else if (currentRecord.value) {
            currentRecord.value = await repository.transitionControl({ id: currentRecord.value.id, action: currentAction.value, expectedVersion: currentRecord.value.version, reason: String(values.reason) });
        }
        modalOpen.value = false;
        await table.reload();
        message.success('控制操作已记录');
    } catch (error) {
        message.error(error instanceof ManagementError && error.category === 'CONFLICT' ? '数据版本冲突，请刷新后重试' : '控制操作失败');
    } finally { loading.value = false; }
}
</script>

<template>
    <section class="page-section list-view">
        <header class="page-heading"><div><h1>控制中心</h1><p>审批、执行与失败重试均需要明确原因，优先处理待审批任务</p></div><AButton type="primary" @click="startAction('EXECUTE')"><PlusOutlined />手工处置</AButton></header>
        <BasicForm class="query-form" :schemas="controlSearchSchemas" :model-value="table.query" :columns="4" :label-width="72" @update:model-value="Object.assign(table.query, $event)"><template #action><ASpace><AButton type="primary" @click="table.reload">查询</AButton><AButton @click="table.reset">重置</AButton></ASpace></template></BasicForm>
        <div class="surface-panel table-panel"><BasicTable :columns="controlColumns" :data-source="table.dataSource.value" :loading="table.loading.value" :pagination="table.pagination" :scroll-x="1420" @change="table.handleTableChange"><template #bodyCell="{ column, record }"><DictTag v-if="column.key === 'status'" :value="record.status" :options="controlStatusOptions" /><span v-else-if="column.key === 'failureReason'" :class="{ 'failure-text': record.failureReason }">{{ record.failureReason ?? '无' }}</span><AButton v-else-if="column.key === 'action'" type="link" size="small" @click="openDetail(record as ControlRecord)">查看</AButton></template></BasicTable></div>
        <ControlDetailDrawer v-model:open="detailOpen" :record="currentRecord" @action="startAction" />
        <ControlActionModal v-model:open="modalOpen" :record="currentRecord" :action="currentAction" :loading="loading" @submit="submit" />
    </section>
</template>

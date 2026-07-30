<script setup lang="ts">
import { nextTick, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Button as AButton, message, Space as ASpace } from 'ant-design-vue';

import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicTable from '@/components/jeecg/BasicTable.vue';
import DictTag from '@/components/jeecg/DictTag.vue';
import { useTable } from '@/components/jeecg/useTable';
import type { AlertAction, AlertQuery, AlertRecord, AlertStatus, RiskLevel } from '@/domain/monitoring';
import { ManagementError } from '@/domain/errors';
import { useMonitoringRepository } from '@/repositories/monitoringRepository';
import AlertActionModal from './AlertActionModal.vue';
import AlertDetailDrawer from './AlertDetailDrawer.vue';
import { alertColumns, alertSearchSchemas, alertStatusOptions, riskOptions } from './alertSchemas';

interface AlertTableQuery extends Record<string, unknown> {
    keyword: string;
    risks: RiskLevel[];
    statuses: AlertStatus[];
}

const repository = useMonitoringRepository();
const route = useRoute();
const router = useRouter();
const initialRisk = typeof route.query.risk === 'string' ? [route.query.risk as RiskLevel] : [];
const table = useTable<AlertRecord, AlertTableQuery>({
    api: (query) => repository.searchAlerts(query as unknown as AlertQuery),
    query: { keyword: '', risks: initialRisk, statuses: [] }
});
const detailOpen = ref(false);
const actionOpen = ref(false);
const actionLoading = ref(false);
const currentRecord = ref<AlertRecord>();
const currentAction = ref<AlertAction>();

async function openDetail(record: AlertRecord): Promise<void> {
    currentRecord.value = await repository.getAlert(record.id);
    detailOpen.value = true;
    await router.replace({ query: { ...route.query, alertId: record.id } });
}

function startAction(action: AlertAction): void {
    currentAction.value = action;
    actionOpen.value = true;
}

async function submitAction(values: { reason: string; assigneeId?: string }): Promise<void> {
    if (!currentRecord.value || !currentAction.value) return;
    actionLoading.value = true;
    try {
        currentRecord.value = await repository.transitionAlert({
            id: currentRecord.value.id,
            action: currentAction.value,
            expectedVersion: currentRecord.value.version,
            reason: values.reason,
            assigneeId: values.assigneeId,
            idempotencyKey: `alert-${currentRecord.value.id}-${currentAction.value}-${currentRecord.value.version}`
        });
        actionOpen.value = false;
        await table.reload();
        message.success('告警操作已记录');
    } catch (error) {
        message.error(error instanceof ManagementError && error.category === 'CONFLICT' ? '数据已更新，请刷新详情后重试' : '告警操作失败');
    } finally {
        actionLoading.value = false;
    }
}

async function applyQuery(): Promise<void> {
    await router.replace({ query: { ...route.query, risk: table.query.risks[0] } });
    await table.reload();
}

void nextTick(async () => {
    if (typeof route.query.alertId === 'string') {
        const record = await repository.getAlert(route.query.alertId);
        await openDetail(record);
    }
});
</script>

<template>
    <section class="page-section list-view">
        <header class="page-heading"><div><h1>告警中心</h1><p>按风险优先级完成确认、分派、研判与闭环，所有操作均可审计</p></div></header>
        <BasicForm class="query-form" :schemas="alertSearchSchemas" :model-value="table.query" :columns="4" :label-width="72" @update:model-value="Object.assign(table.query, $event)">
            <template #action><ASpace><AButton type="primary" @click="applyQuery">查询</AButton><AButton @click="table.reset">重置</AButton></ASpace></template>
        </BasicForm>
        <div class="surface-panel table-panel">
            <BasicTable :columns="alertColumns" :data-source="table.dataSource.value" :loading="table.loading.value" :pagination="table.pagination" :scroll-x="1320" @change="table.handleTableChange">
                <template #bodyCell="{ column, record }">
                    <DictTag v-if="column.key === 'risk'" :value="record.risk" :options="riskOptions" />
                    <DictTag v-else-if="column.key === 'status'" :value="record.status" :options="alertStatusOptions" />
                    <span v-else-if="column.key === 'assigneeId'">{{ record.assigneeId ?? '未分派' }}</span>
                    <AButton v-else-if="column.key === 'action'" type="link" size="small" :data-testid="`alert-${record.id}`" @click="openDetail(record as AlertRecord)">研判</AButton>
                </template>
            </BasicTable>
        </div>
        <AlertDetailDrawer v-model:open="detailOpen" :record="currentRecord" @action="startAction" />
        <AlertActionModal v-model:open="actionOpen" :record="currentRecord" :action="currentAction" :loading="actionLoading" @submit="submitAction" />
    </section>
</template>

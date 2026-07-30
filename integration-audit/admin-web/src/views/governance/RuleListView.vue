<script setup lang="ts">
import { ref } from 'vue';
import { Button as AButton, message, Space as ASpace } from 'ant-design-vue';
import { WarningOutlined } from '@ant-design/icons-vue';
import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicTable from '@/components/jeecg/BasicTable.vue';
import DictTag from '@/components/jeecg/DictTag.vue';
import { useTable } from '@/components/jeecg/useTable';
import type { RiskLevel, RuleMode, RuleQuery, RuleRecord } from '@/domain/monitoring';
import { useMonitoringRepository } from '@/repositories/monitoringRepository';
import RuleChangeModal from './RuleChangeModal.vue';
import { ruleColumns, ruleModeOptions, ruleSearchSchemas } from './governanceSchemas';
import { riskOptions } from '@/views/alerts/alertSchemas';
interface RuleTableQuery extends Record<string, unknown> {
    keyword: string;
    modes: RuleMode[];
    risks: RiskLevel[];
}
const repository = useMonitoringRepository();
const table = useTable<RuleRecord, RuleTableQuery>({
    api: (q) => repository.searchRules(q as unknown as RuleQuery),
    query: { keyword: '', modes: [], risks: [] },
});
const modalOpen = ref(false);
const loading = ref(false);
const current = ref<RuleRecord>();
function open(record: RuleRecord) {
    current.value = record;
    modalOpen.value = true;
}
async function submit(values: Record<string, unknown>) {
    if (!current.value) return;
    if (String(values.approverId) === current.value.createdBy) {
        message.error('审批人必须与创建人不同');
        return;
    }
    if (String(values.reason).trim().length < 5) {
        message.error('变更原因至少 5 个字符');
        return;
    }
    loading.value = true;
    try {
        current.value = await repository.changeRule({
            id: current.value.id,
            mode: values.mode as RuleMode,
            threshold: Number(values.threshold),
            expectedVersion: current.value.version,
            reason: String(values.reason),
            approverId: String(values.approverId),
            idempotencyKey: `rule-${current.value.id}-${current.value.version}`,
        });
        modalOpen.value = false;
        await table.reload();
        message.success('规则持久化版本已更新');
    } finally {
        loading.value = false;
    }
}
</script>
<template>
    <section class="page-section list-view">
        <header class="page-heading">
            <div>
                <h1>检测策略</h1>
                <p>管理持久化规则版本、模式、风险等级与阈值</p>
            </div>
        </header>
        <div class="runtime-warning">
            <WarningOutlined />
            <div>
                <strong>运行时版本冻结</strong
                ><span>新持久化版本不会替换已经冻结的运行时规则，仍需受控发布或重启生效。</span>
            </div>
        </div>
        <BasicForm
            class="query-form"
            :schemas="ruleSearchSchemas"
            :model-value="table.query"
            :columns="4"
            :label-width="72"
            @update:model-value="Object.assign(table.query, $event)"
            ><template #action
                ><ASpace
                    ><AButton type="primary" @click="table.reload">查询</AButton
                    ><AButton @click="table.reset">重置</AButton></ASpace
                ></template
            ></BasicForm
        >
        <div class="surface-panel table-panel">
            <BasicTable
                :columns="ruleColumns"
                :data-source="table.dataSource.value"
                :loading="table.loading.value"
                :pagination="table.pagination"
                :scroll-x="1250"
                @change="table.handleTableChange"
                ><template #bodyCell="{ column, record }"
                    ><DictTag v-if="column.key === 'risk'" :value="record.risk" :options="riskOptions" /><DictTag
                        v-else-if="column.key === 'mode'"
                        :value="record.mode"
                        :options="ruleModeOptions"
                    /><strong v-else-if="column.key === 'version'">v{{ record.version }}</strong
                    ><span v-else-if="column.key === 'approvedBy'">{{ record.approvedBy ?? '待审批' }}</span
                    ><AButton v-else-if="column.key === 'action'" type="link" @click="open(record as RuleRecord)"
                        >变更</AButton
                    ></template
                ></BasicTable
            >
        </div>
        <RuleChangeModal v-model:open="modalOpen" :record="current" :loading="loading" @submit="submit" />
    </section>
</template>

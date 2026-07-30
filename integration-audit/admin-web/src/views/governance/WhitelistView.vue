<script setup lang="ts">
import { ref } from 'vue';
import { Button as AButton, message, Space as ASpace } from 'ant-design-vue';
import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicTable from '@/components/jeecg/BasicTable.vue';
import DictTag from '@/components/jeecg/DictTag.vue';
import { useTable } from '@/components/jeecg/useTable';
import type { WhitelistQuery, WhitelistRecord } from '@/domain/monitoring';
import { useMonitoringRepository } from '@/repositories/monitoringRepository';
import WhitelistActionModal from './WhitelistActionModal.vue';
import { whitelistColumns, whitelistSearchSchemas, whitelistStatusOptions } from './governanceSchemas';
interface WhitelistTableQuery extends Record<string, unknown> {
    keyword: string;
    statuses: WhitelistRecord['status'][];
}
const repository = useMonitoringRepository();
const table = useTable<WhitelistRecord, WhitelistTableQuery>({
    api: (q) => repository.searchWhitelists(q as unknown as WhitelistQuery),
    query: { keyword: '', statuses: [] },
});
const modalOpen = ref(false);
const loading = ref(false);
const current = ref<WhitelistRecord>();
function open(record: WhitelistRecord) {
    current.value = record;
    modalOpen.value = true;
}
async function submit(reason: string) {
    if (!current.value) return;
    loading.value = true;
    try {
        current.value = await repository.transitionWhitelist({
            id: current.value.id,
            action: current.value.status === 'ACTIVE' ? 'REVOKE' : 'GRANT',
            expectedVersion: current.value.version,
            reason,
        });
        modalOpen.value = false;
        await table.reload();
        message.success('白名单状态已更新');
    } finally {
        loading.value = false;
    }
}
</script>
<template>
    <section class="page-section list-view">
        <header class="page-heading">
            <div>
                <h1>白名单</h1>
                <p>管理经审批的检测例外，只允许授予或撤销</p>
            </div>
        </header>
        <BasicForm
            class="query-form"
            :schemas="whitelistSearchSchemas"
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
                :columns="whitelistColumns"
                :data-source="table.dataSource.value"
                :loading="table.loading.value"
                :pagination="table.pagination"
                :scroll-x="1220"
                @change="table.handleTableChange"
                ><template #bodyCell="{ column, record }"
                    ><DictTag
                        v-if="column.key === 'status'"
                        :value="record.status"
                        :options="whitelistStatusOptions"
                    /><span v-else-if="column.key === 'ruleId'">{{ record.ruleId ?? '全部规则' }}</span
                    ><span v-else-if="column.key === 'expiresAt'">{{ record.expiresAt ?? '长期有效' }}</span
                    ><AButton
                        v-else-if="column.key === 'action'"
                        type="link"
                        :danger="record.status === 'ACTIVE'"
                        @click="open(record as WhitelistRecord)"
                        >{{ record.status === 'ACTIVE' ? '撤销' : '授予' }}</AButton
                    ></template
                ></BasicTable
            >
        </div>
        <WhitelistActionModal v-model:open="modalOpen" :record="current" :loading="loading" @submit="submit" />
    </section>
</template>

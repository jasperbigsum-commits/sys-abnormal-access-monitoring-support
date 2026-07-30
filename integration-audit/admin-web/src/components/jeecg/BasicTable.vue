<script setup lang="ts">
import { computed } from 'vue';
import { Table as ATable } from 'ant-design-vue';
import type { TableColumnType } from 'ant-design-vue';

import type { TablePagination } from './useTable';

const props = withDefaults(defineProps<{
    columns: TableColumnType[];
    dataSource: Record<string, unknown>[];
    loading?: boolean;
    pagination?: TablePagination | false;
    rowKey?: string | ((record: Record<string, unknown>) => string);
    selectedRowKeys?: (string | number)[];
    scrollX?: number;
}>(), {
    loading: false,
    pagination: false,
    rowKey: 'id',
    selectedRowKeys: () => [],
    scrollX: 960
});

const emit = defineEmits<{
    change: [pagination: { current?: number; pageSize?: number }];
    'update:selectedRowKeys': [keys: (string | number)[]];
}>();

const rowSelection = computed(() => props.selectedRowKeys.length === 0 ? undefined : ({
    selectedRowKeys: props.selectedRowKeys,
    onChange: (keys: (string | number)[]) => emit('update:selectedRowKeys', keys)
}));
</script>

<template>
    <ATable
        class="jeecg-basic-table"
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-key="rowKey"
        :row-selection="rowSelection"
        :scroll="{ x: scrollX }"
        size="middle"
        @change="(value) => emit('change', value)"
    >
        <template v-for="(_, name) in $slots" #[name]="slotData">
            <slot :name="name" v-bind="slotData ?? {}" />
        </template>
    </ATable>
</template>

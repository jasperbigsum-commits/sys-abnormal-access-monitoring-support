import { onMounted, reactive, ref, toRaw, type Ref } from 'vue';

import type { PageResult } from '@/domain/monitoring';

export interface TablePagination {
    current: number;
    pageSize: number;
    total: number;
    showSizeChanger: boolean;
    showTotal: (total: number) => string;
}

export interface UseTableOptions<T, Q extends Record<string, unknown> = Record<string, unknown>> {
    api: (query: Q & { page: number; pageSize: number }) => Promise<PageResult<T>>;
    query?: Q;
    pageSize?: number;
    immediate?: boolean;
}

export interface UseTableResult<T, Q extends Record<string, unknown>> {
    query: Q;
    dataSource: Ref<T[]>;
    loading: Ref<boolean>;
    error: Ref<unknown>;
    selectedRowKeys: Ref<(string | number)[]>;
    pagination: TablePagination;
    reload: () => Promise<void>;
    reset: () => Promise<void>;
    handleTableChange: (pagination: { current?: number; pageSize?: number }) => Promise<void>;
}

export function useTable<T, Q extends Record<string, unknown> = Record<string, unknown>>(
    options: UseTableOptions<T, Q>
): UseTableResult<T, Q> {
    const initialQuery = { ...(options.query ?? {}) } as Q;
    const query = reactive({ ...initialQuery }) as Q;
    const dataSource = ref<T[]>([]) as Ref<T[]>;
    const loading = ref(false);
    const error = ref<unknown>();
    const selectedRowKeys = ref<(string | number)[]>([]);
    const pagination = reactive<TablePagination>({
        current: 1,
        pageSize: options.pageSize ?? 10,
        total: 0,
        showSizeChanger: true,
        showTotal: (total) => `共 ${total} 条`
    });
    let requestSequence = 0;

    async function reload(): Promise<void> {
        const sequence = ++requestSequence;
        loading.value = true;
        error.value = undefined;
        try {
            const result = await options.api({
                ...(toRaw(query) as Q),
                page: pagination.current,
                pageSize: pagination.pageSize
            });
            if (sequence !== requestSequence) return;
            dataSource.value = result.items;
            pagination.current = result.page;
            pagination.pageSize = result.pageSize;
            pagination.total = result.total;
        } catch (caught) {
            if (sequence === requestSequence) error.value = caught;
        } finally {
            if (sequence === requestSequence) loading.value = false;
        }
    }

    async function reset(): Promise<void> {
        for (const key of Object.keys(query)) delete query[key as keyof Q];
        Object.assign(query, initialQuery);
        pagination.current = 1;
        selectedRowKeys.value = [];
        await reload();
    }

    async function handleTableChange(next: { current?: number; pageSize?: number }): Promise<void> {
        const sizeChanged = next.pageSize !== undefined && next.pageSize !== pagination.pageSize;
        pagination.pageSize = next.pageSize ?? pagination.pageSize;
        pagination.current = sizeChanged ? 1 : (next.current ?? pagination.current);
        await reload();
    }

    if (options.immediate !== false) onMounted(() => void reload());

    return { query, dataSource, loading, error, selectedRowKeys, pagination, reload, reset, handleTableChange };
}

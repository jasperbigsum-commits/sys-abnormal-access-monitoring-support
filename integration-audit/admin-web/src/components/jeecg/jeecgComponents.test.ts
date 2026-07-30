import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import BasicForm from './BasicForm.vue';
import BasicModal from './BasicModal.vue';
import DictTag from './DictTag.vue';
import { useOverlay } from './useOverlay';
import { useTable } from './useTable';

describe('Jeecg-compatible component contracts', () => {
    it('translates pagination and query state into one-based list requests', async () => {
        const api = vi.fn().mockResolvedValue({ items: [], total: 0, page: 3, pageSize: 20 });
        const table = useTable({ api, pageSize: 20, immediate: false });

        table.query.keyword = '异常导出';
        table.pagination.current = 3;
        await table.reload();

        expect(api).toHaveBeenCalledWith({ keyword: '异常导出', page: 3, pageSize: 20 });
        expect(table.pagination.total).toBe(0);
    });

    it('keeps the newest table result when requests resolve out of order', async () => {
        let resolveFirst!: (value: { items: string[]; total: number; page: number; pageSize: number }) => void;
        const first = new Promise<{ items: string[]; total: number; page: number; pageSize: number }>((resolve) => {
            resolveFirst = resolve;
        });
        const api = vi.fn()
            .mockReturnValueOnce(first)
            .mockResolvedValueOnce({ items: ['newest'], total: 1, page: 2, pageSize: 10 });
        const table = useTable<string>({ api, immediate: false });

        const staleRequest = table.reload();
        table.pagination.current = 2;
        await table.reload();
        resolveFirst({ items: ['stale'], total: 1, page: 1, pageSize: 10 });
        await staleRequest;

        expect(table.dataSource.value).toEqual(['newest']);
    });

    it('validates required schema fields and exposes form values', async () => {
        const wrapper = mount(BasicForm, {
            props: {
                schemas: [{ field: 'reason', label: '处置原因', required: true, component: 'Input' }]
            }
        });

        await expect(wrapper.vm.validate()).rejects.toBeDefined();
        await wrapper.get('input').setValue('已核验异常来源');
        await flushPromises();
        await expect(wrapper.vm.validate()).resolves.toMatchObject({ reason: '已核验异常来源' });
    });

    it('falls back to the original code for unknown dictionary values', () => {
        const wrapper = mount(DictTag, {
            props: {
                value: 'POLICY_LOCKED',
                options: [{ value: 'KNOWN', label: '已定义', color: 'success' }]
            }
        });

        expect(wrapper.text()).toContain('POLICY_LOCKED');
        expect(wrapper.attributes('data-known')).toBe('false');
    });

    it('registers and controls modal or drawer overlays', () => {
        const [register, overlay] = useOverlay<{ recordId?: string }>();
        const setProps = vi.fn();
        register({ setProps });

        overlay.open({ recordId: 'ALT-001' });
        overlay.close();

        expect(setProps).toHaveBeenNthCalledWith(1, { recordId: 'ALT-001', open: true });
        expect(setProps).toHaveBeenNthCalledWith(2, { open: false });
    });

    it('keeps dynamic overlay titles and loading state in sync', async () => {
        const wrapper = mount(BasicModal, { attachTo: document.body, props: { open: true, title: '初始标题', confirmLoading: false } });

        await wrapper.setProps({ title: '关闭告警 · ALT-001', confirmLoading: true });

        expect(document.body.textContent).toContain('关闭告警 · ALT-001');
        expect(document.body.querySelector('.ant-btn-loading')).not.toBeNull();
        wrapper.unmount();
    });
});

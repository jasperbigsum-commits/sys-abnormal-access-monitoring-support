import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { describe, expect, it } from 'vitest';

import { createMockMonitoringRepository } from '@/mocks/mockMonitoringRepository';
import { monitoringRepositoryKey } from '@/repositories/monitoringRepository';
import AlertListView from './AlertListView.vue';
import { availableAlertActions } from './alertSchemas';

describe('Alert workbench', () => {
    it('maps each alert state to auditable actions', () => {
        expect(availableAlertActions('NEW').map((item) => item.action)).toEqual(['ASSIGN', 'ACKNOWLEDGE', 'INVESTIGATE', 'FALSE_POSITIVE']);
        expect(availableAlertActions('ACKNOWLEDGED').map((item) => item.action)).toContain('CLOSE');
        expect(availableAlertActions('CLOSED')).toEqual([]);
    });

    it('hydrates the risk filter from the route and keeps the filtered list', async () => {
        const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/alerts', component: AlertListView }] });
        await router.push('/alerts?risk=HIGH');
        await router.isReady();
        const wrapper = mount(AlertListView, {
            global: { plugins: [router], provide: { [monitoringRepositoryKey as symbol]: createMockMonitoringRepository() } }
        });
        await flushPromises();

        expect(wrapper.text()).toContain('非工作时段敏感报表导出');
        expect(wrapper.text()).not.toContain('客户信息批量查询频率异常');
        expect(router.currentRoute.value.query.risk).toBe('HIGH');
        wrapper.unmount();
    }, 15_000);
});

import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { describe, expect, it } from 'vitest';

import { createMockMonitoringRepository } from '@/mocks/mockMonitoringRepository';
import { monitoringRepositoryKey } from '@/repositories/monitoringRepository';
import ManagementAuditView from './audit/ManagementAuditView.vue';
import DashboardView from './dashboard/DashboardView.vue';

async function createTestRouter() {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            { path: '/dashboard', component: DashboardView },
            { path: '/alerts', component: { template: '<div />' } }
        ]
    });
    await router.push('/dashboard');
    await router.isReady();
    return router;
}

describe('read-only monitoring views', () => {
    it('drills the high-risk dashboard metric into alerts', async () => {
        const router = await createTestRouter();
        const wrapper = mount(DashboardView, {
            global: {
                plugins: [router],
                provide: { [monitoringRepositoryKey as symbol]: createMockMonitoringRepository() },
                stubs: { RiskTrendChart: true }
            }
        });
        await flushPromises();

        await wrapper.get('[data-testid="high-risk-alerts"]').trigger('click');
        await flushPromises();

        expect(router.currentRoute.value.path).toBe('/alerts');
        expect(router.currentRoute.value.query.risk).toBe('HIGH');
        wrapper.unmount();
    });

    it('keeps management audit immutable in the interface', async () => {
        const wrapper = mount(ManagementAuditView, {
            global: { provide: { [monitoringRepositoryKey as symbol]: createMockMonitoringRepository() } }
        });
        await flushPromises();

        expect(wrapper.text()).toContain('RULE_CHANGE');
        expect(wrapper.text()).not.toMatch(/编辑|删除/);
    });
});

import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import { createMockMonitoringRepository } from '@/mocks/mockMonitoringRepository';
import { monitoringRepositoryKey } from '@/repositories/monitoringRepository';
import ControlCenterView from './ControlCenterView.vue';
import { availableControlActions } from './controlSchemas';

describe('Control center', () => {
    it('exposes actions only for actionable control states', () => {
        expect(availableControlActions('PENDING_APPROVAL').map((item) => item.action)).toEqual(['APPROVE', 'REJECT']);
        expect(availableControlActions('FAILED').map((item) => item.action)).toEqual(['RETRY']);
        expect(availableControlActions('SUCCEEDED')).toEqual([]);
    });

    it('renders pending approvals and safe failure context', async () => {
        const wrapper = mount(ControlCenterView, {
            global: { provide: { [monitoringRepositoryKey as symbol]: createMockMonitoringRepository() } }
        });
        await flushPromises();

        expect(wrapper.text()).toContain('待审批');
        expect(wrapper.text()).toContain('演示下游服务短暂不可用');
        expect(wrapper.text()).toContain('手工处置');
        wrapper.unmount();
    }, 15_000);
});

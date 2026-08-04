import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import { createMockMonitoringRepository } from '@/mocks/mockMonitoringRepository';
import { monitoringRepositoryKey } from '@/repositories/monitoringRepository';
import RuleListView from './RuleListView.vue';
import WhitelistView from './WhitelistView.vue';

const provide = () => ({ [monitoringRepositoryKey as symbol]: createMockMonitoringRepository() });

describe('governance views', () => {
    it('shows persisted rule versions and the frozen-runtime warning', async () => {
        const wrapper = mount(RuleListView, { global: { provide: provide() } });
        await flushPromises();
        expect(wrapper.text()).toContain('RULE-BULK-QUERY');
        expect(wrapper.text()).toContain('冻结的运行时规则');
        expect(wrapper.text()).toContain('v4');
        wrapper.unmount();
    }, 15_000);

    it('restricts whitelist governance to grant or revoke', async () => {
        const wrapper = mount(WhitelistView, { global: { provide: provide() } });
        await flushPromises();
        expect(wrapper.text()).toContain('ops-demo-batch-01');
        expect(wrapper.text()).toContain('integration-audit');
        expect(wrapper.text()).toContain('已过期');
        expect(wrapper.text()).not.toMatch(/编辑|删除/);
        wrapper.unmount();
    }, 15_000);
});

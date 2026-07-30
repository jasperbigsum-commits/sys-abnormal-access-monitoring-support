import { mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { describe, expect, it } from 'vitest';

import RiskControlLayout from './RiskControlLayout.vue';

describe('RiskControlLayout', () => {
    it('renders all seven management destinations and the runtime mode', async () => {
        const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] });
        await router.push('/');
        await router.isReady();
        const wrapper = mount(RiskControlLayout, { global: { plugins: [router] } });

        for (const label of ['风险态势', '告警中心', '事件审计', '控制中心', '检测策略', '白名单', '管理审计']) {
            expect(wrapper.text()).toContain(label);
        }
        expect(wrapper.text()).toContain('ENFORCE');
    });
});

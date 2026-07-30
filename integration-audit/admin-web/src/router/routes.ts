import type { RouteRecordRaw } from 'vue-router';

import RiskControlLayout from '@/layouts/RiskControlLayout.vue';
import PlaceholderView from '@/views/PlaceholderView.vue';

export const routes: RouteRecordRaw[] = [
    {
        path: '/',
        component: RiskControlLayout,
        redirect: '/dashboard',
        children: [
            { path: 'dashboard', name: 'dashboard', component: PlaceholderView, meta: { title: '风险态势' } },
            { path: 'alerts', name: 'alerts', component: PlaceholderView, meta: { title: '告警中心' } },
            { path: 'events', name: 'events', component: PlaceholderView, meta: { title: '事件审计' } },
            { path: 'controls', name: 'controls', component: PlaceholderView, meta: { title: '控制中心' } },
            { path: 'rules', name: 'rules', component: PlaceholderView, meta: { title: '检测策略' } },
            { path: 'whitelists', name: 'whitelists', component: PlaceholderView, meta: { title: '白名单' } },
            { path: 'management-audit', name: 'management-audit', component: PlaceholderView, meta: { title: '管理审计' } }
        ]
    }
];

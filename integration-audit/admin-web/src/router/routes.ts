import type { RouteRecordRaw } from 'vue-router';

import RiskControlLayout from '@/layouts/RiskControlLayout.vue';
import PlaceholderView from '@/views/PlaceholderView.vue';

export const routes: RouteRecordRaw[] = [
    {
        path: '/',
        component: RiskControlLayout,
        redirect: '/dashboard',
        children: [
            { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '风险态势' } },
            { path: 'alerts', name: 'alerts', component: () => import('@/views/alerts/AlertListView.vue'), meta: { title: '告警中心' } },
            { path: 'events', name: 'events', component: () => import('@/views/events/EventListView.vue'), meta: { title: '事件审计' } },
            { path: 'controls', name: 'controls', component: () => import('@/views/controls/ControlCenterView.vue'), meta: { title: '控制中心' } },
            { path: 'rules', name: 'rules', component: () => import('@/views/governance/RuleListView.vue'), meta: { title: '检测策略' } },
            { path: 'whitelists', name: 'whitelists', component: () => import('@/views/governance/WhitelistView.vue'), meta: { title: '白名单' } },
            { path: 'management-audit', name: 'management-audit', component: () => import('@/views/audit/ManagementAuditView.vue'), meta: { title: '管理审计' } }
        ]
    }
];

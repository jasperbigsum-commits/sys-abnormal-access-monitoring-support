<script setup lang="ts">
import { computed, ref, type Component } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';
import {
    AlertOutlined,
    AuditOutlined,
    BellOutlined,
    ControlOutlined,
    DashboardOutlined,
    FileSearchOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
    SafetyCertificateOutlined,
    SecurityScanOutlined,
    SolutionOutlined,
    UserOutlined
} from '@ant-design/icons-vue';
import { Avatar as AAvatar, Badge as ABadge, Button as AButton, Drawer as ADrawer, Tooltip as ATooltip } from 'ant-design-vue';
import MockScenarioPanel from '@/components/dev/MockScenarioPanel.vue';

interface NavigationItem {
    path: string;
    label: string;
    description: string;
    icon: Component;
}

const navigationItems: NavigationItem[] = [
    { path: '/dashboard', label: '风险态势', description: '运营总览', icon: DashboardOutlined },
    { path: '/alerts', label: '告警中心', description: '研判闭环', icon: AlertOutlined },
    { path: '/events', label: '事件审计', description: '访问证据', icon: FileSearchOutlined },
    { path: '/controls', label: '控制中心', description: '审批处置', icon: ControlOutlined },
    { path: '/rules', label: '检测策略', description: '规则治理', icon: SecurityScanOutlined },
    { path: '/whitelists', label: '白名单', description: '例外管理', icon: SafetyCertificateOutlined },
    { path: '/management-audit', label: '管理审计', description: '操作留痕', icon: AuditOutlined }
];

const route = useRoute();
const collapsed = ref(false);
const mobileOpen = ref(false);
const pageTitle = computed(() => String(route.meta.title ?? '风险态势'));
const showMockPanel = import.meta.env.DEV && import.meta.env.VITE_DATA_MODE !== 'http';

function closeMobile(): void {
    mobileOpen.value = false;
}
</script>

<template>
    <div class="risk-layout" :class="{ 'risk-layout--collapsed': collapsed }">
        <aside class="risk-sidebar" aria-label="主导航">
            <div class="risk-brand">
                <span class="risk-brand__mark"><SolutionOutlined /></span>
                <div class="risk-brand__text">
                    <strong>异常访问监测</strong>
                    <span>科技风险运营中心</span>
                </div>
            </div>
            <nav class="risk-nav">
                <RouterLink
                    v-for="item in navigationItems"
                    :key="item.path"
                    :to="item.path"
                    class="risk-nav__item"
                    :class="{ 'is-active': route.path.startsWith(item.path) }"
                    :title="collapsed ? item.label : undefined"
                >
                    <component :is="item.icon" class="risk-nav__icon" />
                    <span class="risk-nav__copy"><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
                </RouterLink>
            </nav>
            <div class="risk-sidebar__footer">
                <span class="health-dot" />
                <span class="risk-sidebar__health">监测链路正常</span>
            </div>
        </aside>

        <div class="risk-workspace">
            <header class="risk-header">
                <div class="risk-header__leading">
                    <ATooltip :title="collapsed ? '展开导航' : '收起导航'">
                        <AButton class="desktop-nav-toggle" type="text" :aria-label="collapsed ? '展开导航' : '收起导航'" @click="collapsed = !collapsed">
                            <component :is="collapsed ? MenuUnfoldOutlined : MenuFoldOutlined" />
                        </AButton>
                    </ATooltip>
                    <AButton class="mobile-nav-toggle" type="text" aria-label="打开导航" @click="mobileOpen = true"><MenuUnfoldOutlined /></AButton>
                    <div class="risk-breadcrumb"><span>异常访问监测中心</span><b>/</b><strong>{{ pageTitle }}</strong></div>
                </div>
                <div class="risk-header__actions">
                    <div class="runtime-mode"><span class="health-dot" /><span>运行模式</span><strong>ENFORCE</strong></div>
                    <ATooltip title="待办通知"><AButton type="text" class="header-icon" aria-label="待办通知"><ABadge :count="4" size="small"><BellOutlined /></ABadge></AButton></ATooltip>
                    <div class="operator"><AAvatar :size="32"><UserOutlined /></AAvatar><span><strong>审计管理员</strong><small>科技风险一组</small></span></div>
                </div>
            </header>
            <main class="risk-content"><RouterView /></main>
        </div>

        <ADrawer v-model:open="mobileOpen" class="mobile-risk-drawer" title="异常访问监测" placement="left" :width="280">
            <nav class="mobile-risk-nav">
                <RouterLink v-for="item in navigationItems" :key="item.path" :to="item.path" class="mobile-risk-nav__item" @click="closeMobile">
                    <component :is="item.icon" /><span>{{ item.label }}</span>
                </RouterLink>
            </nav>
        </ADrawer>
        <MockScenarioPanel v-if="showMockPanel" />
    </div>
</template>

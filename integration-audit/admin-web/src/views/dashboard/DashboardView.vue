<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ArrowRightOutlined, ClockCircleOutlined, ReloadOutlined } from '@ant-design/icons-vue';
import { Button as AButton, Skeleton as ASkeleton } from 'ant-design-vue';

import type { AlertRecord, DashboardSummary, RiskLevel } from '@/domain/monitoring';
import { useMonitoringRepository } from '@/repositories/monitoringRepository';
import DictTag from '@/components/jeecg/DictTag.vue';
import RiskTrendChart from './RiskTrendChart.vue';

const repository = useMonitoringRepository();
const router = useRouter();
const loading = ref(true);
const summary = ref<DashboardSummary>();
const priorityAlerts = ref<AlertRecord[]>([]);
const updatedAt = ref('');
const riskLabels: Record<RiskLevel, string> = { CRITICAL: '严重', HIGH: '高危', MEDIUM: '中危', LOW: '低危' };
const riskColors: Record<RiskLevel, string> = { CRITICAL: '#b72f2d', HIGH: '#d15a3c', MEDIUM: '#d4942f', LOW: '#27887e' };
const riskOptions = Object.entries(riskLabels).map(([value, label]) => ({ value, label, color: riskColors[value as RiskLevel] }));
const maxRiskCount = computed(() => Math.max(1, ...Object.values(summary.value?.riskDistribution ?? {})));
const maxRuleCount = computed(() => Math.max(1, ...(summary.value?.ruleContribution.map((item) => item.count) ?? [1])));

async function load(): Promise<void> {
    loading.value = true;
    try {
        summary.value = await repository.dashboard();
        const alerts = await Promise.all(summary.value.priorityAlertIds.map((id) => repository.getAlert(id)));
        priorityAlerts.value = alerts;
        updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
    } finally {
        loading.value = false;
    }
}

function openHighRiskAlerts(): void {
    void router.push({ path: '/alerts', query: { risk: 'HIGH' } });
}

onMounted(() => void load());
</script>

<template>
    <section class="page-section dashboard-view">
        <header class="page-heading dashboard-heading">
            <div><h1>今日风险运营</h1><p><ClockCircleOutlined /> 最后同步 {{ updatedAt || '--:--:--' }} · Mock 确定性数据</p></div>
            <AButton :loading="loading" @click="load"><ReloadOutlined />刷新</AButton>
        </header>

        <ASkeleton v-if="loading && !summary" active />
        <template v-else-if="summary">
            <div class="metric-band">
                <button class="metric-item metric-item--danger" data-testid="high-risk-alerts" @click="openHighRiskAlerts">
                    <span>高危待办</span><strong>{{ summary.metrics.openAlerts }}</strong><small>进入告警队列 <ArrowRightOutlined /></small>
                </button>
                <div class="metric-item"><span>今日事件采集</span><strong>{{ summary.metrics.eventsToday }}</strong><small>全链路审计事件</small></div>
                <div class="metric-item"><span>高风险主体</span><strong>{{ summary.metrics.highRiskSubjects }}</strong><small>严重与高危主体去重</small></div>
                <div class="metric-item metric-item--success"><span>控制成功率</span><strong>{{ summary.metrics.controlSuccessRate }}%</strong><small>处置任务执行结果</small></div>
            </div>

            <div class="dashboard-grid dashboard-grid--primary">
                <section class="surface-panel dashboard-panel dashboard-panel--trend">
                    <header class="panel-heading"><div><h2>风险趋势</h2><p>各时点异常事件数量，数值直接标注</p></div><span class="time-pill">近 24 小时</span></header>
                    <RiskTrendChart :points="summary.riskTrend" />
                </section>
                <section class="surface-panel dashboard-panel">
                    <header class="panel-heading"><div><h2>风险等级分布</h2><p>按当前风险级别聚合</p></div></header>
                    <div class="distribution-list">
                        <div v-for="(count, risk) in summary.riskDistribution" :key="risk" class="distribution-row">
                            <div><DictTag :value="risk" :options="riskOptions" /><strong>{{ count }}</strong></div>
                            <span class="distribution-track"><i :style="{ width: `${(count / maxRiskCount) * 100}%`, background: riskColors[risk] }" /></span>
                        </div>
                    </div>
                </section>
            </div>

            <div class="dashboard-grid dashboard-grid--secondary">
                <section class="surface-panel dashboard-panel priority-panel">
                    <header class="panel-heading"><div><h2>重点风险队列</h2><p>优先展示严重及高危未闭环告警</p></div><AButton type="link" @click="router.push('/alerts')">查看全部</AButton></header>
                    <div class="priority-list">
                        <button v-for="alert in priorityAlerts" :key="alert.id" class="priority-row" @click="router.push({ path: '/alerts', query: { alertId: alert.id } })">
                            <DictTag :value="alert.risk" :options="riskOptions" />
                            <span><strong>{{ alert.title }}</strong><small>{{ alert.id }} · {{ alert.subject }}</small></span>
                            <b>{{ alert.occurrenceCount }} 次</b><ArrowRightOutlined />
                        </button>
                        <div v-if="priorityAlerts.length === 0" class="empty-inline">当前无高优先级告警</div>
                    </div>
                </section>
                <section class="surface-panel dashboard-panel">
                    <header class="panel-heading"><div><h2>规则贡献</h2><p>命中告警按规则排序</p></div></header>
                    <div class="rule-bars">
                        <div v-for="item in summary.ruleContribution" :key="item.ruleId" class="rule-bar-row">
                            <div><span>{{ item.ruleId }}</span><strong>{{ item.count }}</strong></div>
                            <span><i :style="{ width: `${(item.count / maxRuleCount) * 100}%` }" /></span>
                        </div>
                    </div>
                </section>
            </div>
        </template>
    </section>
</template>

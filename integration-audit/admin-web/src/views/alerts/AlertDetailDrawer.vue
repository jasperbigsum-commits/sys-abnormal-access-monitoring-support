<script setup lang="ts">
import { computed } from 'vue';
import { Button as AButton, Descriptions as ADescriptions, DescriptionsItem as ADescriptionsItem, Timeline as ATimeline, TimelineItem as ATimelineItem } from 'ant-design-vue';

import BasicDrawer from '@/components/jeecg/BasicDrawer.vue';
import DictTag from '@/components/jeecg/DictTag.vue';
import type { AlertAction, AlertRecord } from '@/domain/monitoring';
import { alertStatusOptions, availableAlertActions, riskOptions } from './alertSchemas';

const props = withDefaults(defineProps<{ open: boolean; record?: AlertRecord }>(), { record: undefined });
const emit = defineEmits<{ 'update:open': [value: boolean]; action: [action: AlertAction] }>();
const actions = computed(() => props.record ? availableAlertActions(props.record.status) : []);
</script>

<template>
    <BasicDrawer :open="open" title="告警研判详情" :width="780" @update:open="emit('update:open', $event)" @close="emit('update:open', false)">
        <template v-if="record">
            <div class="drawer-risk-title"><div><DictTag :value="record.risk" :options="riskOptions" /><h2>{{ record.title }}</h2></div><DictTag :value="record.status" :options="alertStatusOptions" /></div>
            <ADescriptions bordered :column="2" size="small">
                <ADescriptionsItem label="告警编号">{{ record.id }}</ADescriptionsItem>
                <ADescriptionsItem label="规则编号">{{ record.ruleId }}</ADescriptionsItem>
                <ADescriptionsItem label="访问主体">{{ record.subject }}</ADescriptionsItem>
                <ADescriptionsItem label="负责人">{{ record.assigneeId ?? '未分派' }}</ADescriptionsItem>
                <ADescriptionsItem label="首次发生">{{ record.firstSeenAt }}</ADescriptionsItem>
                <ADescriptionsItem label="最近发生">{{ record.lastSeenAt }}</ADescriptionsItem>
                <ADescriptionsItem label="累计次数">{{ record.occurrenceCount }}</ADescriptionsItem>
                <ADescriptionsItem label="数据版本">v{{ record.version }}</ADescriptionsItem>
            </ADescriptions>

            <section class="drawer-section"><h3>检测证据</h3><dl class="fact-list"><template v-for="(value, key) in record.evidence" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl></section>
            <section class="drawer-section"><h3>关联事件</h3><div class="link-list"><span v-for="id in record.eventIds" :key="id">{{ id }}</span></div></section>
            <section class="drawer-section"><h3>处置时间线</h3><ATimeline><ATimelineItem v-for="entry in [...record.timeline].reverse()" :key="entry.id" color="green"><strong>{{ entry.action }}</strong><p>{{ entry.reason }}</p><small>{{ entry.operatorId }} · {{ entry.occurredAt }}</small></ATimelineItem></ATimeline></section>
        </template>
        <template #footer>
            <div class="drawer-actions"><AButton v-for="item in actions" :key="item.action" :danger="item.action === 'FALSE_POSITIVE'" :type="item.action === 'ACKNOWLEDGE' || item.action === 'INVESTIGATE' ? 'primary' : 'default'" :data-testid="item.action.toLowerCase()" @click="emit('action', item.action)">{{ item.label }}</AButton></div>
        </template>
    </BasicDrawer>
</template>

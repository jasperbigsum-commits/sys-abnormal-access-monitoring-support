<script setup lang="ts">
import { computed } from 'vue';
import { Button as AButton, Descriptions as ADescriptions, DescriptionsItem as ADescriptionsItem, Timeline as ATimeline, TimelineItem as ATimelineItem } from 'ant-design-vue';
import BasicDrawer from '@/components/jeecg/BasicDrawer.vue';
import DictTag from '@/components/jeecg/DictTag.vue';
import type { ControlAction, ControlRecord } from '@/domain/monitoring';
import { availableControlActions, controlStatusOptions } from './controlSchemas';

const props = withDefaults(defineProps<{ open: boolean; record?: ControlRecord }>(), { record: undefined });
const emit = defineEmits<{ 'update:open': [value: boolean]; action: [value: ControlAction] }>();
const actions = computed(() => props.record ? availableControlActions(props.record.status) : []);
</script>

<template>
    <BasicDrawer :open="open" title="控制任务详情" :width="760" @update:open="emit('update:open', $event)" @close="emit('update:open', false)">
        <template v-if="record">
            <div class="drawer-risk-title"><div><h2>{{ record.action }}</h2><span>{{ record.subject }}</span></div><DictTag :value="record.status" :options="controlStatusOptions" /></div>
            <ADescriptions bordered :column="2" size="small">
                <ADescriptionsItem label="任务编号">{{ record.id }}</ADescriptionsItem><ADescriptionsItem label="数据版本">v{{ record.version }}</ADescriptionsItem>
                <ADescriptionsItem label="来源告警">{{ record.alertId || '手工处置' }}</ADescriptionsItem><ADescriptionsItem label="规则编号">{{ record.ruleId }}</ADescriptionsItem>
                <ADescriptionsItem label="到期时间">{{ record.expiresAt ?? '长期有效' }}</ADescriptionsItem><ADescriptionsItem label="失败原因">{{ record.failureReason ?? '无' }}</ADescriptionsItem>
            </ADescriptions>
            <section class="drawer-section"><h3>执行尝试</h3><ATimeline><ATimelineItem v-for="attempt in [...record.attempts].reverse()" :key="attempt.attempt" :color="attempt.status === 'FAILED' ? 'red' : 'green'"><strong>第 {{ attempt.attempt }} 次 · {{ attempt.status }}</strong><p v-if="attempt.failureReason">{{ attempt.failureReason }}</p><small>{{ attempt.occurredAt }}</small></ATimelineItem></ATimeline></section>
        </template>
        <template #footer><div class="drawer-actions"><AButton v-for="item in actions" :key="item.action" :danger="item.danger" :type="item.action === 'APPROVE' || item.action === 'RETRY' ? 'primary' : 'default'" @click="emit('action', item.action)">{{ item.label }}</AButton></div></template>
    </BasicDrawer>
</template>

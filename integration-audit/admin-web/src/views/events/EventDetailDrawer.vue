<script setup lang="ts">
import { Descriptions as ADescriptions, DescriptionsItem as ADescriptionsItem, Tag as ATag } from 'ant-design-vue';
import type { EventRecord } from '@/domain/monitoring';
import BasicDrawer from '@/components/jeecg/BasicDrawer.vue';

withDefaults(defineProps<{ open: boolean; record?: EventRecord }>(), { record: undefined });
const emit = defineEmits<{ 'update:open': [value: boolean] }>();
</script>

<template>
    <BasicDrawer :open="open" title="事件证据详情" :width="720" @update:open="emit('update:open', $event)">
        <template v-if="record">
            <ADescriptions bordered :column="1" size="small">
                <ADescriptionsItem label="事件编号">{{ record.id }}</ADescriptionsItem>
                <ADescriptionsItem label="动作类型">{{ record.actionCode }}</ADescriptionsItem>
                <ADescriptionsItem label="访问主体">{{ record.subject }}</ADescriptionsItem>
                <ADescriptionsItem label="资源">{{ record.resourceId }}</ADescriptionsItem>
                <ADescriptionsItem label="结果">{{ record.result }}</ADescriptionsItem>
                <ADescriptionsItem label="发生时间">{{ record.occurredAt }}</ADescriptionsItem>
            </ADescriptions>
            <section class="drawer-section"><h3>可信事实</h3><dl class="fact-list"><template v-for="(value, key) in record.facts" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl></section>
            <section class="drawer-section"><h3>关联告警</h3><div class="tag-list"><ATag v-for="id in record.alertIds" :key="id" color="orange">{{ id }}</ATag><span v-if="!record.alertIds.length">无</span></div></section>
        </template>
    </BasicDrawer>
</template>

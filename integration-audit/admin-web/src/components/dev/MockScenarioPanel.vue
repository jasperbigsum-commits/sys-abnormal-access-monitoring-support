<script setup lang="ts">
import { BugOutlined, CloseOutlined, ExperimentOutlined } from '@ant-design/icons-vue';
import { Button as AButton, Select as ASelect, Tooltip as ATooltip } from 'ant-design-vue';
import { ref } from 'vue';

import { readMockPreset, type MockPreset } from '@/mocks/presets';

const open = ref(false);
const selected = ref<MockPreset>(readMockPreset());
const options: Array<{ label: string; value: MockPreset }> = [
    { label: '默认数据', value: 'default' },
    { label: '空数据', value: 'empty' },
    { label: '慢响应', value: 'slow' },
    { label: '无权限', value: 'forbidden' },
    { label: '服务不可用', value: 'unavailable' },
    { label: '版本冲突', value: 'conflict' },
    { label: '未定义错误', value: 'unknown' }
];

function apply(): void {
    sessionStorage.setItem('audit-mock-preset', selected.value);
    window.location.reload();
}
</script>

<template>
    <div class="mock-scenario" :class="{ 'is-open': open }">
        <ATooltip v-if="!open" title="Mock 场景"><AButton class="mock-scenario__trigger" aria-label="打开 Mock 场景" @click="open = true"><BugOutlined /></AButton></ATooltip>
        <template v-else>
            <div class="mock-scenario__heading"><span><ExperimentOutlined /> 联调场景</span><AButton type="text" aria-label="关闭 Mock 场景" @click="open = false"><CloseOutlined /></AButton></div>
            <ASelect v-model:value="selected" :options="options" />
            <AButton type="primary" block @click="apply">应用场景</AButton>
        </template>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Tag as ATag } from 'ant-design-vue';

export interface DictOption {
    value: string | number;
    label: string;
    color?: string;
}

const props = defineProps<{ value: string | number; options: DictOption[] }>();
const option = computed(() => props.options.find((item) => item.value === props.value));
</script>

<template>
    <ATag class="jeecg-dict-tag" :color="option?.color" :data-known="Boolean(option)">
        {{ option?.label ?? value }}
    </ATag>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue';
import { Drawer as ADrawer } from 'ant-design-vue';

import type { OverlayInstance } from './useOverlay';

const props = withDefaults(defineProps<{ open?: boolean; title?: string; width?: number | string }>(), {
    open: false,
    title: '',
    width: 720
});
const emit = defineEmits<{ register: [instance: OverlayInstance<Record<string, unknown>>]; close: []; 'update:open': [open: boolean] }>();
const state = reactive({ open: props.open, title: props.title, width: props.width });
watch(() => props.open, (value) => { state.open = value; });

function setProps(next: Record<string, unknown> & { open?: boolean }): void {
    Object.assign(state, next);
    if (next.open !== undefined) emit('update:open', next.open);
}

emit('register', { setProps });
defineExpose({ setProps });
</script>

<template>
    <ADrawer v-model:open="state.open" :title="state.title" :width="state.width" destroy-on-close @close="emit('close')">
        <slot />
        <template v-if="$slots.extra" #extra><slot name="extra" /></template>
        <template v-if="$slots.footer" #footer><slot name="footer" /></template>
    </ADrawer>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue';
import { Modal as AModal } from 'ant-design-vue';

import type { OverlayInstance } from './useOverlay';

const props = withDefaults(defineProps<{ open?: boolean; title?: string; width?: number | string; confirmLoading?: boolean; showOk?: boolean }>(), {
    open: false,
    title: '',
    width: 560,
    confirmLoading: false,
    showOk: true
});
const emit = defineEmits<{ register: [instance: OverlayInstance<Record<string, unknown>>]; ok: []; cancel: []; 'update:open': [open: boolean] }>();
const state = reactive({ open: props.open, title: props.title, width: props.width, confirmLoading: props.confirmLoading });
watch(() => props.open, (value) => { state.open = value; });
watch(() => props.title, (value) => { state.title = value; });
watch(() => props.width, (value) => { state.width = value; });
watch(() => props.confirmLoading, (value) => { state.confirmLoading = value; });

function setProps(next: Record<string, unknown> & { open?: boolean }): void {
    Object.assign(state, next);
    if (next.open !== undefined) emit('update:open', next.open);
}

emit('register', { setProps });
defineExpose({ setProps });
</script>

<template>
    <AModal
        v-model:open="state.open"
        :title="state.title"
        :width="state.width"
        :confirm-loading="state.confirmLoading"
        :footer="showOk ? undefined : null"
        destroy-on-close
        @ok="emit('ok')"
        @cancel="emit('cancel')"
    >
        <slot />
        <template v-if="$slots.footer" #footer><slot name="footer" /></template>
    </AModal>
</template>

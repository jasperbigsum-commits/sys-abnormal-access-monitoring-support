<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';

import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicModal from '@/components/jeecg/BasicModal.vue';
import type { FormSchema } from '@/components/jeecg/useForm';
import type { AlertAction, AlertRecord } from '@/domain/monitoring';
import { availableAlertActions } from './alertSchemas';

const props = withDefaults(defineProps<{ open: boolean; record?: AlertRecord; action?: AlertAction; loading?: boolean }>(), { record: undefined, action: undefined, loading: false });
const emit = defineEmits<{ 'update:open': [value: boolean]; submit: [value: { reason: string; assigneeId?: string }] }>();
const formRef = ref<{ validate: () => Promise<Record<string, unknown>>; resetFields: () => void }>();
const model = reactive<Record<string, unknown>>({ reason: '', assigneeId: undefined });
const actionLabel = computed(() => props.record && props.action ? availableAlertActions(props.record.status).find((item) => item.action === props.action)?.label ?? props.action : '告警操作');
const schemas = computed<FormSchema[]>(() => [
    ...(props.action === 'ASSIGN' ? [{ field: 'assigneeId', label: '负责人', component: 'Select' as const, required: true, options: [{ label: '分析员 01', value: 'analyst-01' }, { label: '分析员 02', value: 'analyst-02' }, { label: '分析员 03', value: 'analyst-03' }] }] : []),
    { field: 'reason', label: '操作原因', component: 'Textarea', required: true, colSpan: 24, componentProps: { rows: 4, minlength: 5, maxlength: 500, showCount: true, placeholder: '说明证据、判断和预期影响' } }
]);

watch(() => props.open, (open) => {
    if (open) {
        model.reason = '';
        model.assigneeId = props.record?.assigneeId;
        formRef.value?.resetFields();
    }
});

async function submit(): Promise<void> {
    const values = await formRef.value?.validate();
    if (!values) return;
    emit('submit', { reason: String(values.reason), assigneeId: values.assigneeId ? String(values.assigneeId) : undefined });
}
</script>

<template>
    <BasicModal :open="open" :title="`${actionLabel} · ${record?.id ?? ''}`" :confirm-loading="loading" @update:open="emit('update:open', $event)" @cancel="emit('update:open', false)" @ok="submit">
        <div class="operation-warning">所有操作将写入管理审计，请确认当前证据和数据版本。</div>
        <BasicForm ref="formRef" :schemas="schemas" :model-value="model" :columns="1" :label-width="88" @update:model-value="Object.assign(model, $event)" />
    </BasicModal>
</template>

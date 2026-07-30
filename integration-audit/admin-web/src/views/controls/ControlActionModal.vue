<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicModal from '@/components/jeecg/BasicModal.vue';
import type { FormSchema } from '@/components/jeecg/useForm';
import type { ControlAction, ControlRecord } from '@/domain/monitoring';

type ControlModalAction = ControlAction | 'EXECUTE';
const props = withDefaults(defineProps<{ open: boolean; record?: ControlRecord; action?: ControlModalAction; loading?: boolean }>(), { record: undefined, action: undefined, loading: false });
const emit = defineEmits<{ 'update:open': [value: boolean]; submit: [value: Record<string, unknown>] }>();
const formRef = ref<{ validate: () => Promise<Record<string, unknown>>; resetFields: () => void }>();
const model = reactive<Record<string, unknown>>({ subject: '', action: 'REVOKE_SESSION', ttlMinutes: 30, reason: '' });
const actionLabels: Record<ControlModalAction, string> = { APPROVE: '批准控制', REJECT: '驳回控制', RETRY: '重试执行', EXECUTE: '手工处置' };
const schemas = computed<FormSchema[]>(() => props.action === 'EXECUTE' ? [
    { field: 'subject', label: '目标主体', component: 'Input', required: true, componentProps: { placeholder: '账户或会话主体' } },
    { field: 'action', label: '控制动作', component: 'Select', required: true, options: [{ label: '撤销会话', value: 'REVOKE_SESSION' }, { label: '增强认证', value: 'STEP_UP_AUTH' }] },
    { field: 'ttlMinutes', label: '有效分钟', component: 'InputNumber', required: true, componentProps: { min: 1, max: 1440 } },
    { field: 'reason', label: '操作原因', component: 'Textarea', required: true, colSpan: 24, componentProps: { rows: 4, maxlength: 500, showCount: true } }
] : [{ field: 'reason', label: '审批原因', component: 'Textarea', required: true, colSpan: 24, componentProps: { rows: 4, maxlength: 500, showCount: true } }]);

watch(() => props.open, (open) => { if (open) { Object.assign(model, { subject: '', action: 'REVOKE_SESSION', ttlMinutes: 30, reason: '' }); formRef.value?.resetFields(); } });
async function submit(): Promise<void> { const values = await formRef.value?.validate(); if (values) emit('submit', values); }
</script>

<template>
    <BasicModal :open="open" :title="`${action ? actionLabels[action] : '控制操作'}${record ? ` · ${record.id}` : ''}`" :confirm-loading="loading" @update:open="emit('update:open', $event)" @cancel="emit('update:open', false)" @ok="submit">
        <div class="operation-warning">控制操作可能影响活动会话或账户访问，提交后将写入管理审计。</div>
        <BasicForm ref="formRef" :schemas="schemas" :model-value="model" :columns="1" :label-width="88" @update:model-value="Object.assign(model, $event)" />
    </BasicModal>
</template>

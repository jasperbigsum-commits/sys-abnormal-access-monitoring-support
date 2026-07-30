<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicModal from '@/components/jeecg/BasicModal.vue';
import type { FormSchema } from '@/components/jeecg/useForm';
import type { RuleRecord } from '@/domain/monitoring';
import { ruleModeOptions } from './governanceSchemas';

const props = withDefaults(defineProps<{ open: boolean; record?: RuleRecord; loading?: boolean }>(), {
    record: undefined,
    loading: false,
});
const emit = defineEmits<{ 'update:open': [value: boolean]; submit: [value: Record<string, unknown>] }>();
const formRef = ref<{ validate: () => Promise<Record<string, unknown>> }>();
const model = reactive<Record<string, unknown>>({});
const schemas = computed<FormSchema[]>(() => [
    { field: 'mode', label: '运行模式', component: 'Select', required: true, options: ruleModeOptions },
    {
        field: 'threshold',
        label: '检测阈值',
        component: 'InputNumber',
        required: true,
        componentProps: { min: 1, max: 100000 },
    },
    {
        field: 'approverId',
        label: '审批人',
        component: 'Select',
        required: true,
        options: [
            { label: '审批人 01', value: 'risk-approver-01' },
            { label: '审批人 02', value: 'risk-approver-02' },
            { label: '审批人 03', value: 'risk-approver-03' },
        ],
    },
    {
        field: 'reason',
        label: '变更原因',
        component: 'Textarea',
        required: true,
        colSpan: 24,
        componentProps: { rows: 4, minlength: 5, maxlength: 500, showCount: true },
    },
]);
watch(
    () => props.open,
    (open) => {
        if (open && props.record)
            Object.assign(model, {
                mode: props.record.mode,
                threshold: props.record.threshold,
                approverId: undefined,
                reason: '',
            });
    },
);
async function submit(): Promise<void> {
    const values = await formRef.value?.validate();
    if (values) emit('submit', values);
}
</script>
<template>
    <BasicModal
        :open="open"
        :title="`变更规则 · ${record?.id ?? ''}`"
        :confirm-loading="loading"
        @update:open="emit('update:open', $event)"
        @cancel="emit('update:open', false)"
        @ok="submit"
        ><div class="operation-warning">持久化版本更新后不会替换已冻结的运行时规则，仍需受控发布或重启后生效。</div>
        <BasicForm
            ref="formRef"
            :schemas="schemas"
            :model-value="model"
            :columns="1"
            :label-width="88"
            @update:model-value="Object.assign(model, $event)"
    /></BasicModal>
</template>

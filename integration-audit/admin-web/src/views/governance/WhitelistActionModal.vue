<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import BasicForm from '@/components/jeecg/BasicForm.vue';
import BasicModal from '@/components/jeecg/BasicModal.vue';
import type { FormSchema } from '@/components/jeecg/useForm';
import type { WhitelistRecord } from '@/domain/monitoring';
const props = withDefaults(defineProps<{ open: boolean; record?: WhitelistRecord; loading?: boolean }>(), {
    record: undefined,
    loading: false,
});
const emit = defineEmits<{ 'update:open': [value: boolean]; submit: [reason: string] }>();
const formRef = ref<{ validate: () => Promise<Record<string, unknown>> }>();
const model = reactive<Record<string, unknown>>({ reason: '' });
const action = computed(() => (props.record?.status === 'ACTIVE' ? 'REVOKE' : 'GRANT'));
const schemas: FormSchema[] = [
    {
        field: 'reason',
        label: '操作原因',
        component: 'Textarea',
        required: true,
        colSpan: 24,
        componentProps: { rows: 4, maxlength: 500, showCount: true },
    },
];
watch(
    () => props.open,
    (open) => {
        if (open) model.reason = '';
    },
);
async function submit(): Promise<void> {
    const values = await formRef.value?.validate();
    if (values) emit('submit', String(values.reason));
}
</script>
<template>
    <BasicModal
        :open="open"
        :title="`${action === 'GRANT' ? '授予' : '撤销'}白名单 · ${record?.id ?? ''}`"
        :confirm-loading="loading"
        @update:open="emit('update:open', $event)"
        @cancel="emit('update:open', false)"
        @ok="submit"
        ><div class="operation-warning">白名单会影响检测例外范围，请确认主体、规则和有效期。</div>
        <BasicForm
            ref="formRef"
            :schemas="schemas"
            :model-value="model"
            :columns="1"
            :label-width="88"
            @update:model-value="Object.assign(model, $event)"
    /></BasicModal>
</template>

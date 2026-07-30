<script setup lang="ts">
import { computed, reactive, ref, watch, type Component } from 'vue';
import {
    Col as ACol,
    DatePicker as ADatePicker,
    Form as AForm,
    FormItem as AFormItem,
    Input as AInput,
    InputNumber as AInputNumber,
    RangePicker as ARangePicker,
    Row as ARow,
    Select as ASelect,
    SelectOption as ASelectOption
} from 'ant-design-vue';
import type { FormInstance } from 'ant-design-vue';

import type { FormSchema } from './useForm';

const props = withDefaults(defineProps<{
    schemas: FormSchema[];
    modelValue?: Record<string, unknown>;
    labelWidth?: number;
    columns?: number;
}>(), {
    modelValue: () => ({}),
    labelWidth: 96,
    columns: 3
});

const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>();
const formRef = ref<FormInstance>();
const model = reactive<Record<string, unknown>>({ ...props.modelValue });
const initialValues = { ...props.modelValue };
const componentMap: Record<Exclude<FormSchema['component'], 'Slot'>, Component> = {
    Input: AInput,
    Textarea: AInput.TextArea,
    Select: ASelect,
    DatePicker: ADatePicker,
    RangePicker: ARangePicker,
    InputNumber: AInputNumber
};
const defaultSpan = computed(() => Math.max(1, Math.floor(24 / props.columns)));

watch(() => props.modelValue, (value) => Object.assign(model, value), { deep: true });
watch(model, (value) => emit('update:modelValue', { ...value }), { deep: true });

async function validate(): Promise<Record<string, unknown>> {
    await formRef.value?.validate();
    return { ...model };
}

function resetFields(): void {
    for (const key of Object.keys(model)) delete model[key];
    Object.assign(model, initialValues);
    formRef.value?.clearValidate();
}

function setFieldsValue(values: Record<string, unknown>): void {
    Object.assign(model, values);
}

function getFieldsValue(): Record<string, unknown> {
    return { ...model };
}

defineExpose({ validate, resetFields, setFieldsValue, getFieldsValue });
</script>

<template>
    <AForm ref="formRef" :model="model" :label-col="{ style: { width: `${labelWidth}px` } }" class="jeecg-basic-form">
        <ARow :gutter="16">
            <ACol v-for="schema in schemas" :key="schema.field" :span="schema.colSpan ?? defaultSpan">
                <AFormItem
                    :label="schema.label"
                    :name="schema.field"
                    :rules="schema.required ? [{ required: true, message: `请输入或选择${schema.label}` }] : []"
                >
                    <slot v-if="schema.component === 'Slot'" :name="schema.slot ?? schema.field" :model="model" />
                    <component
                        :is="componentMap[schema.component as Exclude<FormSchema['component'], 'Slot'>]"
                        v-else
                        v-model:value="model[schema.field]"
                        v-bind="schema.componentProps"
                        style="width: 100%"
                    >
                        <ASelectOption v-for="option in schema.options" :key="option.value" :value="option.value" :disabled="option.disabled">
                            {{ option.label }}
                        </ASelectOption>
                    </component>
                </AFormItem>
            </ACol>
            <ACol v-if="$slots.action" :span="defaultSpan" class="jeecg-basic-form__actions">
                <slot name="action" :model="model" />
            </ACol>
        </ARow>
    </AForm>
</template>

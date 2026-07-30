import { reactive } from 'vue';

export type FormComponentType = 'Input' | 'Textarea' | 'Select' | 'DatePicker' | 'RangePicker' | 'InputNumber';

export interface FormOption {
    label: string;
    value: string | number;
    disabled?: boolean;
}

export interface FormSchema {
    field: string;
    label: string;
    component: FormComponentType | 'Slot';
    required?: boolean;
    componentProps?: Record<string, unknown>;
    options?: FormOption[];
    slot?: string;
    colSpan?: number;
}

export interface FormAction {
    validate: () => Promise<Record<string, unknown>>;
    resetFields: () => void;
    setFieldsValue: (values: Record<string, unknown>) => void;
    getFieldsValue: () => Record<string, unknown>;
}

export function useForm(initialValues: Record<string, unknown> = {}) {
    const model = reactive({ ...initialValues });
    let action: FormAction | undefined;
    const register = (instance: FormAction) => { action = instance; };
    return [register, {
        model,
        validate: () => action?.validate() ?? Promise.resolve({ ...model }),
        resetFields: () => action?.resetFields(),
        setFieldsValue: (values: Record<string, unknown>) => action?.setFieldsValue(values),
        getFieldsValue: () => action?.getFieldsValue() ?? { ...model }
    }] as const;
}

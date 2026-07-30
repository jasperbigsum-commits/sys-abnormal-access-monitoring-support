import axios, { type AxiosInstance } from 'axios';

import { normalizeBackendError, normalizeHttpError } from './normalizeError';
import type { DefHttpRequestConfig, ErrorResult, Result } from './types';

function fixtureHeaders(): Record<string, string> {
    const headers: Record<string, string> = {};
    if (!import.meta.env.DEV) return headers;
    if (import.meta.env.VITE_AUDIT_PRINCIPAL) headers['X-Audit-Principal'] = import.meta.env.VITE_AUDIT_PRINCIPAL;
    if (import.meta.env.VITE_AUDIT_APPROVER) headers['X-Audit-Approver'] = import.meta.env.VITE_AUDIT_APPROVER;
    return headers;
}

export const axiosInstance: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/audit/management',
    timeout: 15_000,
    withCredentials: false,
    headers: fixtureHeaders()
});

async function request<T>(config: DefHttpRequestConfig): Promise<T> {
    try {
        const response = await axiosInstance.request<Result<T> | T>(config);
        if (config.requestOptions?.isTransformResponse === false) return response.data as T;
        const envelope = response.data as Result<T>;
        if (envelope && envelope.success === true && envelope.code >= 200 && envelope.code < 300) return envelope.result;
        if (envelope && envelope.success === false) {
            const payload = typeof envelope.result === 'object' && envelope.result !== null ? envelope.result as ErrorResult : {};
            throw normalizeBackendError(envelope.code, envelope.message, payload);
        }
        return response.data as T;
    } catch (error) {
        throw normalizeHttpError(error);
    }
}

export const defHttp = {
    get<T>(config: DefHttpRequestConfig): Promise<T> {
        return request<T>({ ...config, method: 'GET' });
    },
    post<T>(config: DefHttpRequestConfig): Promise<T> {
        return request<T>({ ...config, method: 'POST' });
    },
    put<T>(config: DefHttpRequestConfig): Promise<T> {
        return request<T>({ ...config, method: 'PUT' });
    },
    delete<T>(config: DefHttpRequestConfig): Promise<T> {
        return request<T>({ ...config, method: 'DELETE' });
    }
};

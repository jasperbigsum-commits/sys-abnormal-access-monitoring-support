import axios, { type AxiosError } from 'axios';

import { ManagementError, managementError, type ManagementErrorCategory } from '@/domain/errors';
import type { ErrorResult, Result } from './types';

const knownTypes: Readonly<Record<string, ManagementErrorCategory>> = {
    UNAUTHORIZED: 'UNAUTHORIZED',
    FORBIDDEN: 'FORBIDDEN',
    NOT_FOUND: 'NOT_FOUND',
    VALIDATION: 'VALIDATION',
    CONFLICT: 'CONFLICT',
    INVALID_TRANSITION: 'INVALID_TRANSITION',
    RATE_LIMITED: 'RATE_LIMITED',
    UNAVAILABLE: 'UNAVAILABLE'
};

const statusCategories: Readonly<Record<number, ManagementErrorCategory>> = {
    401: 'UNAUTHORIZED',
    403: 'FORBIDDEN',
    404: 'NOT_FOUND',
    409: 'CONFLICT',
    422: 'VALIDATION',
    429: 'RATE_LIMITED',
    503: 'UNAVAILABLE'
};

function isResult(value: unknown): value is Partial<Result<ErrorResult>> {
    return typeof value === 'object' && value !== null && ('success' in value || 'result' in value);
}

function errorPayload(error: AxiosError): { message?: string; result: ErrorResult } {
    const data = error.response?.data;
    if (isResult(data)) {
        return {
            message: typeof data.message === 'string' ? data.message : undefined,
            result: data.result && typeof data.result === 'object' ? data.result : {}
        };
    }
    if (typeof data === 'object' && data !== null) {
        const raw = data as ErrorResult & { message?: string; status?: string };
        return {
            message: raw.message,
            result: {
                errorType: raw.errorType ?? raw.status,
                errorCode: raw.errorCode,
                requestId: raw.requestId,
                details: raw.details
            }
        };
    }
    return { result: {} };
}

export function normalizeBackendError(status: number, message: string, payload: ErrorResult): ManagementError {
    const originalType = payload.errorType;
    const category = originalType ? knownTypes[originalType] || 'UNKNOWN' : statusCategories[status] || 'UNKNOWN';
    return managementError({
        category,
        message: message || '请求处理失败',
        originalType,
        errorCode: payload.errorCode,
        status,
        requestId: payload.requestId,
        details: payload.details
    });
}

export function normalizeHttpError(error: unknown): ManagementError {
    if (error instanceof ManagementError) return error;
    if (axios.isCancel(error)) {
        return managementError({ category: 'CANCELLED', message: '请求已取消' });
    }
    if (!axios.isAxiosError(error)) {
        return managementError({ category: 'UNKNOWN', message: error instanceof Error ? error.message : '未知请求错误' });
    }

    const status = error.response?.status;
    const payload = errorPayload(error);
    if (status) return normalizeBackendError(status, payload.message || '请求处理失败', payload.result);
    return managementError({ category: 'NETWORK', message: '网络连接失败，请检查服务状态' });
}

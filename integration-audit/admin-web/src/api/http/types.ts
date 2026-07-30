import type { AxiosRequestConfig } from 'axios';

export interface Result<T> {
    success: boolean;
    code: number;
    message: string;
    result: T;
    timestamp: number;
}

export interface ErrorResult {
    errorType?: string;
    errorCode?: string;
    requestId?: string;
    details?: Readonly<Record<string, unknown>>;
}

export interface RequestOptions {
    isTransformResponse?: boolean;
    errorMessageMode?: 'none' | 'message';
}

export interface DefHttpRequestConfig extends AxiosRequestConfig {
    url: string;
    requestOptions?: RequestOptions;
}

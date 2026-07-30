import AxiosMockAdapter from 'axios-mock-adapter';
import { afterEach, describe, expect, it } from 'vitest';

import { axiosInstance, defHttp } from './axios';

const adapter = new AxiosMockAdapter(axiosInstance);

afterEach(() => adapter.reset());

describe('defHttp', () => {
    it('unwraps a successful Jeecg result envelope', async () => {
        adapter.onGet('/ok').reply(200, { success: true, code: 200, message: '操作成功', result: { id: 'A' }, timestamp: 1 });
        await expect(defHttp.get<{ id: string }>({ url: '/ok' })).resolves.toEqual({ id: 'A' });
    });

    it.each([
        [401, 'UNAUTHORIZED'], [403, 'FORBIDDEN'], [404, 'NOT_FOUND'], [409, 'CONFLICT'],
        [422, 'VALIDATION'], [429, 'RATE_LIMITED'], [503, 'UNAVAILABLE']
    ])('normalizes HTTP %s as %s', async (status, category) => {
        adapter.onGet('/failure').reply(status, { success: false, code: status, message: '拒绝', result: {}, timestamp: 1 });
        await expect(defHttp.get({ url: '/failure' })).rejects.toMatchObject({ category, status });
    });

    it('preserves an unknown backend error type', async () => {
        adapter.onGet('/unknown').reply(422, {
            success: false,
            code: 422,
            message: '策略锁定',
            result: { errorType: 'POLICY_LOCKED', errorCode: 'MON-999', requestId: 'req-1', details: { policy: 'P1' } },
            timestamp: 1
        });
        await expect(defHttp.get({ url: '/unknown' })).rejects.toMatchObject({
            category: 'UNKNOWN', originalType: 'POLICY_LOCKED', errorCode: 'MON-999', requestId: 'req-1', details: { policy: 'P1' }
        });
    });

    it('normalizes a Jeecg failure returned with HTTP 200', async () => {
        adapter.onGet('/business-failure').reply(200, {
            success: false, code: 409, message: '版本冲突', result: { errorType: 'CONFLICT', requestId: 'req-2' }, timestamp: 1
        });
        await expect(defHttp.get({ url: '/business-failure' })).rejects.toMatchObject({ category: 'CONFLICT', status: 409, requestId: 'req-2' });
    });

    it('normalizes network and cancellation failures', async () => {
        adapter.onGet('/network').networkError();
        await expect(defHttp.get({ url: '/network' })).rejects.toMatchObject({ category: 'NETWORK' });

        adapter.onGet('/cancel').reply(() => { throw new DOMException('cancelled', 'AbortError'); });
        const controller = new AbortController();
        controller.abort();
        await expect(defHttp.get({ url: '/cancel', signal: controller.signal })).rejects.toMatchObject({ category: 'CANCELLED' });
    });
});

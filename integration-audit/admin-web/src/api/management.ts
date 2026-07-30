import { defHttp } from './http/axios';

export interface ManagementPage<T> {
    items: T[];
    page: number;
    size: number;
    totalElements: number;
}

export const managementApi = {
    dashboard: <T>(params: object) => defHttp.get<T>({ url: '/dashboard', params }),
    alerts: <T>(params: object) => defHttp.get<ManagementPage<T>>({ url: '/alerts', params }),
    alert: <T>(id: string) => defHttp.get<T>({ url: `/alerts/${encodeURIComponent(id)}` }),
    alertTransition: <T>(id: string, action: string, data: object) => defHttp.post<T>({ url: `/alerts/${encodeURIComponent(id)}/${action}`, data }),
    events: <T>(params: object) => defHttp.get<ManagementPage<T>>({ url: '/events', params }),
    event: <T>(id: string) => defHttp.get<T>({ url: `/events/${encodeURIComponent(id)}` }),
    controls: <T>(params: object) => defHttp.get<ManagementPage<T>>({ url: '/controls', params }),
    controlTransition: <T>(id: string, action: string, data: object) => defHttp.post<T>({ url: `/controls/${encodeURIComponent(id)}/${action}`, data }),
    executeControl: <T>(data: object) => defHttp.post<T>({ url: '/controls/execute', data }),
    rules: <T>(params: object) => defHttp.get<ManagementPage<T>>({ url: '/rules', params }),
    changeRule: <T>(id: string, data: object) => defHttp.post<T>({ url: `/rules/${encodeURIComponent(id)}/versions`, data }),
    whitelists: <T>(params: object) => defHttp.get<ManagementPage<T>>({ url: '/whitelists', params }),
    whitelistTransition: <T>(id: string, action: string, data: object) => defHttp.post<T>({ url: `/whitelists/${encodeURIComponent(id)}/${action}`, data }),
    managementAudit: <T>(params: object) => defHttp.get<ManagementPage<T>>({ url: '/audit-log', params })
};

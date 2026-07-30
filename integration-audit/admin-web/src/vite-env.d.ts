/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_DATA_MODE?: 'mock' | 'http';
    readonly VITE_API_BASE_URL?: string;
    readonly VITE_AUDIT_PRINCIPAL?: string;
    readonly VITE_AUDIT_APPROVER?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}

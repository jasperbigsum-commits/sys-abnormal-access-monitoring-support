export type ManagementErrorCategory =
    | 'UNAUTHORIZED'
    | 'FORBIDDEN'
    | 'NOT_FOUND'
    | 'VALIDATION'
    | 'CONFLICT'
    | 'INVALID_TRANSITION'
    | 'RATE_LIMITED'
    | 'UNAVAILABLE'
    | 'NETWORK'
    | 'CANCELLED'
    | 'UNKNOWN';

export interface ManagementErrorInput {
    category: ManagementErrorCategory;
    message: string;
    originalType?: string;
    errorCode?: string;
    status?: number;
    requestId?: string;
    details?: Readonly<Record<string, unknown>>;
}

export class ManagementError extends Error {
    readonly category: ManagementErrorCategory;
    readonly originalType?: string;
    readonly errorCode?: string;
    readonly status?: number;
    readonly requestId?: string;
    readonly details: Readonly<Record<string, unknown>>;

    constructor(input: ManagementErrorInput) {
        super(input.message);
        this.name = 'ManagementError';
        this.category = input.category;
        this.originalType = input.originalType;
        this.errorCode = input.errorCode;
        this.status = input.status;
        this.requestId = input.requestId;
        this.details = Object.freeze({ ...input.details });
    }
}

export function managementError(input: ManagementErrorInput): ManagementError {
    return new ManagementError(input);
}

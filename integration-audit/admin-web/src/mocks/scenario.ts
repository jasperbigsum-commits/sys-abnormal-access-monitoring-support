import { managementError, type ManagementErrorInput } from '@/domain/errors';
import type { MonitoringRepository } from '@/repositories/monitoringRepository';

export type MockOperation = keyof MonitoringRepository;

export interface MockMonitoringRepositoryOptions {
    delayMs?: number;
    failures?: Partial<Record<MockOperation, ManagementErrorInput>>;
}

export class MockScenario {
    constructor(private options: MockMonitoringRepositoryOptions = {}) {}

    configure(options: MockMonitoringRepositoryOptions): void {
        this.options = options;
    }

    async prepare(operation: MockOperation): Promise<void> {
        const delayMs = Math.max(0, this.options.delayMs ?? 0);
        if (delayMs > 0) {
            await new Promise((resolve) => setTimeout(resolve, delayMs));
        }
        const failure = this.options.failures?.[operation];
        if (failure) {
            throw managementError(failure);
        }
    }
}

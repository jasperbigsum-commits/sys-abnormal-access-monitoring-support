# Feature and Optimization Roadmap

[Back to the English README](../README.en.md)

| Milestone | Priority | Scope | Completion signal |
| --- | --- | --- | --- |
| M0: Error contract and integration docs | Now | Stable codes, compatible exceptions, bilingual guides, diagrams, and roadmap | Full reactor passes; both READMEs link to every public guide. |
| M1: Observability and data governance | Next | Metrics SPI, health guidance, retention, and archival policy | Operators can measure persistence, notifications, control outcomes, and data age. |
| M2: Reliable external delivery | Later | Approved transactional-outbox adapter and idempotent notification delivery | External delivery recovers without joining host business transactions. |
| M3: Governed dynamic rules | Later | Validated, versioned, staged dynamic-rule loader | Activation has validation, approval, rollout, and rollback evidence. |

## Explicitly deferred

- Automatic schema migration: the host-controlled migration system remains authoritative.
- Automatic `EventEnricher` discovery: the host must explicitly approve and invoke enrichment.
- Automatic online activation of database rules: persisted versions remain management data until a governed loader exists.
- A built-in HTTP error envelope: the component remains transport-neutral; hosts map the [error contract](error-contract.en.md).

The ordering favors observable, recoverable, and governed behavior before additional automation. See [Architecture and Transaction Boundaries](architecture-and-transaction-boundaries.en.md).

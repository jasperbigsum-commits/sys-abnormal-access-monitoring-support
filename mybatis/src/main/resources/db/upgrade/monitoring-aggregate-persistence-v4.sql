-- Host migration v4 for aggregate persistence. Fresh installations receive these objects from monitoring-schema.sql.
-- The IF NOT EXISTS clauses make this script repeatable on H2 and MySQL 8.0.29+.
ALTER TABLE security_alert ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE control_action ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS security_event_fact (
    event_id VARCHAR(128) NOT NULL, fact_key VARCHAR(128) NOT NULL, value_type VARCHAR(256) NOT NULL,
    value_text VARCHAR(2048) NOT NULL, source_type VARCHAR(64) NOT NULL, PRIMARY KEY (event_id, fact_key),
    CONSTRAINT fk_v4_event_fact_event FOREIGN KEY (event_id) REFERENCES security_event (event_id)
);
CREATE TABLE IF NOT EXISTS control_action_attempt (
    control_id VARCHAR(128) NOT NULL, attempt_no INTEGER NOT NULL, status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(512), attempted_at TIMESTAMP NOT NULL, PRIMARY KEY (control_id, attempt_no),
    CONSTRAINT fk_v4_control_attempt_control FOREIGN KEY (control_id) REFERENCES control_action (control_id)
);
CREATE TABLE IF NOT EXISTS notification_delivery (
    delivery_id VARCHAR(128) NOT NULL, channel VARCHAR(128) NOT NULL, aggregate_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (delivery_id), UNIQUE (channel, aggregate_id)
);
ALTER TABLE security_whitelist ADD COLUMN IF NOT EXISTS whitelist_id VARCHAR(128);
ALTER TABLE security_whitelist ADD COLUMN IF NOT EXISTS system_id VARCHAR(128);
ALTER TABLE security_whitelist ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE security_whitelist ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1;
CREATE UNIQUE INDEX IF NOT EXISTS uk_security_whitelist_id ON security_whitelist (whitelist_id);
CREATE TABLE IF NOT EXISTS management_audit (
    audit_id VARCHAR(128) NOT NULL, system_id VARCHAR(128) NOT NULL, actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL, target_type VARCHAR(64) NOT NULL, target_id VARCHAR(128) NOT NULL,
    outcome VARCHAR(32) NOT NULL, occurred_at TIMESTAMP NOT NULL, PRIMARY KEY (audit_id)
);
CREATE INDEX IF NOT EXISTS idx_management_audit_system_at ON management_audit (system_id, occurred_at, audit_id);
CREATE INDEX IF NOT EXISTS idx_security_event_system_at ON security_event (system_id, occurred_at, event_id);

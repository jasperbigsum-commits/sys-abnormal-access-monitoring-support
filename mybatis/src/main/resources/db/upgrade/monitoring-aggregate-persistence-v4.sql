-- Host migration v4 for aggregate persistence. Fresh installations receive these objects from monitoring-schema.sql.
ALTER TABLE security_alert ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE control_action ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS security_event_fact (
    event_id VARCHAR(128) NOT NULL, fact_key VARCHAR(128) NOT NULL, value_type VARCHAR(32) NOT NULL,
    value_text VARCHAR(2048) NOT NULL, source_type VARCHAR(64) NOT NULL, PRIMARY KEY (event_id, fact_key)
);
CREATE TABLE IF NOT EXISTS control_action_attempt (
    control_id VARCHAR(128) NOT NULL, attempt_no INTEGER NOT NULL, status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(512), attempted_at TIMESTAMP NOT NULL, PRIMARY KEY (control_id, attempt_no)
);
CREATE TABLE IF NOT EXISTS notification_delivery (
    delivery_id VARCHAR(128) NOT NULL, channel VARCHAR(128) NOT NULL, aggregate_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (delivery_id), UNIQUE (channel, aggregate_id)
);
CREATE INDEX IF NOT EXISTS idx_security_event_system_at ON security_event (system_id, occurred_at, event_id);

-- Unpublished legacy rows are assigned an explicit migration scope; operators must replace it before use.
ALTER TABLE monitoring_control_action ADD COLUMN IF NOT EXISTS system_id VARCHAR(128) NOT NULL DEFAULT 'LEGACY_UNSCOPED';
CREATE INDEX IF NOT EXISTS idx_control_authentication_lookup
    ON monitoring_control_action (system_id, subject, status, expires_at, action_type);
CREATE INDEX IF NOT EXISTS idx_monitoring_event_fact_lookup
    ON monitoring_security_event_fact (fact_key, value_text, event_id);

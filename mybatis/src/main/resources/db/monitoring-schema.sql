CREATE TABLE security_event (
    event_id VARCHAR(128) NOT NULL,
    system_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    user_id VARCHAR(128),
    account_type VARCHAR(32) NOT NULL,
    source_ip VARCHAR(128) NOT NULL,
    device_id_hash VARCHAR(256),
    session_id_hash VARCHAR(256),
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128),
    action VARCHAR(128) NOT NULL,
    result VARCHAR(32) NOT NULL,
    reason_code VARCHAR(128),
    resource_type VARCHAR(128),
    resource_id VARCHAR(256),
    org_scope VARCHAR(256),
    data_count BIGINT NOT NULL,
    latency_ms BIGINT NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_security_event_occurred_at ON security_event (occurred_at);
CREATE INDEX idx_security_event_subject_at ON security_event (user_id, source_ip, occurred_at);

CREATE TABLE security_event_role (
    event_id VARCHAR(128) NOT NULL,
    role_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (event_id, role_id)
);

CREATE TABLE security_event_attribute (
    event_id VARCHAR(128) NOT NULL,
    attribute_key VARCHAR(128) NOT NULL,
    attribute_value VARCHAR(512) NOT NULL,
    PRIMARY KEY (event_id, attribute_key)
);

CREATE TABLE security_rule (
    rule_id VARCHAR(128) NOT NULL,
    rule_version INTEGER NOT NULL,
    rule_name VARCHAR(256) NOT NULL,
    rule_definition CLOB NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    rule_mode VARCHAR(32) NOT NULL,
    enabled SMALLINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    PRIMARY KEY (rule_id, rule_version)
);

CREATE TABLE security_alert (
    alert_id VARCHAR(128) NOT NULL,
    rule_id VARCHAR(128) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(512) NOT NULL,
    subject VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    first_seen TIMESTAMP NOT NULL,
    last_seen TIMESTAMP NOT NULL,
    event_count INTEGER NOT NULL,
    PRIMARY KEY (alert_id)
);

CREATE INDEX idx_security_alert_fingerprint_status ON security_alert (fingerprint, status);

CREATE TABLE alert_event_link (
    alert_id VARCHAR(128) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (alert_id, event_id)
);

CREATE TABLE control_action (
    control_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    alert_id VARCHAR(128),
    subject VARCHAR(256) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(512),
    executed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (control_id),
    UNIQUE (idempotency_key)
);

CREATE TABLE alert_disposition (
    disposition_id VARCHAR(128) NOT NULL,
    alert_id VARCHAR(128) NOT NULL,
    disposition_type VARCHAR(64) NOT NULL CHECK (disposition_type IN ('ACKNOWLEDGED', 'IN_PROGRESS', 'CLOSED', 'FALSE_POSITIVE')),
    operator_id VARCHAR(128) NOT NULL,
    comment_text VARCHAR(1024),
    evidence_summary VARCHAR(1024),
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (disposition_id)
);

CREATE INDEX idx_alert_disposition_alert_at ON alert_disposition (alert_id, created_at);

CREATE TABLE security_whitelist (
    rule_id VARCHAR(128) NOT NULL,
    subject VARCHAR(256) NOT NULL,
    reason VARCHAR(512) NOT NULL DEFAULT 'Created by monitoring repository',
    approved_by VARCHAR(128) NOT NULL DEFAULT 'SYSTEM',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id, subject, expires_at)
);

CREATE INDEX idx_security_whitelist_lookup ON security_whitelist (rule_id, subject, expires_at);

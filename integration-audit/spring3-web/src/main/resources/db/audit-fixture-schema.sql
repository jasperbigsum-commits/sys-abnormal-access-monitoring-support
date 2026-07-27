CREATE TABLE audit_account (
    user_id VARCHAR(128) PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    challenge_until TIMESTAMP NULL,
    query_block_until TIMESTAMP NULL
);

CREATE TABLE audit_session (
    session_id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL
);
CREATE INDEX idx_audit_session_user ON audit_session (user_id, status);

CREATE TABLE audit_report (
    report_id VARCHAR(128) PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    sensitivity VARCHAR(32) NOT NULL
);

CREATE TABLE audit_report_row (
    report_id VARCHAR(128) NOT NULL,
    row_id BIGINT NOT NULL,
    organization_id VARCHAR(128) NOT NULL,
    display_value VARCHAR(255) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    sensitive_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (report_id, row_id)
);

CREATE TABLE audit_user_role (
    user_id VARCHAR(128) NOT NULL,
    role_id VARCHAR(128) NOT NULL,
    granted_by VARCHAR(128) NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE audit_control_state (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    subject VARCHAR(255) NOT NULL,
    control_type VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NULL,
    execution_count INTEGER NOT NULL
);
CREATE INDEX idx_audit_control_subject ON audit_control_state (subject, control_type, expires_at);

CREATE TABLE audit_export_ledger (
    export_id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    report_id VARCHAR(128) NOT NULL,
    row_count BIGINT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_audit_export_daily ON audit_export_ledger (user_id, occurred_at);

CREATE TABLE audit_notification_attempt (
    delivery_id VARCHAR(128) NOT NULL,
    attempt_number INTEGER NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    error_category VARCHAR(64) NULL,
    occurred_at TIMESTAMP NOT NULL,
    PRIMARY KEY (delivery_id, attempt_number)
);

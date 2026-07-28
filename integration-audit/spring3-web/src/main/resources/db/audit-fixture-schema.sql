-- 宿主系统业务夹具表（非异常访问监测组件内部表）。
-- 本脚本的全部 audit_* 表由参考宿主拥有，用于模拟账号、会话、报告、权限、控制副作用、
-- 导出台账和通知渠道。组件内部表由先执行的 /db/monitoring-schema.sql 创建。

-- 宿主账号与认证状态。
CREATE TABLE audit_account (
    user_id VARCHAR(128) PRIMARY KEY,
    organization_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    challenge_until TIMESTAMP NULL,
    query_block_until TIMESTAMP NULL
);

-- 宿主会话状态。
CREATE TABLE audit_session (
    session_id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL
);
CREATE INDEX idx_audit_session_user ON audit_session (user_id, status);

-- 宿主报告资源与行数据。
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

-- 宿主角色关系。
CREATE TABLE audit_user_role (
    user_id VARCHAR(128) NOT NULL,
    role_id VARCHAR(128) NOT NULL,
    granted_by VARCHAR(128) NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 宿主控制处理器的幂等副作用记录，不等同于组件的 control_action / control_action_attempt。
CREATE TABLE audit_control_state (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    subject VARCHAR(255) NOT NULL,
    control_type VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NULL,
    execution_count INTEGER NOT NULL
);
CREATE INDEX idx_audit_control_subject ON audit_control_state (subject, control_type, expires_at);

-- 宿主导出业务台账，不等同于组件安全事件或告警表。
CREATE TABLE audit_export_ledger (
    export_id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    report_id VARCHAR(128) NOT NULL,
    row_count BIGINT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_audit_export_daily ON audit_export_ledger (user_id, occurred_at);

-- 宿主模拟通知渠道的尝试记录，不等同于组件的 notification_delivery。
CREATE TABLE audit_notification_attempt (
    delivery_id VARCHAR(128) NOT NULL,
    attempt_number INTEGER NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    error_category VARCHAR(64) NULL,
    occurred_at TIMESTAMP NOT NULL,
    PRIMARY KEY (delivery_id, attempt_number)
);

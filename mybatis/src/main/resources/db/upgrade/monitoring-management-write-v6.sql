-- Management writes: immutable rule versions and alert assignment history.
-- Before running this migration on a database containing rule rows, set the connection variable:
-- SET @monitoring_system_id = 'the-host-system-id';
-- An unset/blank variable leaves historical rows NULL and the NOT NULL conversion fails closed.
SET @monitoring_system_id = NULLIF(TRIM(@monitoring_system_id), '');
ALTER TABLE security_rule ADD COLUMN system_id VARCHAR(128) NULL COMMENT '规则所属宿主系统标识' FIRST;
UPDATE security_rule SET system_id = @monitoring_system_id;
ALTER TABLE security_rule MODIFY COLUMN system_id VARCHAR(128) NOT NULL COMMENT '规则所属宿主系统标识';
ALTER TABLE security_rule DROP PRIMARY KEY, ADD PRIMARY KEY (system_id, rule_id, rule_version);
ALTER TABLE security_rule ADD COLUMN rule_threshold BIGINT NOT NULL DEFAULT 1 COMMENT '规则触发阈值';
ALTER TABLE security_rule ADD COLUMN change_reason VARCHAR(512) NULL COMMENT '版本变更原因';
ALTER TABLE security_rule ADD COLUMN approved_by VARCHAR(128) NULL COMMENT '版本变更审批人';
ALTER TABLE security_rule ADD COLUMN idempotency_key VARCHAR(128) NULL COMMENT '版本变更幂等键';
CREATE UNIQUE INDEX uk_security_rule_idempotency ON security_rule (system_id, idempotency_key);

ALTER TABLE alert_disposition ADD COLUMN assignee_id VARCHAR(128) NULL COMMENT '本次分配的受理人标识';
ALTER TABLE alert_disposition ADD COLUMN expected_version BIGINT NULL COMMENT '触发本次处置的告警期望版本';

CREATE TABLE security_event (
    event_id VARCHAR(128) NOT NULL COMMENT '事件唯一标识',
    system_id VARCHAR(128) NOT NULL COMMENT '来源系统标识',
    event_type VARCHAR(64) NOT NULL COMMENT '安全事件类型',
    occurred_at TIMESTAMP NOT NULL COMMENT '事件实际发生时间',
    received_at TIMESTAMP NOT NULL COMMENT '监测组件接收时间',
    user_id VARCHAR(128) COMMENT '服务端确认的用户标识',
    account_type VARCHAR(32) NOT NULL COMMENT '账号类型',
    source_ip VARCHAR(128) NOT NULL COMMENT '请求来源 IP 地址',
    device_id_hash VARCHAR(256) COMMENT '设备标识哈希值',
    session_id_hash VARCHAR(256) COMMENT '会话标识哈希值',
    request_id VARCHAR(128) NOT NULL COMMENT '请求关联标识',
    trace_id VARCHAR(128) COMMENT '链路追踪标识',
    action VARCHAR(128) NOT NULL COMMENT '被监测操作',
    result VARCHAR(32) NOT NULL COMMENT '操作结果',
    reason_code VARCHAR(128) COMMENT '结果原因码',
    resource_type VARCHAR(128) COMMENT '资源类型',
    resource_id VARCHAR(256) COMMENT '资源标识',
    org_scope VARCHAR(256) COMMENT '组织或租户范围',
    data_count BIGINT NOT NULL COMMENT '涉及数据数量',
    latency_ms BIGINT NOT NULL COMMENT '处理耗时，单位毫秒',
    data_count_known TINYINT(1) NOT NULL DEFAULT 0 COMMENT '数据量是否由宿主明确提供',
    latency_ms_known TINYINT(1) NOT NULL DEFAULT 0 COMMENT '耗时是否由宿主明确提供',
    input_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '规则输入质量状态',
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件明细表';

-- Existing deployments: explicitly include and renumber db/upgrade/monitoring-event-input-quality-v2.sql in the host migration plan.
-- It uses defaults 0, 0, and 'UNKNOWN' so historical events remain unknown; fresh installations use this complete baseline.

CREATE INDEX idx_security_event_occurred_at ON security_event (occurred_at);
CREATE INDEX idx_security_event_subject_at ON security_event (user_id, source_ip, occurred_at);
CREATE INDEX idx_security_event_system_at ON security_event (system_id, occurred_at, event_id);

CREATE TABLE rule_observation (
    observation_id VARCHAR(64) NOT NULL COMMENT '观察证据唯一标识',
    rule_id VARCHAR(128) NOT NULL COMMENT '命中规则标识',
    event_id VARCHAR(128) NOT NULL COMMENT '关联安全事件标识',
    subject VARCHAR(256) NOT NULL COMMENT '规则评估主体',
    observed_at TIMESTAMP NOT NULL COMMENT '观察记录时间',
    PRIMARY KEY (observation_id),
    CONSTRAINT fk_rule_observation_event FOREIGN KEY (event_id) REFERENCES security_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仅观察规则命中证据';

CREATE INDEX idx_rule_observation_rule_at ON rule_observation (rule_id, observed_at, observation_id);
CREATE INDEX idx_rule_observation_event ON rule_observation (event_id);

CREATE TABLE security_event_fact (
    event_id VARCHAR(128) NOT NULL,
    fact_key VARCHAR(128) NOT NULL,
    value_type VARCHAR(256) NOT NULL,
    value_text VARCHAR(2048) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    PRIMARY KEY (event_id, fact_key),
    CONSTRAINT fk_event_fact_event FOREIGN KEY (event_id) REFERENCES security_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件规范化事实值';

CREATE TABLE security_event_role (
    event_id VARCHAR(128) NOT NULL COMMENT '事件唯一标识',
    role_id VARCHAR(128) NOT NULL COMMENT '角色标识',
    PRIMARY KEY (event_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件角色关联表';

CREATE TABLE security_event_attribute (
    event_id VARCHAR(128) NOT NULL COMMENT '事件唯一标识',
    attribute_key VARCHAR(128) NOT NULL COMMENT '受控扩展属性键',
    attribute_value VARCHAR(512) NOT NULL COMMENT '受控扩展属性值',
    PRIMARY KEY (event_id, attribute_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件扩展属性表';

CREATE TABLE security_event_input_issue (
    event_id VARCHAR(128) NOT NULL COMMENT '事件唯一标识',
    issue_index INTEGER NOT NULL COMMENT '事件内稳定问题序号',
    rule_id VARCHAR(128) NOT NULL COMMENT '受影响规则稳定标识',
    fact_name VARCHAR(128) NOT NULL COMMENT '受影响事实稳定名称',
    issue_code VARCHAR(128) NOT NULL COMMENT '受控问题码',
    source_type VARCHAR(64) NOT NULL COMMENT '受控事实来源类别',
    PRIMARY KEY (event_id, issue_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件输入质量问题表';

CREATE TABLE security_rule (
    system_id VARCHAR(128) NOT NULL COMMENT '规则所属宿主系统标识',
    rule_id VARCHAR(128) NOT NULL COMMENT '规则稳定标识',
    rule_version INTEGER NOT NULL COMMENT '规则版本号',
    rule_name VARCHAR(256) NOT NULL COMMENT '规则名称',
    rule_definition LONGTEXT NOT NULL COMMENT '规则定义内容',
    risk_level VARCHAR(32) NOT NULL COMMENT '风险等级',
    rule_mode VARCHAR(32) NOT NULL COMMENT '规则运行模式',
    rule_threshold BIGINT NOT NULL DEFAULT 1 COMMENT '规则触发阈值',
    enabled TINYINT(1) NOT NULL COMMENT '管理侧启用状态',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建人标识',
    change_reason VARCHAR(512) COMMENT '版本变更原因',
    approved_by VARCHAR(128) COMMENT '版本变更审批人',
    idempotency_key VARCHAR(128) COMMENT '版本变更幂等键',
    PRIMARY KEY (system_id, rule_id, rule_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='持久化安全规则版本表';

CREATE UNIQUE INDEX uk_security_rule_idempotency ON security_rule (system_id, idempotency_key);

CREATE TABLE security_alert (
    alert_id VARCHAR(128) NOT NULL COMMENT '告警唯一标识',
    rule_id VARCHAR(128) NOT NULL COMMENT '命中规则标识',
    risk_level VARCHAR(32) NOT NULL COMMENT '告警风险等级',
    fingerprint VARCHAR(512) NOT NULL COMMENT '告警去重指纹',
    subject VARCHAR(256) NOT NULL COMMENT '告警主体',
    status VARCHAR(32) NOT NULL COMMENT '告警状态',
    first_seen TIMESTAMP NOT NULL COMMENT '首次发现时间',
    last_seen TIMESTAMP NOT NULL COMMENT '最近发现时间',
    event_count INTEGER NOT NULL COMMENT '关联事件数量',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (alert_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全告警表';

CREATE INDEX idx_security_alert_fingerprint_status ON security_alert (fingerprint, status);
CREATE INDEX idx_security_alert_rule_status ON security_alert (rule_id, status, last_seen);

CREATE TABLE alert_event_link (
    alert_id VARCHAR(128) NOT NULL COMMENT '告警唯一标识',
    event_id VARCHAR(128) NOT NULL COMMENT '事件唯一标识',
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联创建时间',
    PRIMARY KEY (alert_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警与安全事件关联表';

CREATE TABLE control_action (
    control_id VARCHAR(128) NOT NULL COMMENT '控制执行唯一标识',
    idempotency_key VARCHAR(256) NOT NULL COMMENT '幂等键',
    alert_id VARCHAR(128) COMMENT '关联告警标识',
    rule_id VARCHAR(128) COMMENT '产生控制动作的规则标识',
    subject VARCHAR(256) NOT NULL COMMENT '控制目标主体',
    action_type VARCHAR(64) NOT NULL COMMENT '控制动作类型',
    expires_at TIMESTAMP COMMENT '控制失效时间',
    status VARCHAR(32) NOT NULL COMMENT '控制执行状态',
    failure_reason VARCHAR(512) COMMENT '失败原因',
    executed_at TIMESTAMP NOT NULL COMMENT '执行时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (control_id),
    UNIQUE (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制动作执行记录表';

CREATE TABLE alert_disposition (
    disposition_id VARCHAR(128) NOT NULL COMMENT '处置记录唯一标识',
    alert_id VARCHAR(128) NOT NULL COMMENT '告警唯一标识',
    disposition_type VARCHAR(64) NOT NULL CHECK (disposition_type IN ('ACKNOWLEDGED', 'IN_PROGRESS', 'CLOSED', 'FALSE_POSITIVE')) COMMENT '处置类型',
    operator_id VARCHAR(128) NOT NULL COMMENT '操作人标识',
    assignee_id VARCHAR(128) COMMENT '本次分配的受理人标识',
    expected_version BIGINT COMMENT '触发本次处置的告警期望版本',
    comment_text VARCHAR(1024) COMMENT '处置说明',
    evidence_summary VARCHAR(1024) COMMENT '证据摘要',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    PRIMARY KEY (disposition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警处置历史表';

CREATE INDEX idx_alert_disposition_alert_at ON alert_disposition (alert_id, created_at);

CREATE TABLE security_whitelist (
    whitelist_id VARCHAR(128) COMMENT '管理侧白名单标识',
    system_id VARCHAR(128) COMMENT '管理授权所属系统范围',
    rule_id VARCHAR(128) NOT NULL COMMENT '适用规则标识',
    subject VARCHAR(256) NOT NULL COMMENT '豁免主体',
    reason VARCHAR(512) NOT NULL DEFAULT 'Created by monitoring repository' COMMENT '豁免原因',
    approved_by VARCHAR(128) NOT NULL DEFAULT 'SYSTEM' COMMENT '审批人标识',
    expires_at TIMESTAMP NOT NULL COMMENT '豁免到期时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '管理生命周期状态',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    PRIMARY KEY (rule_id, subject, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全规则白名单表';

CREATE INDEX idx_security_whitelist_lookup ON security_whitelist (rule_id, subject, expires_at);
CREATE UNIQUE INDEX uk_security_whitelist_id ON security_whitelist (whitelist_id);

CREATE TABLE control_action_attempt (
    control_id VARCHAR(128) NOT NULL,
    attempt_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(512),
    attempted_at TIMESTAMP NOT NULL,
    PRIMARY KEY (control_id, attempt_no),
    CONSTRAINT fk_control_attempt_control FOREIGN KEY (control_id) REFERENCES control_action (control_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制执行尝试历史';

CREATE TABLE notification_delivery (
    delivery_id VARCHAR(128) NOT NULL,
    channel VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (delivery_id),
    UNIQUE (channel, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知投递状态';

CREATE TABLE management_audit (
    audit_id VARCHAR(128) NOT NULL,
    system_id VARCHAR(128) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    PRIMARY KEY (audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理操作脱敏审计记录';
CREATE INDEX idx_management_audit_system_at ON management_audit (system_id, occurred_at, audit_id);

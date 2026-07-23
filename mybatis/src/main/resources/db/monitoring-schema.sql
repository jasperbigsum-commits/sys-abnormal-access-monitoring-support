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
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件明细表';

CREATE INDEX idx_security_event_occurred_at ON security_event (occurred_at);
CREATE INDEX idx_security_event_subject_at ON security_event (user_id, source_ip, occurred_at);

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

CREATE TABLE security_rule (
    rule_id VARCHAR(128) NOT NULL COMMENT '规则稳定标识',
    rule_version INTEGER NOT NULL COMMENT '规则版本号',
    rule_name VARCHAR(256) NOT NULL COMMENT '规则名称',
    rule_definition LONGTEXT NOT NULL COMMENT '规则定义内容',
    risk_level VARCHAR(32) NOT NULL COMMENT '风险等级',
    rule_mode VARCHAR(32) NOT NULL COMMENT '规则运行模式',
    enabled TINYINT(1) NOT NULL COMMENT '管理侧启用状态',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    created_by VARCHAR(128) NOT NULL COMMENT '创建人标识',
    PRIMARY KEY (rule_id, rule_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='持久化安全规则版本表';

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
    PRIMARY KEY (alert_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全告警表';

CREATE INDEX idx_security_alert_fingerprint_status ON security_alert (fingerprint, status);

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
    subject VARCHAR(256) NOT NULL COMMENT '控制目标主体',
    action_type VARCHAR(64) NOT NULL COMMENT '控制动作类型',
    expires_at TIMESTAMP COMMENT '控制失效时间',
    status VARCHAR(32) NOT NULL COMMENT '控制执行状态',
    failure_reason VARCHAR(512) COMMENT '失败原因',
    executed_at TIMESTAMP NOT NULL COMMENT '执行时间',
    PRIMARY KEY (control_id),
    UNIQUE (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制动作执行记录表';

CREATE TABLE alert_disposition (
    disposition_id VARCHAR(128) NOT NULL COMMENT '处置记录唯一标识',
    alert_id VARCHAR(128) NOT NULL COMMENT '告警唯一标识',
    disposition_type VARCHAR(64) NOT NULL CHECK (disposition_type IN ('ACKNOWLEDGED', 'IN_PROGRESS', 'CLOSED', 'FALSE_POSITIVE')) COMMENT '处置类型',
    operator_id VARCHAR(128) NOT NULL COMMENT '操作人标识',
    comment_text VARCHAR(1024) COMMENT '处置说明',
    evidence_summary VARCHAR(1024) COMMENT '证据摘要',
    created_at TIMESTAMP NOT NULL COMMENT '创建时间',
    PRIMARY KEY (disposition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警处置历史表';

CREATE INDEX idx_alert_disposition_alert_at ON alert_disposition (alert_id, created_at);

CREATE TABLE security_whitelist (
    rule_id VARCHAR(128) NOT NULL COMMENT '适用规则标识',
    subject VARCHAR(256) NOT NULL COMMENT '豁免主体',
    reason VARCHAR(512) NOT NULL DEFAULT 'Created by monitoring repository' COMMENT '豁免原因',
    approved_by VARCHAR(128) NOT NULL DEFAULT 'SYSTEM' COMMENT '审批人标识',
    expires_at TIMESTAMP NOT NULL COMMENT '豁免到期时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (rule_id, subject, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全规则白名单表';

CREATE INDEX idx_security_whitelist_lookup ON security_whitelist (rule_id, subject, expires_at);

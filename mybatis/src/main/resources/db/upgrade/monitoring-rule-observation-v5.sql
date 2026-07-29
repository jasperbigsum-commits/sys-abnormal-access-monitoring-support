CREATE TABLE monitoring_rule_observation (
    observation_id VARCHAR(64) NOT NULL COMMENT '观察证据唯一标识',
    rule_id VARCHAR(128) NOT NULL COMMENT '命中规则标识',
    event_id VARCHAR(128) NOT NULL COMMENT '关联安全事件标识',
    subject VARCHAR(256) NOT NULL COMMENT '规则评估主体',
    observed_at TIMESTAMP NOT NULL COMMENT '观察记录时间',
    PRIMARY KEY (observation_id),
    CONSTRAINT fk_rule_observation_event FOREIGN KEY (event_id) REFERENCES monitoring_security_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仅观察规则命中证据';

CREATE INDEX idx_rule_observation_rule_at ON monitoring_rule_observation (rule_id, observed_at, observation_id);
CREATE INDEX idx_rule_observation_event ON monitoring_rule_observation (event_id);

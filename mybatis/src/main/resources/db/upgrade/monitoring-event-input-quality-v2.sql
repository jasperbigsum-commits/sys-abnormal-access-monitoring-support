-- Host-controlled upgrade: explicitly include and renumber this script in the host migration plan.
-- Do not place this script in a default migration discovery directory.
ALTER TABLE monitoring_security_event
    ADD COLUMN data_count_known TINYINT(1) NOT NULL DEFAULT 0 COMMENT '数据量是否由宿主明确提供';

ALTER TABLE monitoring_security_event
    ADD COLUMN latency_ms_known TINYINT(1) NOT NULL DEFAULT 0 COMMENT '耗时是否由宿主明确提供';

ALTER TABLE monitoring_security_event
    ADD COLUMN input_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '规则输入质量状态';

CREATE TABLE monitoring_security_event_input_issue (
    event_id VARCHAR(128) NOT NULL COMMENT '事件唯一标识',
    issue_index INTEGER NOT NULL COMMENT '事件内稳定问题序号',
    rule_id VARCHAR(128) NOT NULL COMMENT '受影响规则稳定标识',
    fact_name VARCHAR(128) NOT NULL COMMENT '受影响事实稳定名称',
    issue_code VARCHAR(128) NOT NULL COMMENT '受控问题码',
    source_type VARCHAR(64) NOT NULL COMMENT '受控事实来源类别',
    PRIMARY KEY (event_id, issue_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件输入质量问题表';

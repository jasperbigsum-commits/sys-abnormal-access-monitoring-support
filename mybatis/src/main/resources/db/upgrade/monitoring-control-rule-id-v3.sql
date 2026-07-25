-- Host-controlled upgrade: explicitly include and renumber this script in the host migration plan.
-- The column remains nullable so historical controls and manually-created commands stay compatible.
ALTER TABLE control_action
    ADD COLUMN rule_id VARCHAR(128) NULL COMMENT '产生控制动作的规则标识';

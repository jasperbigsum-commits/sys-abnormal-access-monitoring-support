ALTER TABLE notification_delivery
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE notification_delivery
    ADD COLUMN next_attempt_at TIMESTAMP NULL;

ALTER TABLE notification_delivery
    ADD COLUMN failure_category VARCHAR(64) NULL;

ALTER TABLE notification_delivery
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE notification_delivery
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE notification_delivery
SET attempt_count = CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END,
    next_attempt_at = CASE WHEN status = 'RETRY_PENDING' THEN CURRENT_TIMESTAMP ELSE NULL END,
    failure_category = CASE
        WHEN status = 'FAILED' THEN 'LEGACY_FAILURE'
        WHEN status = 'RETRY_PENDING' THEN 'LEGACY_RETRY_PENDING'
        ELSE NULL
    END;

UPDATE notification_delivery
SET status = 'FAILED',
    attempt_count = 1,
    next_attempt_at = NULL,
    failure_category = 'LEGACY_UNKNOWN_STATUS'
WHERE status NOT IN ('PENDING', 'RETRY_PENDING', 'DELIVERED', 'FAILED');

CREATE INDEX idx_notification_retry
    ON notification_delivery (channel, status, next_attempt_at, delivery_id);

CREATE INDEX idx_notification_pending
    ON notification_delivery (channel, status, updated_at, delivery_id);

ALTER TABLE pair_photo
    ADD COLUMN revision_no INT NOT NULL DEFAULT 1 AFTER rejection_reason,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at,
    ADD INDEX idx_pair_photo_pair_status (pair_id, status),
    ADD CONSTRAINT ck_pair_photo_status CHECK (status IN ('PEER_PENDING','ADMIN_PENDING','APPROVED','REJECTED'));

ALTER TABLE pair_reflection
    ADD INDEX idx_pair_reflection_pair_status (pair_id, status),
    ADD CONSTRAINT ck_pair_reflection_status CHECK (status IN ('ADMIN_PENDING','APPROVED','REJECTED'));

CREATE TABLE pair_material_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    pair_id BIGINT NOT NULL,
    material_type VARCHAR(20) NOT NULL COMMENT 'PHOTO, REFLECTION',
    material_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    action VARCHAR(24) NOT NULL COMMENT 'SUBMIT, PEER_CONFIRM, APPROVE, REJECT, RESUBMIT',
    reason VARCHAR(500) NULL,
    snapshot JSON NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_material_history_target (material_type, material_id, created_at),
    INDEX idx_material_history_pair (pair_id, created_at),
    CONSTRAINT fk_material_history_pair FOREIGN KEY (pair_id) REFERENCES support_pair(id),
    CONSTRAINT fk_material_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id),
    CONSTRAINT ck_material_history_type CHECK (material_type IN ('PHOTO','REFLECTION')),
    CONSTRAINT ck_material_history_action CHECK (action IN ('SUBMIT','PEER_CONFIRM','APPROVE','REJECT','RESUBMIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE notification
    ADD COLUMN audience_label VARCHAR(100) NULL AFTER audience_type,
    ADD INDEX idx_notification_period_time (period_id, created_at);

ALTER TABLE notification_recipient
    ADD INDEX idx_notification_recipient_user (user_id, read_at);

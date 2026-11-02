ALTER TABLE grade_import_batch
    DROP INDEX uk_grade_import_period_stage,
    ADD COLUMN mime_type VARCHAR(100) NOT NULL DEFAULT 'application/octet-stream' AFTER original_filename,
    ADD COLUMN file_size BIGINT NOT NULL DEFAULT 0 AFTER mime_type,
    ADD INDEX idx_grade_import_period_stage (period_id, stage, created_at);

ALTER TABLE moral_score_file
    ADD COLUMN file_size BIGINT NOT NULL DEFAULT 0 AFTER mime_type,
    ADD COLUMN withdrawn_by BIGINT NULL AFTER withdrawn_at,
    ADD COLUMN deleted_by BIGINT NULL AFTER deleted,
    ADD COLUMN deleted_at DATETIME(3) NULL AFTER deleted_by,
    ADD CONSTRAINT fk_moral_score_file_withdrawn_by FOREIGN KEY (withdrawn_by) REFERENCES sys_user(id),
    ADD CONSTRAINT fk_moral_score_file_deleted_by FOREIGN KEY (deleted_by) REFERENCES sys_user(id),
    ADD INDEX idx_moral_score_period_status (period_id, status, deleted, created_at);

ALTER TABLE participant_term_grade
    ADD COLUMN professional_student_count INT NULL AFTER term_gpa_rank,
    ADD CONSTRAINT ck_participant_grade_professional_count
        CHECK (professional_student_count IS NULL OR professional_student_count > 0),
    ADD CONSTRAINT ck_participant_grade_rank_within_cohort
        CHECK (term_gpa_rank IS NULL OR professional_student_count IS NULL OR term_gpa_rank <= professional_student_count);

CREATE TABLE grade_cohort_stat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    import_batch_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    stage VARCHAR(12) NOT NULL,
    grade VARCHAR(32) NOT NULL,
    major VARCHAR(128) NOT NULL,
    student_count INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_grade_cohort_batch_group UNIQUE (import_batch_id, grade, major),
    CONSTRAINT fk_grade_cohort_batch FOREIGN KEY (import_batch_id) REFERENCES grade_import_batch(id),
    CONSTRAINT fk_grade_cohort_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT ck_grade_cohort_stage CHECK (stage IN ('BEFORE', 'AFTER')),
    CONSTRAINT ck_grade_cohort_student_count CHECK (student_count > 0),
    INDEX idx_grade_cohort_period_stage (period_id, stage, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

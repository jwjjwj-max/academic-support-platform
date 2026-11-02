CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    login_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL COMMENT 'STUDENT, ADMIN, SYSTEM_OWNER',
    display_name VARCHAR(64) NOT NULL,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, DISABLED',
    token_version INT NOT NULL DEFAULT 0,
    last_login_at DATETIME(3) NULL,
    password_reset_at DATETIME(3) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_sys_user_login_name UNIQUE (login_name),
    CONSTRAINT fk_sys_user_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT ck_sys_user_account_type CHECK (account_type IN ('STUDENT', 'ADMIN', 'SYSTEM_OWNER')),
    CONSTRAINT ck_sys_user_account_status CHECK (account_status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE student_profile (
    user_id BIGINT PRIMARY KEY,
    student_no VARCHAR(32) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    college VARCHAR(128) NOT NULL,
    major VARCHAR(128) NOT NULL,
    grade SMALLINT NOT NULL,
    wechat VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED',
    rejection_reason VARCHAR(500) NULL,
    submitted_at DATETIME(3) NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    profile_version INT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_student_profile_student_no UNIQUE (student_no),
    CONSTRAINT fk_student_profile_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_student_profile_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user(id),
    CONSTRAINT ck_student_profile_review_status CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE student_profile_review_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    profile_version INT NOT NULL,
    action VARCHAR(20) NOT NULL COMMENT 'SUBMIT, APPROVE, REJECT, RESUBMIT',
    reason VARCHAR(500) NULL,
    profile_snapshot JSON NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_profile_history_student (student_user_id, created_at),
    CONSTRAINT fk_profile_history_student FOREIGN KEY (student_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_profile_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id),
    CONSTRAINT ck_profile_history_action CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'RESUBMIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activity_period (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    short_name VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ANNOUNCED',
    registration_start DATETIME(3) NOT NULL,
    registration_end DATETIME(3) NOT NULL,
    confirmation_deadline DATETIME(3) NOT NULL,
    tracking_start DATETIME(3) NOT NULL,
    tracking_end DATETIME(3) NOT NULL,
    acceptance_start DATETIME(3) NOT NULL,
    acceptance_end DATETIME(3) NOT NULL,
    photo_min INT NOT NULL,
    photo_max INT NOT NULL,
    subject_weight INT NOT NULL DEFAULT 40,
    major_weight INT NOT NULL DEFAULT 25,
    grade_weight INT NOT NULL DEFAULT 15,
    learning_style_weight INT NOT NULL DEFAULT 10,
    personality_weight INT NOT NULL DEFAULT 5,
    gpa_gap_weight INT NOT NULL DEFAULT 5,
    archived_at DATETIME(3) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_activity_period_name UNIQUE (name),
    CONSTRAINT uk_activity_period_short_name UNIQUE (short_name),
    CONSTRAINT fk_activity_period_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT ck_activity_period_status CHECK (status IN ('ANNOUNCED','REGISTRATION','REVIEW','MATCHING','CONFIRMATION','TRACKING','ACCEPTANCE','ENDED','ARCHIVED')),
    CONSTRAINT ck_activity_period_photo_range CHECK (photo_min >= 0 AND photo_max >= photo_min),
    CONSTRAINT ck_activity_period_weight_sum CHECK (
        subject_weight + major_weight + grade_weight + learning_style_weight + personality_weight + gpa_gap_weight = 100
    ),
    CONSTRAINT ck_activity_period_dates CHECK (
        registration_start < registration_end
        AND registration_end <= confirmation_deadline
        AND confirmation_deadline <= tracking_start
        AND tracking_start < tracking_end
        AND tracking_end <= acceptance_start
        AND acceptance_start < acceptance_end
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activity_period_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    old_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_period_status_history (period_id, created_at),
    CONSTRAINT fk_period_status_history_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT fk_period_status_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    participant_role VARCHAR(20) NOT NULL COMMENT 'CLIMBER, NAVIGATOR',
    cumulative_gpa DECIMAL(4,2) NOT NULL COMMENT '学生报名累计平均绩点，禁止与学期平均学分绩点混用',
    subjects_json JSON NOT NULL,
    statement_text VARCHAR(1000) NOT NULL,
    learning_styles_json JSON NOT NULL,
    personalities_json JSON NOT NULL,
    application_status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    selection_status VARCHAR(24) NOT NULL DEFAULT 'UNDECIDED' COMMENT 'UNDECIDED, MATCHING_POOL, WAITLIST, NOT_SELECTED',
    rejection_reason VARCHAR(500) NULL,
    submitted_at DATETIME(3) NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_application_period_student UNIQUE (period_id, student_user_id),
    INDEX idx_application_period_status (period_id, application_status, selection_status),
    CONSTRAINT fk_application_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT fk_application_student FOREIGN KEY (student_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_application_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user(id),
    CONSTRAINT ck_application_role CHECK (participant_role IN ('CLIMBER', 'NAVIGATOR')),
    CONSTRAINT ck_application_gpa CHECK (cumulative_gpa >= 0 AND cumulative_gpa <= 5),
    CONSTRAINT ck_application_status CHECK (application_status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','LOCKED','WITHDRAWN')),
    CONSTRAINT ck_application_selection CHECK (selection_status IN ('UNDECIDED','MATCHING_POOL','WAITLIST','NOT_SELECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE application_profile_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    profile_version INT NOT NULL,
    student_no VARCHAR(32) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    college VARCHAR(128) NOT NULL,
    major VARCHAR(128) NOT NULL,
    grade SMALLINT NOT NULL,
    wechat VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NULL,
    snapshot_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_application_profile_snapshot UNIQUE (application_id),
    CONSTRAINT fk_application_profile_snapshot_application FOREIGN KEY (application_id) REFERENCES application(id),
    CONSTRAINT fk_application_profile_snapshot_student FOREIGN KEY (student_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE application_review_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    reason VARCHAR(500) NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_application_review_history (application_id, created_at),
    CONSTRAINT fk_application_review_history_application FOREIGN KEY (application_id) REFERENCES application(id),
    CONSTRAINT fk_application_review_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE match_round (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    weight_snapshot JSON NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    CONSTRAINT uk_match_round_period_no UNIQUE (period_id, round_no),
    CONSTRAINT fk_match_round_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT fk_match_round_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE match_candidate (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    round_id BIGINT NOT NULL,
    navigator_application_id BIGINT NOT NULL,
    climber_application_id BIGINT NOT NULL,
    total_score DECIMAL(5,2) NOT NULL,
    score_detail JSON NOT NULL,
    matched_subjects JSON NOT NULL,
    admin_confirmed TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_match_candidate_pair UNIQUE (round_id, navigator_application_id, climber_application_id),
    CONSTRAINT fk_match_candidate_round FOREIGN KEY (round_id) REFERENCES match_round(id),
    CONSTRAINT fk_match_candidate_navigator FOREIGN KEY (navigator_application_id) REFERENCES application(id),
    CONSTRAINT fk_match_candidate_climber FOREIGN KEY (climber_application_id) REFERENCES application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rejected_match_pair (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    navigator_user_id BIGINT NOT NULL,
    climber_user_id BIGINT NOT NULL,
    rejected_by BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    released_by BIGINT NULL,
    released_at DATETIME(3) NULL,
    release_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_rejected_match_pair (period_id, navigator_user_id, climber_user_id, active),
    CONSTRAINT fk_rejected_match_pair_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT fk_rejected_match_pair_navigator FOREIGN KEY (navigator_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_rejected_match_pair_climber FOREIGN KEY (climber_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_rejected_match_pair_rejected_by FOREIGN KEY (rejected_by) REFERENCES sys_user(id),
    CONSTRAINT fk_rejected_match_pair_released_by FOREIGN KEY (released_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE support_pair (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    pair_no VARCHAR(40) NOT NULL,
    navigator_application_id BIGINT NOT NULL,
    climber_application_id BIGINT NOT NULL,
    source_candidate_id BIGINT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    confirmed_at DATETIME(3) NOT NULL,
    completed_by BIGINT NULL,
    completed_at DATETIME(3) NULL,
    acceptance_note VARCHAR(500) NULL,
    ended_by BIGINT NULL,
    ended_at DATETIME(3) NULL,
    end_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_support_pair_no UNIQUE (pair_no),
    CONSTRAINT uk_support_pair_navigator UNIQUE (period_id, navigator_application_id),
    CONSTRAINT uk_support_pair_climber UNIQUE (period_id, climber_application_id),
    CONSTRAINT fk_support_pair_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT fk_support_pair_navigator FOREIGN KEY (navigator_application_id) REFERENCES application(id),
    CONSTRAINT fk_support_pair_climber FOREIGN KEY (climber_application_id) REFERENCES application(id),
    CONSTRAINT fk_support_pair_candidate FOREIGN KEY (source_candidate_id) REFERENCES match_candidate(id),
    CONSTRAINT fk_support_pair_completed_by FOREIGN KEY (completed_by) REFERENCES sys_user(id),
    CONSTRAINT fk_support_pair_ended_by FOREIGN KEY (ended_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pair_photo (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    pair_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL,
    peer_confirmed_by BIGINT NULL,
    peer_confirmed_at DATETIME(3) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    rejection_reason VARCHAR(500) NULL,
    submitted_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_pair_photo_pair FOREIGN KEY (pair_id) REFERENCES support_pair(id),
    CONSTRAINT fk_pair_photo_uploader FOREIGN KEY (uploader_id) REFERENCES sys_user(id),
    CONSTRAINT fk_pair_photo_peer FOREIGN KEY (peer_confirmed_by) REFERENCES sys_user(id),
    CONSTRAINT fk_pair_photo_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pair_reflection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    pair_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    word_count INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    rejection_reason VARCHAR(500) NULL,
    submitted_at DATETIME(3) NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_pair_reflection_author UNIQUE (pair_id, author_id),
    CONSTRAINT fk_pair_reflection_pair FOREIGN KEY (pair_id) REFERENCES support_pair(id),
    CONSTRAINT fk_pair_reflection_author FOREIGN KEY (author_id) REFERENCES sys_user(id),
    CONSTRAINT fk_pair_reflection_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user(id),
    CONSTRAINT ck_pair_reflection_word_count CHECK (word_count BETWEEN 300 AND 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grade_import_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    stage VARCHAR(12) NOT NULL COMMENT 'BEFORE, AFTER',
    label VARCHAR(100) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    field_mapping JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    participant_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    CONSTRAINT uk_grade_import_period_stage UNIQUE (period_id, stage),
    CONSTRAINT fk_grade_import_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT fk_grade_import_uploader FOREIGN KEY (uploaded_by) REFERENCES sys_user(id),
    CONSTRAINT ck_grade_import_stage CHECK (stage IN ('BEFORE', 'AFTER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE participant_term_grade (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    stage VARCHAR(12) NOT NULL,
    import_batch_id BIGINT NOT NULL,
    term_gpa DECIMAL(4,2) NOT NULL COMMENT '成绩单平均学分绩点',
    term_gpa_rank INT NULL COMMENT '成绩单平均学分绩点排名，仅管理员可见',
    student_no_snapshot VARCHAR(32) NOT NULL,
    name_snapshot VARCHAR(64) NOT NULL,
    college_snapshot VARCHAR(128) NULL,
    grade_snapshot VARCHAR(32) NULL,
    major_snapshot VARCHAR(128) NULL,
    class_name_snapshot VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_participant_term_grade UNIQUE (period_id, student_user_id, stage),
    CONSTRAINT fk_participant_term_grade_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT fk_participant_term_grade_student FOREIGN KEY (student_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_participant_term_grade_application FOREIGN KEY (application_id) REFERENCES application(id),
    CONSTRAINT fk_participant_term_grade_batch FOREIGN KEY (import_batch_id) REFERENCES grade_import_batch(id),
    CONSTRAINT ck_participant_term_grade_stage CHECK (stage IN ('BEFORE', 'AFTER')),
    CONSTRAINT ck_participant_term_grade_gpa CHECK (term_gpa >= 0 AND term_gpa <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grade_correction_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    grade_id BIGINT NOT NULL,
    old_term_gpa DECIMAL(4,2) NOT NULL,
    new_term_gpa DECIMAL(4,2) NOT NULL,
    old_term_gpa_rank INT NULL,
    new_term_gpa_rank INT NULL,
    reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_grade_correction_grade FOREIGN KEY (grade_id) REFERENCES participant_term_grade(id),
    CONSTRAINT fk_grade_correction_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE moral_score_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    uploaded_by BIGINT NOT NULL,
    published_by BIGINT NULL,
    published_at DATETIME(3) NULL,
    withdrawn_at DATETIME(3) NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_moral_score_file_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT fk_moral_score_file_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES sys_user(id),
    CONSTRAINT fk_moral_score_file_published_by FOREIGN KEY (published_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    audience_type VARCHAR(24) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_notification_period FOREIGN KEY (period_id) REFERENCES activity_period(id),
    CONSTRAINT fk_notification_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_recipient (
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at DATETIME(3) NULL,
    PRIMARY KEY (notification_id, user_id),
    CONSTRAINT fk_notification_recipient_notification FOREIGN KEY (notification_id) REFERENCES notification(id),
    CONSTRAINT fk_notification_recipient_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NULL,
    actor_login VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NULL,
    before_snapshot JSON NULL,
    after_snapshot JSON NULL,
    reason VARCHAR(500) NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_operation_log_actor_time (actor_id, created_at),
    INDEX idx_operation_log_target (target_type, target_id, created_at),
    CONSTRAINT fk_operation_log_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

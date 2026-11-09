DROP TABLE IF EXISTS matching_evidence;
DROP TABLE IF EXISTS matching_exclusion;
DROP TABLE IF EXISTS matching_policy;
DROP TABLE IF EXISTS matching_provider;
DROP TABLE IF EXISTS storage_upload;
DROP TABLE IF EXISTS notification_recipient;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS grade_correction_history;
DROP TABLE IF EXISTS participant_term_grade;
DROP TABLE IF EXISTS grade_cohort_stat;
DROP TABLE IF EXISTS grade_import_batch;
DROP TABLE IF EXISTS moral_score_file;
DROP TABLE IF EXISTS pair_material_history;
DROP TABLE IF EXISTS pair_reflection;
DROP TABLE IF EXISTS pair_photo;
DROP TABLE IF EXISTS support_pair;
DROP TABLE IF EXISTS rejected_match_pair;
DROP TABLE IF EXISTS match_candidate;
DROP TABLE IF EXISTS match_round;
DROP TABLE IF EXISTS application_review_history;
DROP TABLE IF EXISTS application_profile_snapshot;
DROP TABLE IF EXISTS application;
DROP TABLE IF EXISTS activity_period_status_history;
DROP TABLE IF EXISTS activity_period;
DROP TABLE IF EXISTS student_profile_review_history;
DROP TABLE IF EXISTS student_profile;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  login_name VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  account_type VARCHAR(20) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  token_version INT NOT NULL DEFAULT 0,
  last_login_at TIMESTAMP NULL,
  password_reset_at TIMESTAMP NULL,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE student_profile (
  user_id BIGINT PRIMARY KEY,
  student_no VARCHAR(32) NOT NULL UNIQUE,
  real_name VARCHAR(64) NOT NULL,
  college VARCHAR(128) NOT NULL,
  major VARCHAR(128) NOT NULL,
  grade SMALLINT NOT NULL,
  wechat VARCHAR(100) NOT NULL,
  phone VARCHAR(32) NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  rejection_reason VARCHAR(500) NULL,
  submitted_at TIMESTAMP NOT NULL,
  reviewed_by BIGINT NULL,
  reviewed_at TIMESTAMP NULL,
  profile_version INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE student_profile_review_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_user_id BIGINT NOT NULL,
  profile_version INT NOT NULL,
  action VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NULL,
  profile_snapshot JSON NOT NULL,
  operator_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_id BIGINT NULL,
  actor_login VARCHAR(64) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(64) NULL,
  before_snapshot CLOB NULL,
  after_snapshot CLOB NULL,
  reason VARCHAR(500) NULL,
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activity_period (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL UNIQUE,
  short_name VARCHAR(64) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL,
  registration_start TIMESTAMP NOT NULL,
  registration_end TIMESTAMP NOT NULL,
  confirmation_deadline TIMESTAMP NOT NULL,
  tracking_start TIMESTAMP NOT NULL,
  tracking_end TIMESTAMP NOT NULL,
  acceptance_start TIMESTAMP NOT NULL,
  acceptance_end TIMESTAMP NOT NULL,
  photo_min INT NOT NULL,
  photo_max INT NOT NULL,
  subject_weight INT NOT NULL,
  major_weight INT NOT NULL,
  grade_weight INT NOT NULL,
  learning_style_weight INT NOT NULL,
  personality_weight INT NOT NULL,
  gpa_gap_weight INT NOT NULL,
  archived_at TIMESTAMP NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activity_period_status_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  period_id BIGINT NOT NULL,
  old_status VARCHAR(20) NOT NULL,
  new_status VARCHAR(20) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE application (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  period_id BIGINT NOT NULL,
  student_user_id BIGINT NOT NULL,
  participant_role VARCHAR(20) NOT NULL,
  cumulative_gpa DECIMAL(4,2) NOT NULL,
  subjects_json VARCHAR(2000) NOT NULL,
  statement_text VARCHAR(1000) NOT NULL,
  learning_styles_json VARCHAR(2000) NOT NULL,
  personalities_json VARCHAR(2000) NOT NULL,
  application_status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
  revision INT NOT NULL DEFAULT 0,
  selection_status VARCHAR(24) NOT NULL DEFAULT 'UNDECIDED',
  rejection_reason VARCHAR(500),
  submitted_at TIMESTAMP NOT NULL,
  reviewed_by BIGINT,
  reviewed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(period_id, student_user_id)
);

CREATE TABLE application_profile_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  application_id BIGINT NOT NULL UNIQUE,
  student_user_id BIGINT NOT NULL,
  profile_version INT NOT NULL,
  student_no VARCHAR(32) NOT NULL,
  real_name VARCHAR(64) NOT NULL,
  college VARCHAR(128) NOT NULL,
  major VARCHAR(128) NOT NULL,
  grade SMALLINT NOT NULL,
  wechat VARCHAR(100) NOT NULL,
  phone VARCHAR(32),
  snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE application_review_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  application_id BIGINT NOT NULL,
  action VARCHAR(24) NOT NULL,
  reason VARCHAR(500),
  operator_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE match_round (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  period_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  round_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  policy_revision INT,
  draft_revision INT,
  status VARCHAR(20) NOT NULL,
  weight_snapshot VARCHAR(4000) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP,
  UNIQUE(period_id, round_no)
);

CREATE TABLE match_candidate (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  round_id BIGINT NOT NULL,
  navigator_application_id BIGINT NOT NULL,
  climber_application_id BIGINT NOT NULL,
  total_score DECIMAL(5,2) NOT NULL,
  score_detail VARCHAR(8000) NOT NULL,
  matched_subjects VARCHAR(2000) NOT NULL,
  match_kind VARCHAR(24) NOT NULL DEFAULT 'LEGACY',
  manually_selected BOOLEAN NOT NULL DEFAULT FALSE,
  admin_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(24) NOT NULL,
  navigator_decision VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  navigator_decided_at TIMESTAMP,
  climber_decision VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  climber_decided_at TIMESTAMP,
  rejection_reason VARCHAR(500),
  published_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(round_id, navigator_application_id, climber_application_id)
);

CREATE TABLE rejected_match_pair (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  period_id BIGINT NOT NULL,
  navigator_user_id BIGINT NOT NULL,
  climber_user_id BIGINT NOT NULL,
  rejected_by BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  released_by BIGINT,
  released_at TIMESTAMP,
  release_reason VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE support_pair (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  period_id BIGINT NOT NULL,
  pair_no VARCHAR(40) NOT NULL UNIQUE,
  navigator_application_id BIGINT NOT NULL,
  climber_application_id BIGINT NOT NULL,
  source_candidate_id BIGINT,
  match_kind VARCHAR(24) NOT NULL DEFAULT 'LEGACY',
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  confirmed_at TIMESTAMP NOT NULL,
  completed_by BIGINT,
  completed_at TIMESTAMP,
  acceptance_note VARCHAR(500),
  ended_by BIGINT,
  ended_at TIMESTAMP,
  end_reason VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(period_id, navigator_application_id),
  UNIQUE(period_id, climber_application_id)
);

CREATE TABLE pair_photo (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, pair_id BIGINT NOT NULL, uploader_id BIGINT NOT NULL,
  storage_key VARCHAR(255) NOT NULL, original_filename VARCHAR(255) NOT NULL, mime_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL, description VARCHAR(500), status VARCHAR(30) NOT NULL,
  peer_confirmed_by BIGINT, peer_confirmed_at TIMESTAMP, reviewed_by BIGINT, reviewed_at TIMESTAMP,
  rejection_reason VARCHAR(500), revision_no INT NOT NULL DEFAULT 1, submitted_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE matching_policy (
  period_id BIGINT PRIMARY KEY, revision INT NOT NULL, policy_json CLOB NOT NULL
);
CREATE TABLE matching_evidence (
  application_id BIGINT PRIMARY KEY, application_revision INT NOT NULL, facts_json CLOB NOT NULL, reviewed BOOLEAN NOT NULL
);
CREATE TABLE matching_exclusion (
  period_id BIGINT NOT NULL, first_application_id BIGINT NOT NULL, second_application_id BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL, PRIMARY KEY (period_id, first_application_id, second_application_id)
);
CREATE TABLE matching_provider (
  id INT PRIMARY KEY, ciphertext VARCHAR(2048) NOT NULL, key_suffix VARCHAR(4) NOT NULL
);

CREATE TABLE pair_reflection (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, pair_id BIGINT NOT NULL, author_id BIGINT NOT NULL,
  content VARCHAR(2000) NOT NULL, word_count INT NOT NULL, status VARCHAR(24) NOT NULL,
  rejection_reason VARCHAR(500), submitted_at TIMESTAMP NOT NULL, reviewed_by BIGINT, reviewed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(pair_id, author_id)
);

CREATE TABLE pair_material_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, pair_id BIGINT NOT NULL, material_type VARCHAR(20) NOT NULL,
  material_id BIGINT NOT NULL, revision_no INT NOT NULL, action VARCHAR(24) NOT NULL,
  reason VARCHAR(500), snapshot VARCHAR(8000) NOT NULL, operator_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE grade_import_batch (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, period_id BIGINT NOT NULL, stage VARCHAR(12) NOT NULL,
  label VARCHAR(100) NOT NULL, storage_key VARCHAR(255) NOT NULL, original_filename VARCHAR(255) NOT NULL,
  mime_type VARCHAR(100) NOT NULL, file_size BIGINT NOT NULL, field_mapping JSON NOT NULL,
  status VARCHAR(24) NOT NULL, total_rows INT NOT NULL DEFAULT 0, participant_rows INT NOT NULL DEFAULT 0,
  error_rows INT NOT NULL DEFAULT 0, uploaded_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL
);

CREATE TABLE participant_term_grade (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, period_id BIGINT NOT NULL, student_user_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL, stage VARCHAR(12) NOT NULL, import_batch_id BIGINT NOT NULL,
  term_gpa DECIMAL(4,2) NOT NULL, term_gpa_rank INT NULL, professional_student_count INT NULL, student_no_snapshot VARCHAR(32) NOT NULL,
  name_snapshot VARCHAR(64) NOT NULL, college_snapshot VARCHAR(128), grade_snapshot VARCHAR(32),
  major_snapshot VARCHAR(128), class_name_snapshot VARCHAR(128), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(period_id, student_user_id, stage)
);

CREATE TABLE grade_cohort_stat (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, import_batch_id BIGINT NOT NULL, period_id BIGINT NOT NULL,
  stage VARCHAR(12) NOT NULL, grade VARCHAR(32) NOT NULL, major VARCHAR(128) NOT NULL,
  student_count INT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(import_batch_id, grade, major)
);

CREATE TABLE grade_correction_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, grade_id BIGINT NOT NULL, old_term_gpa DECIMAL(4,2) NOT NULL,
  new_term_gpa DECIMAL(4,2) NOT NULL, old_term_gpa_rank INT, new_term_gpa_rank INT,
  reason VARCHAR(500) NOT NULL, operator_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE moral_score_file (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, period_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL,
  storage_key VARCHAR(255) NOT NULL, original_filename VARCHAR(255) NOT NULL, mime_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', uploaded_by BIGINT NOT NULL,
  published_by BIGINT, published_at TIMESTAMP, withdrawn_at TIMESTAMP, withdrawn_by BIGINT,
  deleted BOOLEAN NOT NULL DEFAULT FALSE, deleted_by BIGINT, deleted_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, period_id BIGINT, title VARCHAR(200) NOT NULL,
  content VARCHAR(2000) NOT NULL, audience_type VARCHAR(24) NOT NULL, audience_label VARCHAR(100),
  created_by BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_recipient (
  notification_id BIGINT NOT NULL, user_id BIGINT NOT NULL, read_at TIMESTAMP,
  PRIMARY KEY(notification_id,user_id)
);

CREATE TABLE storage_upload (
 id VARCHAR(36) PRIMARY KEY,
 owner_id BIGINT NOT NULL,
 object_key VARCHAR(255) NOT NULL UNIQUE,
 request_json TEXT NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 status VARCHAR(20) NOT NULL,
 result_json MEDIUMTEXT NULL,
 INDEX idx_storage_upload_expiry (expires_at)
);

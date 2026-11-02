ALTER TABLE match_candidate
    ADD COLUMN navigator_decision VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER status,
    ADD COLUMN navigator_decided_at DATETIME(3) NULL AFTER navigator_decision,
    ADD COLUMN climber_decision VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER navigator_decided_at,
    ADD COLUMN climber_decided_at DATETIME(3) NULL AFTER climber_decision,
    ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER climber_decided_at,
    ADD COLUMN published_at DATETIME(3) NULL AFTER rejection_reason,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at,
    ADD CONSTRAINT ck_match_candidate_navigator_decision
        CHECK (navigator_decision IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    ADD CONSTRAINT ck_match_candidate_climber_decision
        CHECK (climber_decision IN ('PENDING', 'CONFIRMED', 'REJECTED'));

CREATE INDEX idx_match_candidate_status ON match_candidate(round_id, status);
CREATE INDEX idx_match_candidate_navigator ON match_candidate(navigator_application_id, status);
CREATE INDEX idx_match_candidate_climber ON match_candidate(climber_application_id, status);

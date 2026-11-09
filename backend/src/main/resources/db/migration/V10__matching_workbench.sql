CREATE TABLE matching_policy (
    period_id BIGINT PRIMARY KEY,
    revision INT NOT NULL,
    policy_json TEXT NOT NULL,
    FOREIGN KEY (period_id) REFERENCES activity_period(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE matching_evidence (
    application_id BIGINT PRIMARY KEY,
    application_revision INT NOT NULL,
    facts_json TEXT NOT NULL,
    reviewed BOOLEAN NOT NULL,
    FOREIGN KEY (application_id) REFERENCES application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE matching_exclusion (
    period_id BIGINT NOT NULL,
    first_application_id BIGINT NOT NULL,
    second_application_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (period_id, first_application_id, second_application_id),
    FOREIGN KEY (period_id) REFERENCES activity_period(id),
    FOREIGN KEY (first_application_id) REFERENCES application(id),
    FOREIGN KEY (second_application_id) REFERENCES application(id),
    CHECK (first_application_id < second_application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE matching_provider (
    id INT PRIMARY KEY,
    ciphertext VARCHAR(2048) NOT NULL,
    key_suffix VARCHAR(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE match_round ADD COLUMN policy_revision INT NULL, ADD COLUMN draft_revision INT NULL;
ALTER TABLE match_candidate ADD COLUMN match_kind VARCHAR(24) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN manually_selected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE support_pair ADD COLUMN match_kind VARCHAR(24) NOT NULL DEFAULT 'LEGACY';

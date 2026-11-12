ALTER TABLE application ADD COLUMN extra_navigator_slot BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE match_candidate ADD COLUMN navigator_slot TINYINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_match_candidate_navigator_slot CHECK (navigator_slot IN (1, 2));

ALTER TABLE support_pair ADD COLUMN navigator_slot TINYINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_support_pair_navigator_slot CHECK (navigator_slot IN (1, 2)),
    ADD UNIQUE KEY uk_support_pair_navigator_slot (period_id, navigator_application_id, navigator_slot),
    DROP INDEX uk_support_pair_navigator;

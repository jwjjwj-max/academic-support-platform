ALTER TABLE application
    DROP CHECK ck_application_selection,
    ADD CONSTRAINT ck_application_selection CHECK (
        selection_status IN ('UNDECIDED','MATCHING_POOL','WAITLIST','NOT_SELECTED','REMOVED_FROM_POOL')
    );

ALTER TABLE pair_reflection
    DROP CHECK ck_pair_reflection_status;

-- MySQL applies ALTER TABLE statements immediately, so the old constraint must
-- be removed before existing ADMIN_PENDING rows can be migrated.
UPDATE pair_reflection
SET status = 'SUBMITTED'
WHERE status = 'ADMIN_PENDING';

ALTER TABLE pair_reflection
    ADD CONSTRAINT ck_pair_reflection_status CHECK (status IN ('SUBMITTED','APPROVED','REJECTED'));

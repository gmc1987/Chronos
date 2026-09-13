ALTER TABLE edu_schedule_candidate_plan
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS owner_username VARCHAR(128),
    ADD COLUMN IF NOT EXISTS collaboration_remark VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_comment VARCHAR(1000);

UPDATE edu_schedule_candidate_plan
SET owner_username = generated_by
WHERE owner_username IS NULL;

ALTER TABLE edu_schedule_candidate_plan
    ALTER COLUMN owner_username SET NOT NULL;

ALTER TABLE edu_schedule_candidate_plan
    DROP CONSTRAINT IF EXISTS ck_edu_schedule_candidate_review_status;
ALTER TABLE edu_schedule_candidate_plan
    ADD CONSTRAINT ck_edu_schedule_candidate_review_status CHECK (
        review_status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')
    );

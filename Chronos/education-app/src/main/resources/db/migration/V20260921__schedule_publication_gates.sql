ALTER TABLE edu_schedule_policy
    ADD COLUMN IF NOT EXISTS block_hard_conflicts BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS block_incomplete_offerings BOOLEAN NOT NULL DEFAULT TRUE;

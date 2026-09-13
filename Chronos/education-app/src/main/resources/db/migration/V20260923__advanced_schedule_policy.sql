ALTER TABLE edu_schedule_policy
    ADD COLUMN IF NOT EXISTS teacher_gap_penalty INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS minimum_campus_travel_periods INTEGER NOT NULL DEFAULT 1;

ALTER TABLE edu_schedule_policy
    DROP CONSTRAINT IF EXISTS ck_edu_schedule_policy_advanced;
ALTER TABLE edu_schedule_policy
    ADD CONSTRAINT ck_edu_schedule_policy_advanced CHECK (
        teacher_gap_penalty BETWEEN 0 AND 100000
        AND minimum_campus_travel_periods BETWEEN 0 AND 10
    );

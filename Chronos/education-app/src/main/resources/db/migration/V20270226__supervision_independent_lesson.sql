ALTER TABLE edu_supervision_assignment
    ALTER COLUMN schedule_entry_id DROP NOT NULL,
    ADD COLUMN observed_course varchar(150),
    ADD COLUMN observed_location varchar(150),
    ADD COLUMN observed_starts_at timestamp,
    ADD COLUMN observed_ends_at timestamp;

ALTER TABLE edu_supervision_assignment ADD CONSTRAINT ck_supervision_lesson_source
    CHECK (
        (schedule_entry_id IS NOT NULL AND observed_course IS NULL AND observed_location IS NULL
            AND observed_starts_at IS NULL AND observed_ends_at IS NULL)
        OR
        (schedule_entry_id IS NULL AND observed_course IS NOT NULL AND observed_location IS NOT NULL
            AND observed_starts_at IS NOT NULL AND observed_ends_at IS NOT NULL
            AND observed_starts_at < observed_ends_at)
    );

CREATE UNIQUE INDEX uq_supervision_observed_lesson_assignment
    ON edu_supervision_assignment(plan_id, supervisor_id, teacher_id, observed_starts_at)
    WHERE schedule_entry_id IS NULL;

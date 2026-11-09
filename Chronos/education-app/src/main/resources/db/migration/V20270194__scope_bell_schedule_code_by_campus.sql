ALTER TABLE edu_bell_schedule
    DROP CONSTRAINT IF EXISTS edu_bell_schedule_schedule_code_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_bell_schedule_scope_code
    ON edu_bell_schedule (academic_term_id, campus_id, schedule_code);

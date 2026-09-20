ALTER TABLE edu_teacher_profile
    ADD COLUMN IF NOT EXISTS employment_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

UPDATE edu_teacher_profile
SET employment_status = CASE WHEN enabled THEN 'ACTIVE' ELSE 'SUSPENDED' END
WHERE employment_status = 'ACTIVE' AND enabled = FALSE;

CREATE TABLE IF NOT EXISTS edu_teacher_employment_change (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128),
    create_time TIMESTAMP,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    teacher_id VARCHAR(64) NOT NULL REFERENCES edu_teacher_profile(id),
    change_type VARCHAR(24) NOT NULL,
    from_status VARCHAR(24) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    from_department_id VARCHAR(64),
    to_department_id VARCHAR(64),
    effective_date DATE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    applied_at TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_teacher_employment_change_type
        CHECK (change_type IN ('TRANSFER', 'SUSPEND', 'RESUME', 'TERMINATE')),
    CONSTRAINT ck_teacher_employment_change_status
        CHECK (status IN ('SCHEDULED', 'APPLIED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_teacher_employment_change_history
    ON edu_teacher_employment_change (teacher_id, effective_date DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_teacher_employment_change_scheduled
    ON edu_teacher_employment_change (teacher_id)
    WHERE status = 'SCHEDULED';

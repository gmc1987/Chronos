ALTER TABLE edu_internship_position
    DROP CONSTRAINT ck_edu_internship_position_status,
    ADD CONSTRAINT ck_edu_internship_position_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'ACTIVE', 'REJECTED', 'INACTIVE')),
    ALTER COLUMN status SET DEFAULT 'DRAFT',
    ADD COLUMN submitted_by varchar(128),
    ADD COLUMN submitted_at timestamp,
    ADD COLUMN approved_by varchar(128),
    ADD COLUMN approved_at timestamp,
    ADD COLUMN decision_comment varchar(1000);

CREATE TABLE IF NOT EXISTS edu_schedule_date_exception (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    semester_code varchar(32) NOT NULL,
    source_entry_id varchar(64) NOT NULL,
    source_date date NOT NULL,
    exception_type varchar(24) NOT NULL,
    target_date date,
    target_period_no integer,
    target_classroom_id varchar(64),
    substitute_teacher_id varchar(64),
    reason varchar(500) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_edu_schedule_exception_type CHECK (
        exception_type IN ('MOVE', 'CANCEL', 'SUBSTITUTE', 'MAKEUP')
    ),
    CONSTRAINT fk_edu_schedule_exception_entry FOREIGN KEY (source_entry_id)
        REFERENCES edu_schedule_entry(id),
    CONSTRAINT fk_edu_schedule_exception_room FOREIGN KEY (target_classroom_id)
        REFERENCES edu_classroom(id)
);

CREATE INDEX IF NOT EXISTS idx_edu_schedule_exception_dates
    ON edu_schedule_date_exception (semester_code, source_date, target_date, status);

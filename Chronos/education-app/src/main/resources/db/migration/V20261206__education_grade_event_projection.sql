CREATE TABLE IF NOT EXISTS data_grade_event_fact (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    event_id varchar(128) NOT NULL UNIQUE,
    event_type varchar(80) NOT NULL,
    aggregate_id varchar(64) NOT NULL,
    occurred_at timestamp NOT NULL,
    offering_id varchar(64),
    student_id varchar(64),
    score numeric(18,4),
    max_score numeric(18,4),
    source_version varchar(32) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_data_grade_event_fact_type_time
    ON data_grade_event_fact(event_type, occurred_at);

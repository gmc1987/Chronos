CREATE TABLE IF NOT EXISTS edu_schedule_generation_job (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    last_update_by VARCHAR(64),
    last_update_time TIMESTAMP,
    semester_code VARCHAR(32) NOT NULL,
    request_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
    progress INTEGER NOT NULL DEFAULT 0,
    result_candidate_ids TEXT,
    error_message VARCHAR(2000),
    requested_by VARCHAR(128) NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_edu_schedule_generation_job_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_edu_schedule_generation_job_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_edu_schedule_job_semester_time
    ON edu_schedule_generation_job (semester_code, create_time DESC);

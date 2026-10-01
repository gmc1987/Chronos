ALTER TABLE edu_leave_request
    ADD COLUMN IF NOT EXISTS cancellation_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS cancellation_requested_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cancellation_requested_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancellation_decided_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cancellation_decided_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancellation_comment VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_leave_request_applicant
    ON edu_leave_request (applicant_type, applicant_id, start_date DESC);
CREATE INDEX IF NOT EXISTS idx_leave_cancellation_pending
    ON edu_leave_request (cancellation_requested_at)
    WHERE cancellation_status = 'PENDING';

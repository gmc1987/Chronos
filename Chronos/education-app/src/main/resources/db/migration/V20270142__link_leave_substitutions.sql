ALTER TABLE edu_schedule_date_exception
    ADD COLUMN IF NOT EXISTS leave_record_id varchar(64);

-- Earlier browser-created leave substitutions stored the record ID only in the generated reason.
-- Recover that narrow, machine-generated format without treating arbitrary manual notes as links.
UPDATE edu_schedule_date_exception exception
SET leave_record_id = leave.id
FROM edu_leave_request leave
WHERE exception.leave_record_id IS NULL
  AND exception.exception_type = 'SUBSTITUTE'
  AND exception.reason = '教师请假代课（请假记录：' || leave.id || '）';

CREATE INDEX IF NOT EXISTS idx_edu_date_exception_leave_active
    ON edu_schedule_date_exception (leave_record_id, exception_type, status, source_date)
    WHERE leave_record_id IS NOT NULL;

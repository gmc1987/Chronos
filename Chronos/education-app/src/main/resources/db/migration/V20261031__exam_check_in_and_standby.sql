-- 报到、缺勤和考前升级保留独立时间戳，避免覆盖原有确认领取状态。
ALTER TABLE edu_exam_invigilation
    ADD COLUMN IF NOT EXISTS checked_in_at timestamp;

ALTER TABLE edu_exam_invigilation
    ADD COLUMN IF NOT EXISTS absent_at timestamp;

ALTER TABLE edu_exam_invigilation
    ADD COLUMN IF NOT EXISTS absence_escalated_at timestamp;

CREATE INDEX IF NOT EXISTS idx_edu_exam_invigilation_check_in
    ON edu_exam_invigilation(status, checked_in_at, absence_escalated_at);

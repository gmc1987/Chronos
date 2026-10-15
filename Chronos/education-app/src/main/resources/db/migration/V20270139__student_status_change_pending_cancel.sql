-- 待生效异动可以取消；同一学生最多保留一条尚未结束的异动。
ALTER TABLE edu_student_status_change
  DROP CONSTRAINT IF EXISTS ck_student_status_change_status;

ALTER TABLE edu_student_status_change
  ADD CONSTRAINT ck_student_status_change_status
  CHECK (status IN ('PENDING', 'APPROVED_PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

DROP INDEX IF EXISTS uk_student_status_change_pending;

CREATE UNIQUE INDEX uk_student_status_change_open
  ON edu_student_status_change (student_id)
  WHERE status IN ('PENDING', 'APPROVED_PENDING');

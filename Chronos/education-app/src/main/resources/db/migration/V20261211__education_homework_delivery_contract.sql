-- Follow-up contract for the already-created homework delivery tables.
-- Keep this version after all existing education migrations; do not edit executed files.
UPDATE edu_homework_assignment
SET late_rule = CASE
  WHEN allow_late THEN 'ALLOW'
  ELSE 'REJECT'
END
WHERE late_rule IS NULL OR late_rule NOT IN ('REJECT', 'ALLOW');

ALTER TABLE edu_homework_assignment
  DROP CONSTRAINT IF EXISTS ck_edu_homework_assignment_late_rule;
ALTER TABLE edu_homework_assignment
  ADD CONSTRAINT ck_edu_homework_assignment_late_rule
  CHECK (late_rule IN ('REJECT', 'ALLOW'));

ALTER TABLE edu_homework_assignment
  DROP CONSTRAINT IF EXISTS ck_edu_homework_assignment_publish_audience;

-- 兼容旧版本使用的 STUDENT 值及历史空值。新模型统一使用教学班在籍学生范围，
-- 先归一化历史数据再增加约束，避免生产库升级被已有作业记录阻断。
UPDATE edu_homework_assignment
SET publish_audience = 'ENROLLED_STUDENTS'
WHERE publish_audience IS NULL
   OR publish_audience NOT IN ('ENROLLED_STUDENTS', 'ALL_STUDENTS');

ALTER TABLE edu_homework_assignment
  ADD CONSTRAINT ck_edu_homework_assignment_publish_audience
  CHECK (publish_audience IN ('ENROLLED_STUDENTS', 'ALL_STUDENTS'));

CREATE INDEX IF NOT EXISTS idx_edu_homework_submission_student_status
  ON edu_homework_submission(student_id, status);

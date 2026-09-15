ALTER TABLE edu_homework_assignment
  ADD COLUMN IF NOT EXISTS type varchar(32) NOT NULL DEFAULT 'HOMEWORK',
  ADD COLUMN IF NOT EXISTS start_at timestamp,
  ADD COLUMN IF NOT EXISTS attempt_limit integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS late_rule varchar(32) NOT NULL DEFAULT 'REJECT',
  ADD COLUMN IF NOT EXISTS publish_audience varchar(32) NOT NULL DEFAULT 'ENROLLED_STUDENTS',
  ADD COLUMN IF NOT EXISTS attachment_snapshot_json text NOT NULL DEFAULT '[]';

ALTER TABLE edu_homework_submission
  ALTER COLUMN status SET DEFAULT 'NOT_STARTED',
  ADD COLUMN IF NOT EXISTS attempt_no integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS question_scores_json text NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS annotation_snapshot_json text NOT NULL DEFAULT '[]',
  ADD COLUMN IF NOT EXISTS attachment_snapshot_json text NOT NULL DEFAULT '[]',
  ADD COLUMN IF NOT EXISTS grades_published boolean NOT NULL DEFAULT false;

ALTER TABLE edu_homework_submission DROP CONSTRAINT IF EXISTS ck_edu_homework_submission_status;
ALTER TABLE edu_homework_submission ADD CONSTRAINT ck_edu_homework_submission_status
  CHECK (status IN ('NOT_STARTED','DRAFT','SUBMITTED','RETURNED_FOR_REVISION','GRADED'));

INSERT INTO t_permission
 (id, create_by, create_time, permission_code, permission_name, permission_type,
  action_type, built_in, status)
VALUES
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:archive', '归档作业', 'MENU_ACTION', 'EXECUTE', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:submission:publish', '发布作业成绩', 'MENU_ACTION', 'EXECUTE', true, 1)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code IN ('ROLE_PLATFORM_ADMIN', 'EDU_TEACHER', 'EDU_ADMIN')
  AND p.permission_code IN ('education:homework:archive', 'education:homework:submission:publish')
ON CONFLICT DO NOTHING;

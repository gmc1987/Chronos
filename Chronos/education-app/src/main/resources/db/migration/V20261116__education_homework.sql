CREATE TABLE IF NOT EXISTS edu_homework_assignment (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  offering_id varchar(64) NOT NULL,
  teaching_plan_item_id varchar(64),
  preparation_id varchar(64),
  lesson_plan_id varchar(64),
  title varchar(200) NOT NULL,
  question_snapshot_json text NOT NULL DEFAULT '[]',
  instructions_json text,
  due_at timestamp,
  max_score integer NOT NULL DEFAULT 100,
  allow_late boolean NOT NULL DEFAULT false,
  status varchar(24) NOT NULL DEFAULT 'DRAFT',
  CONSTRAINT ck_edu_homework_assignment_status CHECK (status IN ('DRAFT','PUBLISHED','CLOSED','ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_edu_homework_assignment_offering
  ON edu_homework_assignment(offering_id, status);

CREATE TABLE IF NOT EXISTS edu_homework_submission (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  assignment_id varchar(64) NOT NULL,
  student_id varchar(64) NOT NULL,
  answer_snapshot_json text NOT NULL DEFAULT '{}',
  status varchar(24) NOT NULL DEFAULT 'DRAFT',
  score integer,
  teacher_feedback text,
  submitted_at timestamp,
  graded_at timestamp,
  CONSTRAINT uk_edu_homework_submission_student UNIQUE (assignment_id, student_id),
  CONSTRAINT ck_edu_homework_submission_status CHECK
    (status IN ('DRAFT','SUBMITTED','GRADED','RETURNED_FOR_REVISION'))
);
CREATE INDEX IF NOT EXISTS idx_edu_homework_submission_assignment
  ON edu_homework_submission(assignment_id, status);

INSERT INTO t_permission
 (id, create_by, create_time, permission_code, permission_name, permission_type,
  action_type, built_in, status)
VALUES
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:view', '查看作业', 'MENU_ACTION', 'VIEW', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:create', '创建作业', 'MENU_ACTION', 'CREATE', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:update', '修改作业', 'MENU_ACTION', 'UPDATE', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:publish', '发布作业', 'MENU_ACTION', 'EXECUTE', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:close', '关闭作业', 'MENU_ACTION', 'EXECUTE', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:submission:view', '查看作业提交', 'MENU_ACTION', 'VIEW', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:submission:create', '创建作业答案', 'MENU_ACTION', 'CREATE', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:submission:update', '修改作业答案', 'MENU_ACTION', 'UPDATE', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:submission:submit', '提交作业答案', 'MENU_ACTION', 'EXECUTE', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:submission:grade', '批改作业', 'MENU_ACTION', 'UPDATE', true, 1),
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'education:homework:manage', '管理作业', 'MENU_ACTION', 'MANAGE', true, 1)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code = 'ROLE_PLATFORM_USER'
  AND p.permission_code IN (
    'education:homework:view',
    'education:homework:submission:create',
    'education:homework:submission:update',
    'education:homework:submission:submit')
ON CONFLICT DO NOTHING;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code IN ('ROLE_PLATFORM_ADMIN', 'EDU_TEACHER', 'EDU_ADMIN')
  AND p.permission_code LIKE 'education:homework:%'
ON CONFLICT DO NOTHING;

-- V20261023 indexes the homework table and V20261107/V20261111 extend it.
-- Create the same base tables as V20261116 early so fresh databases can run
-- those immutable migrations in order. The later CREATE TABLE IF NOT EXISTS
-- remains valid for databases that already applied it.
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

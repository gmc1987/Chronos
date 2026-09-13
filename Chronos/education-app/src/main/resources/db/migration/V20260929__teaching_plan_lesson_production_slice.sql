-- Production slice: teaching plans and lesson plans.
-- This migration never rewrites a ran migration.  Checks deliberately fail before
-- constraints are changed so an operator can repair bad historical data.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM edu_teaching_review_record
             GROUP BY resource_type, resource_id HAVING count(*) > 1)
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                     WHERE table_name='edu_teaching_review_record' AND column_name='submission_no') THEN
    RAISE EXCEPTION 'duplicate teaching review history requires manual backup/review';
  END IF;
  IF EXISTS (SELECT 1 FROM edu_teaching_plan_item i
             LEFT JOIN edu_teaching_plan p ON p.id=i.plan_id WHERE p.id IS NULL)
     OR EXISTS (SELECT 1 FROM edu_teaching_plan_version v
             LEFT JOIN edu_teaching_plan p ON p.id=v.plan_id WHERE p.id IS NULL)
     OR EXISTS (SELECT 1 FROM edu_lesson_plan_version v
             LEFT JOIN edu_lesson_plan p ON p.id=v.lesson_plan_id WHERE p.id IS NULL) THEN
    RAISE EXCEPTION 'orphan teaching plan/lesson rows require manual repair';
  END IF;
END $$;

ALTER TABLE edu_teaching_plan
  ADD COLUMN IF NOT EXISTS school_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS campus_id varchar(64),
  ADD COLUMN IF NOT EXISTS owner_teacher_id varchar(64),
  ADD COLUMN IF NOT EXISTS semester_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS course_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS plan_type varchar(24) NOT NULL DEFAULT 'SEMESTER',
  ADD COLUMN IF NOT EXISTS total_hours integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS objective text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS assessment_method text,
  ADD COLUMN IF NOT EXISTS remarks text,
  ADD COLUMN IF NOT EXISTS current_version_no integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS published_version_no integer,
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
UPDATE edu_teaching_plan p SET semester_id=o.semester_code, course_id=o.course_code,
  campus_id=COALESCE(p.campus_id,o.campus_id), owner_teacher_id=o.teacher_id
  FROM edu_course_offering o WHERE o.id=p.offering_id;

ALTER TABLE edu_teaching_plan_item
  ADD COLUMN IF NOT EXISTS week_start integer,
  ADD COLUMN IF NOT EXISTS week_end integer,
  ADD COLUMN IF NOT EXISTS training_hours integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS assessment_method text,
  ADD COLUMN IF NOT EXISTS linked_knowledge_point_id varchar(64);

ALTER TABLE edu_teaching_plan_version
  ADD COLUMN IF NOT EXISTS snapshot_hash varchar(64),
  ADD COLUMN IF NOT EXISTS source_row_version bigint,
  ADD COLUMN IF NOT EXISTS review_record_id varchar(64);
ALTER TABLE edu_lesson_plan
  ADD COLUMN IF NOT EXISTS school_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS campus_id varchar(64),
  ADD COLUMN IF NOT EXISTS owner_teacher_id varchar(64),
  ADD COLUMN IF NOT EXISTS teaching_plan_id varchar(64),
  ADD COLUMN IF NOT EXISTS plan_item_id varchar(64),
  ADD COLUMN IF NOT EXISTS lesson_no integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS teaching_week integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS lesson_hours integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS lesson_type varchar(24) NOT NULL DEFAULT 'REGULAR',
  ADD COLUMN IF NOT EXISTS objectives text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS key_points text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS difficult_points text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS teaching_method text,
  ADD COLUMN IF NOT EXISTS classroom_activity text,
  ADD COLUMN IF NOT EXISTS assessment_design text,
  ADD COLUMN IF NOT EXISTS after_class_reflection text,
  ADD COLUMN IF NOT EXISTS safety_notes text,
  ADD COLUMN IF NOT EXISTS equipment_requirements text,
  ADD COLUMN IF NOT EXISTS published_version_no integer,
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
UPDATE edu_lesson_plan l SET campus_id=o.campus_id, owner_teacher_id=o.teacher_id
  FROM edu_course_offering o WHERE o.id=l.offering_id;
ALTER TABLE edu_lesson_plan_version
  ADD COLUMN IF NOT EXISTS snapshot_json text,
  ADD COLUMN IF NOT EXISTS snapshot_hash varchar(64),
  ADD COLUMN IF NOT EXISTS submitted_at timestamp;

ALTER TABLE edu_teaching_review_record
  ADD COLUMN IF NOT EXISTS version_id varchar(64),
  ADD COLUMN IF NOT EXISTS submitter_id varchar(128),
  ADD COLUMN IF NOT EXISTS submitted_at timestamp,
  ADD COLUMN IF NOT EXISTS completed_at timestamp,
  ADD COLUMN IF NOT EXISTS snapshot_hash varchar(64),
  ADD COLUMN IF NOT EXISTS submission_no integer NOT NULL DEFAULT 1;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uq_teaching_review_resource') THEN
    ALTER TABLE edu_teaching_review_record DROP CONSTRAINT uq_teaching_review_resource;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='uq_teaching_review_resource') THEN
    DROP INDEX uq_teaching_review_resource;
  END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_teaching_review_submission
 ON edu_teaching_review_record(resource_type, resource_id, submission_no);
CREATE INDEX IF NOT EXISTS idx_teaching_plan_offering ON edu_teaching_plan(offering_id);
CREATE INDEX IF NOT EXISTS idx_lesson_plan_offering ON edu_lesson_plan(offering_id);

-- Idempotent IAM atoms. Data scope is still enforced by EducationDataScopeService.
INSERT INTO t_permission
 (id,create_by,create_time,permission_code,permission_name,permission_type,action_type,built_in,status)
VALUES
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:plan:view','查看教学计划','MENU_ACTION','VIEW',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:plan:create','创建教学计划','MENU_ACTION','CREATE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:plan:update','修改教学计划','MENU_ACTION','UPDATE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:plan:submit','提交教学计划','MENU_ACTION','EXECUTE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:plan:archive','归档教学计划','MENU_ACTION','DELETE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:lesson:view','查看教案','MENU_ACTION','VIEW',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:lesson:create','创建教案','MENU_ACTION','CREATE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:lesson:update','修改教案','MENU_ACTION','UPDATE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:lesson:submit','提交教案','MENU_ACTION','EXECUTE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:lesson:archive','归档教案','MENU_ACTION','DELETE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:review','审核教学内容','MENU_ACTION','MANAGE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:plan:export','导出教学计划','MENU_ACTION','VIEW',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:lesson:export','导出教案','MENU_ACTION','VIEW',true,1)
ON CONFLICT (permission_code) DO NOTHING;

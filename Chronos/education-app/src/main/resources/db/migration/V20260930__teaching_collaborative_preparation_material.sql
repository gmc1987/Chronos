-- 第二切片：集体备课与课件/教学材料。仅新增结构，不修改已运行迁移或课表表。
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM edu_preparation WHERE offering_id IS NULL)
     OR EXISTS (SELECT 1 FROM edu_courseware WHERE offering_id IS NULL)
     OR EXISTS (SELECT 1 FROM edu_teaching_material WHERE offering_id IS NULL) THEN
    RAISE EXCEPTION 'orphan teaching collaboration rows require manual repair';
  END IF;
END $$;

ALTER TABLE edu_preparation
  ADD COLUMN IF NOT EXISTS school_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS semester_id varchar(64),
  ADD COLUMN IF NOT EXISTS course_id varchar(64),
  ADD COLUMN IF NOT EXISTS campus_id varchar(64),
  ADD COLUMN IF NOT EXISTS schedule_entry_id varchar(64),
  ADD COLUMN IF NOT EXISTS owner_teacher_id varchar(64),
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS conclusion_lesson_plan_id varchar(64),
  ADD COLUMN IF NOT EXISTS target_plan_item_id varchar(64),
  ADD COLUMN IF NOT EXISTS agenda text,
  ADD COLUMN IF NOT EXISTS scheduled_at timestamp,
  ADD COLUMN IF NOT EXISTS location varchar(200),
  ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
  ADD COLUMN IF NOT EXISTS last_update_time timestamp;
ALTER TABLE edu_preparation_member
  ADD COLUMN IF NOT EXISTS invitation_status varchar(24) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS invited_by varchar(128),
  ADD COLUMN IF NOT EXISTS invited_at timestamp,
  ADD COLUMN IF NOT EXISTS responded_at timestamp,
  ADD COLUMN IF NOT EXISTS response_comment text;
ALTER TABLE edu_preparation_material
  ADD COLUMN IF NOT EXISTS bind_state varchar(24) NOT NULL DEFAULT 'PENDING_BIND',
  ADD COLUMN IF NOT EXISTS bound_at timestamp,
  ADD COLUMN IF NOT EXISTS create_by varchar(128),
  ADD COLUMN IF NOT EXISTS create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE edu_preparation_comment
  ADD COLUMN IF NOT EXISTS audit_action varchar(24) NOT NULL DEFAULT 'CREATE',
  ADD COLUMN IF NOT EXISTS updated_at timestamp,
  ADD COLUMN IF NOT EXISTS archived boolean NOT NULL DEFAULT false;
ALTER TABLE edu_courseware
  ADD COLUMN IF NOT EXISTS school_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS semester_id varchar(64),
  ADD COLUMN IF NOT EXISTS course_id varchar(64),
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS resource_category varchar(32),
  ADD COLUMN IF NOT EXISTS license_code varchar(32),
  ADD COLUMN IF NOT EXISTS published_version_no integer,
  ADD COLUMN IF NOT EXISTS campus_id varchar(64),
  ADD COLUMN IF NOT EXISTS owner_teacher_id varchar(64),
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
  ADD COLUMN IF NOT EXISTS last_update_time timestamp;
ALTER TABLE edu_courseware_version
  ADD COLUMN IF NOT EXISTS bind_state varchar(24) NOT NULL DEFAULT 'PENDING_BIND',
  ADD COLUMN IF NOT EXISTS bound_at timestamp,
  ADD COLUMN IF NOT EXISTS published_at timestamp,
  ADD COLUMN IF NOT EXISTS archived_at timestamp;
ALTER TABLE edu_courseware_version
  ADD COLUMN IF NOT EXISTS file_name varchar(255),
  ADD COLUMN IF NOT EXISTS mime_type varchar(128),
  ADD COLUMN IF NOT EXISTS file_size bigint,
  ADD COLUMN IF NOT EXISTS checksum_sha256 varchar(64),
  ADD COLUMN IF NOT EXISTS uploaded_at timestamp,
  ADD COLUMN IF NOT EXISTS review_record_id varchar(64);
ALTER TABLE edu_teaching_material
  ADD COLUMN IF NOT EXISTS school_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS semester_id varchar(64),
  ADD COLUMN IF NOT EXISTS course_id varchar(64),
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS resource_category varchar(32),
  ADD COLUMN IF NOT EXISTS license_code varchar(32),
  ADD COLUMN IF NOT EXISTS published_version_no integer,
  ADD COLUMN IF NOT EXISTS campus_id varchar(64),
  ADD COLUMN IF NOT EXISTS owner_teacher_id varchar(64),
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
  ADD COLUMN IF NOT EXISTS last_update_time timestamp;
ALTER TABLE edu_teaching_material_version
  ADD COLUMN IF NOT EXISTS bind_state varchar(24) NOT NULL DEFAULT 'PENDING_BIND',
  ADD COLUMN IF NOT EXISTS bound_at timestamp,
  ADD COLUMN IF NOT EXISTS published_at timestamp,
  ADD COLUMN IF NOT EXISTS archived_at timestamp;
ALTER TABLE edu_teaching_material_version
  ADD COLUMN IF NOT EXISTS file_name varchar(255),
  ADD COLUMN IF NOT EXISTS mime_type varchar(128),
  ADD COLUMN IF NOT EXISTS file_size bigint,
  ADD COLUMN IF NOT EXISTS checksum_sha256 varchar(64),
  ADD COLUMN IF NOT EXISTS uploaded_at timestamp,
  ADD COLUMN IF NOT EXISTS review_record_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_preparation_schedule ON edu_preparation(schedule_entry_id);
CREATE INDEX IF NOT EXISTS idx_preparation_material_bind ON edu_preparation_material(bind_state);
CREATE INDEX IF NOT EXISTS idx_courseware_version_bind ON edu_courseware_version(bind_state);
CREATE INDEX IF NOT EXISTS idx_material_version_bind ON edu_teaching_material_version(bind_state);
CREATE UNIQUE INDEX IF NOT EXISTS uq_courseware_version_no ON edu_courseware_version(courseware_id, version_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_material_version_no ON edu_teaching_material_version(material_id, version_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_preparation_member_teacher ON edu_preparation_member(preparation_id, teacher_id);

INSERT INTO t_permission
 (id,create_by,create_time,permission_code,permission_name,permission_type,action_type,built_in,status)
VALUES
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:preparation:view','查看集体备课','MENU_ACTION','VIEW',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:preparation:manage','管理集体备课','MENU_ACTION','MANAGE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:teaching:material:manage','管理课件教学材料','MENU_ACTION','MANAGE',true,1)
ON CONFLICT (permission_code) DO NOTHING;

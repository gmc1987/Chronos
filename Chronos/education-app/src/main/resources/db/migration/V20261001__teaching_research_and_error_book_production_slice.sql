-- 第四切片：教研组/活动/成果与错题沉淀。只增量扩展已存在的教学中心表。
ALTER TABLE edu_research_group
  ADD COLUMN IF NOT EXISTS school_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS campus_id varchar(64),
  ADD COLUMN IF NOT EXISTS leader_teacher_id varchar(64),
  ADD COLUMN IF NOT EXISTS course_scope_json jsonb,
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
ALTER TABLE edu_research_activity
  ADD COLUMN IF NOT EXISTS end_time timestamp,
  ADD COLUMN IF NOT EXISTS location varchar(200),
  ADD COLUMN IF NOT EXISTS agenda text,
  ADD COLUMN IF NOT EXISTS organizer_id varchar(64),
  ADD COLUMN IF NOT EXISTS minutes text,
  ADD COLUMN IF NOT EXISTS cancel_reason text,
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
ALTER TABLE edu_research_activity_member
  ADD COLUMN IF NOT EXISTS attendance_status varchar(24) NOT NULL DEFAULT 'INVITED',
  ADD COLUMN IF NOT EXISTS attendance_at timestamp,
  ADD COLUMN IF NOT EXISTS leave_reason text,
  ADD COLUMN IF NOT EXISTS responded_at timestamp;
ALTER TABLE edu_research_result
  ADD COLUMN IF NOT EXISTS result_type varchar(32),
  ADD COLUMN IF NOT EXISTS published_at timestamp,
  ADD COLUMN IF NOT EXISTS review_record_id varchar(64),
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
ALTER TABLE edu_research_material
  ADD COLUMN IF NOT EXISTS create_by varchar(128),
  ADD COLUMN IF NOT EXISTS create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE edu_error_book
  ADD COLUMN IF NOT EXISTS course_id varchar(64),
  ADD COLUMN IF NOT EXISTS semester_id varchar(64),
  ADD COLUMN IF NOT EXISTS owner_type varchar(16) NOT NULL DEFAULT 'STUDENT',
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
ALTER TABLE edu_error_item
  ADD COLUMN IF NOT EXISTS source_item_id varchar(64),
  ADD COLUMN IF NOT EXISTS wrong_count integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS last_wrong_at timestamp,
  ADD COLUMN IF NOT EXISTS mastery_status varchar(24) NOT NULL DEFAULT 'NEEDS_PRACTICE',
  ADD COLUMN IF NOT EXISTS student_note text,
  ADD COLUMN IF NOT EXISTS teacher_note text;

CREATE UNIQUE INDEX IF NOT EXISTS uq_research_group_member_teacher
  ON edu_research_group_member(group_id, teacher_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_research_activity_member_teacher
  ON edu_research_activity_member(activity_id, teacher_id);
CREATE INDEX IF NOT EXISTS idx_research_activity_calendar
  ON edu_research_activity(group_id, activity_time, status);
CREATE INDEX IF NOT EXISTS idx_research_result_review
  ON edu_research_result(review_record_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_error_confirmed_source
  ON edu_error_item(book_id, source_type, source_item_id)
  WHERE source_type = 'WRONG_ANSWER_CONFIRMED' AND source_item_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_error_book_student ON edu_error_book(student_id, course_id, semester_id);
CREATE INDEX IF NOT EXISTS idx_error_item_mastery ON edu_error_item(book_id, mastery_status);

INSERT INTO t_permission
 (id,create_by,create_time,permission_code,permission_name,permission_type,action_type,built_in,status)
VALUES
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:research:view','查看教研组与活动','MENU_ACTION','VIEW',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:research:manage','管理教研组与活动','MENU_ACTION','MANAGE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:research:result:review','审核发布教研成果','MENU_ACTION','MANAGE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:error-book:view','查看错题沉淀','MENU_ACTION','VIEW',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:error-book:manage','管理错题沉淀','MENU_ACTION','MANAGE',true,1)
ON CONFLICT (permission_code) DO NOTHING;

-- 第三切片：题库/题目与知识点。只增量补齐既有教学中心表，不修改已运行迁移。
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM edu_question_knowledge_point q
             LEFT JOIN edu_question x ON x.id=q.question_id WHERE x.id IS NULL)
     OR EXISTS (SELECT 1 FROM edu_question_knowledge_point q
             LEFT JOIN edu_knowledge_point k ON k.id=q.knowledge_point_id WHERE k.id IS NULL)
     OR EXISTS (SELECT 1 FROM edu_knowledge_point k
             LEFT JOIN edu_knowledge_point p ON p.id=k.parent_id
             WHERE k.parent_id IS NOT NULL AND p.id IS NULL) THEN
    RAISE EXCEPTION 'orphan question/knowledge rows require manual repair';
  END IF;
END $$;

ALTER TABLE edu_question_bank
  ADD COLUMN IF NOT EXISTS school_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS course_id varchar(64),
  ADD COLUMN IF NOT EXISTS owner_teacher_id varchar(64),
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS visibility varchar(24) NOT NULL DEFAULT 'PRIVATE',
  ADD COLUMN IF NOT EXISTS question_count integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
ALTER TABLE edu_question
  ADD COLUMN IF NOT EXISTS score numeric(6,2),
  ADD COLUMN IF NOT EXISTS objective boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS answer_schema_json jsonb,
  ADD COLUMN IF NOT EXISTS source varchar(32),
  ADD COLUMN IF NOT EXISTS usable_from timestamp,
  ADD COLUMN IF NOT EXISTS usable_until timestamp,
  ADD COLUMN IF NOT EXISTS published_version_id varchar(64),
  ADD COLUMN IF NOT EXISTS current_version_no integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
ALTER TABLE edu_knowledge_point
  ADD COLUMN IF NOT EXISTS school_id varchar(64) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN IF NOT EXISTS code varchar(64),
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS learning_objective text,
  ADD COLUMN IF NOT EXISTS level smallint,
  ADD COLUMN IF NOT EXISTS status varchar(24) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS edu_question_version (
 id varchar(64) PRIMARY KEY, question_id varchar(64) NOT NULL,
 version_no integer NOT NULL, snapshot_json jsonb NOT NULL,
 snapshot_hash varchar(64) NOT NULL, status varchar(24) NOT NULL DEFAULT 'DRAFT',
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_at timestamp
);
CREATE TABLE IF NOT EXISTS edu_question_reference (
 id varchar(64) PRIMARY KEY, question_id varchar(64) NOT NULL,
 version_id varchar(64) NOT NULL, consumer_type varchar(24) NOT NULL,
 consumer_id varchar(64) NOT NULL, create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS edu_question_file (
 question_id varchar(64) NOT NULL, file_id varchar(64) NOT NULL,
 PRIMARY KEY(question_id, file_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_question_version_no ON edu_question_version(question_id, version_no);
CREATE INDEX IF NOT EXISTS idx_question_reference_question ON edu_question_reference(question_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_question_bank_name
 ON edu_question_bank(school_id, course_id, name) WHERE archived = false;
CREATE UNIQUE INDEX IF NOT EXISTS uq_knowledge_point_code
 ON edu_knowledge_point(school_id, course_id, code) WHERE code IS NOT NULL AND archived = false;

INSERT INTO t_permission
 (id,create_by,create_time,permission_code,permission_name,permission_type,action_type,built_in,status)
VALUES
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:question-bank:view','查看题库','MENU_ACTION','VIEW',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:question-bank:manage','管理题库','MENU_ACTION','MANAGE',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:knowledge-point:view','查看知识点','MENU_ACTION','VIEW',true,1),
 (gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,'education:knowledge-point:manage','管理知识点','MENU_ACTION','MANAGE',true,1)
ON CONFLICT (permission_code) DO NOTHING;

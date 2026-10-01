-- Configurable, versioned grade rules. The PLATFORM_DEFAULT rows are explicit
-- seed data and may be copied into a school-owned draft before editing.
BEGIN;

CREATE TABLE IF NOT EXISTS edu_grade_rule_set (
 id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
 last_update_by varchar(128), last_update_time timestamp, school_id varchar(64) NOT NULL,
 name varchar(128) NOT NULL, code varchar(32) NOT NULL, status varchar(24) NOT NULL DEFAULT 'DRAFT',
 version_no integer NOT NULL DEFAULT 1, platform_default boolean NOT NULL DEFAULT false,
 makeup_strategy varchar(24) NOT NULL DEFAULT 'OVERWRITE', row_version bigint NOT NULL DEFAULT 0,
 CONSTRAINT ck_edu_grade_rule_set_status CHECK(status IN('DRAFT','PUBLISHED','INACTIVE')),
 CONSTRAINT ck_edu_grade_rule_set_makeup CHECK(makeup_strategy IN('OVERWRITE','HIGHEST','PASS_CAP','SEPARATE_RECORD')),
 CONSTRAINT uk_edu_grade_rule_set_code_version UNIQUE(code,version_no)
);

CREATE TABLE IF NOT EXISTS edu_grade_rule (
 id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
 last_update_by varchar(128), last_update_time timestamp, rule_set_id varchar(64) NOT NULL,
 min_score numeric(8,2) NOT NULL, max_score numeric(8,2) NOT NULL, grade_level varchar(16) NOT NULL,
 grade_point numeric(5,2) NOT NULL, passed boolean NOT NULL, sort_order integer NOT NULL DEFAULT 0,
 CONSTRAINT ck_edu_grade_rule_range CHECK(min_score >= 0 AND max_score >= min_score),
 CONSTRAINT uk_edu_grade_rule_order UNIQUE(rule_set_id,sort_order)
);

ALTER TABLE edu_assessment_scheme ADD COLUMN IF NOT EXISTS grade_rule_set_id varchar(64);
ALTER TABLE edu_course_grade ADD COLUMN IF NOT EXISTS grade_rule_set_id varchar(64) DEFAULT 'PLATFORM_DEFAULT';
ALTER TABLE edu_course_grade ADD COLUMN IF NOT EXISTS grade_rule_version_no integer DEFAULT 1;
ALTER TABLE edu_course_grade ADD COLUMN IF NOT EXISTS grade_rule_snapshot_json text DEFAULT '[]';
ALTER TABLE edu_grade_publish_snapshot ADD COLUMN IF NOT EXISTS grade_rule_set_id varchar(64) DEFAULT 'PLATFORM_DEFAULT';
ALTER TABLE edu_grade_publish_snapshot ADD COLUMN IF NOT EXISTS grade_rule_version_no integer DEFAULT 1;
ALTER TABLE edu_grade_publish_snapshot ADD COLUMN IF NOT EXISTS grade_rule_snapshot_json text DEFAULT '[]';

INSERT INTO edu_grade_rule_set(id,create_by,create_time,last_update_by,last_update_time,school_id,name,code,status,version_no,platform_default,makeup_strategy)
VALUES ('00000000-0000-0000-0000-000000000001','SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'PLATFORM',
        '平台默认等级绩点规则','PLATFORM_DEFAULT','PUBLISHED',1,true,'OVERWRITE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO edu_grade_rule(id,create_by,create_time,last_update_by,last_update_time,rule_set_id,min_score,max_score,grade_level,grade_point,passed,sort_order)
SELECT v.id,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'00000000-0000-0000-0000-000000000001',v.min_score,v.max_score,v.grade_level,v.grade_point,v.passed,v.sort_order
FROM (VALUES
 ('00000000-0000-0000-0000-000000000011',0.00,59.99,'F',0.00,false,1),
 ('00000000-0000-0000-0000-000000000012',60.00,69.99,'D',1.00,true,2),
 ('00000000-0000-0000-0000-000000000013',70.00,79.99,'C',2.00,true,3),
 ('00000000-0000-0000-0000-000000000014',80.00,89.99,'B',3.00,true,4),
 ('00000000-0000-0000-0000-000000000015',90.00,100.00,'A',4.00,true,5)
) AS v(id,min_score,max_score,grade_level,grade_point,passed,sort_order)
WHERE NOT EXISTS (SELECT 1 FROM edu_grade_rule WHERE rule_set_id='00000000-0000-0000-0000-000000000001');

COMMIT;

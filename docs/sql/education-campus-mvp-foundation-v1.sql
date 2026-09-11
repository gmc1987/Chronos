-- 智慧校园 MVP 基础领域 V1，目标数据库 ChronosEducation。
-- 包含年级、学科、家长监护关系、教师任教关系，以及现有主数据的正式关联字段。

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('education-campus-mvp-foundation-v1'));

CREATE TABLE IF NOT EXISTS edu_grade (
    id varchar(64) PRIMARY KEY,
    grade_code varchar(64) NOT NULL UNIQUE,
    grade_name varchar(128) NOT NULL,
    enrollment_year integer NOT NULL,
    education_stage varchar(32) NOT NULL,
    director_teacher_id varchar(64),
    enabled boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp
);

CREATE TABLE IF NOT EXISTS edu_subject (
    id varchar(64) PRIMARY KEY,
    subject_code varchar(64) NOT NULL UNIQUE,
    subject_name varchar(128) NOT NULL,
    subject_category varchar(32) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp
);

CREATE TABLE IF NOT EXISTS edu_parent_profile (
    id varchar(64) PRIMARY KEY,
    parent_no varchar(64) NOT NULL UNIQUE,
    parent_name varchar(128) NOT NULL,
    gender varchar(16),
    phone varchar(32) NOT NULL,
    employment varchar(128),
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp
);

CREATE TABLE IF NOT EXISTS edu_student_guardian (
    id varchar(64) PRIMARY KEY,
    student_id varchar(64) NOT NULL REFERENCES edu_student_profile(id),
    parent_id varchar(64) NOT NULL REFERENCES edu_parent_profile(id),
    relationship varchar(32) NOT NULL,
    primary_guardian boolean NOT NULL DEFAULT false,
    emergency_contact boolean NOT NULL DEFAULT false,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_student_guardian UNIQUE (student_id, parent_id)
);

CREATE TABLE IF NOT EXISTS edu_teacher_teaching_assignment (
    id varchar(64) PRIMARY KEY,
    academic_term_id varchar(64) NOT NULL REFERENCES edu_academic_term(id),
    teacher_id varchar(64) NOT NULL REFERENCES edu_teacher_profile(id),
    subject_id varchar(64) NOT NULL REFERENCES edu_subject(id),
    grade_id varchar(64) REFERENCES edu_grade(id),
    administrative_class_id varchar(64) REFERENCES edu_administrative_class(id),
    assignment_role varchar(32) NOT NULL DEFAULT 'TEACHER',
    weekly_lessons integer NOT NULL DEFAULT 0,
    enabled boolean NOT NULL DEFAULT true,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_edu_assignment_weekly_lessons CHECK (weekly_lessons >= 0)
);

ALTER TABLE edu_administrative_class
    ADD COLUMN IF NOT EXISTS grade_id varchar(64);
ALTER TABLE edu_student_profile
    ADD COLUMN IF NOT EXISTS grade_id varchar(64);
ALTER TABLE edu_course_catalog
    ADD COLUMN IF NOT EXISTS subject_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_edu_class_grade ON edu_administrative_class (grade_id);
CREATE INDEX IF NOT EXISTS idx_edu_student_grade ON edu_student_profile (grade_id);
CREATE INDEX IF NOT EXISTS idx_edu_course_subject ON edu_course_catalog (subject_id);
CREATE INDEX IF NOT EXISTS idx_edu_guardian_student ON edu_student_guardian (student_id);
CREATE INDEX IF NOT EXISTS idx_edu_guardian_parent ON edu_student_guardian (parent_id);
CREATE INDEX IF NOT EXISTS idx_edu_assignment_teacher_term
    ON edu_teacher_teaching_assignment (teacher_id, academic_term_id);

CREATE TEMP TABLE tmp_mvp_dictionary (
    dict_code varchar(100) NOT NULL,
    root_name varchar(200) NOT NULL,
    item_value varchar(200) NOT NULL,
    item_name varchar(200) NOT NULL,
    sort_order integer NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_mvp_dictionary VALUES
    ('EDU_EDUCATION_STAGE', '教育阶段', 'PRIMARY', '小学', 10),
    ('EDU_EDUCATION_STAGE', '教育阶段', 'JUNIOR_HIGH', '初中', 20),
    ('EDU_EDUCATION_STAGE', '教育阶段', 'SENIOR_HIGH', '高中', 30),
    ('EDU_EDUCATION_STAGE', '教育阶段', 'VOCATIONAL', '中职技校', 40),
    ('EDU_EDUCATION_STAGE', '教育阶段', 'HIGHER_EDUCATION', '高等教育', 50),
    ('EDU_SUBJECT_CATEGORY', '学科类别', 'GENERAL', '公共基础学科', 10),
    ('EDU_SUBJECT_CATEGORY', '学科类别', 'PROFESSIONAL', '专业学科', 20),
    ('EDU_SUBJECT_CATEGORY', '学科类别', 'PRACTICE', '实训学科', 30),
    ('EDU_SUBJECT_CATEGORY', '学科类别', 'ACTIVITY', '活动课程', 40),
    ('EDU_GUARDIAN_RELATIONSHIP', '监护关系', 'FATHER', '父亲', 10),
    ('EDU_GUARDIAN_RELATIONSHIP', '监护关系', 'MOTHER', '母亲', 20),
    ('EDU_GUARDIAN_RELATIONSHIP', '监护关系', 'GRANDFATHER', '祖父或外祖父', 30),
    ('EDU_GUARDIAN_RELATIONSHIP', '监护关系', 'GRANDMOTHER', '祖母或外祖母', 40),
    ('EDU_GUARDIAN_RELATIONSHIP', '监护关系', 'OTHER', '其他监护人', 50),
    ('EDU_TEACHING_ROLE', '任教角色', 'TEACHER', '任课教师', 10),
    ('EDU_TEACHING_ROLE', '任教角色', 'LEAD_TEACHER', '备课组长', 20),
    ('EDU_TEACHING_ROLE', '任教角色', 'ASSISTANT', '助教', 30);

INSERT INTO t_dict (
    id, dict_code, dict_name, dict_value, parent_id, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, definition.dict_code, min(definition.root_name),
    NULL, NULL, 1, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM tmp_mvp_dictionary definition
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict existing
    WHERE existing.dict_code = definition.dict_code
      AND existing.parent_id IS NULL
)
GROUP BY definition.dict_code;

INSERT INTO t_dict (
    id, dict_code, dict_name, dict_value, parent_id, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, definition.dict_code, definition.item_name,
    definition.item_value, root.id, 1,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM tmp_mvp_dictionary definition
JOIN t_dict root
  ON root.dict_code = definition.dict_code
 AND root.parent_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict existing
    WHERE existing.dict_code = definition.dict_code
      AND existing.dict_value = definition.item_value
      AND existing.parent_id IS NOT NULL
);

-- 现有教育菜单只补路径；新菜单挂到教务中心下。
UPDATE t_menu SET path = '/admin/education/grades'
WHERE menu_name = '年级管理';
UPDATE t_menu SET path = '/admin/education/parents'
WHERE menu_name = '家长管理';

INSERT INTO t_menu (
    id, menu_name, path, parent_id, order_num,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, menu_data.menu_name, menu_data.path,
    parent.id, menu_data.order_num,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('学科管理', '/admin/education/subjects', 35),
    ('教师任教关系', '/admin/education/teaching-assignments', 65)
) AS menu_data(menu_name, path, order_num)
JOIN t_menu parent ON parent.menu_name = '教务中心'
WHERE NOT EXISTS (
    SELECT 1 FROM t_menu existing
    WHERE existing.menu_name = menu_data.menu_name
      AND existing.parent_id = parent.id
);

-- 系统管理员默认获得新增菜单，普通角色仍通过授权管理分配。
INSERT INTO t_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role
CROSS JOIN t_menu menu
WHERE role.role_code = 'ADMIN'
  AND menu.path IN (
      '/admin/education/grades',
      '/admin/education/subjects',
      '/admin/education/parents',
      '/admin/education/teaching-assignments'
  )
  AND NOT EXISTS (
      SELECT 1 FROM t_role_menu existing
      WHERE existing.role_id = role.id
        AND existing.menu_id = menu.id
  );

COMMIT;

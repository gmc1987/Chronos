BEGIN;

CREATE TABLE IF NOT EXISTS t_business_number_rule (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    business_type varchar(32) NOT NULL,
    organization_id varchar(64) NOT NULL DEFAULT 'GLOBAL',
    pattern varchar(100) NOT NULL,
    sequence_length integer NOT NULL,
    reset_period varchar(16) NOT NULL DEFAULT 'YEAR',
    enabled boolean NOT NULL DEFAULT true,
    description varchar(500),
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_business_number_rule_scope UNIQUE (business_type, organization_id),
    CONSTRAINT ck_business_number_rule_type CHECK (business_type IN ('EMPLOYEE', 'STUDENT')),
    CONSTRAINT ck_business_number_rule_reset CHECK (reset_period IN ('YEAR', 'NEVER')),
    CONSTRAINT ck_business_number_rule_length CHECK (sequence_length BETWEEN 3 AND 12)
);

CREATE TABLE IF NOT EXISTS t_business_number_sequence (
    rule_id varchar(64) NOT NULL REFERENCES t_business_number_rule(id),
    scope_key varchar(32) NOT NULL,
    current_value bigint NOT NULL,
    last_update_time timestamp NOT NULL,
    PRIMARY KEY (rule_id, scope_key)
);

INSERT INTO t_business_number_rule (
    id, create_by, create_time, business_type, organization_id, pattern,
    sequence_length, reset_period, enabled, description, row_version
) VALUES
    ('72000000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP,
     'EMPLOYEE', 'GLOBAL', '{YEAR}{SEQ}', 5, 'YEAR', true, '默认工号：入职年份加5位流水号', 0),
    ('72000000-0000-4000-8000-000000000002', 'SYSTEM', CURRENT_TIMESTAMP,
     'STUDENT', 'GLOBAL', '{YEAR}{LEVEL}{SEQ}', 6, 'YEAR', true, '默认学号：入学年份加培养层次加6位流水号', 0)
ON CONFLICT (business_type, organization_id) DO NOTHING;

ALTER TABLE edu_student_profile
    ADD COLUMN IF NOT EXISTS candidate_no varchar(64),
    ADD COLUMN IF NOT EXISTS admission_no varchar(64),
    ADD COLUMN IF NOT EXISTS national_student_no varchar(64),
    ADD COLUMN IF NOT EXISTS registration_status varchar(24) NOT NULL DEFAULT 'NOT_SUBMITTED';

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_student_national_no
    ON edu_student_profile (national_student_no)
    WHERE national_student_no IS NOT NULL AND national_student_no <> '';

CREATE TABLE IF NOT EXISTS edu_admission_candidate (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    source_batch_no varchar(64) NOT NULL,
    source_type varchar(24) NOT NULL DEFAULT 'PROVINCIAL_IMPORT',
    candidate_no varchar(64) NOT NULL,
    admission_no varchar(64),
    student_name varchar(128) NOT NULL,
    gender varchar(16),
    phone varchar(32),
    admission_year integer NOT NULL,
    education_level varchar(32) NOT NULL,
    admitted_major_id varchar(64),
    planned_class_id varchar(64),
    admission_status varchar(24) NOT NULL DEFAULT 'ADMITTED',
    reviewed_by varchar(128),
    reviewed_at timestamp,
    review_remark varchar(500),
    checked_in_at timestamp,
    student_id varchar(64),
    registration_status varchar(24) NOT NULL DEFAULT 'NOT_SUBMITTED',
    national_student_no varchar(64),
    registered_at timestamp,
    registration_error varchar(500),
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_edu_admission_candidate UNIQUE (admission_year, candidate_no),
    CONSTRAINT uk_edu_admission_student UNIQUE (student_id),
    CONSTRAINT ck_edu_admission_status CHECK (admission_status IN ('ADMITTED', 'VERIFIED', 'REJECTED', 'ENROLLED')),
    CONSTRAINT ck_edu_registration_status CHECK (registration_status IN ('NOT_SUBMITTED', 'PENDING', 'REGISTERED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_edu_admission_status
    ON edu_admission_candidate (admission_year DESC, admission_status, create_time DESC);

-- 教师教务编号统一回填为 IAM 工号；此后由服务端在保存时强制保持一致。
UPDATE edu_teacher_profile teacher
SET teacher_no = employee.employee_code,
    last_update_time = CURRENT_TIMESTAMP,
    last_update_by = 'SYSTEM'
FROM t_iam_employee employee
WHERE employee.id = teacher.employee_id
  AND teacher.teacher_no <> employee.employee_code;

INSERT INTO t_menu (id, create_by, create_time, last_update_by, last_update_time, menu_name, order_num, parent_id, path)
SELECT '72000000-0000-4000-8000-000000000010', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       '新生入学', GREATEST(student_menu.order_num - 1, 0), student_menu.parent_id, '/admin/education/admissions'
FROM t_menu student_menu
WHERE student_menu.path = '/admin/education/students'
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE path = '/admin/education/admissions')
LIMIT 1;

INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time, permission_code, permission_name,
    permission_type, action_type, built_in, status, resource_type, scope_type, menu_id
)
SELECT source.id, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       source.code, source.name, 'MENU_ACTION', source.action, true, 1, 'MENU', 'ROLE', menu.id
FROM (VALUES
    ('72000000-0000-4000-8000-000000000011', 'education:admission:view', '查看新生入学', 'VIEW'),
    ('72000000-0000-4000-8000-000000000012', 'education:admission:import', '导入录取数据', 'IMPORT'),
    ('72000000-0000-4000-8000-000000000013', 'education:admission:review', '新生资格复查', 'APPROVE'),
    ('72000000-0000-4000-8000-000000000014', 'education:admission:enroll', '新生报到建档', 'CREATE'),
    ('72000000-0000-4000-8000-000000000015', 'education:admission:register', '登记学籍注册结果', 'UPDATE'),
    ('72000000-0000-4000-8000-000000000016', 'education:admission:manage', '管理新生入学', 'MANAGE')
) source(id, code, name, action)
JOIN t_menu menu ON menu.path = '/admin/education/admissions'
WHERE NOT EXISTS (SELECT 1 FROM t_permission permission WHERE permission.permission_code = source.code);

INSERT INTO t_role_menu (role_id, menu_id)
SELECT relation.role_id, admission_menu.id
FROM t_role_menu relation
JOIN t_menu student_menu ON student_menu.id = relation.menu_id
JOIN t_menu admission_menu ON admission_menu.path = '/admin/education/admissions'
WHERE student_menu.path = '/admin/education/students'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.permission_code LIKE 'education:admission:%'
WHERE role.role_code IN ('ROLE_PLATFORM_ADMIN', 'SUPER_ADMIN', 'EDU_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission (role_id, menu_id, permission_id)
SELECT role.id, menu.id, permission.id
FROM t_role role
JOIN t_menu menu ON menu.path = '/admin/education/admissions'
JOIN t_permission permission ON permission.permission_code LIKE 'education:admission:%'
WHERE role.role_code IN ('ROLE_PLATFORM_ADMIN', 'SUPER_ADMIN', 'EDU_ADMIN')
ON CONFLICT DO NOTHING;

COMMIT;

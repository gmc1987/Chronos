-- 高职生产版教务主链：培养方案、学期注册、选退课与毕业审核。
-- 正式发布和审核记录使用不可变快照，避免后续课程或成绩变更篡改历史结论。

CREATE TABLE IF NOT EXISTS edu_academic_program (
    id varchar(64) PRIMARY KEY,
    school_id varchar(64) NOT NULL,
    major_id varchar(64) NOT NULL,
    cohort_year integer NOT NULL,
    education_level varchar(24) NOT NULL,
    program_code varchar(64) NOT NULL,
    program_name varchar(200) NOT NULL,
    version_no integer NOT NULL DEFAULT 1,
    minimum_credits numeric(8,2) NOT NULL DEFAULT 0,
    total_hours integer NOT NULL DEFAULT 0,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    effective_start_date date,
    effective_end_date date,
    submitted_by varchar(128),
    submitted_at timestamp,
    published_by varchar(128),
    published_at timestamp,
    snapshot_json text,
    snapshot_hash varchar(64),
    row_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_academic_program_version UNIQUE (school_id, program_code, version_no),
    CONSTRAINT ck_edu_academic_program_status CHECK (status IN ('DRAFT','SUBMITTED','PUBLISHED','ARCHIVED')),
    CONSTRAINT ck_edu_academic_program_values CHECK (cohort_year >= 2000 AND version_no > 0 AND minimum_credits >= 0 AND total_hours >= 0)
);

CREATE INDEX IF NOT EXISTS idx_edu_academic_program_scope
    ON edu_academic_program(school_id, major_id, cohort_year, status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_academic_program_published
    ON edu_academic_program(school_id, major_id, cohort_year, education_level)
    WHERE status = 'PUBLISHED';

CREATE TABLE IF NOT EXISTS edu_academic_program_course (
    id varchar(64) PRIMARY KEY,
    program_id varchar(64) NOT NULL REFERENCES edu_academic_program(id),
    course_id varchar(64) NOT NULL,
    suggested_term_no integer NOT NULL,
    course_nature varchar(24) NOT NULL,
    credits numeric(8,2) NOT NULL,
    total_hours integer NOT NULL,
    theory_hours integer NOT NULL DEFAULT 0,
    practice_hours integer NOT NULL DEFAULT 0,
    prerequisite_course_ids_json text NOT NULL DEFAULT '[]',
    course_group_code varchar(64),
    sort_order integer NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_academic_program_course UNIQUE (program_id, course_id),
    CONSTRAINT ck_edu_academic_program_course_nature CHECK (course_nature IN ('REQUIRED','ELECTIVE','LIMITED_ELECTIVE')),
    CONSTRAINT ck_edu_academic_program_course_values CHECK (
        suggested_term_no > 0 AND credits >= 0 AND total_hours >= 0
        AND theory_hours >= 0 AND practice_hours >= 0
        AND theory_hours + practice_hours <= total_hours
    )
);

CREATE TABLE IF NOT EXISTS edu_term_registration (
    id varchar(64) PRIMARY KEY,
    school_id varchar(64) NOT NULL,
    term_code varchar(32) NOT NULL,
    student_id varchar(64) NOT NULL,
    program_id varchar(64) NOT NULL REFERENCES edu_academic_program(id),
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    eligibility_snapshot_json text NOT NULL,
    submitted_at timestamp,
    confirmed_by varchar(128),
    confirmed_at timestamp,
    rejected_reason varchar(1000),
    cancelled_reason varchar(1000),
    row_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_term_registration UNIQUE (school_id, term_code, student_id),
    CONSTRAINT ck_edu_term_registration_status CHECK (status IN ('DRAFT','SUBMITTED','REGISTERED','REJECTED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_edu_term_registration_scope
    ON edu_term_registration(school_id, term_code, status);

CREATE TABLE IF NOT EXISTS edu_course_enrollment (
    id varchar(64) PRIMARY KEY,
    school_id varchar(64) NOT NULL,
    term_code varchar(32) NOT NULL,
    student_id varchar(64) NOT NULL,
    offering_id varchar(64) NOT NULL,
    program_course_id varchar(64) REFERENCES edu_academic_program_course(id),
    enrollment_source varchar(24) NOT NULL DEFAULT 'MANUAL',
    status varchar(24) NOT NULL DEFAULT 'ENROLLED',
    enrolled_at timestamp NOT NULL,
    withdrawn_at timestamp,
    withdrawn_reason varchar(1000),
    row_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_course_enrollment UNIQUE (term_code, student_id, offering_id),
    CONSTRAINT ck_edu_course_enrollment_status CHECK (status IN ('ENROLLED','WITHDRAWN','REJECTED')),
    CONSTRAINT ck_edu_course_enrollment_source CHECK (enrollment_source IN ('MANUAL','BATCH','AGENT','IMPORT'))
);

CREATE INDEX IF NOT EXISTS idx_edu_course_enrollment_student
    ON edu_course_enrollment(school_id, term_code, student_id, status);

CREATE TABLE IF NOT EXISTS edu_graduation_audit (
    id varchar(64) PRIMARY KEY,
    school_id varchar(64) NOT NULL,
    student_id varchar(64) NOT NULL,
    program_id varchar(64) NOT NULL REFERENCES edu_academic_program(id),
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    required_credits numeric(8,2) NOT NULL,
    earned_credits numeric(8,2) NOT NULL,
    unmet_requirements_json text NOT NULL DEFAULT '[]',
    evidence_snapshot_json text NOT NULL,
    snapshot_hash varchar(64) NOT NULL,
    reviewed_by varchar(128),
    reviewed_at timestamp,
    approved_by varchar(128),
    approved_at timestamp,
    row_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_edu_graduation_audit_status CHECK (status IN ('DRAFT','READY','NOT_READY','APPROVED','REJECTED')),
    CONSTRAINT ck_edu_graduation_audit_credits CHECK (required_credits >= 0 AND earned_credits >= 0)
);

CREATE INDEX IF NOT EXISTS idx_edu_graduation_audit_scope
    ON edu_graduation_audit(school_id, program_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_graduation_audit_active
    ON edu_graduation_audit(student_id, program_id)
    WHERE status IN ('DRAFT','READY','NOT_READY');

INSERT INTO t_menu (id, create_by, create_time, last_update_by, last_update_time,
                    menu_name, order_num, parent_id, path)
SELECT '76000000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       '培养与学籍运行', 11,
       (SELECT parent_id FROM t_menu WHERE path = '/admin/education/courses' LIMIT 1),
       '/admin/education/academic-lifecycle'
WHERE NOT EXISTS (
    SELECT 1 FROM t_menu existing WHERE existing.path = '/admin/education/academic-lifecycle'
);

INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time,
    permission_code, permission_name, permission_type, action_type,
    built_in, status, resource_type, scope_type, menu_id)
SELECT source.id, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       source.code, source.name, 'MENU_ACTION', source.action,
       true, 1, 'MENU', 'ROLE', menu.id
FROM (VALUES
    ('76000000-0000-4000-8000-000000000011','education:academic-program:view','查看培养方案','VIEW'),
    ('76000000-0000-4000-8000-000000000012','education:academic-program:manage','维护培养方案','MANAGE'),
    ('76000000-0000-4000-8000-000000000013','education:academic-program:review','审核发布培养方案','APPROVE'),
    ('76000000-0000-4000-8000-000000000014','education:term-registration:view','查看学期注册','VIEW'),
    ('76000000-0000-4000-8000-000000000015','education:term-registration:manage','办理学期注册','MANAGE'),
    ('76000000-0000-4000-8000-000000000016','education:course-enrollment:view','查看选退课','VIEW'),
    ('76000000-0000-4000-8000-000000000017','education:course-enrollment:manage','办理选退课','MANAGE'),
    ('76000000-0000-4000-8000-000000000018','education:graduation-audit:view','查看毕业审核','VIEW'),
    ('76000000-0000-4000-8000-000000000019','education:graduation-audit:manage','执行毕业审核','MANAGE'),
    ('76000000-0000-4000-8000-000000000020','education:graduation-audit:approve','批准毕业审核','APPROVE')
) source(id, code, name, action)
JOIN t_menu menu ON menu.path = '/admin/education/academic-lifecycle'
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission existing WHERE existing.permission_code = source.code
);

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN')
  AND permission.permission_code IN (
      'education:academic-program:view','education:academic-program:manage','education:academic-program:review',
      'education:term-registration:view','education:term-registration:manage',
      'education:course-enrollment:view','education:course-enrollment:manage',
      'education:graduation-audit:view','education:graduation-audit:manage','education:graduation-audit:approve')
ON CONFLICT DO NOTHING;

-- 复用现有教务审核角色，明确赋予只读和审核权限，不授予方案维护或毕业核验权限。
INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
  AND permission.permission_code IN (
      'education:academic-program:view','education:academic-program:review',
      'education:term-registration:view','education:term-registration:manage',
      'education:course-enrollment:view',
      'education:graduation-audit:view','education:graduation-audit:approve')
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role CROSS JOIN t_menu menu
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN')
  AND menu.path = '/admin/education/academic-lifecycle'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role CROSS JOIN t_menu menu
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
  AND menu.path = '/admin/education/academic-lifecycle'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission(role_id, menu_id, permission_id)
SELECT role.id, menu.id, permission.id
FROM t_role role CROSS JOIN t_menu menu CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN')
  AND menu.path = '/admin/education/academic-lifecycle'
  AND permission.permission_code LIKE 'education:%'
  AND permission.permission_code IN (
      'education:academic-program:view','education:academic-program:manage','education:academic-program:review',
      'education:term-registration:view','education:term-registration:manage',
      'education:course-enrollment:view','education:course-enrollment:manage',
      'education:graduation-audit:view','education:graduation-audit:manage','education:graduation-audit:approve')
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission(role_id, menu_id, permission_id)
SELECT role.id, menu.id, permission.id
FROM t_role role CROSS JOIN t_menu menu CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
  AND menu.path = '/admin/education/academic-lifecycle'
  AND permission.permission_code IN (
      'education:academic-program:view','education:academic-program:review',
      'education:term-registration:view','education:term-registration:manage',
      'education:course-enrollment:view',
      'education:graduation-audit:view','education:graduation-audit:approve')
ON CONFLICT DO NOTHING;

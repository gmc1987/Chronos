ALTER TABLE edu_grade ADD COLUMN organization_id varchar(64);
CREATE INDEX idx_edu_grade_organization ON edu_grade(organization_id);

CREATE TABLE edu_term_course_plan (
    id varchar(64) PRIMARY KEY,
    academic_term_id varchar(64) NOT NULL REFERENCES edu_academic_term(id),
    administrative_class_id varchar(64) NOT NULL REFERENCES edu_administrative_class(id),
    program_id varchar(64) NOT NULL REFERENCES edu_academic_program(id),
    course_id varchar(64) NOT NULL REFERENCES edu_course_catalog(id),
    weekly_lessons integer NOT NULL CHECK (weekly_lessons > 0),
    CONSTRAINT uk_edu_term_course_plan UNIQUE (academic_term_id, administrative_class_id, course_id)
);

ALTER TABLE edu_course_offering ADD COLUMN term_course_plan_id varchar(64)
    UNIQUE REFERENCES edu_term_course_plan(id);

INSERT INTO t_menu (id, create_by, create_time, menu_name, order_num, parent_id, path)
SELECT '76200000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP,
       '教学运行', 6, parent_id, ''
FROM t_menu WHERE path = '/admin/education/scheduling'
ORDER BY id LIMIT 1;

UPDATE t_menu
SET parent_id = '76200000-0000-4000-8000-000000000001',
    menu_name = CASE WHEN path = '/admin/education/academic-lifecycle' THEN '培养方案' ELSE menu_name END,
    order_num = CASE WHEN path = '/admin/education/academic-lifecycle' THEN 1 ELSE 3 END
WHERE path IN ('/admin/education/academic-lifecycle', '/admin/education/scheduling');

INSERT INTO t_menu (id, create_by, create_time, menu_name, order_num, parent_id, path)
VALUES ('76200000-0000-4000-8000-000000000002', 'SYSTEM', CURRENT_TIMESTAMP,
        '学期开课计划', 2, '76200000-0000-4000-8000-000000000001', '/admin/education/term-course-plans');

INSERT INTO t_menu (id, create_by, create_time, menu_name, order_num, parent_id, path)
SELECT entry.id, 'SYSTEM', CURRENT_TIMESTAMP, entry.name, entry.position, student_menu.parent_id, entry.path
FROM (VALUES
    ('76200000-0000-4000-8000-000000000003', '学期注册', 20, '/admin/education/term-registrations'),
    ('76200000-0000-4000-8000-000000000004', '选退课', 21, '/admin/education/course-enrollments'),
    ('76200000-0000-4000-8000-000000000005', '毕业审核', 22, '/admin/education/graduation-audits')
) entry(id, name, position, path)
CROSS JOIN (SELECT parent_id FROM t_menu WHERE path = '/admin/education/students' ORDER BY id LIMIT 1) student_menu;

-- 把原菜单上的操作权限迁移到新入口，保留权限编码及原角色授权。
CREATE TEMP TABLE tmp_academic_menu_grant ON COMMIT DROP AS
SELECT grant_row.role_id, permission.id AS permission_id, target.id AS menu_id
FROM t_role_menu_permission grant_row
JOIN t_permission permission ON permission.id = grant_row.permission_id
JOIN t_menu source ON source.id = grant_row.menu_id AND source.path = '/admin/education/academic-lifecycle'
JOIN t_menu target ON target.path = CASE split_part(permission.permission_code, ':', 2)
    WHEN 'term-registration' THEN '/admin/education/term-registrations'
    WHEN 'course-enrollment' THEN '/admin/education/course-enrollments'
    WHEN 'graduation-audit' THEN '/admin/education/graduation-audits' END;

UPDATE t_permission permission SET menu_id = target.id
FROM t_menu target
WHERE target.path = CASE split_part(permission.permission_code, ':', 2)
    WHEN 'term-registration' THEN '/admin/education/term-registrations'
    WHEN 'course-enrollment' THEN '/admin/education/course-enrollments'
    WHEN 'graduation-audit' THEN '/admin/education/graduation-audits' END
  AND permission.permission_code LIKE 'education:%';

INSERT INTO t_role_menu(role_id, menu_id)
SELECT DISTINCT role_id, menu_id FROM tmp_academic_menu_grant
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission(role_id, menu_id, permission_id)
SELECT role_id, menu_id, permission_id FROM tmp_academic_menu_grant
ON CONFLICT DO NOTHING;

DELETE FROM t_role_menu_permission old
USING tmp_academic_menu_grant moved, t_menu source
WHERE old.role_id = moved.role_id AND old.permission_id = moved.permission_id
  AND old.menu_id = source.id AND source.path = '/admin/education/academic-lifecycle';

INSERT INTO t_permission (
    id, create_by, create_time, permission_code, permission_name, permission_type,
    action_type, built_in, status, resource_type, scope_type, menu_id)
VALUES
    ('76200000-0000-4000-8000-000000000011', 'SYSTEM', CURRENT_TIMESTAMP, 'education:term-course-plan:view',
        '查看学期开课计划', 'MENU_ACTION', 'VIEW', true, 1, 'MENU', 'ROLE', '76200000-0000-4000-8000-000000000002'),
    ('76200000-0000-4000-8000-000000000012', 'SYSTEM', CURRENT_TIMESTAMP, 'education:term-course-plan:manage',
        '维护开课计划并生成教学任务', 'MENU_ACTION', 'MANAGE', true, 1, 'MENU', 'ROLE', '76200000-0000-4000-8000-000000000002');

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN', 'ROLE_PLATFORM_ADMIN', 'EDU_ADMIN')
  AND permission.permission_code LIKE 'education:term-course-plan:%'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT DISTINCT role_id, '76200000-0000-4000-8000-000000000002'
FROM t_role_permission grant_row JOIN t_permission permission ON permission.id = grant_row.permission_id
WHERE permission.permission_code LIKE 'education:term-course-plan:%'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission(role_id, menu_id, permission_id)
SELECT grant_row.role_id, permission.menu_id, permission.id
FROM t_role_permission grant_row JOIN t_permission permission ON permission.id = grant_row.permission_id
WHERE permission.permission_code LIKE 'education:term-course-plan:%'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT DISTINCT grant_row.role_id, menu.parent_id
FROM t_role_menu grant_row JOIN t_menu menu ON menu.id = grant_row.menu_id
WHERE menu.path IN ('/admin/education/academic-lifecycle', '/admin/education/scheduling',
    '/admin/education/term-course-plans', '/admin/education/term-registrations',
    '/admin/education/course-enrollments', '/admin/education/graduation-audits')
  AND NULLIF(menu.parent_id, '') IS NOT NULL
ON CONFLICT DO NOTHING;

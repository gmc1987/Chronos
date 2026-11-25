INSERT INTO t_permission (
    id, create_by, create_time, permission_code, permission_name, permission_type,
    action_type, built_in, status, resource_type, scope_type, menu_id
)
SELECT '42420000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP,
       'education:term-course-plan:setup', '初始化教学基础资料及指定任课教师',
       'MENU_ACTION', 'MANAGE', true, 1, 'MENU', 'ROLE', menu.id
FROM t_menu menu
WHERE menu.path = '/admin/education/term-course-plans'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN', 'ROLE_PLATFORM_ADMIN', 'EDU_ADMIN')
  AND permission.permission_code = 'education:term-course-plan:setup'
ON CONFLICT DO NOTHING;

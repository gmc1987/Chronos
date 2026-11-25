INSERT INTO t_menu (id, create_by, create_time, menu_name, order_num, path)
SELECT gen_random_uuid()::text, 'SYSTEM', now(), entry.name, entry.sort_order, entry.path
FROM (VALUES
    ('督导中心', 45, '/admin/education/supervision'),
    ('我的督导任务', 46, '/portal/education/supervision'),
    ('授课签到与整改', 47, '/portal/education/supervision/teaching')
) AS entry(name, sort_order, path)
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE t_menu.path = entry.path);

INSERT INTO t_permission (
    id, create_by, create_time, built_in, permission_code, permission_name,
    permission_type, resource_type, action_type, scope_type, menu_id, status
)
SELECT gen_random_uuid()::text, 'SYSTEM', now(), true, entry.code, entry.name,
    'MENU_ACTION', 'MENU', entry.action, 'ROLE',
    (SELECT id FROM t_menu WHERE path = entry.path ORDER BY id LIMIT 1), 1
FROM (VALUES
    ('education:supervision:plan:view', '查看督导计划', 'VIEW', '/admin/education/supervision'),
    ('education:supervision:plan:create', '新建督导计划', 'CREATE', '/admin/education/supervision'),
    ('education:supervision:plan:publish', '发布督导计划', 'PUBLISH', '/admin/education/supervision'),
    ('education:supervision:plan:update', '更新督导计划状态', 'UPDATE', '/admin/education/supervision'),
    ('education:supervision:assignment:create', '分派督导任务', 'CREATE', '/admin/education/supervision'),
    ('education:supervision:issue:create', '登记督导问题', 'CREATE', '/admin/education/supervision'),
    ('education:supervision:review:create', '复查督导整改', 'CREATE', '/admin/education/supervision'),
    ('education:supervision:assignment:view', '查看本人督导任务', 'VIEW', '/portal/education/supervision'),
    ('education:supervision:assignment:accept', '接受本人督导任务', 'UPDATE', '/portal/education/supervision'),
    ('education:supervision:assignment:check-in', '现场督导签到', 'UPDATE', '/portal/education/supervision'),
    ('education:supervision:assignment:update', '完成本人督导任务', 'UPDATE', '/portal/education/supervision'),
    ('education:supervision:record:create', '填写督导评价', 'CREATE', '/portal/education/supervision'),
    ('education:supervision:record:view', '查看本人督导评价', 'VIEW', '/portal/education/supervision'),
    ('education:supervision:rectification:create', '提交本人督导整改', 'CREATE', '/portal/education/supervision/teaching')
) AS entry(code, name, action, path)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.permission_code LIKE 'education:supervision:%'
WHERE (role.role_code = 'EDU_SUPERVISION_MANAGER' AND permission.permission_code IN (
    'education:supervision:plan:view', 'education:supervision:plan:create',
    'education:supervision:plan:publish', 'education:supervision:plan:update',
    'education:supervision:assignment:create', 'education:supervision:issue:create', 'education:supervision:review:create'
))
OR (role.role_code = 'EDU_SUPERVISOR' AND permission.permission_code IN (
    'education:supervision:assignment:view', 'education:supervision:assignment:accept',
    'education:supervision:assignment:check-in', 'education:supervision:assignment:update',
    'education:supervision:record:create', 'education:supervision:record:view'
))
OR (role.role_code = 'EDU_TEACHER' AND permission.permission_code = 'education:supervision:rectification:create')
ON CONFLICT DO NOTHING;

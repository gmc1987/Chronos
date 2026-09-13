-- 教学中心菜单、页面路由和原子权限初始化数据。
-- 目标数据库：ChronosEducation。脚本可重复执行。

BEGIN;

CREATE TEMP TABLE tmp_teaching_center_menu (
    menu_name varchar(200) NOT NULL,
    path varchar(500) NOT NULL,
    order_num integer NOT NULL,
    permission_prefix varchar(200) NOT NULL,
    permission_label varchar(200) NOT NULL,
    permission_profile varchar(32) NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_teaching_center_menu
    (menu_name, path, order_num, permission_prefix, permission_label, permission_profile)
VALUES
    ('教学中心', '/admin/education/teaching-center', 14, 'education:teaching', '教学中心', 'ROOT'),
    ('教学计划', '/admin/education/teaching-center', 1, 'education:teaching:plan', '教学计划', 'CRUD'),
    ('教案管理', '/admin/education/teaching-center', 2, 'education:teaching:lesson-plan', '教案', 'CRUD'),
    ('备课管理', '/admin/education/teaching-center', 3, 'education:teaching:preparation', '备课', 'CRUD'),
    ('课件管理', '/admin/education/teaching-center', 4, 'education:teaching:courseware', '课件', 'CRUD'),
    ('教学材料', '/admin/education/teaching-center', 5, 'education:teaching:material', '教学材料', 'CRUD'),
    ('作业管理', '/admin/education/teaching-center', 6, 'education:teaching:homework', '作业入口', 'CRUD'),
    ('题库维护', '/admin/education/teaching-center', 7, 'education:teaching:question-bank', '题库', 'CRUD_IMPORT'),
    ('知识点维护', '/admin/education/teaching-center', 8, 'education:teaching:knowledge-point', '知识点', 'CRUD_IMPORT'),
    ('错题维护', '/admin/education/teaching-center', 9, 'education:teaching:error-book', '错题', 'CRUD'),
    ('教研管理', '/admin/education/teaching-center', 10, 'education:teaching:research', '教研', 'CRUD');

-- 根菜单不存在时创建；已有菜单只补齐统一门户路由。
INSERT INTO t_menu (
    id, menu_name, path, parent_id, order_num,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, '教学中心', path, NULL, order_num,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM tmp_teaching_center_menu
WHERE menu_name = '教学中心'
  AND NOT EXISTS (
      SELECT 1 FROM t_menu WHERE menu_name = '教学中心' AND parent_id IS NULL
  );

UPDATE t_menu menu
SET path = desired.path,
    order_num = desired.order_num,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM tmp_teaching_center_menu desired
WHERE menu.menu_name = desired.menu_name
  AND (
      (desired.menu_name = '教学中心' AND menu.parent_id IS NULL)
      OR (desired.menu_name <> '教学中心'
          AND menu.parent_id = (
              SELECT root.id FROM t_menu root
              WHERE root.menu_name = '教学中心' AND root.parent_id IS NULL
          ))
  );

-- 根菜单下缺少的功能菜单统一创建。
INSERT INTO t_menu (
    id, menu_name, path, parent_id, order_num,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, desired.menu_name, desired.path, root.id, desired.order_num,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM tmp_teaching_center_menu desired
JOIN t_menu root
  ON root.menu_name = '教学中心'
 AND root.parent_id IS NULL
WHERE desired.menu_name <> '教学中心'
  AND NOT EXISTS (
      SELECT 1 FROM t_menu existing
      WHERE existing.menu_name = desired.menu_name
        AND existing.parent_id = root.id
  );

CREATE TEMP TABLE tmp_teaching_center_permissions (
    permission_code varchar(200) NOT NULL,
    permission_name varchar(200) NOT NULL,
    action_type varchar(32) NOT NULL,
    menu_name varchar(200) NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_teaching_center_permissions
    (permission_code, permission_name, action_type, menu_name)
SELECT
    CASE WHEN menu.permission_prefix = 'education:teaching'
         THEN menu.permission_prefix || ':' || action.action_code
         ELSE menu.permission_prefix || ':' || action.action_code END,
    action.action_name || menu.permission_label,
    action.action_type,
    menu.menu_name
FROM tmp_teaching_center_menu menu
CROSS JOIN (
    VALUES
        ('VIEW', 'view', '查看'),
        ('CREATE', 'create', '新增'),
        ('UPDATE', 'update', '修改'),
        ('DELETE', 'delete', '删除'),
        ('MANAGE', 'manage', '管理'),
        ('IMPORT', 'import', '导入'),
        ('EXPORT', 'export', '导出')
) AS action(action_type, action_code, action_name)
WHERE menu.permission_profile = 'ROOT'
   OR (menu.permission_profile = 'CRUD'
       AND action.action_type IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE', 'MANAGE'))
   OR (menu.permission_profile = 'CRUD_IMPORT'
       AND action.action_type IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE', 'MANAGE', 'IMPORT', 'EXPORT'));

INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time,
    permission_name, permission_code, permission_type, menu_id,
    action_type, resource_type, scope_type, status, built_in, description
)
SELECT
    gen_random_uuid()::text,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
    desired.permission_name, desired.permission_code, 'MENU_ACTION',
    menu.id, desired.action_type, 'MENU', 'ROLE', 1, true,
    '教学中心菜单和页面路由原子权限'
FROM tmp_teaching_center_permissions desired
JOIN t_menu menu
  ON menu.menu_name = desired.menu_name
 AND (
      (desired.menu_name = '教学中心' AND menu.parent_id IS NULL)
      OR (desired.menu_name <> '教学中心'
          AND menu.parent_id = (
              SELECT root.id FROM t_menu root
              WHERE root.menu_name = '教学中心' AND root.parent_id IS NULL
          ))
 )
ON CONFLICT (permission_code) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    permission_type = EXCLUDED.permission_type,
    menu_id = EXCLUDED.menu_id,
    action_type = EXCLUDED.action_type,
    resource_type = EXCLUDED.resource_type,
    scope_type = EXCLUDED.scope_type,
    status = 1,
    built_in = true,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 门户应用入口使用统一页面；正式作业发布、提交和评分仍由作业中心负责。
INSERT INTO t_portal_application (
    id, create_by, create_time, last_update_by, last_update_time,
    app_code, app_name, description, icon, route_path, open_mode,
    sort_order, enabled, recommended, required_permission
)
SELECT
    gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
    'teaching-center', '教学中心', '教学计划、教案、备课、课件、教学材料、题库、知识点、错题和教研',
    'Reading', '/portal/education/teaching-center', 'INTERNAL',
    70, true, true, 'education:teaching:view'
WHERE NOT EXISTS (
    SELECT 1 FROM t_portal_application WHERE app_code = 'teaching-center'
);

-- 系统管理员默认获得入口和全部教学中心权限；其他角色通过授权管理分配。
INSERT INTO t_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role
CROSS JOIN t_menu menu
WHERE role.role_code = 'SUPER_ADMIN'
  AND (
      (menu.menu_name = '教学中心' AND menu.parent_id IS NULL)
      OR menu.parent_id = (
          SELECT root.id FROM t_menu root
          WHERE root.menu_name = '教学中心' AND root.parent_id IS NULL
      )
  )
  AND NOT EXISTS (
      SELECT 1 FROM t_role_menu existing
      WHERE existing.role_id = role.id AND existing.menu_id = menu.id
  );

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'SUPER_ADMIN'
  AND permission.permission_code IN (
      SELECT permission_code FROM tmp_teaching_center_permissions
  )
  AND NOT EXISTS (
      SELECT 1 FROM t_role_permission existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

COMMIT;

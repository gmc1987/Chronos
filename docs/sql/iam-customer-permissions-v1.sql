-- 客户账号管理的独立原子权限。
-- 客户账号与后台用户是不同资源，不能复用 iam:user:* 造成授权边界混淆。

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('iam-customer-permissions-v1'));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM t_menu
        WHERE menu_name = '用户管理'
    ) THEN
        RAISE EXCEPTION '缺少用户管理菜单，无法绑定客户管理权限';
    END IF;
END
$$;

WITH user_menu AS (
    SELECT id
    FROM t_menu
    WHERE menu_name = '用户管理'
    ORDER BY create_time
    LIMIT 1
), desired AS (
    SELECT *
    FROM (VALUES
        ('iam:customer:view', '查看客户账号', 'QUERY'),
        ('iam:customer:create', '新增客户账号', 'CREATE'),
        ('iam:customer:update', '修改客户账号', 'UPDATE'),
        ('iam:customer:delete', '删除客户账号', 'DELETE'),
        ('iam:customer:manage', '管理客户账号', 'MANAGE')
    ) AS value(permission_code, permission_name, action_type)
)
INSERT INTO t_permission (
    id,
    create_by,
    create_time,
    last_update_by,
    last_update_time,
    permission_name,
    permission_code,
    permission_type,
    menu_id,
    action_type,
    resource_type,
    scope_type,
    status,
    built_in,
    description
)
SELECT
    gen_random_uuid()::text,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    desired.permission_name,
    desired.permission_code,
    'MENU_ACTION',
    user_menu.id,
    desired.action_type,
    'CUSTOMER_ACCOUNT',
    'ROLE',
    1,
    true,
    '客户账号管理原子权限'
FROM desired
CROSS JOIN user_menu
WHERE NOT EXISTS (
    SELECT 1
    FROM t_permission existing
    WHERE existing.permission_code = desired.permission_code
);

-- 超级管理员保留显式绑定，普通角色由授权管理页面按职责分配。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'SUPER_ADMIN'
  AND permission.permission_code LIKE 'iam:customer:%'
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_permission existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

COMMIT;

SELECT permission_code,
       permission_name,
       action_type,
       resource_type
FROM t_permission
WHERE permission_code LIKE 'iam:customer:%'
ORDER BY permission_code;

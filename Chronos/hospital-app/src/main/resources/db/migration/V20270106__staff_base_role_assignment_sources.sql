BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM t_role
        WHERE role_code = 'ROLE_PLATFORM_USER'
          AND status = 1
    ) THEN
        RAISE EXCEPTION 'ROLE_PLATFORM_USER role is missing or disabled';
    END IF;
END $$;

-- 岗位自动映射退出运行链路后，将历史授权固化为人工来源，避免升级时丢权。
INSERT INTO t_user_role_assignment_source (
    id, create_by, create_time, user_id, role_id, source_type, source_key)
SELECT gen_random_uuid()::text,
       'SYSTEM',
       CURRENT_TIMESTAMP,
       source.user_id,
       source.role_id,
       'MANUAL',
       'MIGRATED_POSITION_MAPPING:' || source.id
FROM t_user_role_assignment_source source
WHERE source.source_type = 'POSITION_MAPPING'
ON CONFLICT (user_id, role_id, source_type, source_key) DO NOTHING;

DELETE FROM t_user_role_assignment_source
WHERE source_type = 'POSITION_MAPPING';

-- 所有绑定员工档案的教职工账号必须具有最小可登录角色。
INSERT INTO t_user_role (user_id, role_id)
SELECT user_account.id, base_role.id
FROM t_admin_user user_account
JOIN t_iam_employee employee ON employee.id = user_account.employee_id
JOIN t_role base_role
  ON base_role.role_code = 'ROLE_PLATFORM_USER'
 AND base_role.status = 1
WHERE COALESCE(user_account.account_type, 'STAFF') = 'STAFF'
ON CONFLICT DO NOTHING;

INSERT INTO t_user_role_assignment_source (
    id, create_by, create_time, user_id, role_id, source_type, source_key)
SELECT gen_random_uuid()::text,
       'SYSTEM',
       CURRENT_TIMESTAMP,
       user_account.id,
       base_role.id,
       'BASE_ACCOUNT',
       'ROLE_PLATFORM_USER'
FROM t_admin_user user_account
JOIN t_iam_employee employee ON employee.id = user_account.employee_id
JOIN t_role base_role
  ON base_role.role_code = 'ROLE_PLATFORM_USER'
 AND base_role.status = 1
WHERE COALESCE(user_account.account_type, 'STAFF') = 'STAFF'
ON CONFLICT (user_id, role_id, source_type, source_key) DO NOTHING;

DELETE FROM t_role_permission
WHERE permission_id IN (
    SELECT id FROM t_permission WHERE permission_code = 'iam:directory:role-sync'
);

UPDATE t_permission
SET status = 0,
    description = '已停用：岗位不再自动映射业务角色，改由人工授权或智能体授权提案处理',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE permission_code = 'iam:directory:role-sync';

-- 基础角色必须保持最小权限；业务流程和专业功能由人工或智能体业务角色提供。
DELETE FROM t_role_menu_permission
WHERE role_id IN (
    SELECT id FROM t_role WHERE role_code = 'ROLE_PLATFORM_USER'
);

DELETE FROM t_role_menu
WHERE role_id IN (
    SELECT id FROM t_role WHERE role_code = 'ROLE_PLATFORM_USER'
);

DELETE FROM t_role_permission relation
USING t_role role, t_permission permission
WHERE relation.role_id = role.id
  AND relation.permission_id = permission.id
  AND role.role_code = 'ROLE_PLATFORM_USER'
  AND permission.permission_code <> 'message:publication:read';

COMMIT;

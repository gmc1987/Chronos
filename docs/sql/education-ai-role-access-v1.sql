-- 教育 AI 能力的最小生产角色授权。
--
-- 教务审批人负责查看、确认或驳回 Scheduling Agent 建议，并可使用
-- AI 教务分析；普通班主任不获得建议确认权，避免排课约束被越权写入。

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('education-ai-role-access-v1'));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM t_role
        WHERE role_code = 'EDU_ACADEMIC_APPROVER'
          AND status = 1
    ) THEN
        RAISE EXCEPTION '缺少有效角色 EDU_ACADEMIC_APPROVER';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM t_permission
        WHERE permission_code = 'education:ai:agent:use'
          AND status = 1
    ) OR NOT EXISTS (
        SELECT 1
        FROM t_permission
        WHERE permission_code = 'education:ai:agent:confirm'
          AND status = 1
    ) THEN
        RAISE EXCEPTION '教育 Agent 原子权限尚未初始化';
    END IF;
END
$$;

-- 菜单授权包含父菜单，保证导航树能够正确挂载业务入口。
INSERT INTO t_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role
CROSS JOIN t_menu menu
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
  AND menu.menu_name IN ('AI智能中心', '智能体能力配置')
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_menu existing
      WHERE existing.role_id = role.id
        AND existing.menu_id = menu.id
  );

-- use 允许读取自身数据范围内的建议与分析；confirm 才允许落地或驳回建议。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
  AND permission.permission_code IN (
      'education:ai:agent:use',
      'education:ai:agent:confirm'
  )
  AND permission.status = 1
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_permission existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

COMMIT;

SELECT role.role_code,
       permission.permission_code
FROM t_role role
JOIN t_role_permission role_permission ON role_permission.role_id = role.id
JOIN t_permission permission ON permission.id = role_permission.permission_id
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
  AND permission.permission_code LIKE 'education:ai:agent:%'
ORDER BY permission.permission_code;

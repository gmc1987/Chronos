-- 第三阶段：候选任务、认领和个人流程委托权限。
-- PostgreSQL 可重复执行。

BEGIN;

-- 实体模型要求权限编码唯一、角色权限组合唯一。部分早期数据库由 Hibernate 演进而来，
-- 实际缺少对应约束，会导致历史触发器中的 ON CONFLICT 无法匹配冲突目标。
CREATE UNIQUE INDEX IF NOT EXISTS uk_t_permission_permission_code
    ON t_permission(permission_code);

CREATE UNIQUE INDEX IF NOT EXISTS uk_t_role_permission_role_permission
    ON t_role_permission(role_id, permission_id);

ALTER TABLE wf_task
    ALTER COLUMN assignee DROP NOT NULL;

WITH permission_data(code, name) AS (VALUES
    ('workflow:task:claim', '认领流程候选任务'),
    ('workflow:delegation:manage', '管理个人流程委托'))
INSERT INTO t_permission(
    id,
    create_by,
    create_time,
    permission_code,
    permission_name,
    permission_type,
    status,
    built_in)
SELECT
    gen_random_uuid()::text,
    'migration',
    CURRENT_TIMESTAMP,
    code,
    name,
    'WORKFLOW',
    1,
    true
FROM permission_data
WHERE NOT EXISTS (
    SELECT 1
    FROM t_permission existing
    WHERE existing.permission_code = permission_data.code
);

-- 已拥有旧 workflow:use 的角色自动继承第三阶段的普通用户能力。
INSERT INTO t_role_permission(role_id, permission_id)
SELECT DISTINCT legacy.role_id, target.id
FROM t_role_permission legacy
JOIN t_permission source ON source.id = legacy.permission_id
JOIN t_permission target ON target.permission_code IN (
    'workflow:task:claim',
    'workflow:delegation:manage'
)
WHERE source.permission_code = 'workflow:use'
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_permission existing
      WHERE existing.role_id = legacy.role_id
        AND existing.permission_id = target.id
  );

COMMIT;

-- 修复历史环境中考试审批权限已定义、但角色授权关系缺失的问题。
-- 该迁移只补充缺失关系，不覆盖管理员后续在授权界面做出的配置。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN', 'EDU_ACADEMIC_APPROVER')
  AND permission.permission_code IN (
      'education:exam:suspension:approve',
      'education:exam:change:approve'
  )
ON CONFLICT DO NOTHING;

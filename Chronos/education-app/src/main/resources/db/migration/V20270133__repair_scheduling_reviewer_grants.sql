-- 恢复独立排课审核员的最小权限。演示数据重建后角色可能保留，但授权关联被清空。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_SCHEDULING_REVIEWER'
  AND permission.permission_code IN (
      'education:term:view',
      'education:scheduling:view',
      'education:scheduling:review'
  )
ON CONFLICT DO NOTHING;

-- 考试占课审核页面需读取学期、学科和考场选项，审核角色仅获得查看权限。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
  AND permission.permission_code IN (
    'education:term:view',
    'education:subject:view',
    'education:resource:venue:view'
  )
ON CONFLICT DO NOTHING;

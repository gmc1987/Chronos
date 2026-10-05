-- 审核角色在原迁移中晚于授权语句创建，补齐可查看成绩册和处理流程任务的权限。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('EDU_GRADE_REVIEWER', 'EDU_ACADEMIC_APPROVER')
  AND permission.permission_code IN (
    'education:score:gradebook:view',
    'education:score:scheme:view',
    'education:score:review',
    'workflow:task:view',
    'workflow:task:claim',
    'workflow:task:approve',
    'workflow:task:reject'
  )
ON CONFLICT DO NOTHING;

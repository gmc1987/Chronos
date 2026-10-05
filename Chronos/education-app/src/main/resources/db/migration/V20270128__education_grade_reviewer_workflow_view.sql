-- 成绩审核待办、已办和通知共用流程实例查看权限。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('EDU_GRADE_REVIEWER', 'EDU_ACADEMIC_APPROVER')
  AND permission.permission_code = 'workflow:instance:view'
ON CONFLICT DO NOTHING;

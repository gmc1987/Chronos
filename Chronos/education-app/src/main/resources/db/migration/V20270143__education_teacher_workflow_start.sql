-- Teachers need the generic workflow-start gate before flow-level START ACLs
-- can authorize their leave, course-adjustment and classroom applications.
-- WorkflowSecurityService still evaluates each published definition's ACL.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_TEACHER'
  AND permission.permission_code = 'workflow:instance:start'
ON CONFLICT DO NOTHING;

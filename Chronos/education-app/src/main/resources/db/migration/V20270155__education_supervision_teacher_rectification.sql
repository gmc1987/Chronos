-- The observed teacher owns the corrective action, even when they are not a supervisor.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission
  ON permission.permission_code = 'education:supervision:rectification:create'
WHERE role.role_code = 'EDU_TEACHER'
ON CONFLICT DO NOTHING;

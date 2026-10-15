-- An initiator must be able to open the instance created by the start form.
-- The instance API also checks participant/data-scope access per record.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_TEACHER'
  AND permission.permission_code = 'workflow:instance:view'
ON CONFLICT DO NOTHING;

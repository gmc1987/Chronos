-- Some installations create EDU_TEACHER after the homework permission migration.
-- Reconcile the intended teacher grants without changing other roles.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.permission_code = 'education:teaching:view'
    OR permission.permission_code LIKE 'education:homework:%'
WHERE role.role_code = 'EDU_TEACHER'
ON CONFLICT DO NOTHING;

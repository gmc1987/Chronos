-- The academic approver role is used by the education Agent MVP readiness
-- contract, but the original Agent migration only granted these actions to
-- ADMIN and SUPER_ADMIN. Restore the intended ordinary-role grant without
-- broadening the newer full-school AI scheduling Run permissions.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission ON permission.permission_code IN (
    'education:ai:agent:use',
    'education:ai:agent:confirm'
)
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
  AND role.status = 1
  AND permission.status = 1
ON CONFLICT (permission_id, role_id) DO NOTHING;

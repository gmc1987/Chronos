-- Teacher-facing mobile and portal navigation exposes published announcements.
-- Keep the role grant idempotent so upgraded demo and production databases align.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('EDU_TEACHER', 'EDU_CLASS_ADVISOR')
  AND permission.permission_code = 'message:publication:read'
  AND NOT EXISTS (
      SELECT 1
      FROM t_role_permission existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

-- A publisher must be able to find and inspect the approved gradebook it may publish.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_GRADE_PUBLISHER'
  AND permission.permission_code IN (
      'education:score:gradebook:view',
      'education:score:scheme:view'
  )
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission (role_id, menu_id, permission_id)
SELECT role.id, permission.menu_id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_GRADE_PUBLISHER'
  AND permission.menu_id IS NOT NULL
  AND permission.permission_code IN (
      'education:score:gradebook:view',
      'education:score:scheme:view'
  )
ON CONFLICT DO NOTHING;

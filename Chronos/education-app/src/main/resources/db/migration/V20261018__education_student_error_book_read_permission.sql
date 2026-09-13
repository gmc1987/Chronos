-- Students may read only their own error-book data; writes remain teacher-managed.
INSERT INTO t_permission
 (id, create_by, create_time, permission_code, permission_name, permission_type,
  action_type, built_in, status)
VALUES
 (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
  'education:error-book:view', '查看本人错题', 'MENU_ACTION',
  'VIEW', true, 1)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM t_role r
JOIN t_permission p ON p.permission_code = 'education:error-book:view'
WHERE r.role_code = 'ROLE_PLATFORM_USER'
ON CONFLICT DO NOTHING;

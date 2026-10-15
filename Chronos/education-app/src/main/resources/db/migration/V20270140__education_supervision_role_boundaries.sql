-- Separate school supervision management from the assigned supervisor's work.
-- The generic teacher role must not be able to assign or review supervision tasks.
BEGIN;

INSERT INTO t_permission (
    id, create_by, create_time, built_in, permission_code, permission_name,
    permission_type, resource_type, action_type, menu_id, status
)
SELECT gen_random_uuid()::text, 'SYSTEM', now(), true,
       'education:supervision:assignment:update', '完成本人督导任务',
       'MENU_ACTION', NULL, 'UPDATE', NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission
    WHERE permission_code = 'education:supervision:assignment:update'
);

INSERT INTO t_role (
    id, create_by, create_time, built_in, role_code, role_name, status
)
SELECT gen_random_uuid()::text, 'SYSTEM', now(), true,
       role_code, role_name, 1
FROM (VALUES
    ('EDU_SUPERVISION_MANAGER', '教学督导管理员'),
    ('EDU_SUPERVISOR', '教学督导员')
) AS roles(role_code, role_name)
WHERE NOT EXISTS (
    SELECT 1 FROM t_role WHERE t_role.role_code = roles.role_code
);

DELETE FROM t_role_permission grant_row
USING t_role role, t_permission permission
WHERE grant_row.role_id = role.id
  AND grant_row.permission_id = permission.id
  AND role.role_code = 'EDU_TEACHER'
  AND permission.permission_code IN (
      'education:supervision:assignment:create',
      'education:supervision:review:create'
  );

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE (
    role.role_code = 'EDU_SUPERVISION_MANAGER'
    AND permission.permission_code IN (
        'education:supervision:plan:view',
        'education:supervision:plan:create',
        'education:supervision:plan:publish',
        'education:supervision:plan:update',
        'education:supervision:assignment:create',
        'education:supervision:issue:create',
        'education:supervision:review:create'
    )
) OR (
    role.role_code = 'EDU_SUPERVISOR'
    AND permission.permission_code IN (
        'education:supervision:assignment:view',
        'education:supervision:assignment:accept',
        'education:supervision:assignment:check-in',
        'education:supervision:assignment:update',
        'education:supervision:record:create',
        'education:supervision:record:view',
        'education:supervision:rectification:create'
    )
)
ON CONFLICT DO NOTHING;

COMMIT;

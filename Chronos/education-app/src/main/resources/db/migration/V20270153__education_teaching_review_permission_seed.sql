-- The IAM permission catalog may be populated after Flyway migrations on a
-- fresh school. Seed the read permission first so the role binding is not lost.
INSERT INTO t_permission (
	id, create_by, create_time, permission_code, permission_name,
	permission_type, status, built_in
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
	'education:teaching:plan:view', '教学计划查看', 'MENU_ACTION', '1', true
WHERE NOT EXISTS (
	SELECT 1 FROM t_permission
	WHERE permission_code = 'education:teaching:plan:view'
);

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission
	ON permission.permission_code = 'education:teaching:plan:view'
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
	AND NOT EXISTS (
		SELECT 1 FROM t_role_permission existing
		WHERE existing.role_id = role.id
			AND existing.permission_id = permission.id
	);

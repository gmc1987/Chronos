-- Academic approvers need read-only access to the immutable plan snapshot
-- they are asked to review. EducationDataScopeService still enforces school scope.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission
	ON permission.permission_code = 'education:teaching:plan:view'
WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
	AND NOT EXISTS (
		SELECT 1
		FROM t_role_permission existing
		WHERE existing.role_id = role.id
			AND existing.permission_id = permission.id
	);

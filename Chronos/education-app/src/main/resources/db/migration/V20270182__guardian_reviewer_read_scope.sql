-- 家长关系审核员必须能够读取待核验的家长和学生摘要，但不授予新增、编辑或删除权限。
INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission
  ON permission.permission_code IN ('education:home-school:parent:view', 'education:student:view')
WHERE role.role_code = 'HOME_SCHOOL_GUARDIAN_REVIEWER'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission(role_id, menu_id, permission_id)
SELECT role.id, menu.id, permission.id
FROM t_role role
JOIN t_menu menu ON menu.path = '/admin/education/parents'
JOIN t_permission permission ON permission.permission_code = 'education:home-school:parent:view'
WHERE role.role_code = 'HOME_SCHOOL_GUARDIAN_REVIEWER'
ON CONFLICT DO NOTHING;

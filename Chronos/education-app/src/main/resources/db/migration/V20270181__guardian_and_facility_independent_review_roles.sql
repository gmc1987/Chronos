-- 家校关系核验与后勤安全事件结案必须具备独立复核角色，避免仅靠超级管理员完成生产验收。
INSERT INTO t_role(id, role_code, role_name, description, create_by, create_time, built_in, status)
VALUES
  ('81000000-0000-4000-8000-000000000001', 'HOME_SCHOOL_GUARDIAN_REVIEWER', '家长关系审核员',
   '核验监护关系、访问限制与成年学生授权，不包含家长档案维护权限', 'SYSTEM', CURRENT_TIMESTAMP, true, 1),
  ('81000000-0000-4000-8000-000000000002', 'FACILITY_SAFETY_REVIEWER', '后勤安全复核员',
   '查看资产并独立处置后勤安全事件，不包含资产和库存维护权限', 'SYSTEM', CURRENT_TIMESTAMP, true, 1)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO t_permission(
  id, create_by, create_time, last_update_by, last_update_time,
  permission_code, permission_name, permission_type, action_type,
  built_in, status, resource_type, scope_type, menu_id)
SELECT
  '81000000-0000-4000-8000-000000000003', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
  'education:home-school:guardian:review', '审核家长与监护关系', 'MENU_ACTION', 'REVIEW',
  true, 1, 'MENU', 'ROLE', menu.id
FROM t_menu menu
WHERE menu.path = '/admin/education/parents'
  AND NOT EXISTS (
    SELECT 1 FROM t_permission permission
    WHERE permission.permission_code = 'education:home-school:guardian:review'
  );

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission
  ON permission.permission_code = 'education:home-school:guardian:review'
WHERE role.role_code IN ('SUPER_ADMIN', 'ROLE_PLATFORM_ADMIN', 'EDU_ADMIN', 'HOME_SCHOOL_GUARDIAN_REVIEWER')
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role
JOIN t_menu menu ON menu.path = '/admin/education/parents'
WHERE role.role_code = 'HOME_SCHOOL_GUARDIAN_REVIEWER'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission(role_id, menu_id, permission_id)
SELECT role.id, menu.id, permission.id
FROM t_role role
JOIN t_menu menu ON menu.path = '/admin/education/parents'
JOIN t_permission permission ON permission.permission_code = 'education:home-school:guardian:review'
WHERE role.role_code = 'HOME_SCHOOL_GUARDIAN_REVIEWER'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
JOIN t_permission permission
  ON permission.permission_code IN ('facility:asset:view', 'facility:safety:manage')
WHERE role.role_code = 'FACILITY_SAFETY_REVIEWER'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role
JOIN t_menu menu ON menu.path = '/admin/facility/assets'
WHERE role.role_code = 'FACILITY_SAFETY_REVIEWER'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission(role_id, menu_id, permission_id)
SELECT role.id, menu.id, permission.id
FROM t_role role
JOIN t_menu menu ON menu.path = '/admin/facility/assets'
JOIN t_permission permission
  ON permission.permission_code IN ('facility:asset:view', 'facility:safety:manage')
WHERE role.role_code = 'FACILITY_SAFETY_REVIEWER'
ON CONFLICT DO NOTHING;

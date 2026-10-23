INSERT INTO t_menu (
    id, create_by, create_time, last_update_by, last_update_time,
    menu_name, order_num, parent_id, path
)
SELECT
    '78000000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP,
    'SYSTEM', CURRENT_TIMESTAMP, '专业管理', 4, education_center.id,
    '/admin/education/majors'
FROM t_menu education_center
WHERE education_center.menu_name = '教务中心'
  AND NOT EXISTS (
      SELECT 1 FROM t_menu WHERE path = '/admin/education/majors'
  )
LIMIT 1;

-- 专业与行政班共用班级主数据权限，已有班级中心角色自动获得专业入口。
INSERT INTO t_role_menu (role_id, menu_id)
SELECT relation.role_id, major_menu.id
FROM t_role_menu relation
JOIN t_menu class_menu ON class_menu.id = relation.menu_id
JOIN t_menu major_menu ON major_menu.path = '/admin/education/majors'
WHERE class_menu.path = '/admin/education/classes'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission (role_id, menu_id, permission_id)
SELECT relation.role_id, major_menu.id, relation.permission_id
FROM t_role_menu_permission relation
JOIN t_menu class_menu ON class_menu.id = relation.menu_id
JOIN t_menu major_menu ON major_menu.path = '/admin/education/majors'
WHERE class_menu.path = '/admin/education/classes'
ON CONFLICT DO NOTHING;

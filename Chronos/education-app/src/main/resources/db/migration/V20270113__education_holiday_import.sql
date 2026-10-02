ALTER TABLE edu_academic_calendar_day
    ADD COLUMN IF NOT EXISTS source_type varchar(16) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS source_url varchar(500),
    ADD COLUMN IF NOT EXISTS imported_at timestamp;

INSERT INTO t_menu (
    id, create_by, create_time, last_update_by, last_update_time,
    menu_name, order_num, parent_id, path
)
SELECT 'a07d26b9-188f-4d58-8e37-92d92f25bc10', 'SYSTEM', CURRENT_TIMESTAMP,
       'SYSTEM', CURRENT_TIMESTAMP, '节假日维护', 2,
       'd0b26c1f-7c13-46a3-836e-f886bae612f4',
       '/admin/education/holidays'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path = '/admin/education/holidays');

INSERT INTO t_role_menu (role_id, menu_id)
SELECT relation.role_id, holiday.id
FROM t_role_menu relation
JOIN t_menu term_menu ON term_menu.id = relation.menu_id
JOIN t_menu holiday ON holiday.path = '/admin/education/holidays'
WHERE term_menu.path = '/admin/education/terms'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission (role_id, menu_id, permission_id)
SELECT relation.role_id, holiday.id, relation.permission_id
FROM t_role_menu_permission relation
JOIN t_menu term_menu ON term_menu.id = relation.menu_id
JOIN t_menu holiday ON holiday.path = '/admin/education/holidays'
WHERE term_menu.path = '/admin/education/terms'
ON CONFLICT DO NOTHING;

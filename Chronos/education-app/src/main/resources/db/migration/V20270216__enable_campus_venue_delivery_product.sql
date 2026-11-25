ALTER TABLE edu_classroom_reservation
    ALTER COLUMN workflow_instance_id DROP NOT NULL,
    ADD COLUMN reviewed_at timestamp,
    ADD COLUMN review_note varchar(1000);

UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'CAMPUS_VENUE' AND updated_by IS NULL;

INSERT INTO t_menu (id, create_by, create_time, menu_name, order_num, path, parent_id)
SELECT '76216000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP,
       '教室预约', 20, '/admin/education/classroom-reservations', parent_id
FROM t_menu WHERE id = 'e4bcc456-fe06-4934-8aa9-be76e7cc8f57';

INSERT INTO t_permission (id, create_by, create_time, permission_code, permission_name,
    permission_type, resource_type, action_type, scope_type, built_in, status, menu_id)
VALUES ('76216000-0000-4000-8000-000000000002', 'SYSTEM', CURRENT_TIMESTAMP,
        'education:venue-reservation:apply', '提交本人教室预约', 'MENU_ACTION', 'MENU',
        'CREATE', 'ROLE', true, 1, '76216000-0000-4000-8000-000000000001');

INSERT INTO t_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, '76216000-0000-4000-8000-000000000001' FROM t_role_menu
WHERE menu_id = 'e4bcc456-fe06-4934-8aa9-be76e7cc8f57'
ON CONFLICT DO NOTHING;

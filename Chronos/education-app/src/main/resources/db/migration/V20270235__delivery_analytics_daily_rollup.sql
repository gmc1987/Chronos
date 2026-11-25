CREATE TABLE edu_delivery_daily_rollup (
    school_id varchar(64) NOT NULL REFERENCES t_organization(id),
    snapshot_date date NOT NULL,
    student_count integer NOT NULL CHECK (student_count >= 0),
    teacher_count integer NOT NULL CHECK (teacher_count >= 0),
    class_count integer NOT NULL CHECK (class_count >= 0),
    updated_by varchar(128) NOT NULL,
    updated_at timestamp NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (school_id, snapshot_date)
);

INSERT INTO t_menu (id, create_by, create_time, menu_name, order_num, path)
SELECT md5('delivery-school-analytics-menu'), 'SYSTEM', now(), '校务数据分析', 48, '/admin/education/data-center'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path='/admin/education/data-center');

INSERT INTO t_permission (
    id, create_by, create_time, built_in, permission_code, permission_name,
    permission_type, resource_type, action_type, scope_type, menu_id, status
)
SELECT md5(p.code), 'SYSTEM', now(), true, p.code, p.name, 'MENU_ACTION', 'MENU', p.action, 'ROLE',
    (SELECT id FROM t_menu WHERE path='/admin/education/data-center' ORDER BY id LIMIT 1), 1
FROM (VALUES
    ('education:data-center:view', '查看校务指标', 'VIEW'),
    ('education:data-center:import', '导入校务指标', 'CREATE')
) AS p(code,name,action)
ON CONFLICT (permission_code) DO NOTHING;

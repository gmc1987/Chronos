CREATE TABLE edu_teaching_hours_confirmation (
    semester_code varchar(128) NOT NULL,
    occurrence_key varchar(192) NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    organization_id varchar(64) NOT NULL,
    campus_id varchar(64) NOT NULL,
    source_entry_id varchar(64) NOT NULL,
    occurrence_date date NOT NULL,
    actual_minutes integer CHECK (actual_minutes BETWEEN 0 AND 1440),
    reason varchar(500) NOT NULL,
    recorded_by varchar(128) NOT NULL,
    recorded_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    snapshot_json text NOT NULL,
    PRIMARY KEY (semester_code, occurrence_key, revision)
);

CREATE INDEX idx_edu_teaching_hours_scope
    ON edu_teaching_hours_confirmation (organization_id, semester_code, occurrence_date);
CREATE INDEX idx_edu_teaching_hours_source
    ON edu_teaching_hours_confirmation (semester_code, source_entry_id, occurrence_date);

INSERT INTO t_menu (id, create_by, create_time, menu_name, order_num, parent_id, path)
SELECT '76206000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP,
       '教师课时台账', 4, parent_id, '/admin/education/teaching-hours'
FROM t_menu WHERE path = '/admin/education/scheduling' ORDER BY id LIMIT 1;

INSERT INTO t_role_menu (role_id, menu_id)
SELECT DISTINCT grants.role_id, '76206000-0000-4000-8000-000000000001'
FROM (
    SELECT role_id, permission_id FROM t_role_permission
    UNION
    SELECT role_id, permission_id FROM t_role_menu_permission
) grants
JOIN t_role role ON role.id = grants.role_id
JOIN t_permission permission ON permission.id = grants.permission_id
JOIN t_menu ledger ON ledger.id = '76206000-0000-4000-8000-000000000001'
WHERE permission.permission_code IN ('education:scheduling:view', 'education:scheduling:manage')
ON CONFLICT DO NOTHING;

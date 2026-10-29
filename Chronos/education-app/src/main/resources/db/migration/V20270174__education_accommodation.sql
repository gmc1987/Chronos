-- 新生住宿人工业务：宿舍楼、房间、床位和学期住宿分配。
CREATE TABLE IF NOT EXISTS edu_dorm_building (
    id varchar(64) PRIMARY KEY,
    building_code varchar(64) NOT NULL UNIQUE,
    building_name varchar(128) NOT NULL,
    gender_policy varchar(16) NOT NULL DEFAULT 'MIXED',
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS edu_dorm_room (
    id varchar(64) PRIMARY KEY,
    building_id varchar(64) NOT NULL REFERENCES edu_dorm_building(id) ON DELETE CASCADE,
    room_no varchar(32) NOT NULL,
    floor_no integer NOT NULL DEFAULT 1,
    capacity integer NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_edu_dorm_room_no UNIQUE (building_id, room_no),
    CONSTRAINT ck_edu_dorm_room_capacity CHECK (capacity > 0)
);

CREATE TABLE IF NOT EXISTS edu_dorm_bed (
    id varchar(64) PRIMARY KEY,
    room_id varchar(64) NOT NULL REFERENCES edu_dorm_room(id) ON DELETE CASCADE,
    bed_no varchar(32) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'AVAILABLE',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_edu_dorm_bed_no UNIQUE (room_id, bed_no)
);

CREATE TABLE IF NOT EXISTS edu_student_accommodation (
    id varchar(64) PRIMARY KEY,
    semester_code varchar(32) NOT NULL,
    student_id varchar(64) NOT NULL REFERENCES edu_student_profile(id),
    bed_id varchar(64) NOT NULL REFERENCES edu_dorm_bed(id),
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    assigned_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by varchar(128) NOT NULL,
    CONSTRAINT uk_edu_accommodation_student UNIQUE (semester_code, student_id, status),
    CONSTRAINT uk_edu_accommodation_bed UNIQUE (semester_code, bed_id, status)
);

INSERT INTO t_menu (id, create_by, create_time, last_update_by, last_update_time, menu_name, order_num, parent_id, path)
SELECT '74000000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       '住宿分配', 17, (select parent_id from t_menu where path = '/admin/education/students' limit 1), '/admin/education/accommodations'
WHERE NOT EXISTS (SELECT 1 FROM t_menu existing WHERE existing.path = '/admin/education/accommodations');

INSERT INTO t_permission (id, create_by, create_time, last_update_by, last_update_time, permission_code, permission_name,
                          permission_type, action_type, built_in, status, resource_type, scope_type, menu_id)
SELECT source.id, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       source.code, source.name, 'MENU_ACTION', source.action, true, 1, 'MENU', 'ROLE', menu.id
FROM (VALUES
    ('74000000-0000-4000-8000-000000000011','education:accommodation:view','查看住宿分配','VIEW'),
    ('74000000-0000-4000-8000-000000000012','education:accommodation:manage','管理住宿分配','MANAGE')
) source(id, code, name, action)
JOIN t_menu menu ON menu.path = '/admin/education/accommodations'
WHERE NOT EXISTS (SELECT 1 FROM t_permission existing WHERE existing.permission_code = source.code);

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN')
  AND permission.permission_code LIKE 'education:accommodation:%'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT role.id, menu.id FROM t_role role CROSS JOIN t_menu menu
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN')
  AND menu.path = '/admin/education/accommodations'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission(role_id, menu_id, permission_id)
SELECT role.id, menu.id, permission.id FROM t_role role CROSS JOIN t_menu menu CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN')
  AND menu.path = '/admin/education/accommodations'
  AND permission.permission_code LIKE 'education:accommodation:%'
ON CONFLICT DO NOTHING;

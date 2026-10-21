BEGIN;

CREATE SEQUENCE IF NOT EXISTS fm_repair_order_no_seq START WITH 1;
CREATE SEQUENCE IF NOT EXISTS fm_material_request_no_seq START WITH 1;

CREATE TABLE IF NOT EXISTS fm_repair_order (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    order_no varchar(32) NOT NULL UNIQUE,
    title varchar(200) NOT NULL,
    category varchar(32) NOT NULL,
    description text NOT NULL,
    location varchar(300) NOT NULL,
    urgency varchar(16) NOT NULL DEFAULT 'NORMAL',
    contact_name varchar(100) NOT NULL,
    contact_phone varchar(32),
    reporter_username varchar(100) NOT NULL,
    reporter_name varchar(100) NOT NULL,
    organization_id varchar(64),
    status varchar(32) NOT NULL DEFAULT 'PENDING_DISPATCH',
    dispatch_mode varchar(16) NOT NULL DEFAULT 'AUTO',
    technician_id varchar(64),
    technician_username varchar(100),
    technician_name varchar(100),
    dispatched_at timestamp,
    accepted_at timestamp,
    started_at timestamp,
    completed_at timestamp,
    closed_at timestamp,
    completion_note varchar(2000),
    close_comment varchar(1000),
    rating integer,
    cancel_reason varchar(500),
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_fm_order_category CHECK (category IN ('ELECTRICAL','WATER','NETWORK','HVAC','FURNITURE','SECURITY','OTHER')),
    CONSTRAINT ck_fm_order_urgency CHECK (urgency IN ('NORMAL','URGENT','CRITICAL')),
    CONSTRAINT ck_fm_order_status CHECK (status IN ('PENDING_DISPATCH','DISPATCHED','ACCEPTED','IN_PROGRESS','WAITING_MATERIAL','COMPLETED','CLOSED','CANCELLED')),
    CONSTRAINT ck_fm_order_dispatch_mode CHECK (dispatch_mode IN ('AUTO','MANUAL')),
    CONSTRAINT ck_fm_order_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);

CREATE INDEX IF NOT EXISTS idx_fm_order_reporter ON fm_repair_order(reporter_username, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_fm_order_technician ON fm_repair_order(technician_username, status, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_fm_order_admin ON fm_repair_order(status, urgency, create_time DESC);

CREATE TABLE IF NOT EXISTS fm_technician (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    employee_id varchar(64) NOT NULL UNIQUE REFERENCES t_iam_employee(id),
    username varchar(100) NOT NULL UNIQUE REFERENCES t_admin_user(username),
    employee_code varchar(100) NOT NULL,
    technician_name varchar(100) NOT NULL,
    phone varchar(32),
    skill_categories varchar(500) NOT NULL,
    work_status varchar(16) NOT NULL DEFAULT 'AVAILABLE',
    active boolean NOT NULL DEFAULT true,
    auto_dispatch boolean NOT NULL DEFAULT true,
    current_load integer NOT NULL DEFAULT 0,
    max_concurrent integer NOT NULL DEFAULT 5,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_fm_technician_status CHECK (work_status IN ('AVAILABLE','BUSY','OFFLINE')),
    CONSTRAINT ck_fm_technician_load CHECK (current_load >= 0 AND max_concurrent > 0)
);

CREATE TABLE IF NOT EXISTS fm_material (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    material_code varchar(64) NOT NULL UNIQUE,
    material_name varchar(200) NOT NULL,
    specification varchar(200),
    unit varchar(32) NOT NULL,
    stock_quantity numeric(18,3) NOT NULL DEFAULT 0,
    reserved_quantity numeric(18,3) NOT NULL DEFAULT 0,
    safety_stock numeric(18,3) NOT NULL DEFAULT 0,
    unit_cost numeric(18,2) NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_fm_material_stock CHECK (stock_quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= stock_quantity),
    CONSTRAINT ck_fm_material_status CHECK (status IN ('ACTIVE','DISABLED'))
);

CREATE TABLE IF NOT EXISTS fm_material_request (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    request_no varchar(32) NOT NULL UNIQUE,
    repair_order_id varchar(64) NOT NULL REFERENCES fm_repair_order(id),
    applicant_username varchar(100) NOT NULL,
    applicant_name varchar(100) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'SUBMITTED',
    remark varchar(1000),
    reviewed_by varchar(100),
    reviewed_at timestamp,
    review_comment varchar(1000),
    issued_by varchar(100),
    issued_at timestamp,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_fm_material_request_status CHECK (status IN ('SUBMITTED','APPROVED','REJECTED','ISSUED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_fm_material_request_order ON fm_material_request(repair_order_id, create_time);
CREATE INDEX IF NOT EXISTS idx_fm_material_request_status ON fm_material_request(status, create_time DESC);

CREATE TABLE IF NOT EXISTS fm_material_request_item (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    request_id varchar(64) NOT NULL REFERENCES fm_material_request(id) ON DELETE CASCADE,
    material_id varchar(64) NOT NULL REFERENCES fm_material(id),
    material_code varchar(64) NOT NULL,
    material_name varchar(200) NOT NULL,
    requested_quantity numeric(18,3) NOT NULL,
    approved_quantity numeric(18,3),
    issued_quantity numeric(18,3),
    CONSTRAINT ck_fm_material_request_qty CHECK (requested_quantity > 0 AND (approved_quantity IS NULL OR approved_quantity > 0) AND (issued_quantity IS NULL OR issued_quantity > 0))
);

CREATE TABLE IF NOT EXISTS fm_ledger_entry (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    repair_order_id varchar(64),
    material_id varchar(64),
    entry_type varchar(32) NOT NULL,
    quantity numeric(18,3),
    amount numeric(18,2),
    operator_username varchar(100) NOT NULL,
    description varchar(1000) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fm_ledger_created ON fm_ledger_entry(create_time DESC);
CREATE INDEX IF NOT EXISTS idx_fm_ledger_order ON fm_ledger_entry(repair_order_id, create_time);

CREATE TABLE IF NOT EXISTS fm_repair_order_event (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    repair_order_id varchar(64) NOT NULL REFERENCES fm_repair_order(id) ON DELETE CASCADE,
    event_type varchar(32) NOT NULL,
    from_status varchar(32),
    to_status varchar(32) NOT NULL,
    operator_username varchar(100) NOT NULL,
    remark varchar(1000)
);

CREATE INDEX IF NOT EXISTS idx_fm_order_event ON fm_repair_order_event(repair_order_id, create_time);

INSERT INTO t_role (id, create_by, create_time, built_in, role_code, role_name, status, description)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, true, source.code, source.name, 1, source.description
FROM (VALUES
    ('FACILITY_MANAGER', '后勤维修管理员', '管理报修派单、维修人员、物料和维修台账'),
    ('FACILITY_TECHNICIAN', '维修人员', '接单、维修、申请物料并提交完工结果')
) source(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM t_role existing WHERE existing.role_code = source.code);

INSERT INTO t_menu (id, create_by, create_time, last_update_by, last_update_time, menu_name, order_num, parent_id, path)
SELECT '73000000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       '故障报修', 70, NULL, '/admin/facility'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path = '/admin/facility');

INSERT INTO t_menu (id, create_by, create_time, last_update_by, last_update_time, menu_name, order_num, parent_id, path)
SELECT source.id, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, source.name, source.order_num,
       parent.id, source.path
FROM (VALUES
    ('73000000-0000-4000-8000-000000000002', '报修单管理', 1, '/admin/facility/repairs'),
    ('73000000-0000-4000-8000-000000000003', '维修人员管理', 2, '/admin/facility/technicians'),
    ('73000000-0000-4000-8000-000000000004', '维修物料管理', 3, '/admin/facility/materials'),
    ('73000000-0000-4000-8000-000000000005', '物料申请管理', 4, '/admin/facility/material-requests'),
    ('73000000-0000-4000-8000-000000000006', '维修台账', 5, '/admin/facility/ledger')
) source(id, name, order_num, path)
JOIN t_menu parent ON parent.path = '/admin/facility'
WHERE NOT EXISTS (SELECT 1 FROM t_menu existing WHERE existing.path = source.path);

INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time, permission_code, permission_name,
    permission_type, action_type, built_in, status, resource_type, scope_type, menu_id
)
SELECT source.id, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       source.code, source.name, 'MENU_ACTION', source.action, true, 1, 'MENU', 'ROLE', menu.id
FROM (VALUES
    ('73000000-0000-4000-8000-000000000011','facility:repair:view','查看报修单','VIEW','/admin/facility/repairs'),
    ('73000000-0000-4000-8000-000000000012','facility:repair:dispatch','报修派单','UPDATE','/admin/facility/repairs'),
    ('73000000-0000-4000-8000-000000000013','facility:repair:work','执行维修工单','UPDATE','/admin/facility/repairs'),
    ('73000000-0000-4000-8000-000000000014','facility:repair:manage','管理报修业务','MANAGE','/admin/facility/repairs'),
    ('73000000-0000-4000-8000-000000000015','facility:technician:view','查看维修人员','VIEW','/admin/facility/technicians'),
    ('73000000-0000-4000-8000-000000000016','facility:technician:manage','管理维修人员','MANAGE','/admin/facility/technicians'),
    ('73000000-0000-4000-8000-000000000017','facility:material:view','查看维修物料','VIEW','/admin/facility/materials'),
    ('73000000-0000-4000-8000-000000000018','facility:material:manage','管理维修物料','MANAGE','/admin/facility/materials'),
    ('73000000-0000-4000-8000-000000000019','facility:material-request:view','查看物料申请','VIEW','/admin/facility/material-requests'),
    ('73000000-0000-4000-8000-000000000020','facility:material-request:manage','审批和出库物料','APPROVE','/admin/facility/material-requests'),
    ('73000000-0000-4000-8000-000000000021','facility:ledger:view','查看维修台账','VIEW','/admin/facility/ledger')
) source(id, code, name, action, path)
JOIN t_menu menu ON menu.path = source.path
WHERE NOT EXISTS (SELECT 1 FROM t_permission existing WHERE existing.permission_code = source.code);

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN','FACILITY_MANAGER')
  AND permission.permission_code LIKE 'facility:%'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code = 'FACILITY_TECHNICIAN'
  AND permission.permission_code IN ('facility:repair:work','facility:material:view')
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT role.id, menu.id FROM t_role role CROSS JOIN t_menu menu
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN','FACILITY_MANAGER')
  AND (menu.path = '/admin/facility' OR menu.path LIKE '/admin/facility/%')
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission(role_id, menu_id, permission_id)
SELECT role.id, permission.menu_id, permission.id FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN','FACILITY_MANAGER')
  AND permission.permission_code LIKE 'facility:%' AND permission.menu_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO t_portal_application (
    id, create_by, create_time, app_code, app_name, description, enabled, icon, open_mode,
    recommended, route_path, sort_order, required_permission, presentation_mode, native_feature_code
)
VALUES (
    '73000000-0000-4000-8000-000000000030', 'SYSTEM', CURRENT_TIMESTAMP,
    'FACILITY_REPAIR', '故障报修', '拍照提交校园设施故障并跟踪维修进度', true, 'Tools', 'INTERNAL',
    true, '/portal/facility/repairs', 48, NULL, 'NATIVE', 'FACILITY_REPAIR'
)
ON CONFLICT (app_code) DO UPDATE SET
    app_name = EXCLUDED.app_name,
    description = EXCLUDED.description,
    enabled = true,
    route_path = EXCLUDED.route_path,
    presentation_mode = 'NATIVE',
    native_feature_code = 'FACILITY_REPAIR',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

COMMIT;

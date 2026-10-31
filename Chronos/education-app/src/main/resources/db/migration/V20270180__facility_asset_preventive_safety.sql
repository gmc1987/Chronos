-- 设施运维生产治理：资产二维码、不可变库存流水、预防性保养和安全事件。
CREATE TABLE IF NOT EXISTS fm_asset (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp,
  asset_code varchar(64) NOT NULL UNIQUE, asset_name varchar(200) NOT NULL, category varchar(64) NOT NULL,
  serial_number varchar(128), organization_id varchar(64), location varchar(300) NOT NULL,
  qr_token varchar(64) NOT NULL UNIQUE, status varchar(24) NOT NULL DEFAULT 'IN_SERVICE',
  purchase_date date, warranty_expiry date, next_maintenance_date date, row_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_fm_asset_status CHECK(status IN ('IN_SERVICE','OUT_OF_SERVICE','UNDER_REPAIR','RETIRED'))
);
CREATE INDEX IF NOT EXISTS idx_fm_asset_scope ON fm_asset(organization_id,status,category);

CREATE TABLE IF NOT EXISTS fm_preventive_plan (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp,
  asset_id varchar(64) NOT NULL REFERENCES fm_asset(id), plan_name varchar(200) NOT NULL,
  interval_days integer NOT NULL, next_due_date date NOT NULL, assigned_username varchar(100),
  checklist varchar(4000) NOT NULL, status varchar(24) NOT NULL DEFAULT 'ACTIVE',
  last_completed_at timestamp, last_completed_by varchar(100), completion_note varchar(2000),
  row_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_fm_preventive_interval CHECK(interval_days > 0),
  CONSTRAINT ck_fm_preventive_status CHECK(status IN ('ACTIVE','PAUSED','OVERDUE'))
);
CREATE INDEX IF NOT EXISTS idx_fm_preventive_due ON fm_preventive_plan(status,next_due_date);

CREATE TABLE IF NOT EXISTS fm_inventory_transaction (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp,
  material_id varchar(64) NOT NULL REFERENCES fm_material(id), transaction_type varchar(24) NOT NULL,
  quantity numeric(18,3) NOT NULL, before_quantity numeric(18,3) NOT NULL, after_quantity numeric(18,3) NOT NULL,
  reference_type varchar(32), reference_id varchar(64), operator_username varchar(100) NOT NULL,
  reason varchar(1000) NOT NULL, idempotency_key varchar(128) NOT NULL UNIQUE,
  CONSTRAINT ck_fm_inventory_type CHECK(transaction_type IN ('IN','OUT','RETURN','ADJUST')),
  CONSTRAINT ck_fm_inventory_quantity CHECK(quantity <> 0 AND before_quantity >= 0 AND after_quantity >= 0)
);
CREATE INDEX IF NOT EXISTS idx_fm_inventory_material ON fm_inventory_transaction(material_id,create_time DESC);

CREATE TABLE IF NOT EXISTS fm_safety_incident (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp,
  repair_order_id varchar(64) REFERENCES fm_repair_order(id), asset_id varchar(64) REFERENCES fm_asset(id),
  occurred_at timestamp NOT NULL, severity varchar(16) NOT NULL, description varchar(4000) NOT NULL,
  emergency_action varchar(2000) NOT NULL, status varchar(24) NOT NULL DEFAULT 'INVESTIGATING',
  reported_by varchar(100) NOT NULL, closed_by varchar(100), closed_at timestamp, closure_comment varchar(2000),
  row_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_fm_safety_severity CHECK(severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  CONSTRAINT ck_fm_safety_status CHECK(status IN ('REPORTED','INVESTIGATING','CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_fm_safety_open ON fm_safety_incident(status,severity,occurred_at DESC);

INSERT INTO t_menu(id,create_by,create_time,last_update_by,last_update_time,menu_name,order_num,parent_id,path)
SELECT '73000000-0000-4000-8000-000000000007','SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,
       '资产与保养',6,parent.id,'/admin/facility/assets'
FROM t_menu parent WHERE parent.path='/admin/facility'
  AND NOT EXISTS(SELECT 1 FROM t_menu WHERE path='/admin/facility/assets');

INSERT INTO t_permission(id,create_by,create_time,last_update_by,last_update_time,permission_code,permission_name,
                         permission_type,action_type,built_in,status,resource_type,scope_type,menu_id)
SELECT source.id,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,source.code,source.name,'MENU_ACTION',source.action,
       true,1,'MENU','ROLE',menu.id
FROM (VALUES
 ('73000000-0000-4000-8000-000000000022','facility:asset:view','查看资产与保养','VIEW'),
 ('73000000-0000-4000-8000-000000000023','facility:asset:manage','管理资产与保养','MANAGE'),
 ('73000000-0000-4000-8000-000000000024','facility:safety:manage','处置后勤安全事件','MANAGE')
) source(id,code,name,action)
JOIN t_menu menu ON menu.path='/admin/facility/assets'
WHERE NOT EXISTS(SELECT 1 FROM t_permission p WHERE p.permission_code=source.code);

INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN','FACILITY_MANAGER')
  AND p.permission_code IN ('facility:asset:view','facility:asset:manage','facility:safety:manage')
ON CONFLICT DO NOTHING;
INSERT INTO t_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM t_role r CROSS JOIN t_menu m
WHERE r.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN','FACILITY_MANAGER')
  AND m.path='/admin/facility/assets'
ON CONFLICT DO NOTHING;
INSERT INTO t_role_menu_permission(role_id,menu_id,permission_id)
SELECT r.id,m.id,p.id FROM t_role r CROSS JOIN t_menu m CROSS JOIN t_permission p
WHERE r.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN','FACILITY_MANAGER')
  AND m.path='/admin/facility/assets'
  AND p.permission_code IN ('facility:asset:view','facility:asset:manage','facility:safety:manage')
ON CONFLICT DO NOTHING;

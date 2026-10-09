CREATE TABLE IF NOT EXISTS edu_business_trip_config (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    config_code VARCHAR(64) NOT NULL,
    finance_required BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_business_trip_config_code UNIQUE (config_code)
);

INSERT INTO edu_business_trip_config (
    id, create_by, create_time, config_code, finance_required, row_version
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'DEFAULT', FALSE, 0
WHERE NOT EXISTS (
    SELECT 1 FROM edu_business_trip_config WHERE config_code = 'DEFAULT'
);

CREATE TABLE IF NOT EXISTS edu_business_trip (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    workflow_instance_id VARCHAR(64) NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    destination VARCHAR(200) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    budget_project_code VARCHAR(100),
    cost_center_code VARCHAR(100),
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    transport_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    accommodation_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    meal_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    other_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    estimated_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    finance_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    finance_reference VARCHAR(128),
    approved_by VARCHAR(128),
    cancel_reason VARCHAR(1000),
    cancelled_by VARCHAR(128),
    cancelled_at TIMESTAMP,
    withdrawn_by VARCHAR(128),
    withdrawn_at TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_business_trip_workflow UNIQUE (workflow_instance_id),
    CONSTRAINT uk_business_trip_business_key UNIQUE (business_key),
    CONSTRAINT ck_business_trip_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_business_trip_amounts CHECK (
        transport_amount >= 0 AND accommodation_amount >= 0
        AND meal_amount >= 0 AND other_amount >= 0 AND estimated_amount >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_business_trip_employee
    ON edu_business_trip (employee_id, start_date DESC);
CREATE INDEX IF NOT EXISTS idx_business_trip_attendance
    ON edu_business_trip (employee_id, start_date, end_date)
    WHERE status = 'APPROVED';

INSERT INTO form_definition (
    id, form_key, form_name, version, status, description, published_at,
    create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, 'STAFF_BUSINESS_TRIP', '教职工出差申请', 'v1', 'PUBLISHED',
       '教职工出差行程、预计费用和预算归属', CURRENT_TIMESTAMP,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM form_definition
    WHERE form_key = 'STAFF_BUSINESS_TRIP' AND version = 'v1'
);

WITH form AS (
    SELECT id FROM form_definition
    WHERE form_key = 'STAFF_BUSINESS_TRIP' AND version = 'v1'
), fields(field_key, field_label, field_type, sort_order, required) AS (
    VALUES
        ('destination', '出差地点', 'TEXT', 10, true),
        ('startDate', '开始日期', 'DATE', 20, true),
        ('endDate', '结束日期', 'DATE', 30, true),
        ('purpose', '出差事由', 'TEXTAREA', 40, true),
        ('budgetProjectCode', '预算项目', 'TEXT', 50, false),
        ('costCenterCode', '成本中心', 'TEXT', 60, false),
        ('currency', '币种', 'TEXT', 70, true),
        ('transportAmount', '交通费预算', 'NUMBER', 80, true),
        ('accommodationAmount', '住宿费预算', 'NUMBER', 90, true),
        ('mealAmount', '餐费及补助预算', 'NUMBER', 100, true),
        ('otherAmount', '其他费用预算', 'NUMBER', 110, true),
        ('estimatedAmount', '预计总额', 'NUMBER', 120, true)
)
INSERT INTO form_field (
    id, form_id, field_key, field_label, field_type, sort_order, required,
    options_json, create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, form.id, fields.field_key, fields.field_label,
       fields.field_type, fields.sort_order, fields.required,
       lo_from_bytea(0, convert_to('[]', 'UTF8')),
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM form CROSS JOIN fields
WHERE NOT EXISTS (
    SELECT 1 FROM form_field existing
    WHERE existing.form_id = form.id AND existing.field_key = fields.field_key
);

WITH form AS (
    SELECT id FROM form_definition
    WHERE form_key = 'STAFF_BUSINESS_TRIP' AND version = 'v1'
)
INSERT INTO wf_definition (
    id, flow_code, flow_name, category, version, description, entry_node_key,
    status, tags, config_json, main_form_id, manager_user, starter_scope_json,
    ai_assist_enabled, published_at, create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, 'STAFF_BUSINESS_TRIP_APPROVAL', '教职工出差审批',
       'COLLABORATION', 'v1', '审批出差行程和预计费用，并按配置衔接财务预算',
       'start', 'PUBLISHED', '协同办公,出差,教职工',
       lo_from_bytea(0, convert_to('{}', 'UTF8')), form.id, 'admin',
       lo_from_bytea(0, convert_to('{"type":"ALL"}', 'UTF8')), false,
       CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM form
WHERE NOT EXISTS (
    SELECT 1 FROM wf_definition
    WHERE flow_code = 'STAFF_BUSINESS_TRIP_APPROVAL' AND version = 'v1'
);

WITH flow AS (
    SELECT id FROM wf_definition
    WHERE flow_code = 'STAFF_BUSINESS_TRIP_APPROVAL' AND version = 'v1'
), nodes(node_key, node_name, node_type, properties_json) AS (
    VALUES
        ('start', '开始', 'START', '{"position":{"x":80,"y":160}}'),
        ('approval', '出差审批', 'APPROVAL',
         '{"position":{"x":330,"y":160},"assigneeMode":"ROLE","assigneeValue":"EDU_ACADEMIC_APPROVER","approvalMode":"SINGLE","dueHours":24,"returnPolicy":"PREVIOUS"}'),
        ('end', '结束', 'END', '{"position":{"x":580,"y":160}}')
)
INSERT INTO wf_node (
    id, flow_id, node_key, node_name, node_type, executor, timeout_sec, retry_max,
    retry_interval_sec, input_schema, output_schema, properties_json,
    additional_form_ids, field_permissions_json, create_by, create_time,
    last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, flow.id, nodes.node_key, nodes.node_name, nodes.node_type,
       '', 0, 0, 0, lo_from_bytea(0, convert_to('{}', 'UTF8')),
       lo_from_bytea(0, convert_to('{}', 'UTF8')),
       lo_from_bytea(0, convert_to(nodes.properties_json, 'UTF8')),
       lo_from_bytea(0, convert_to('[]', 'UTF8')),
       lo_from_bytea(0, convert_to('{"permissions":{},"required":{}}', 'UTF8')),
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flow CROSS JOIN nodes
ON CONFLICT (flow_id, node_key) DO NOTHING;

WITH flow AS (
    SELECT id FROM wf_definition
    WHERE flow_code = 'STAFF_BUSINESS_TRIP_APPROVAL' AND version = 'v1'
), edges(from_key, to_key) AS (
    VALUES ('start', 'approval'), ('approval', 'end')
)
INSERT INTO wf_edge (
    id, flow_id, from_node_key, to_node_key, condition_expr, is_default,
    create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, flow.id, edges.from_key, edges.to_key, '', true,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flow CROSS JOIN edges
WHERE NOT EXISTS (
    SELECT 1 FROM wf_edge existing
    WHERE existing.flow_id = flow.id
      AND existing.from_node_key = edges.from_key
      AND existing.to_node_key = edges.to_key
);

INSERT INTO wf_definition_acl (
    id, definition_id, subject_type, subject_id, action, enabled,
    create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, definition.id, 'ROLE', 'ROLE_PLATFORM_USER', 'START', true,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM wf_definition definition
WHERE definition.flow_code = 'STAFF_BUSINESS_TRIP_APPROVAL'
  AND definition.version = 'v1'
ON CONFLICT (definition_id, subject_type, subject_id, action)
DO UPDATE SET enabled = true;

INSERT INTO t_portal_application (
    id, create_by, create_time, app_code, app_name, description,
    enabled, icon, open_mode, recommended, route_path, sort_order
)
VALUES (
    gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
    'STAFF_BUSINESS_TRIP', '我的出差', '发起出差申请、查看审批和财务协同状态',
    true, 'Suitcase', 'INTERNAL', true,
    '/portal/collaboration/business-trips', 46
)
ON CONFLICT (app_code) DO UPDATE SET
    app_name = EXCLUDED.app_name,
    description = EXCLUDED.description,
    enabled = true,
    route_path = EXCLUDED.route_path,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

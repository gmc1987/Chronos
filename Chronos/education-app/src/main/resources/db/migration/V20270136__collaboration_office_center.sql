CREATE TABLE IF NOT EXISTS collab_office_resource (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    resource_type VARCHAR(24) NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    resource_name VARCHAR(128) NOT NULL,
    license_plate VARCHAR(32),
    capacity INTEGER,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_office_resource_code UNIQUE (resource_type, resource_code),
    CONSTRAINT ck_office_resource_type CHECK (resource_type IN ('VEHICLE', 'SEAL')),
    CONSTRAINT ck_office_resource_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE TABLE IF NOT EXISTS collab_office_request (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    request_type VARCHAR(24) NOT NULL,
    workflow_instance_id VARCHAR(64),
    business_key VARCHAR(128) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL REFERENCES collab_office_resource(id),
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    destination VARCHAR(200),
    passenger_count INTEGER,
    document_title VARCHAR(300),
    copy_count INTEGER,
    attachment_file_id VARCHAR(64),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    approved_by VARCHAR(128),
    withdrawn_by VARCHAR(128),
    withdrawn_at TIMESTAMP,
    completed_by VARCHAR(128),
    completed_at TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_office_request_workflow UNIQUE (workflow_instance_id),
    CONSTRAINT uk_office_request_business_key UNIQUE (business_key),
    CONSTRAINT ck_office_request_type CHECK (request_type IN ('VEHICLE', 'SEAL')),
    CONSTRAINT ck_office_request_time CHECK (end_at > start_at),
    CONSTRAINT ck_office_request_counts CHECK (
        (passenger_count IS NULL OR passenger_count > 0)
        AND (copy_count IS NULL OR copy_count > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_office_request_employee
    ON collab_office_request (employee_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_office_request_resource_time
    ON collab_office_request (resource_id, start_at, end_at)
    WHERE status IN ('PENDING', 'APPROVED', 'IN_USE');

CREATE TABLE IF NOT EXISTS collab_official_document (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    direction VARCHAR(16) NOT NULL,
    title VARCHAR(300) NOT NULL,
    document_number VARCHAR(100),
    urgency VARCHAR(24) NOT NULL DEFAULT 'NORMAL',
    summary VARCHAR(2000),
    drafter_username VARCHAR(128) NOT NULL,
    workflow_instance_id VARCHAR(64),
    business_key VARCHAR(128),
    primary_file_id VARCHAR(64),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    issued_at TIMESTAMP,
    archived_at TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_official_document_direction CHECK (direction IN ('OUTGOING', 'INCOMING')),
    CONSTRAINT uk_official_document_workflow UNIQUE (workflow_instance_id),
    CONSTRAINT uk_official_document_business_key UNIQUE (business_key)
);

CREATE INDEX IF NOT EXISTS idx_official_document_owner
    ON collab_official_document (drafter_username, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_official_document_status
    ON collab_official_document (status, create_time DESC);

CREATE TABLE IF NOT EXISTS collab_file_share (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    owner_username VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    file_id VARCHAR(64),
    audience_type VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
    audience_value VARCHAR(500),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_file_share_audience CHECK (
        audience_type IN ('PRIVATE', 'USER', 'DEPARTMENT', 'ALL')
    )
);

CREATE INDEX IF NOT EXISTS idx_file_share_owner
    ON collab_file_share (owner_username, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_file_share_active
    ON collab_file_share (status, create_time DESC);

WITH forms(form_key, form_name, description) AS (
    VALUES
        ('STAFF_VEHICLE_USE', '教职工用车申请', '公务用车时段、目的地、人数和事由'),
        ('STAFF_SEAL_USE', '教职工用印申请', '印章、文件名称、份数、时间和事由'),
        ('OFFICIAL_DOCUMENT_ISSUE', '发文审批', '公文拟稿、核稿与签发')
)
INSERT INTO form_definition (
    id, form_key, form_name, version, status, description, published_at,
    create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, forms.form_key, forms.form_name, 'v1', 'PUBLISHED',
       forms.description, CURRENT_TIMESTAMP,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM forms
WHERE NOT EXISTS (
    SELECT 1 FROM form_definition existing
    WHERE existing.form_key = forms.form_key AND existing.version = 'v1'
);

WITH fields(form_key, field_key, field_label, field_type, sort_order, required) AS (
    VALUES
        ('STAFF_VEHICLE_USE', 'resourceName', '车辆', 'TEXT', 10, true),
        ('STAFF_VEHICLE_USE', 'purpose', '用车事由', 'TEXTAREA', 20, true),
        ('STAFF_VEHICLE_USE', 'startAt', '开始时间', 'TEXT', 30, true),
        ('STAFF_VEHICLE_USE', 'endAt', '结束时间', 'TEXT', 40, true),
        ('STAFF_VEHICLE_USE', 'destination', '目的地', 'TEXT', 50, true),
        ('STAFF_VEHICLE_USE', 'passengerCount', '乘车人数', 'NUMBER', 60, true),
        ('STAFF_SEAL_USE', 'resourceName', '印章', 'TEXT', 10, true),
        ('STAFF_SEAL_USE', 'purpose', '用印事由', 'TEXTAREA', 20, true),
        ('STAFF_SEAL_USE', 'startAt', '期望开始时间', 'TEXT', 30, true),
        ('STAFF_SEAL_USE', 'endAt', '期望结束时间', 'TEXT', 40, true),
        ('STAFF_SEAL_USE', 'documentTitle', '文件名称', 'TEXT', 50, true),
        ('STAFF_SEAL_USE', 'copyCount', '用印份数', 'NUMBER', 60, true),
        ('STAFF_SEAL_USE', 'attachmentFileId', '用印附件编号', 'TEXT', 70, false),
        ('OFFICIAL_DOCUMENT_ISSUE', 'title', '公文标题', 'TEXT', 10, true),
        ('OFFICIAL_DOCUMENT_ISSUE', 'urgency', '紧急程度', 'TEXT', 20, true),
        ('OFFICIAL_DOCUMENT_ISSUE', 'summary', '内容摘要', 'TEXTAREA', 30, false)
)
INSERT INTO form_field (
    id, form_id, field_key, field_label, field_type, sort_order, required,
    options_json, create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, form.id, fields.field_key, fields.field_label,
       fields.field_type, fields.sort_order, fields.required,
       lo_from_bytea(0, convert_to('[]', 'UTF8')),
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM fields
JOIN form_definition form
  ON form.form_key = fields.form_key AND form.version = 'v1'
WHERE NOT EXISTS (
    SELECT 1 FROM form_field existing
    WHERE existing.form_id = form.id AND existing.field_key = fields.field_key
);

WITH flows(flow_code, flow_name, form_key, description) AS (
    VALUES
        ('STAFF_VEHICLE_USE_APPROVAL', '教职工用车审批', 'STAFF_VEHICLE_USE', '审批公务用车并锁定车辆时段'),
        ('STAFF_SEAL_USE_APPROVAL', '教职工用印审批', 'STAFF_SEAL_USE', '审批用印申请并进入执行登记'),
        ('OFFICIAL_DOCUMENT_ISSUE_APPROVAL', '公文签发审批', 'OFFICIAL_DOCUMENT_ISSUE', '完成公文核稿和签发')
)
INSERT INTO wf_definition (
    id, flow_code, flow_name, category, version, description, entry_node_key,
    status, tags, config_json, main_form_id, manager_user, starter_scope_json,
    ai_assist_enabled, published_at, create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, flows.flow_code, flows.flow_name,
       'COLLABORATION', 'v1', flows.description, 'start', 'PUBLISHED', '协同办公',
       lo_from_bytea(0, convert_to('{}', 'UTF8')), form.id, 'admin',
       lo_from_bytea(0, convert_to('{"type":"ALL"}', 'UTF8')), false,
       CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flows
JOIN form_definition form ON form.form_key = flows.form_key AND form.version = 'v1'
WHERE NOT EXISTS (
    SELECT 1 FROM wf_definition existing
    WHERE existing.flow_code = flows.flow_code AND existing.version = 'v1'
);

WITH flows AS (
    SELECT id FROM wf_definition
    WHERE version = 'v1'
      AND flow_code IN (
          'STAFF_VEHICLE_USE_APPROVAL',
          'STAFF_SEAL_USE_APPROVAL',
          'OFFICIAL_DOCUMENT_ISSUE_APPROVAL'
      )
), nodes(node_key, node_name, node_type, properties_json) AS (
    VALUES
        ('start', '开始', 'START', '{"position":{"x":80,"y":160}}'),
        ('approval', '协同办公审批', 'APPROVAL',
         '{"position":{"x":330,"y":160},"assigneeMode":"ROLE","assigneeValue":"EDU_ACADEMIC_APPROVER","approvalMode":"SINGLE","dueHours":24,"returnPolicy":"PREVIOUS"}'),
        ('end', '结束', 'END', '{"position":{"x":580,"y":160}}')
)
INSERT INTO wf_node (
    id, flow_id, node_key, node_name, node_type, executor, timeout_sec, retry_max,
    retry_interval_sec, input_schema, output_schema, properties_json,
    additional_form_ids, field_permissions_json, create_by, create_time,
    last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, flows.id, nodes.node_key, nodes.node_name, nodes.node_type,
       '', 0, 0, 0, lo_from_bytea(0, convert_to('{}', 'UTF8')),
       lo_from_bytea(0, convert_to('{}', 'UTF8')),
       lo_from_bytea(0, convert_to(nodes.properties_json, 'UTF8')),
       lo_from_bytea(0, convert_to('[]', 'UTF8')),
       lo_from_bytea(0, convert_to('{"permissions":{},"required":{}}', 'UTF8')),
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flows CROSS JOIN nodes
ON CONFLICT (flow_id, node_key) DO NOTHING;

WITH flows AS (
    SELECT id FROM wf_definition
    WHERE version = 'v1'
      AND flow_code IN (
          'STAFF_VEHICLE_USE_APPROVAL',
          'STAFF_SEAL_USE_APPROVAL',
          'OFFICIAL_DOCUMENT_ISSUE_APPROVAL'
      )
), edges(from_key, to_key) AS (
    VALUES ('start', 'approval'), ('approval', 'end')
)
INSERT INTO wf_edge (
    id, flow_id, from_node_key, to_node_key, condition_expr, is_default,
    create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, flows.id, edges.from_key, edges.to_key, '', true,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flows CROSS JOIN edges
WHERE NOT EXISTS (
    SELECT 1 FROM wf_edge existing
    WHERE existing.flow_id = flows.id
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
WHERE definition.version = 'v1'
  AND definition.flow_code IN (
      'STAFF_VEHICLE_USE_APPROVAL',
      'STAFF_SEAL_USE_APPROVAL',
      'OFFICIAL_DOCUMENT_ISSUE_APPROVAL'
  )
ON CONFLICT (definition_id, subject_type, subject_id, action)
DO UPDATE SET enabled = true;

UPDATE t_portal_application
SET route_path = '/portal/collaboration',
    description = '请假、出差、用车、用印、会议与个人办公',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE app_code IN ('oa', 'COLLABORATION');

UPDATE t_portal_application
SET route_path = '/portal/collaboration/documents',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE app_code IN ('document', 'DOCUMENT_CENTER');

UPDATE t_portal_application
SET route_path = '/portal/collaboration/files',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE app_code IN ('file', 'FILE_CENTER');

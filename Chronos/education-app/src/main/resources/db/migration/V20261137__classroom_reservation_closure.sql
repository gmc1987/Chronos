CREATE TABLE IF NOT EXISTS edu_classroom_reservation (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    workflow_instance_id VARCHAR(64) NOT NULL,
    business_key VARCHAR(128),
    applicant_username VARCHAR(128) NOT NULL,
    applicant_id VARCHAR(64),
    semester_code VARCHAR(32) NOT NULL,
    classroom_id VARCHAR(64) NOT NULL,
    usage_date DATE NOT NULL,
    start_period INTEGER NOT NULL,
    duration_periods INTEGER NOT NULL,
    attendee_count INTEGER NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL,
    approved_by VARCHAR(128),
    failure_message VARCHAR(1000),
    cancelled_by VARCHAR(128),
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(1000),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_edu_classroom_reservation_workflow UNIQUE (workflow_instance_id),
    CONSTRAINT ck_edu_classroom_reservation_period CHECK (start_period > 0 AND duration_periods > 0),
    CONSTRAINT ck_edu_classroom_reservation_attendee CHECK (attendee_count > 0)
);

CREATE INDEX IF NOT EXISTS idx_classroom_reservation_calendar
    ON edu_classroom_reservation (classroom_id, usage_date, start_period);
CREATE INDEX IF NOT EXISTS idx_classroom_reservation_applicant
    ON edu_classroom_reservation (applicant_username, usage_date DESC);
CREATE INDEX IF NOT EXISTS idx_classroom_reservation_failed
    ON edu_classroom_reservation (create_time DESC)
    WHERE status = 'APPLY_FAILED';

-- 教室申请主表单。实体选项通过受数据范围保护的远程接口加载，不把教室 ID 固化进字典。
INSERT INTO form_definition (
    id, form_key, form_name, version, status, description, published_at,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    'EDU_CLASSROOM_RESERVATION',
    '教室使用申请',
    'v1',
    'PUBLISHED',
    '教师申请临时使用教室，审批完成后写入教室占用台账',
    CURRENT_TIMESTAMP,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM form_definition
    WHERE form_key = 'EDU_CLASSROOM_RESERVATION' AND version = 'v1'
);

WITH form AS (
    SELECT id
    FROM form_definition
    WHERE form_key = 'EDU_CLASSROOM_RESERVATION' AND version = 'v1'
), fields(field_key, field_label, field_type, sort_order, required, options_json) AS (
    VALUES
        ('semesterCode', '学期', 'SELECT', 10, true,
            '{"source":"REMOTE","url":"/portal/education/academic-terms/options"}'),
        ('classroomId', '使用教室', 'SELECT', 20, true,
            '{"source":"REMOTE","url":"/portal/education/classrooms/options"}'),
        ('usageDate', '使用日期', 'DATE', 30, true, '[]'),
        ('startPeriod', '开始节次', 'NUMBER', 40, true, '[]'),
        ('durationPeriods', '持续节数', 'NUMBER', 50, true, '[]'),
        ('attendeeCount', '使用人数', 'NUMBER', 60, true, '[]'),
        ('purpose', '使用用途', 'TEXTAREA', 70, true, '[]')
)
INSERT INTO form_field (
    id, form_id, field_key, field_label, field_type, sort_order, required,
    options_json, create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    form.id,
    fields.field_key,
    fields.field_label,
    fields.field_type,
    fields.sort_order,
    fields.required,
    lo_from_bytea(0, convert_to(fields.options_json, 'UTF8')),
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM form
CROSS JOIN fields
WHERE NOT EXISTS (
    SELECT 1
    FROM form_field existing
    WHERE existing.form_id = form.id
      AND existing.field_key = fields.field_key
);

INSERT INTO wf_definition (
    id, flow_code, flow_name, category, version, description,
    entry_node_key, status, tags, config_json, main_form_id,
    manager_user, starter_scope_json, ai_assist_enabled, published_at,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    'EDU_CLASSROOM_RESERVATION_APPROVAL',
    '教室使用申请审批',
    'EDUCATION',
    'v1',
    '教师提交教室使用申请，由教务审批后形成正式资源占用',
    'start',
    'PUBLISHED',
    '教育,教务,教室申请',
    lo_from_bytea(0, convert_to('{}', 'UTF8')),
    form.id,
    'admin',
    lo_from_bytea(0, convert_to('{"type":"ALL"}', 'UTF8')),
    false,
    CURRENT_TIMESTAMP,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM form_definition form
WHERE form.form_key = 'EDU_CLASSROOM_RESERVATION'
  AND form.version = 'v1'
  AND NOT EXISTS (
      SELECT 1 FROM wf_definition
      WHERE flow_code = 'EDU_CLASSROOM_RESERVATION_APPROVAL' AND version = 'v1'
  );

WITH flow AS (
    SELECT id
    FROM wf_definition
    WHERE flow_code = 'EDU_CLASSROOM_RESERVATION_APPROVAL' AND version = 'v1'
), nodes(node_key, node_name, node_type, properties_json) AS (
    VALUES
        ('start', '开始', 'START', '{"position":{"x":80,"y":160}}'),
        ('academicApproval', '教务审批', 'APPROVAL',
            '{"position":{"x":330,"y":160},"assigneeMode":"ROLE","assigneeValue":"EDU_ACADEMIC_APPROVER","approvalMode":"SINGLE","dueHours":24,"returnPolicy":"PREVIOUS"}'),
        ('end', '结束', 'END', '{"position":{"x":580,"y":160}}')
)
INSERT INTO wf_node (
    id, flow_id, node_key, node_name, node_type, executor,
    timeout_sec, retry_max, retry_interval_sec,
    input_schema, output_schema, properties_json,
    additional_form_ids, field_permissions_json,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    flow.id,
    nodes.node_key,
    nodes.node_name,
    nodes.node_type,
    '', 0, 0, 0,
    lo_from_bytea(0, convert_to('{}', 'UTF8')),
    lo_from_bytea(0, convert_to('{}', 'UTF8')),
    lo_from_bytea(0, convert_to(nodes.properties_json, 'UTF8')),
    lo_from_bytea(0, convert_to('[]', 'UTF8')),
    lo_from_bytea(0, convert_to('{"permissions":{},"required":{}}', 'UTF8')),
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flow
CROSS JOIN nodes
ON CONFLICT (flow_id, node_key) DO NOTHING;

WITH flow AS (
    SELECT id
    FROM wf_definition
    WHERE flow_code = 'EDU_CLASSROOM_RESERVATION_APPROVAL' AND version = 'v1'
), edges(from_key, to_key) AS (
    VALUES
        ('start', 'academicApproval'),
        ('academicApproval', 'end')
)
INSERT INTO wf_edge (
    id, flow_id, from_node_key, to_node_key, condition_expr, is_default,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    flow.id,
    edges.from_key,
    edges.to_key,
    '',
    true,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flow
CROSS JOIN edges
WHERE NOT EXISTS (
    SELECT 1
    FROM wf_edge existing
    WHERE existing.flow_id = flow.id
      AND existing.from_node_key = edges.from_key
      AND existing.to_node_key = edges.to_key
);

INSERT INTO wf_definition_acl (
    id, definition_id, subject_type, subject_id, action, enabled,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    definition.id,
    'ROLE',
    role_code,
    'START',
    true,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM wf_definition definition
CROSS JOIN (VALUES ('EDU_TEACHER')) role(role_code)
WHERE definition.flow_code = 'EDU_CLASSROOM_RESERVATION_APPROVAL'
  AND definition.version = 'v1'
ON CONFLICT (definition_id, subject_type, subject_id, action)
DO UPDATE SET enabled = true;

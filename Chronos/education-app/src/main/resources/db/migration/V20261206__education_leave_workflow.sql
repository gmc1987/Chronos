-- Published leave workflows use the existing Flowable runtime and workflow
-- notification outbox; this migration only adds definitions/forms, no data repair.
INSERT INTO form_definition (
    id, form_key, form_name, version, status, description, published_at,
    create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, 'EDU_LEAVE_REQUEST', '请假申请', 'v1', 'PUBLISHED',
       '学生或教师提交请假申请，由教务审批后进入请假台账', CURRENT_TIMESTAMP,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM form_definition WHERE form_key = 'EDU_LEAVE_REQUEST' AND version = 'v1'
);

WITH form AS (
    SELECT id FROM form_definition WHERE form_key = 'EDU_LEAVE_REQUEST' AND version = 'v1'
), fields(field_key, field_label, field_type, sort_order, required, options_json) AS (
    VALUES
        ('leaveType', '请假类型', 'SELECT', 10, true,
         '{"source":"DICTIONARY","dictCode":"EDU_LEAVE_TYPE"}'),
        ('startDate', '开始日期', 'DATE', 20, true, '[]'),
        ('endDate', '结束日期', 'DATE', 30, true, '[]'),
        ('reason', '请假原因', 'TEXTAREA', 40, true, '[]'),
        ('studentId', '学生', 'TEXT', 50, false, '[]')
)
INSERT INTO form_field (
    id, form_id, field_key, field_label, field_type, sort_order, required,
    options_json, create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, form.id, fields.field_key, fields.field_label,
       fields.field_type, fields.sort_order, fields.required,
       lo_from_bytea(0, convert_to(fields.options_json, 'UTF8')),
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM form CROSS JOIN fields
WHERE NOT EXISTS (
    SELECT 1 FROM form_field existing
    WHERE existing.form_id = form.id AND existing.field_key = fields.field_key
);

WITH form AS (
    SELECT id FROM form_definition WHERE form_key = 'EDU_LEAVE_REQUEST' AND version = 'v1'
), flows(flow_code, flow_name, tags, role_code) AS (
    VALUES
        ('EDU_STUDENT_LEAVE_APPROVAL', '学生请假审批', '教育,请假,学生', 'EDU_STUDENT'),
        ('EDU_TEACHER_LEAVE_APPROVAL', '教师请假审批', '教育,请假,教师', 'EDU_TEACHER')
)
INSERT INTO wf_definition (
    id, flow_code, flow_name, category, version, description, entry_node_key,
    status, tags, config_json, main_form_id, manager_user, starter_scope_json,
    ai_assist_enabled, published_at, create_by, create_time, last_update_by, last_update_time
)
SELECT gen_random_uuid()::text, flows.flow_code, flows.flow_name, 'EDUCATION', 'v1',
       '请假申请审批完成后写入请假台账并通知发起人', 'start', 'PUBLISHED',
       flows.tags, lo_from_bytea(0, convert_to('{}', 'UTF8')), form.id, 'admin',
       lo_from_bytea(0, convert_to('{"type":"ALL"}', 'UTF8')), false,
       CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM form CROSS JOIN flows
WHERE NOT EXISTS (
    SELECT 1 FROM wf_definition existing
    WHERE existing.flow_code = flows.flow_code AND existing.version = 'v1'
);

WITH flow AS (
    SELECT id FROM wf_definition
    WHERE flow_code IN ('EDU_STUDENT_LEAVE_APPROVAL', 'EDU_TEACHER_LEAVE_APPROVAL')
      AND version = 'v1'
), nodes(node_key, node_name, node_type, properties_json) AS (
    VALUES
        ('start', '开始', 'START', '{"position":{"x":80,"y":160}}'),
        ('approval', '教务审批', 'APPROVAL',
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
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flow CROSS JOIN nodes
ON CONFLICT (flow_id, node_key) DO NOTHING;

WITH flow AS (
    SELECT id FROM wf_definition
    WHERE flow_code IN ('EDU_STUDENT_LEAVE_APPROVAL', 'EDU_TEACHER_LEAVE_APPROVAL')
      AND version = 'v1'
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
SELECT gen_random_uuid()::text, definition.id, 'ROLE', role_code, 'START', true,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM wf_definition definition
CROSS JOIN (VALUES
    ('EDU_STUDENT', 'EDU_STUDENT_LEAVE_APPROVAL'),
    ('EDU_PARENT', 'EDU_STUDENT_LEAVE_APPROVAL'),
    ('EDU_TEACHER', 'EDU_TEACHER_LEAVE_APPROVAL')
) roles(role_code, flow_code)
WHERE definition.flow_code = roles.flow_code AND definition.version = 'v1'
ON CONFLICT (definition_id, subject_type, subject_id, action)
DO UPDATE SET enabled = true;

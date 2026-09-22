-- 成绩中心第二阶段：导入、更正、补考重修及分析权限。
ALTER TABLE edu_exam_paper_item
    ADD COLUMN IF NOT EXISTS question_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_exam_paper_item_question
    ON edu_exam_paper_item(question_id);

CREATE TABLE IF NOT EXISTS edu_grade_change_request (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    gradebook_id varchar(64) NOT NULL,
    course_grade_id varchar(64) NOT NULL,
    student_id varchar(64) NOT NULL,
    before_score numeric(8,2) NOT NULL,
    after_score numeric(8,2) NOT NULL,
    reason varchar(1000) NOT NULL,
    workflow_instance_id varchar(64),
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    approved_by varchar(128),
    approved_at timestamp,
    row_version bigint NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_grade_change_gradebook
    ON edu_grade_change_request(gradebook_id, status);

-- 服务层校验提供友好提示，部分唯一索引则兜底阻止并发请求重复建单。
CREATE UNIQUE INDEX IF NOT EXISTS uk_grade_change_reviewing
    ON edu_grade_change_request(course_grade_id)
    WHERE status = 'REVIEWING';

CREATE TABLE IF NOT EXISTS edu_grade_change_incident (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    change_request_id varchar(64) NOT NULL,
    workflow_instance_id varchar(64) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'OPEN',
    retry_count integer NOT NULL DEFAULT 0,
    last_error varchar(2000) NOT NULL,
    last_retry_by varchar(128),
    last_retry_at timestamp,
    resolved_by varchar(128),
    resolved_at timestamp,
    resolution_note varchar(1000),
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_grade_change_incident_request UNIQUE(change_request_id),
    CONSTRAINT ck_grade_change_incident_status CHECK(status IN ('OPEN', 'RESOLVED', 'IGNORED'))
);

CREATE INDEX IF NOT EXISTS idx_grade_change_incident_status
    ON edu_grade_change_incident(status, create_time DESC);

CREATE TABLE IF NOT EXISTS edu_makeup_exam_record (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    gradebook_id varchar(64) NOT NULL,
    source_grade_id varchar(64) NOT NULL,
    student_id varchar(64) NOT NULL,
    attempt_type varchar(16) NOT NULL,
    result_score numeric(8,2),
    status varchar(24) NOT NULL DEFAULT 'REGISTERED',
    published_grade_id varchar(64),
    remark varchar(500),
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_makeup_source_type UNIQUE(source_grade_id, attempt_type),
    CONSTRAINT ck_makeup_attempt_type CHECK(attempt_type IN ('MAKEUP', 'RETAKE'))
);

CREATE INDEX IF NOT EXISTS idx_makeup_gradebook_status
    ON edu_makeup_exam_record(gradebook_id, status);

-- 成绩特殊状态属于学校可管理的业务字典，前端不再硬编码下拉选项。
INSERT INTO t_dict (
    id,
    create_by,
    create_time,
    dict_code,
    dict_name,
    dict_value,
    parent_id,
    status
)
SELECT
    gen_random_uuid()::text,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'EDU_GRADE_SPECIAL_STATUS',
    '成绩特殊状态',
    NULL,
    NULL,
    1
WHERE NOT EXISTS (
    SELECT 1
    FROM t_dict
    WHERE dict_code = 'EDU_GRADE_SPECIAL_STATUS'
      AND parent_id IS NULL
);

INSERT INTO t_dict (
    id,
    create_by,
    create_time,
    dict_code,
    dict_name,
    dict_value,
    parent_id,
    status
)
SELECT
    gen_random_uuid()::text,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'EDU_GRADE_SPECIAL_STATUS',
    source.dict_name,
    source.dict_value,
    root.id,
    1
FROM (
    VALUES
        ('缺考', 'ABSENT'),
        ('免修', 'EXEMPT'),
        ('缓考', 'DEFERRED'),
        ('无效', 'INVALID')
) AS source(dict_name, dict_value)
JOIN t_dict root
  ON root.dict_code = 'EDU_GRADE_SPECIAL_STATUS'
 AND root.parent_id IS NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM t_dict existing
    WHERE existing.dict_code = 'EDU_GRADE_SPECIAL_STATUS'
      AND existing.dict_value = source.dict_value
);

INSERT INTO t_permission(
    id, create_by, create_time, permission_code, permission_name,
    permission_type, action_type, built_in, status, resource_type, scope_type, description, menu_id)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, value.code, value.name,
       'MENU_ACTION', value.action, true, 1, 'EDUCATION_SCORE', 'ROLE', value.description,
       '827b2fef-b55f-49be-a226-3c9b5dcc71df'
FROM (VALUES
    ('education:score:gradebook:import', '导入成绩册', 'IMPORT', '校验并导入成绩册 Excel'),
    ('education:score:change:request', '申请成绩更正', 'CREATE', '申请更正已发布成绩'),
    ('education:score:change:approve', '审核成绩更正', 'REVIEW', '审核并发布成绩更正版本'),
    ('education:score:makeup:manage', '管理补考重修', 'MANAGE', '登记并发布补考重修成绩'),
    ('education:score:incident:manage', '处理成绩更正事故', 'MANAGE', '查看、重试或忽略成绩更正回写事故')
) value(code, name, action, description)
WHERE NOT EXISTS (SELECT 1 FROM t_permission permission WHERE permission.permission_code = value.code);

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('ROLE_PLATFORM_ADMIN', 'SUPER_ADMIN', 'EDU_ADMIN')
  AND permission.permission_code LIKE 'education:score:%'
ON CONFLICT DO NOTHING;

-- 成绩更正必须独立审批，不能复用成绩册发布流程以免业务键和审核职责混淆。
INSERT INTO wf_definition(
    id, flow_code, flow_name, category, version, description, entry_node_key, status, tags,
    config_json, starter_scope_json, ai_assist_enabled, published_at,
    create_by, create_time, last_update_by, last_update_time)
SELECT gen_random_uuid()::text, 'EDU_GRADE_CHANGE_REVIEW', '成绩更正审核', 'EDUCATION', 'v1',
       '审核已发布成绩的更正申请，通过后生成不可变新版本', 'START', 'PUBLISHED', '教育,成绩中心',
       lo_from_bytea(0, convert_to('{"approvalMode":"SINGLE","allowReturn":false}', 'UTF8')),
       lo_from_bytea(0, convert_to('{"type":"ALL"}', 'UTF8')), false, CURRENT_TIMESTAMP,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM wf_definition
    WHERE flow_code = 'EDU_GRADE_CHANGE_REVIEW' AND version = 'v1');

WITH flow AS (
    SELECT id FROM wf_definition
    WHERE flow_code = 'EDU_GRADE_CHANGE_REVIEW' AND version = 'v1'
), nodes AS (
    SELECT * FROM (VALUES
        ('START', '开始', 'START', '{}'),
        ('ACADEMIC_APPROVAL', '教务审核', 'APPROVAL',
         '{"assigneeMode":"ROLE","assigneeValue":"EDU_ACADEMIC_APPROVER","approvalMode":"SINGLE"}'),
        ('END', '结束', 'END', '{}')
    ) value(node_key, node_name, node_type, properties_json)
)
INSERT INTO wf_node(
    id, flow_id, node_key, node_name, node_type, executor, timeout_sec, retry_max,
    retry_interval_sec, input_schema, output_schema, properties_json, additional_form_ids,
    field_permissions_json, create_by, create_time, last_update_by, last_update_time)
SELECT gen_random_uuid()::text, flow.id, nodes.node_key, nodes.node_name, nodes.node_type,
       '', 0, 0, 0,
       lo_from_bytea(0, convert_to('{}', 'UTF8')),
       lo_from_bytea(0, convert_to('{}', 'UTF8')),
       lo_from_bytea(0, convert_to(nodes.properties_json, 'UTF8')),
       lo_from_bytea(0, convert_to('[]', 'UTF8')),
       lo_from_bytea(0, convert_to('{"permissions":{},"required":{}}', 'UTF8')),
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flow CROSS JOIN nodes
ON CONFLICT(flow_id, node_key) DO NOTHING;

WITH flow AS (
    SELECT id FROM wf_definition
    WHERE flow_code = 'EDU_GRADE_CHANGE_REVIEW' AND version = 'v1'
)
INSERT INTO wf_edge(
    id, flow_id, from_node_key, to_node_key, condition_expr, is_default,
    create_by, create_time, last_update_by, last_update_time)
SELECT gen_random_uuid()::text, flow.id, edge.from_key, edge.to_key, NULL, true,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM flow
CROSS JOIN (VALUES ('START', 'ACADEMIC_APPROVAL'), ('ACADEMIC_APPROVAL', 'END'))
    edge(from_key, to_key)
WHERE NOT EXISTS (
    SELECT 1 FROM wf_edge existing
    WHERE existing.flow_id = flow.id
      AND existing.from_node_key = edge.from_key
      AND existing.to_node_key = edge.to_key);

INSERT INTO wf_definition_acl(
    id, definition_id, subject_type, subject_id, action, enabled,
    create_by, create_time, last_update_by, last_update_time)
SELECT gen_random_uuid()::text, definition.id, 'ROLE', role.role_code, 'START', true,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM wf_definition definition
CROSS JOIN (VALUES ('EDU_TEACHER'), ('EDU_ADMIN'), ('SUPER_ADMIN')) role(role_code)
WHERE definition.flow_code = 'EDU_GRADE_CHANGE_REVIEW'
  AND definition.version = 'v1'
ON CONFLICT(definition_id, subject_type, subject_id, action) DO NOTHING;

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_TEACHER'
  AND permission.permission_code IN (
      'education:score:gradebook:import',
      'education:score:gradebook:export',
      'education:score:change:request')
ON CONFLICT DO NOTHING;

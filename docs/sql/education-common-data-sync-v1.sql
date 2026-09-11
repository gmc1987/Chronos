-- Chronos -> ChronosEducation 通用平台数据同步 V1（PostgreSQL 17）
-- 必须连接目标库 ChronosEducation 执行，并通过 psql 变量 source_conn 提供源库连接串。
-- 本脚本只同步通用配置，不同步组织、科室、员工档案、流程实例、任务和历史数据。

\set ON_ERROR_STOP on

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('chronos-education-common-data-sync-v1'));

CREATE EXTENSION IF NOT EXISTS dblink;
SELECT dblink_connect('chronos_source', :'source_conn');

-- 教育库当前只有启动种子。先清理关系数据，再替换为源库中的稳定 ID，
-- 避免同一个权限编码在两个库中对应不同 ID，导致后续授权关系错位。
DELETE FROM t_role_menu_permission;
DELETE FROM t_role_permission;
DELETE FROM t_role_menu;
DELETE FROM t_user_role;
DELETE FROM t_role_data_scope;
DELETE FROM t_role;
DELETE FROM t_admin_user;
DELETE FROM t_permission;
DELETE FROM t_menu;

INSERT INTO t_menu (
    id, create_by, create_time, last_update_by, last_update_time,
    menu_name, order_num, parent_id, path
)
SELECT *
FROM dblink(
    'chronos_source',
    'SELECT id, create_by, create_time, last_update_by, last_update_time,
            menu_name, order_num, parent_id, path
       FROM t_menu'
) AS source_menu(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    menu_name varchar, order_num integer, parent_id varchar, path varchar
);

INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time,
    description, permission_code, permission_name, http_method,
    permission_type, resource_pattern, status, menu_id, action_type,
    config_json, resource_type, scope_type, built_in
)
SELECT *
FROM dblink(
    'chronos_source',
    'SELECT id, create_by, create_time, last_update_by, last_update_time,
            description, permission_code, permission_name, http_method,
            permission_type, resource_pattern, status, menu_id, action_type,
            config_json, resource_type, scope_type, built_in
       FROM t_permission'
) AS source_permission(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    description varchar, permission_code varchar, permission_name varchar,
    http_method varchar, permission_type varchar, resource_pattern varchar,
    status integer, menu_id varchar, action_type varchar, config_json text,
    resource_type varchar, scope_type varchar, built_in boolean
);

-- 数据库中的旧权限名称带有医院字样，目标库统一修正为行业无关名称。
UPDATE t_permission
SET permission_name = '组织机构管理'
WHERE permission_code = 'iam:organization:manage';

UPDATE t_permission
SET permission_name = '人员岗位任职管理'
WHERE permission_code = 'iam:directory:manage';

INSERT INTO t_role (
    id, create_by, create_time, last_update_by, last_update_time,
    description, role_name, built_in, role_code, status
)
SELECT *
FROM dblink(
    'chronos_source',
    $$SELECT id, create_by, create_time, last_update_by, last_update_time,
             description,
             CASE WHEN role_code = 'WORKFLOW_DEMO_ADMIN' THEN '流程管理员' ELSE role_name END,
             built_in,
             CASE WHEN role_code = 'WORKFLOW_DEMO_ADMIN' THEN 'WORKFLOW_ADMIN' ELSE role_code END,
             status
        FROM t_role$$
) AS source_role(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    description varchar, role_name varchar, built_in boolean,
    role_code varchar, status integer
);

-- 仅复制平台系统账号。组织和员工引用必须清空，由学校重新建立人员主数据。
INSERT INTO t_admin_user (
    id, create_by, create_time, last_update_by, last_update_time,
    email, password, status, username, avatar_url, display_name,
    organization_id, phone, position_name, account_locked, account_type,
    employee_id, failed_login_attempts, last_login_at, last_login_ip,
    lock_until, must_change_password, password_changed_at, token_version
)
SELECT
    id, create_by, create_time, last_update_by, last_update_time,
    email, password, status, username, avatar_url, display_name,
    NULL, phone, position_name, account_locked, account_type,
    NULL, failed_login_attempts, last_login_at, last_login_ip,
    lock_until, must_change_password, password_changed_at, token_version
FROM dblink(
    'chronos_source',
    $$SELECT id, create_by, create_time, last_update_by, last_update_time,
             email, password, status, username, avatar_url, display_name,
             organization_id, phone, position_name, account_locked, account_type,
             employee_id, failed_login_attempts, last_login_at, last_login_ip,
             lock_until, must_change_password, password_changed_at, token_version
        FROM t_admin_user
       WHERE username IN ('admin', 'wf.admin', '审核1')$$
) AS source_user(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    email varchar, password varchar, status integer, username varchar,
    avatar_url varchar, display_name varchar, organization_id varchar,
    phone varchar, position_name varchar, account_locked boolean,
    account_type varchar, employee_id varchar, failed_login_attempts integer,
    last_login_at timestamp, last_login_ip varchar, lock_until timestamp,
    must_change_password boolean, password_changed_at timestamp,
    token_version integer
);

INSERT INTO t_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM dblink(
    'chronos_source',
    'SELECT role_id, menu_id FROM t_role_menu'
) AS source_relation(role_id varchar, menu_id varchar);

INSERT INTO t_role_permission (permission_id, role_id)
SELECT permission_id, role_id
FROM dblink(
    'chronos_source',
    'SELECT permission_id, role_id FROM t_role_permission'
) AS source_relation(permission_id varchar, role_id varchar);

INSERT INTO t_role_menu_permission (menu_id, permission_id, role_id)
SELECT menu_id, permission_id, role_id
FROM dblink(
    'chronos_source',
    'SELECT menu_id, permission_id, role_id FROM t_role_menu_permission'
) AS source_relation(menu_id varchar, permission_id varchar, role_id varchar);

INSERT INTO t_user_role (user_id, role_id)
SELECT user_id, role_id
FROM dblink(
    'chronos_source',
    $$SELECT relation.user_id, relation.role_id
        FROM t_user_role relation
        JOIN t_admin_user account ON account.id = relation.user_id
        JOIN t_role role ON role.id = relation.role_id
       WHERE account.username IN ('admin', 'wf.admin', '审核1')$$
) AS source_relation(user_id varchar, role_id varchar);

-- 只同步行业、AI 模型和工作流状态字典，排除影视项目等行业业务字典。
DELETE FROM t_dict;
INSERT INTO t_dict (
    id, create_by, create_time, last_update_by, last_update_time,
    dict_code, dict_name, dict_value, parent_id, status
)
SELECT *
FROM dblink(
    'chronos_source',
    $$SELECT id, create_by, create_time, last_update_by, last_update_time,
             dict_code, dict_name, dict_value, parent_id, status
        FROM t_dict
       WHERE dict_code LIKE 'DICT_INDUSTRY%'
          OR dict_code LIKE 'DICT_MODEL%'
          OR dict_code LIKE 'DICT_WORKFLOW%'$$
) AS source_dict(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    dict_code varchar, dict_name varchar, dict_value varchar,
    parent_id varchar, status integer
);

-- 清理可能重复执行时遗留的目标流程模板；不触碰任何运行实例和历史。
WITH template_large_objects AS (
    SELECT field.options_json AS oid
    FROM form_field field
    JOIN form_definition form ON form.id = field.form_id
    WHERE form.form_key IN ('OA_LEAVE', 'OA_TRAVEL', 'OA_PURCHASE')
      AND form.version = 'v1'
    UNION
    SELECT definition.config_json
    FROM wf_definition definition
    WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND definition.version = 'v1'
    UNION
    SELECT definition.starter_scope_json
    FROM wf_definition definition
    WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND definition.version = 'v1'
    UNION
    SELECT node.input_schema
    FROM wf_node node
    JOIN wf_definition definition ON definition.id = node.flow_id
    WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND definition.version = 'v1'
    UNION
    SELECT node.output_schema
    FROM wf_node node
    JOIN wf_definition definition ON definition.id = node.flow_id
    WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND definition.version = 'v1'
    UNION
    SELECT node.properties_json
    FROM wf_node node
    JOIN wf_definition definition ON definition.id = node.flow_id
    WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND definition.version = 'v1'
    UNION
    SELECT node.additional_form_ids
    FROM wf_node node
    JOIN wf_definition definition ON definition.id = node.flow_id
    WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND definition.version = 'v1'
    UNION
    SELECT node.field_permissions_json
    FROM wf_node node
    JOIN wf_definition definition ON definition.id = node.flow_id
    WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND definition.version = 'v1'
)
SELECT lo_unlink(oid)
FROM template_large_objects
WHERE oid IS NOT NULL;

DELETE FROM wf_definition_acl
WHERE definition_id IN (
    SELECT id FROM wf_definition
    WHERE flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND version = 'v1'
);
DELETE FROM wf_edge
WHERE flow_id IN (
    SELECT id FROM wf_definition
    WHERE flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND version = 'v1'
);
DELETE FROM wf_node
WHERE flow_id IN (
    SELECT id FROM wf_definition
    WHERE flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
      AND version = 'v1'
);
DELETE FROM wf_definition
WHERE flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
  AND version = 'v1';

DELETE FROM form_field
WHERE form_id IN (
    SELECT id FROM form_definition
    WHERE form_key IN ('OA_LEAVE', 'OA_TRAVEL', 'OA_PURCHASE')
      AND version = 'v1'
);
DELETE FROM form_definition
WHERE form_key IN ('OA_LEAVE', 'OA_TRAVEL', 'OA_PURCHASE')
  AND version = 'v1';

INSERT INTO form_definition (
    id, create_by, create_time, last_update_by, last_update_time,
    description, form_key, form_name, status, version, published_at
)
SELECT *
FROM dblink(
    'chronos_source',
    $$SELECT id, create_by, create_time, last_update_by, last_update_time,
             description, form_key, form_name, status, version, published_at
        FROM form_definition
       WHERE form_key IN ('OA_LEAVE', 'OA_TRAVEL', 'OA_PURCHASE')
         AND version = 'v1'$$
) AS source_form(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    description varchar, form_key varchar, form_name varchar,
    status varchar, version varchar, published_at timestamp
);

INSERT INTO form_field (
    id, create_by, create_time, last_update_by, last_update_time,
    field_key, field_label, field_type, form_id, options_json,
    required, sort_order
)
SELECT
    id, create_by, create_time, last_update_by, last_update_time,
    field_key, field_label, field_type, form_id,
    CASE WHEN options_bytes IS NULL THEN NULL ELSE lo_from_bytea(0, options_bytes) END,
    required, sort_order
FROM dblink(
    'chronos_source',
    $$SELECT field.id, field.create_by, field.create_time,
             field.last_update_by, field.last_update_time,
             field.field_key, field.field_label, field.field_type, field.form_id,
             CASE WHEN field.options_json IS NULL THEN NULL ELSE lo_get(field.options_json) END,
             field.required, field.sort_order
        FROM form_field field
        JOIN form_definition form ON form.id = field.form_id
       WHERE form.form_key IN ('OA_LEAVE', 'OA_TRAVEL', 'OA_PURCHASE')
         AND form.version = 'v1'$$
) AS source_field(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    field_key varchar, field_label varchar, field_type varchar, form_id varchar,
    options_bytes bytea, required boolean, sort_order integer
);

-- 流程只作为草稿导入。Flowable deployment ID 属于源库，绝不能跨库复用。
INSERT INTO wf_definition (
    id, create_by, create_time, last_update_by, last_update_time,
    ai_assist_enabled, config_json, description, entry_node_key, flow_name,
    published_at, status, tags, version, main_form_id, category, flow_code,
    manager_user, starter_scope_json, flowable_deployment_id, flowable_process_key
)
SELECT
    id, create_by, create_time, last_update_by, last_update_time,
    ai_assist_enabled,
    CASE WHEN config_bytes IS NULL THEN NULL ELSE lo_from_bytea(0, config_bytes) END,
    description, entry_node_key, flow_name,
    NULL, 'DRAFT', tags, version, main_form_id, category, flow_code,
    manager_user,
    CASE WHEN starter_scope_bytes IS NULL THEN NULL ELSE lo_from_bytea(0, starter_scope_bytes) END,
    NULL, NULL
FROM dblink(
    'chronos_source',
    $$SELECT id, create_by, create_time, last_update_by, last_update_time,
             ai_assist_enabled,
             CASE WHEN config_json IS NULL THEN NULL ELSE lo_get(config_json) END,
             description, entry_node_key, flow_name, published_at, status,
             tags, version, main_form_id, category, flow_code, manager_user,
             CASE WHEN starter_scope_json IS NULL THEN NULL ELSE lo_get(starter_scope_json) END,
             flowable_deployment_id, flowable_process_key
        FROM wf_definition
       WHERE flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
         AND version = 'v1'$$
) AS source_definition(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    ai_assist_enabled boolean, config_bytes bytea, description varchar,
    entry_node_key varchar, flow_name varchar, published_at timestamp,
    status varchar, tags varchar, version varchar, main_form_id varchar,
    category varchar, flow_code varchar, manager_user varchar,
    starter_scope_bytes bytea, flowable_deployment_id varchar,
    flowable_process_key varchar
);

INSERT INTO wf_node (
    id, create_by, create_time, last_update_by, last_update_time,
    executor, flow_id, input_schema, node_key, node_name, node_type,
    output_schema, properties_json, retry_interval_sec, retry_max,
    timeout_sec, additional_form_ids, field_permissions_json
)
SELECT
    id, create_by, create_time, last_update_by, last_update_time,
    executor, flow_id,
    CASE WHEN input_bytes IS NULL THEN NULL ELSE lo_from_bytea(0, input_bytes) END,
    node_key, node_name, node_type,
    CASE WHEN output_bytes IS NULL THEN NULL ELSE lo_from_bytea(0, output_bytes) END,
    CASE WHEN properties_bytes IS NULL THEN NULL ELSE lo_from_bytea(0, properties_bytes) END,
    retry_interval_sec, retry_max, timeout_sec,
    CASE WHEN additional_forms_bytes IS NULL THEN NULL ELSE lo_from_bytea(0, additional_forms_bytes) END,
    CASE WHEN field_permissions_bytes IS NULL THEN NULL ELSE lo_from_bytea(0, field_permissions_bytes) END
FROM dblink(
    'chronos_source',
    $$SELECT node.id, node.create_by, node.create_time,
             node.last_update_by, node.last_update_time,
             node.executor, node.flow_id,
             CASE WHEN node.input_schema IS NULL THEN NULL ELSE lo_get(node.input_schema) END,
             node.node_key, node.node_name, node.node_type,
             CASE WHEN node.output_schema IS NULL THEN NULL ELSE lo_get(node.output_schema) END,
             CASE WHEN node.properties_json IS NULL THEN NULL ELSE lo_get(node.properties_json) END,
             node.retry_interval_sec, node.retry_max, node.timeout_sec,
             CASE WHEN node.additional_form_ids IS NULL THEN NULL ELSE lo_get(node.additional_form_ids) END,
             CASE WHEN node.field_permissions_json IS NULL THEN NULL ELSE lo_get(node.field_permissions_json) END
        FROM wf_node node
        JOIN wf_definition definition ON definition.id = node.flow_id
       WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
         AND definition.version = 'v1'$$
) AS source_node(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    executor varchar, flow_id varchar, input_bytes bytea,
    node_key varchar, node_name varchar, node_type varchar,
    output_bytes bytea, properties_bytes bytea,
    retry_interval_sec integer, retry_max integer, timeout_sec integer,
    additional_forms_bytes bytea, field_permissions_bytes bytea
);

INSERT INTO wf_edge (
    id, create_by, create_time, last_update_by, last_update_time,
    condition_expr, flow_id, from_node_key, is_default, to_node_key
)
SELECT *
FROM dblink(
    'chronos_source',
    $$SELECT edge.id, edge.create_by, edge.create_time,
             edge.last_update_by, edge.last_update_time,
             edge.condition_expr, edge.flow_id, edge.from_node_key,
             edge.is_default, edge.to_node_key
        FROM wf_edge edge
        JOIN wf_definition definition ON definition.id = edge.flow_id
       WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
         AND definition.version = 'v1'$$
) AS source_edge(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    condition_expr varchar, flow_id varchar, from_node_key varchar,
    is_default boolean, to_node_key varchar
);

INSERT INTO wf_definition_acl (
    id, create_by, create_time, last_update_by, last_update_time,
    action, definition_id, enabled, subject_id, subject_type
)
SELECT *
FROM dblink(
    'chronos_source',
    $$SELECT acl.id, acl.create_by, acl.create_time,
             acl.last_update_by, acl.last_update_time,
             acl.action, acl.definition_id, acl.enabled,
             acl.subject_id, acl.subject_type
        FROM wf_definition_acl acl
        JOIN wf_definition definition ON definition.id = acl.definition_id
       WHERE definition.flow_code IN ('OA_LEAVE_APPROVAL', 'OA_TRAVEL_APPROVAL', 'OA_PURCHASE_APPROVAL')
         AND definition.version = 'v1'$$
) AS source_acl(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    action varchar, definition_id varchar, enabled boolean,
    subject_id varchar, subject_type varchar
);

DELETE FROM wf_ai_setting;
INSERT INTO wf_ai_setting (
    id, create_by, create_time, last_update_by, last_update_time,
    allow_external, enabled, mask_sensitive_data, provider_mode
)
SELECT
    id, create_by, create_time, last_update_by, last_update_time,
    false, false, true, provider_mode
FROM dblink(
    'chronos_source',
    'SELECT id, create_by, create_time, last_update_by, last_update_time,
            allow_external, enabled, mask_sensitive_data, provider_mode
       FROM wf_ai_setting'
) AS source_ai_setting(
    id varchar, create_by varchar, create_time timestamp,
    last_update_by varchar, last_update_time timestamp,
    allow_external boolean, enabled boolean,
    mask_sensitive_data boolean, provider_mode varchar
);

CREATE TABLE IF NOT EXISTS sys_industry_data_sync_log (
    id bigserial PRIMARY KEY,
    source_database varchar(64) NOT NULL,
    target_database varchar(64) NOT NULL,
    sync_scope varchar(128) NOT NULL,
    sync_time timestamp NOT NULL DEFAULT current_timestamp,
    details jsonb NOT NULL
);

INSERT INTO sys_industry_data_sync_log (
    source_database, target_database, sync_scope, details
)
VALUES (
    'Chronos',
    'ChronosEducation',
    'COMMON_PLATFORM_V1',
    jsonb_build_object(
        'users', (SELECT count(*) FROM t_admin_user),
        'roles', (SELECT count(*) FROM t_role),
        'menus', (SELECT count(*) FROM t_menu),
        'permissions', (SELECT count(*) FROM t_permission),
        'forms', (SELECT count(*) FROM form_definition),
        'workflowDefinitions', (SELECT count(*) FROM wf_definition)
    )
);

SELECT dblink_disconnect('chronos_source');
COMMIT;

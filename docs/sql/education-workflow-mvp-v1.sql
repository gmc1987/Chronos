-- 智慧校园教师请假、学生请假、调课审批 MVP。
-- 调课闭环字段和幂等落地记录由显式 DDL 管理，生产环境不依赖 Hibernate 自动改表。
ALTER TABLE IF EXISTS edu_schedule_entry
    ADD COLUMN IF NOT EXISTS substitute_teacher_id varchar(64);

ALTER TABLE IF EXISTS edu_schedule_entry
    ADD COLUMN IF NOT EXISTS source_adjustment_instance_id varchar(64);

CREATE TABLE IF NOT EXISTS edu_course_adjustment_record (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp,
    lock_version bigint DEFAULT 0,
    workflow_instance_id varchar(64) NOT NULL,
    business_key varchar(128),
    schedule_entry_id varchar(64) NOT NULL,
    adjustment_type varchar(24) NOT NULL,
    result_entry_id varchar(64),
    status varchar(24) NOT NULL,
    message varchar(1000),
	request_payload text,
	retry_count integer NOT NULL DEFAULT 0,
	last_retry_by varchar(100),
    CONSTRAINT uk_edu_adjustment_workflow_instance UNIQUE (workflow_instance_id)
);

-- 兼容已经执行过早期脚本的环境，显式补齐事故重放字段。
ALTER TABLE IF EXISTS edu_course_adjustment_record
    ADD COLUMN IF NOT EXISTS request_payload text;

ALTER TABLE IF EXISTS edu_course_adjustment_record
    ADD COLUMN IF NOT EXISTS retry_count integer NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS edu_course_adjustment_record
    ADD COLUMN IF NOT EXISTS last_retry_by varchar(100);

CREATE TABLE IF NOT EXISTS edu_leave_request (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128),
    create_time timestamp,
    last_update_by varchar(128),
    last_update_time timestamp,
    lock_version bigint DEFAULT 0,
    workflow_instance_id varchar(64) NOT NULL,
    business_key varchar(128),
    applicant_type varchar(16) NOT NULL,
    applicant_id varchar(64) NOT NULL,
    leave_type varchar(32) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    reason varchar(1000) NOT NULL,
    status varchar(24) NOT NULL,
    approved_by varchar(128),
    CONSTRAINT uk_edu_leave_workflow_instance UNIQUE (workflow_instance_id)
);
-- 初始化为已发布流程；应用启动时由 WorkflowDeploymentRecoveryService 补齐 Flowable 部署。

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('education-workflow-mvp-v1'));

CREATE OR REPLACE FUNCTION pg_temp.education_lob(value text)
RETURNS oid
LANGUAGE sql
AS $$ SELECT lo_from_bytea(0, convert_to(value, 'UTF8')) $$;

INSERT INTO t_role (
    id, role_name, role_code, status, built_in, description,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, value.role_name, value.role_code,
    1, true, value.description,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('班主任', 'EDU_CLASS_ADVISOR', '审批学生请假'),
    ('系部审批人', 'EDU_DEPARTMENT_APPROVER', '审批教师请假'),
    ('教务审批人', 'EDU_ACADEMIC_APPROVER', '审批调课申请')
) AS value(role_name, role_code, description)
ON CONFLICT (role_code) DO UPDATE SET
    role_name = EXCLUDED.role_name,
    status = 1,
    description = EXCLUDED.description,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN (
      'EDU_CLASS_ADVISOR',
      'EDU_DEPARTMENT_APPROVER',
      'EDU_ACADEMIC_APPROVER'
  )
  AND permission.permission_code IN (
      'workflow:use',
      'workflow:instance:view',
      'workflow:task:approve',
      'workflow:task:reject',
      'workflow:task:return'
  )
  AND NOT EXISTS (
      SELECT 1 FROM t_role_permission existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

-- 演示环境让 admin 同时具备三类审批候选身份；生产可在角色授权中移除并改配真实人员。
INSERT INTO t_user_role (user_id, role_id)
SELECT account.id, role.id
FROM t_admin_user account
CROSS JOIN t_role role
WHERE account.username = 'admin'
  AND role.role_code IN (
      'EDU_CLASS_ADVISOR',
      'EDU_DEPARTMENT_APPROVER',
      'EDU_ACADEMIC_APPROVER'
  )
  AND NOT EXISTS (
      SELECT 1 FROM t_user_role existing
      WHERE existing.user_id = account.id
        AND existing.role_id = role.id
  );

INSERT INTO form_definition (
    id, form_key, form_name, version, status, description, published_at,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, value.form_key, value.form_name, 'v1',
    'PUBLISHED', value.description, CURRENT_TIMESTAMP,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('EDU_TEACHER_LEAVE', '教师请假申请单', '教师请假审批主表单'),
    ('EDU_TEACHER_LEAVE_DEPARTMENT_REVIEW', '教师请假系部核验单', '系部审批节点补充核验信息'),
    ('EDU_STUDENT_LEAVE', '学生请假申请单', '学生请假审批主表单'),
    ('EDU_COURSE_ADJUSTMENT', '调课申请单', '调课审批与课表回写主表单')
) AS value(form_key, form_name, description)
ON CONFLICT (form_key, version) DO UPDATE SET
    form_name = EXCLUDED.form_name,
    status = 'PUBLISHED',
    description = EXCLUDED.description,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

WITH field_definition AS (
    SELECT *
    FROM (VALUES
        ('EDU_TEACHER_LEAVE', 'leaveType', '请假类型', 'SELECT', 10, true,
            '{"source":"DICTIONARY","dictCode":"EDU_LEAVE_TYPE"}'),
        ('EDU_TEACHER_LEAVE', 'startDate', '开始日期', 'DATE', 20, true, '[]'),
        ('EDU_TEACHER_LEAVE', 'endDate', '结束日期', 'DATE', 30, true, '[]'),
		('EDU_TEACHER_LEAVE', 'substituteTeacherId', '代课教师', 'USER', 40, false, '[]'),
		('EDU_TEACHER_LEAVE', 'reason', '请假事由', 'TEXTAREA', 50, true, '[]'),
		('EDU_TEACHER_LEAVE', 'attachments', '相关附件', 'FILE', 60, false, '[]'),
        ('EDU_TEACHER_LEAVE_DEPARTMENT_REVIEW', 'handoverVerified', '工作交接已核验', 'BOOLEAN', 10, false, '[]'),
        ('EDU_TEACHER_LEAVE_DEPARTMENT_REVIEW', 'reviewOpinion', '系部核验意见', 'TEXTAREA', 20, true, '[]'),
        ('EDU_TEACHER_LEAVE_DEPARTMENT_REVIEW', 'schedulingImpact', '教学安排影响说明', 'TEXTAREA', 30, false, '[]'),
        ('EDU_STUDENT_LEAVE', 'studentId', '学生', 'STUDENT', 10, true, '[]'),
        ('EDU_STUDENT_LEAVE', 'classId', '行政班', 'CLASS', 20, true, '[]'),
        ('EDU_STUDENT_LEAVE', 'leaveType', '请假类型', 'SELECT', 30, true,
            '{"source":"DICTIONARY","dictCode":"EDU_LEAVE_TYPE"}'),
        ('EDU_STUDENT_LEAVE', 'startDate', '开始日期', 'DATE', 40, true, '[]'),
        ('EDU_STUDENT_LEAVE', 'endDate', '结束日期', 'DATE', 50, true, '[]'),
		('EDU_STUDENT_LEAVE', 'guardianConfirmed', '监护人已确认', 'BOOLEAN', 60, true, '[]'),
		('EDU_STUDENT_LEAVE', 'reason', '请假事由', 'TEXTAREA', 70, true, '[]'),
		('EDU_STUDENT_LEAVE', 'attachments', '相关附件', 'FILE', 80, false, '[]'),
        ('EDU_COURSE_ADJUSTMENT', 'scheduleEntryId', '原课表项', 'SCHEDULE', 10, true, '[]'),
        ('EDU_COURSE_ADJUSTMENT', 'adjustmentType', '调整类型', 'SELECT', 20, true,
            '{"source":"DICTIONARY","dictCode":"EDU_COURSE_ADJUSTMENT_TYPE"}'),
        ('EDU_COURSE_ADJUSTMENT', 'targetDayOfWeek', '目标星期', 'NUMBER', 30, false, '[]'),
        ('EDU_COURSE_ADJUSTMENT', 'targetPeriodNo', '目标节次', 'NUMBER', 40, false, '[]'),
        ('EDU_COURSE_ADJUSTMENT', 'targetClassroomId', '目标教室', 'CLASSROOM', 50, false, '[]'),
		('EDU_COURSE_ADJUSTMENT', 'substituteTeacherId', '代课教师', 'USER', 60, false, '[]'),
		('EDU_COURSE_ADJUSTMENT', 'reason', '调整原因', 'TEXTAREA', 70, true, '[]'),
		('EDU_COURSE_ADJUSTMENT', 'attachments', '相关附件', 'FILE', 80, false, '[]')
    ) AS value(form_key, field_key, field_label, field_type, sort_order, required, options_json)
)
INSERT INTO form_field (
    id, form_id, field_key, field_label, field_type, sort_order,
    required, options_json, create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, form.id,
    field.field_key, field.field_label, field.field_type, field.sort_order,
    field.required, pg_temp.education_lob(field.options_json),
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM field_definition field
JOIN form_definition form
  ON form.form_key = field.form_key
 AND form.version = 'v1'
ON CONFLICT (form_id, field_key) DO UPDATE SET
    field_label = EXCLUDED.field_label,
    field_type = EXCLUDED.field_type,
    sort_order = EXCLUDED.sort_order,
    required = EXCLUDED.required,
    options_json = EXCLUDED.options_json,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

WITH workflow_definition AS (
    SELECT *
    FROM (VALUES
        ('EDU_TEACHER_LEAVE_APPROVAL', '教师请假审批', '教师请假由系部审批人办理', 'EDU_TEACHER_LEAVE'),
        ('EDU_STUDENT_LEAVE_APPROVAL', '学生请假审批', '学生请假由班主任办理', 'EDU_STUDENT_LEAVE'),
        ('EDU_COURSE_ADJUSTMENT_APPROVAL', '调课审批', '调课申请由教务审批人办理', 'EDU_COURSE_ADJUSTMENT')
    ) AS value(flow_code, flow_name, description, form_key)
)
INSERT INTO wf_definition (
    id, flow_code, flow_name, category, version, description,
    entry_node_key, status, tags, config_json, main_form_id,
    manager_user, starter_scope_json, ai_assist_enabled, published_at,
    flowable_deployment_id, flowable_process_key,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, workflow.flow_code, workflow.flow_name,
    'EDUCATION', 'v1', workflow.description,
    'start', 'PUBLISHED', '教育,教务,MVP', pg_temp.education_lob('{}'),
    form.id, 'admin', pg_temp.education_lob('{"type":"ALL"}'),
    false, CURRENT_TIMESTAMP, NULL, NULL,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM workflow_definition workflow
JOIN form_definition form
  ON form.form_key = workflow.form_key
 AND form.version = 'v1'
ON CONFLICT (flow_code, version) DO UPDATE SET
    flow_name = EXCLUDED.flow_name,
    description = EXCLUDED.description,
    status = 'PUBLISHED',
    main_form_id = EXCLUDED.main_form_id,
    entry_node_key = 'start',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 节点属性中明确审批角色。节点不存在时插入，已存在时只更新业务配置。
WITH node_definition AS (
    SELECT *
    FROM (VALUES
        ('EDU_TEACHER_LEAVE_APPROVAL', 'start', '开始', 'START', NULL, 80, 160),
        ('EDU_TEACHER_LEAVE_APPROVAL', 'departmentApproval', '系部审批', 'APPROVAL', 'EDU_DEPARTMENT_APPROVER', 330, 160),
        ('EDU_TEACHER_LEAVE_APPROVAL', 'end', '结束', 'END', NULL, 580, 160),
        ('EDU_STUDENT_LEAVE_APPROVAL', 'start', '开始', 'START', NULL, 80, 160),
        ('EDU_STUDENT_LEAVE_APPROVAL', 'advisorApproval', '班主任审批', 'APPROVAL', 'EDU_CLASS_ADVISOR', 330, 160),
        ('EDU_STUDENT_LEAVE_APPROVAL', 'end', '结束', 'END', NULL, 580, 160),
        ('EDU_COURSE_ADJUSTMENT_APPROVAL', 'start', '开始', 'START', NULL, 80, 160),
        ('EDU_COURSE_ADJUSTMENT_APPROVAL', 'academicApproval', '教务审批', 'APPROVAL', 'EDU_ACADEMIC_APPROVER', 330, 160),
        ('EDU_COURSE_ADJUSTMENT_APPROVAL', 'end', '结束', 'END', NULL, 580, 160)
    ) AS value(flow_code, node_key, node_name, node_type, role_code, x, y)
), desired AS (
    SELECT
        workflow.id AS flow_id,
        node.node_key,
        node.node_name,
        node.node_type,
        CASE
            WHEN node.role_code IS NULL THEN jsonb_build_object(
                'position', jsonb_build_object('x', node.x, 'y', node.y)
            )::text
            ELSE jsonb_build_object(
                'position', jsonb_build_object('x', node.x, 'y', node.y),
                'assigneeMode', 'ROLE',
                'assigneeValue', node.role_code,
                'approvalMode', 'SINGLE',
                'dueHours', 24,
                'returnPolicy', 'PREVIOUS'
            )::text
        END AS properties_json
    FROM node_definition node
    JOIN wf_definition workflow
      ON workflow.flow_code = node.flow_code
     AND workflow.version = 'v1'
)
INSERT INTO wf_node (
    id, flow_id, node_key, node_name, node_type, executor,
    timeout_sec, retry_max, retry_interval_sec,
    input_schema, output_schema, properties_json,
    additional_form_ids, field_permissions_json,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, desired.flow_id, desired.node_key,
    desired.node_name, desired.node_type, '', 0, 0, 0,
    pg_temp.education_lob('{}'), pg_temp.education_lob('{}'),
    pg_temp.education_lob(desired.properties_json),
    pg_temp.education_lob('[]'),
    pg_temp.education_lob('{"permissions":{},"required":{}}'),
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM desired
ON CONFLICT (flow_id, node_key) DO UPDATE SET
    node_name = EXCLUDED.node_name,
    node_type = EXCLUDED.node_type,
    properties_json = EXCLUDED.properties_json,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 教师请假在系部审批节点展示独立核验单。权限键包含表单 ID，避免不同表单同名字段互相覆盖。
WITH target AS (
    SELECT
        node.id AS node_id,
        main_form.id AS main_form_id,
        review_form.id AS review_form_id
    FROM wf_node node
    JOIN wf_definition workflow
      ON workflow.id = node.flow_id
     AND workflow.flow_code = 'EDU_TEACHER_LEAVE_APPROVAL'
     AND workflow.version = 'v1'
    JOIN form_definition main_form
      ON main_form.form_key = 'EDU_TEACHER_LEAVE'
     AND main_form.version = 'v1'
    JOIN form_definition review_form
      ON review_form.form_key = 'EDU_TEACHER_LEAVE_DEPARTMENT_REVIEW'
     AND review_form.version = 'v1'
    WHERE node.node_key = 'departmentApproval'
), permission_definition AS (
    SELECT
        target.node_id,
        target.review_form_id,
        jsonb_object_agg(
            field.form_id || '.' || field.field_key,
            CASE
                WHEN field.form_id = target.review_form_id THEN 'EDIT'
                ELSE 'READ'
            END
        ) AS permissions,
        jsonb_object_agg(
            field.form_id || '.' || field.field_key,
            field.form_id = target.review_form_id
                AND field.field_key = 'reviewOpinion'
        ) AS required_fields
    FROM target
    JOIN form_field field
      ON field.form_id IN (target.main_form_id, target.review_form_id)
    GROUP BY target.node_id, target.review_form_id
)
UPDATE wf_node node
SET additional_form_ids = pg_temp.education_lob(
        jsonb_build_array(permission.review_form_id)::text
    ),
    field_permissions_json = pg_temp.education_lob(
        jsonb_build_object(
            'permissions', permission.permissions,
            'required', permission.required_fields
        )::text
    ),
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM permission_definition permission
WHERE node.id = permission.node_id;

DELETE FROM wf_edge edge
USING wf_definition workflow
WHERE edge.flow_id = workflow.id
  AND workflow.flow_code IN (
      'EDU_TEACHER_LEAVE_APPROVAL',
      'EDU_STUDENT_LEAVE_APPROVAL',
      'EDU_COURSE_ADJUSTMENT_APPROVAL'
  )
  AND workflow.version = 'v1';

WITH edge_definition AS (
    SELECT *
    FROM (VALUES
        ('EDU_TEACHER_LEAVE_APPROVAL', 'start', 'departmentApproval'),
        ('EDU_TEACHER_LEAVE_APPROVAL', 'departmentApproval', 'end'),
        ('EDU_STUDENT_LEAVE_APPROVAL', 'start', 'advisorApproval'),
        ('EDU_STUDENT_LEAVE_APPROVAL', 'advisorApproval', 'end'),
        ('EDU_COURSE_ADJUSTMENT_APPROVAL', 'start', 'academicApproval'),
        ('EDU_COURSE_ADJUSTMENT_APPROVAL', 'academicApproval', 'end')
    ) AS value(flow_code, from_node_key, to_node_key)
)
INSERT INTO wf_edge (
    id, flow_id, from_node_key, to_node_key, condition_expr, is_default,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, workflow.id,
    edge.from_node_key, edge.to_node_key, '', false,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM edge_definition edge
JOIN wf_definition workflow
  ON workflow.flow_code = edge.flow_code
 AND workflow.version = 'v1';

COMMIT;

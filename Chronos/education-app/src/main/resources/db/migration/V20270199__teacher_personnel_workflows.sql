CREATE TABLE edu_teacher_personnel_case (
    instance_id varchar(64) PRIMARY KEY REFERENCES wf_instance(id),
    employee_id varchar(64) NOT NULL,
    employee_code varchar(64) NOT NULL,
    employee_name varchar(128) NOT NULL,
    organization_id varchar(64) NOT NULL,
    target_organization_id varchar(64),
    change_type varchar(24) NOT NULL,
    effective_date date NOT NULL,
    reason text NOT NULL,
    applicant varchar(128) NOT NULL,
    reviewer varchar(128),
    status varchar(24) NOT NULL DEFAULT 'REVIEWING',
    error_message text,
    created_at timestamp NOT NULL DEFAULT current_timestamp,
    applied_at timestamp
);
CREATE INDEX idx_teacher_personnel_due ON edu_teacher_personnel_case(status, effective_date);
CREATE INDEX idx_teacher_personnel_org ON edu_teacher_personnel_case(organization_id, created_at DESC);
CREATE INDEX idx_teacher_personnel_applicant ON edu_teacher_personnel_case(applicant, created_at DESC);

UPDATE t_menu SET path='/admin/education/teacher-personnel'
WHERE id='2427bf1b-5026-4182-a09f-d31b3d61665a' OR (menu_name='教师业务' AND coalesce(path,'')='');

INSERT INTO t_role(id, role_code, role_name, description, create_by, create_time, built_in, status)
VALUES (md5('EDU_TEACHER_PERSONNEL_REVIEWER'), 'EDU_TEACHER_PERSONNEL_REVIEWER', '教师人事审核员',
        '审核教师入职、离职和任职异动，按机构数据范围授权', 'SYSTEM', current_timestamp, true, 1)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code='EDU_TEACHER_PERSONNEL_REVIEWER'
  AND p.permission_code IN ('workflow:task:claim','workflow:task:approve','workflow:task:reject','workflow:instance:view')
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE teacher_personnel_flow_seed (suffix text, title text) ON COMMIT DROP;
INSERT INTO teacher_personnel_flow_seed VALUES
('ONBOARD','教师入职'),('TERMINATE','教师离职'),('TRANSFER','教师调动'),('SUSPEND','教师停职'),('RESUME','教师复职');

INSERT INTO form_definition(id,form_key,form_name,version,status,description,published_at,create_by,create_time)
SELECT md5('teacher-personnel-form:'||suffix),'EDU_TEACHER_PERSONNEL_'||suffix,title,'v1','PUBLISHED',
       '审批通过且到达生效日期后执行。',current_timestamp,'SYSTEM',current_timestamp
FROM teacher_personnel_flow_seed;

WITH fields(field_key,field_label,field_type,sort_order,required) AS (
  VALUES ('employeeCode','教职工工号（本人可留空）','TEXT',10,false),
         ('organizationCode','学校编码（按主聘可留空）','TEXT',20,false),
         ('effectiveDate','生效日期','DATE',40,true),
         ('reason','申请说明','TEXTAREA',50,true)
)
INSERT INTO form_field(id,form_id,field_key,field_label,field_type,sort_order,required,options_json,create_by,create_time)
SELECT md5('teacher-personnel-field:'||suffix||':'||field_key),md5('teacher-personnel-form:'||suffix),
       field_key,field_label,field_type,sort_order,required,lo_from_bytea(0,convert_to('[]','UTF8')),'SYSTEM',current_timestamp
FROM teacher_personnel_flow_seed CROSS JOIN fields
UNION ALL
SELECT md5('teacher-personnel-field:TRANSFER:targetOrganizationCode'),md5('teacher-personnel-form:TRANSFER'),
       'targetOrganizationCode','调入学校编码','TEXT',30,true,lo_from_bytea(0,convert_to('[]','UTF8')),'SYSTEM',current_timestamp;

INSERT INTO wf_definition(id,flow_code,flow_name,category,version,description,entry_node_key,status,tags,
    config_json,main_form_id,manager_user,starter_scope_json,ai_assist_enabled,published_at,create_by,create_time)
SELECT md5('teacher-personnel-flow:'||suffix),'EDU_TEACHER_PERSONNEL_'||suffix,title||'审批','EDUCATION','v1',
       '人事申请须经异人审批，通过后按生效日期执行。','start','PUBLISHED','教育,教师人事',
       lo_from_bytea(0,convert_to('{}','UTF8')),md5('teacher-personnel-form:'||suffix),'admin',
       lo_from_bytea(0,convert_to('{"type":"ALL"}','UTF8')),false,current_timestamp,'SYSTEM',current_timestamp
FROM teacher_personnel_flow_seed;

WITH nodes(node_key,node_name,node_type,properties_json) AS (
  VALUES ('start','开始','START','{"position":{"x":80,"y":160}}'),
         ('approval','人事审核','APPROVAL','{"position":{"x":330,"y":160},"assigneeMode":"FORM_FIELD","assigneeValue":"personnelReviewers","approvalMode":"SINGLE","dueHours":24}'),
         ('end','结束','END','{"position":{"x":580,"y":160}}')
)
INSERT INTO wf_node(id,flow_id,node_key,node_name,node_type,executor,timeout_sec,retry_max,retry_interval_sec,
    input_schema,output_schema,properties_json,additional_form_ids,field_permissions_json,create_by,create_time)
SELECT md5('teacher-personnel-node:'||suffix||':'||node_key),md5('teacher-personnel-flow:'||suffix),
       node_key,node_name,node_type,'',0,0,0,lo_from_bytea(0,convert_to('{}','UTF8')),
       lo_from_bytea(0,convert_to('{}','UTF8')),lo_from_bytea(0,convert_to(properties_json,'UTF8')),
       lo_from_bytea(0,convert_to('[]','UTF8')),lo_from_bytea(0,convert_to('{"permissions":{},"required":{}}','UTF8')),
       'SYSTEM',current_timestamp
FROM teacher_personnel_flow_seed CROSS JOIN nodes;

INSERT INTO wf_edge(id,flow_id,from_node_key,to_node_key,condition_expr,is_default,create_by,create_time)
SELECT md5('teacher-personnel-edge:'||suffix||':'||from_key),md5('teacher-personnel-flow:'||suffix),
       from_key,to_key,'',true,'SYSTEM',current_timestamp
FROM teacher_personnel_flow_seed CROSS JOIN (VALUES ('start','approval'),('approval','end')) e(from_key,to_key);

INSERT INTO wf_definition_acl(id,definition_id,subject_type,subject_id,action,enabled,create_by,create_time)
SELECT md5('teacher-personnel-acl:'||suffix||':'||role_code),md5('teacher-personnel-flow:'||suffix),
       'ROLE',role_code,'START',true,'SYSTEM',current_timestamp
FROM teacher_personnel_flow_seed
CROSS JOIN (VALUES ('SUPER_ADMIN'),('ROLE_PLATFORM_ADMIN'),('EDU_ADMIN'),('EDU_TEACHER')) r(role_code)
WHERE role_code <> 'EDU_TEACHER' OR suffix='TERMINATE'
ON CONFLICT DO NOTHING;

INSERT INTO t_portal_application(id,create_by,create_time,app_code,app_name,description,enabled,icon,open_mode,
    recommended,route_path,sort_order,required_permission,presentation_mode,native_feature_code)
VALUES (md5('EDU_TEACHER_PERSONNEL'),'SYSTEM',current_timestamp,'EDU_TEACHER_PERSONNEL','教师人事',
        '办理教师任职业务并查询申请进度',true,'User','INTERNAL',true,'/portal/education/teacher-personnel',49,
        'workflow:instance:start','NATIVE','TEACHER_PERSONNEL')
ON CONFLICT (app_code) DO NOTHING;

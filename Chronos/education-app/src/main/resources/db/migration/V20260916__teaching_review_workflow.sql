CREATE TABLE IF NOT EXISTS edu_teaching_review_record (
 id varchar(64) PRIMARY KEY, resource_type varchar(32) NOT NULL, resource_id varchar(64) NOT NULL,
 offering_id varchar(64), business_key varchar(160) NOT NULL, workflow_instance_id varchar(64) NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'SUBMITTED', decision varchar(24), comment varchar(2000),
 create_by varchar(128), create_time timestamp, last_update_by varchar(128), last_update_time timestamp,
 CONSTRAINT uq_teaching_review_resource UNIQUE(resource_type, resource_id),
 CONSTRAINT uq_teaching_review_workflow UNIQUE(workflow_instance_id)
);
CREATE INDEX IF NOT EXISTS idx_teaching_review_workflow ON edu_teaching_review_record(workflow_instance_id);

-- Idempotent seed; the workflow engine remains the only approval state machine.
INSERT INTO form_definition(id, form_key, form_name, version, status, description, published_at,
 create_by, create_time, last_update_by, last_update_time)
SELECT gen_random_uuid()::text, 'EDU_TEACHING_CONTENT', '教学内容审核单', 'v1', 'PUBLISHED',
 '教学计划、教案、备课、课件、材料、题库及教研资源审核', CURRENT_TIMESTAMP,
 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM form_definition WHERE form_key='EDU_TEACHING_CONTENT' AND version='v1');
INSERT INTO form_field(id, form_id, field_key, field_label, field_type, sort_order, required, options_json,
 create_by, create_time, last_update_by, last_update_time)
SELECT gen_random_uuid()::text, f.id, v.field_key, v.field_label, v.field_type, v.sort_order, v.required,
 lo_from_bytea(0,convert_to(v.options_json,'UTF8')), 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM form_definition f CROSS JOIN (VALUES
 ('resourceType','资源类型','TEXT',10,true,'[]'),('resourceId','资源 ID','TEXT',20,true,'[]'),
 ('offeringId','教学班','TEXT',30,false,'[]'),('submitComment','提交说明','TEXTAREA',40,false,'[]'),
 ('attachments','附件/文件引用','FILE',50,false,'[]')) v(field_key,field_label,field_type,sort_order,required,options_json)
WHERE f.form_key='EDU_TEACHING_CONTENT' AND f.version='v1'
ON CONFLICT (form_id,field_key) DO UPDATE SET required=EXCLUDED.required, field_type=EXCLUDED.field_type;
INSERT INTO wf_definition(id,flow_code,flow_name,category,version,description,entry_node_key,status,tags,
 config_json,main_form_id,manager_user,starter_scope_json,ai_assist_enabled,published_at,
 create_by,create_time,last_update_by,last_update_time)
SELECT gen_random_uuid()::text,'EDU_TEACHING_CONTENT_REVIEW','教学内容审核','EDUCATION','v1',
 '教务审核教学中心资源','start','PUBLISHED','教育,教学中心',lo_from_bytea(0,convert_to('{}','UTF8')),f.id,'admin',lo_from_bytea(0,convert_to('{"type":"ALL"}','UTF8')),false,
 CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP
FROM form_definition f WHERE f.form_key='EDU_TEACHING_CONTENT' AND f.version='v1'
AND NOT EXISTS (SELECT 1 FROM wf_definition WHERE flow_code='EDU_TEACHING_CONTENT_REVIEW' AND version='v1');
WITH flow AS (SELECT id FROM wf_definition WHERE flow_code='EDU_TEACHING_CONTENT_REVIEW' AND version='v1'),
nodes AS (SELECT * FROM (VALUES
 ('start','开始','START','{}'),('academicApproval','教务审核','APPROVAL',
 '{"assigneeMode":"ROLE","assigneeValue":"EDU_ACADEMIC_APPROVER","approvalMode":"SINGLE","returnPolicy":"PREVIOUS"}'),
 ('end','结束','END','{}')) n(node_key,node_name,node_type,props))
INSERT INTO wf_node(id,flow_id,node_key,node_name,node_type,executor,timeout_sec,retry_max,retry_interval_sec,
 input_schema,output_schema,properties_json,additional_form_ids,field_permissions_json,
 create_by,create_time,last_update_by,last_update_time)
SELECT gen_random_uuid()::text,flow.id,n.node_key,n.node_name,n.node_type,'',0,0,0,
 lo_from_bytea(0,convert_to('{}','UTF8')),lo_from_bytea(0,convert_to('{}','UTF8')),
 lo_from_bytea(0,convert_to(n.props,'UTF8')),lo_from_bytea(0,convert_to('[]','UTF8')),
 lo_from_bytea(0,convert_to('{"permissions":{},"required":{}}','UTF8')),
 'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP FROM flow CROSS JOIN nodes n
ON CONFLICT(flow_id,node_key) DO UPDATE SET node_name=EXCLUDED.node_name,node_type=EXCLUDED.node_type,
 properties_json=EXCLUDED.properties_json,last_update_by='SYSTEM',last_update_time=CURRENT_TIMESTAMP;
WITH flow AS (SELECT id FROM wf_definition WHERE flow_code='EDU_TEACHING_CONTENT_REVIEW' AND version='v1')
INSERT INTO wf_edge(id,flow_id,from_node_key,to_node_key,condition_expr,is_default,create_by,create_time,last_update_by,last_update_time)
SELECT gen_random_uuid()::text,flow.id,v.from_key,v.to_key,NULL,true,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP
FROM flow CROSS JOIN (VALUES ('start','academicApproval'),('academicApproval','end')) v(from_key,to_key)
WHERE NOT EXISTS (SELECT 1 FROM wf_edge e WHERE e.flow_id=flow.id AND e.from_node_key=v.from_key AND e.to_node_key=v.to_key);

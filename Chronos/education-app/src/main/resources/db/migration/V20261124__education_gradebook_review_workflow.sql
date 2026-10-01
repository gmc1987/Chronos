-- 独立成绩册审核流程；候选人由角色解析器按角色码分配。
INSERT INTO wf_definition(
  id,flow_code,flow_name,category,version,description,entry_node_key,status,tags,
  config_json,starter_scope_json,ai_assist_enabled,published_at,
  create_by,create_time,last_update_by,last_update_time
)
SELECT gen_random_uuid()::text,'EDU_GRADEBOOK_REVIEW','成绩册审核','EDUCATION','v1',
 '教研审核后由教务审核，完成只回写成绩册 APPROVED','START','PUBLISHED',
 '教育,成绩中心',lo_from_bytea(0,convert_to('{"approvalMode":"SINGLE","allowReturn":true}','UTF8')),
 lo_from_bytea(0,convert_to('{"type":"ALL"}','UTF8')),false,CURRENT_TIMESTAMP,
 'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wf_definition WHERE flow_code='EDU_GRADEBOOK_REVIEW' AND version='v1');

WITH flow AS (SELECT id FROM wf_definition WHERE flow_code='EDU_GRADEBOOK_REVIEW' AND version='v1'),
nodes AS (SELECT * FROM (VALUES
 ('START','开始','START','{}'),
 ('RESEARCH_REVIEW','教研审核','APPROVAL','{"assigneeMode":"ROLE","assigneeValue":"EDU_GRADE_REVIEWER","approvalMode":"SINGLE","returnPolicy":"PREVIOUS"}'),
 ('ACADEMIC_APPROVAL','教务审核','APPROVAL','{"assigneeMode":"ROLE","assigneeValue":"EDU_ACADEMIC_APPROVER","approvalMode":"SINGLE","returnPolicy":"PREVIOUS"}'),
 ('END','结束','END','{}')
) n(node_key,node_name,node_type,properties_json))
INSERT INTO wf_node(
 id,flow_id,node_key,node_name,node_type,executor,timeout_sec,retry_max,retry_interval_sec,
 input_schema,output_schema,properties_json,additional_form_ids,field_permissions_json,
 create_by,create_time,last_update_by,last_update_time
)
SELECT gen_random_uuid()::text,flow.id,n.node_key,n.node_name,n.node_type,'',0,0,0,
 lo_from_bytea(0,convert_to('{}','UTF8')),lo_from_bytea(0,convert_to('{}','UTF8')),
 lo_from_bytea(0,convert_to(n.properties_json,'UTF8')),lo_from_bytea(0,convert_to('[]','UTF8')),
 lo_from_bytea(0,convert_to('{"permissions":{},"required":{}}','UTF8')),
 'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP
FROM flow CROSS JOIN nodes n
ON CONFLICT(flow_id,node_key) DO UPDATE SET
 node_name=EXCLUDED.node_name,node_type=EXCLUDED.node_type,
 properties_json=EXCLUDED.properties_json,last_update_by='SYSTEM',last_update_time=CURRENT_TIMESTAMP;

WITH flow AS (SELECT id FROM wf_definition WHERE flow_code='EDU_GRADEBOOK_REVIEW' AND version='v1')
INSERT INTO wf_edge(id,flow_id,from_node_key,to_node_key,condition_expr,is_default,create_by,create_time,last_update_by,last_update_time)
SELECT gen_random_uuid()::text,flow.id,v.from_key,v.to_key,NULL,true,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP
FROM flow CROSS JOIN (VALUES
 ('START','RESEARCH_REVIEW'),('RESEARCH_REVIEW','ACADEMIC_APPROVAL'),('ACADEMIC_APPROVAL','END')
) v(from_key,to_key)
WHERE NOT EXISTS (
 SELECT 1 FROM wf_edge e WHERE e.flow_id=flow.id AND e.from_node_key=v.from_key AND e.to_node_key=v.to_key
);

INSERT INTO wf_definition_acl(
 id,definition_id,subject_type,subject_id,action,enabled,create_by,create_time,last_update_by,last_update_time
)
SELECT gen_random_uuid()::text,d.id,'ROLE',r.role_code,'START',true,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP
FROM wf_definition d CROSS JOIN (VALUES ('EDU_TEACHER'),('EDU_ADMIN'),('SUPER_ADMIN')) r(role_code)
WHERE d.flow_code='EDU_GRADEBOOK_REVIEW' AND d.version='v1'
ON CONFLICT(definition_id,subject_type,subject_id,action) DO UPDATE SET enabled=true;

-- 审批候选角色必须存在；角色权限由 V20261123 初始化。
INSERT INTO t_role(id,role_code,role_name,create_by,create_time,built_in,status)
SELECT gen_random_uuid()::text,v.role_code,v.role_name,'SYSTEM',CURRENT_TIMESTAMP,true,1
FROM (VALUES ('EDU_GRADE_REVIEWER','成绩教研审核人'),('EDU_ACADEMIC_APPROVER','成绩教务审核人')) v(role_code,role_name)
WHERE NOT EXISTS (SELECT 1 FROM t_role r WHERE r.role_code=v.role_code);

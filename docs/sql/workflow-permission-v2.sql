-- Chronos workflow permission v2 (PostgreSQL, idempotent)
-- Execute in one transaction before deploying with ddl-auto=validate/none.
BEGIN;

CREATE TABLE IF NOT EXISTS wf_definition_acl (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp,
  definition_id varchar(64) NOT NULL, subject_type varchar(32) NOT NULL,
  subject_id varchar(128) NOT NULL, action varchar(32) NOT NULL, enabled boolean NOT NULL DEFAULT true,
  CONSTRAINT uk_wf_acl_rule UNIQUE(definition_id,subject_type,subject_id,action)
);
CREATE INDEX IF NOT EXISTS idx_wf_acl_definition_action ON wf_definition_acl(definition_id,action);
CREATE INDEX IF NOT EXISTS idx_wf_acl_subject ON wf_definition_acl(subject_type,subject_id);

CREATE TABLE IF NOT EXISTS wf_instance_participant (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp,
  instance_id varchar(64) NOT NULL, username varchar(128) NOT NULL,
  participant_type varchar(32) NOT NULL, source_task_id varchar(64), active boolean NOT NULL DEFAULT true,
  CONSTRAINT uk_wf_participant UNIQUE(instance_id,username,participant_type)
);
CREATE INDEX IF NOT EXISTS idx_wf_participant_instance ON wf_instance_participant(instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_participant_user ON wf_instance_participant(username,participant_type);

CREATE TABLE IF NOT EXISTS wf_task_candidate (
  id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
  last_update_by varchar(128), last_update_time timestamp,
  task_id varchar(64) NOT NULL, subject_type varchar(32) NOT NULL, subject_id varchar(128) NOT NULL,
  CONSTRAINT uk_wf_candidate UNIQUE(task_id,subject_type,subject_id)
);
CREATE INDEX IF NOT EXISTS idx_wf_candidate_task ON wf_task_candidate(task_id);
CREATE INDEX IF NOT EXISTS idx_wf_candidate_subject ON wf_task_candidate(subject_type,subject_id);

WITH permission_data(code,name) AS (VALUES
 ('workflow:definition:view','查看流程定义'),('workflow:definition:create','创建流程定义'),
 ('workflow:definition:update','编辑流程定义'),('workflow:definition:delete','删除流程定义'),
 ('workflow:definition:publish','发布与停用流程'),('workflow:form:manage','流程表单管理'),
 ('workflow:instance:start','发起流程实例'),('workflow:instance:view','查看流程实例'),
 ('workflow:instance:manage','管理流程实例'),('workflow:instance:terminate','终止流程实例'),
 ('workflow:monitor:view','查看流程监控'),('workflow:task:approve','审批流程任务'),
 ('workflow:task:return','退回流程任务'),('workflow:task:transfer','转办流程任务'),
 ('workflow:task:add-sign','加签流程任务'),('workflow:task:cc','抄送流程任务'),
 ('workflow:task:remind','催办流程任务'),('workflow:instance:withdraw','撤回流程实例'),
 ('workflow:directory:view','查看流程人员目录'))
INSERT INTO t_permission(id,create_by,create_time,permission_code,permission_name,permission_type,status)
SELECT gen_random_uuid()::text,'migration',CURRENT_TIMESTAMP,code,name,'API',1 FROM permission_data
ON CONFLICT(permission_code) DO NOTHING;

-- Compatibility: expand legacy workflow:manage/use grants into the fine-grained bundles.
INSERT INTO t_role_permission(role_id,permission_id)
SELECT DISTINCT legacy.role_id,target.id FROM t_role_permission legacy
JOIN t_permission source ON source.id=legacy.permission_id
JOIN t_permission target ON
 (source.permission_code='workflow:manage' AND target.permission_code IN
  ('workflow:definition:view','workflow:definition:create','workflow:definition:update','workflow:definition:delete','workflow:definition:publish','workflow:form:manage','workflow:instance:view','workflow:instance:manage','workflow:instance:terminate','workflow:monitor:view','workflow:directory:view'))
 OR
 (source.permission_code='workflow:use' AND target.permission_code IN
  ('workflow:definition:view','workflow:instance:start','workflow:instance:view','workflow:task:approve','workflow:task:return','workflow:task:transfer','workflow:task:add-sign','workflow:task:cc','workflow:task:remind','workflow:instance:withdraw','workflow:directory:view'))
ON CONFLICT DO NOTHING;

INSERT INTO wf_definition_acl(id,create_by,create_time,definition_id,subject_type,subject_id,action,enabled)
SELECT gen_random_uuid()::text,'migration',CURRENT_TIMESTAMP,d.id,'USER',COALESCE(NULLIF(d.manager_user,''),d.create_by),'MANAGE',true
FROM wf_definition d WHERE COALESCE(NULLIF(d.manager_user,''),d.create_by) IS NOT NULL
ON CONFLICT(definition_id,subject_type,subject_id,action) DO NOTHING;

INSERT INTO wf_instance_participant(id,create_by,create_time,instance_id,username,participant_type,active)
SELECT gen_random_uuid()::text,'migration',CURRENT_TIMESTAMP,i.id,i.initiator,'INITIATOR',true FROM wf_instance i
ON CONFLICT(instance_id,username,participant_type) DO NOTHING;
INSERT INTO wf_instance_participant(id,create_by,create_time,instance_id,username,participant_type,source_task_id,active)
SELECT gen_random_uuid()::text,'migration',CURRENT_TIMESTAMP,t.instance_id,t.assignee,
       CASE WHEN t.status='CC' THEN 'CC' ELSE 'ASSIGNEE' END,t.id,true FROM wf_task t
ON CONFLICT(instance_id,username,participant_type) DO NOTHING;
INSERT INTO wf_task_candidate(id,create_by,create_time,task_id,subject_type,subject_id)
SELECT gen_random_uuid()::text,'migration',CURRENT_TIMESTAMP,t.id,'USER',t.assignee FROM wf_task t WHERE t.status<>'CC'
ON CONFLICT(task_id,subject_type,subject_id) DO NOTHING;

UPDATE t_portal_application SET required_permission='workflow:instance:view',last_update_by='migration',last_update_time=CURRENT_TIMESTAMP
WHERE app_code='workflow';

COMMIT;

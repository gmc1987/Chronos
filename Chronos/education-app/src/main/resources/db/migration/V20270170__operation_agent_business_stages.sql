BEGIN;

ALTER TABLE edu_operation_agent_plan_step
    ADD COLUMN IF NOT EXISTS business_stage_order integer,
    ADD COLUMN IF NOT EXISTS business_stage_code varchar(48),
    ADD COLUMN IF NOT EXISTS business_stage_name varchar(128);

ALTER TABLE edu_operation_agent_step_run
    ADD COLUMN IF NOT EXISTS business_stage_order integer,
    ADD COLUMN IF NOT EXISTS business_stage_code varchar(48),
    ADD COLUMN IF NOT EXISTS business_stage_name varchar(128);

WITH stage_mapping(tool_code, stage_order, stage_code, stage_name) AS (VALUES
    ('semester.context.check',1,'FOUNDATION','基础数据准备'),
    ('knowledge.policy.search',1,'FOUNDATION','基础数据准备'),
    ('teacher.onboarding.batch',1,'FOUNDATION','基础数据准备'),
    ('admission.import.managed-file',2,'ADMISSION_IMPORT','新生数据接入'),
    ('admission.data.check',2,'ADMISSION_IMPORT','新生数据接入'),
    ('admission.class.assign.batch',3,'CLASS_ASSIGNMENT','行政班分班'),
    ('admission.review.batch',4,'ENROLLMENT_ACCOUNT','报到、学籍、学号及账号'),
    ('admission.enroll.batch',4,'ENROLLMENT_ACCOUNT','报到、学籍、学号及账号'),
    ('student.account.provision.batch',4,'ENROLLMENT_ACCOUNT','报到、学籍、学号及账号'),
    ('account.binding.check',4,'ENROLLMENT_ACCOUNT','报到、学籍、学号及账号'),
    ('teaching.assignment.generate',5,'TEACHING_ASSIGNMENT','教学任务与教师安排'),
    ('teaching.offering.generate',6,'TEACHING_CLASS','教学班与选课关系'),
    ('teaching.readiness.check',6,'TEACHING_CLASS','教学班与选课关系'),
    ('scheduling.generate.full',7,'SCHEDULING','自动排课、审核与发布'),
    ('scheduling.submit.best',7,'SCHEDULING','自动排课、审核与发布'),
    ('scheduling.review.submitted',7,'SCHEDULING','自动排课、审核与发布'),
    ('scheduling.apply.approved',7,'SCHEDULING','自动排课、审核与发布'),
    ('scheduling.publish',7,'SCHEDULING','自动排课、审核与发布')
)
UPDATE edu_operation_agent_plan_step step
SET business_stage_order = mapping.stage_order,
    business_stage_code = mapping.stage_code,
    business_stage_name = mapping.stage_name
FROM stage_mapping mapping
WHERE step.tool_code = mapping.tool_code;

UPDATE edu_operation_agent_step_run run
SET business_stage_order = source.business_stage_order,
    business_stage_code = source.business_stage_code,
    business_stage_name = source.business_stage_name
FROM edu_operation_agent_plan_step source
WHERE run.plan_step_id = source.id;

UPDATE edu_operation_agent_plan
SET authorized_tools_json = CASE
    WHEN authorized_tools_json LIKE '%"teaching.assignment.generate"%' THEN authorized_tools_json
    ELSE replace(authorized_tools_json, '"teaching.offering.generate"',
                 '"teaching.assignment.generate","teaching.offering.generate"')
END,
last_update_by = 'SYSTEM', last_update_time = CURRENT_TIMESTAMP,
plan_version = plan_version + 1
WHERE id = '76000000-0000-4000-8000-000000000002';

-- 在已存在的全链路模板中插入第5阶段；先整体偏移，避免唯一键逐行更新冲突。
UPDATE edu_operation_agent_plan_step
SET step_order = step_order + 100
WHERE plan_id = '76000000-0000-4000-8000-000000000002' AND step_order >= 9;

UPDATE edu_operation_agent_plan_step
SET step_order = step_order - 99
WHERE plan_id = '76000000-0000-4000-8000-000000000002' AND step_order >= 109;

INSERT INTO edu_operation_agent_plan_step (
    id, create_by, create_time, plan_id, step_order, step_code, step_name, description,
    tool_code, risk_level, approval_required, success_criteria, tool_config_json,
    business_stage_order, business_stage_code, business_stage_name
)
SELECT '77000000-0000-4000-8000-000000000201','SYSTEM',CURRENT_TIMESTAMP,
       '76000000-0000-4000-8000-000000000002',9,'TEACHING_ASSIGNMENT',
       '生成教师分工候选','根据用户确认的工号、学科编码和行政班编码建立本学期教师分工。',
       'teaching.assignment.generate','HIGH',true,'形成完整且不重复的教师任教关系',
       '{"semesterCode":"2026-2027-1","assignments":[]}',
       5,'TEACHING_ASSIGNMENT','教学任务与教师安排'
WHERE NOT EXISTS (
    SELECT 1 FROM edu_operation_agent_plan_step
    WHERE plan_id='76000000-0000-4000-8000-000000000002'
      AND tool_code='teaching.assignment.generate'
);

COMMIT;

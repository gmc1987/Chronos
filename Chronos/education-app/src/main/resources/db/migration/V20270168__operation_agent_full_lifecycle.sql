-- 学期运营智能体完整业务工具、独立审核权限和生产计划模板。
BEGIN;

INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time,
    permission_code, permission_name, permission_type, action_type,
    built_in, status, resource_type, scope_type
)
SELECT source.id, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       source.code, source.name, 'API_ACTION', source.action,
       true, 1, 'AGENT', 'ROLE'
FROM (VALUES
    ('76000000-0000-4000-8000-000000000101','education:operation-agent:view','查看学期运营智能体','VIEW'),
    ('76000000-0000-4000-8000-000000000102','education:operation-agent:manage','管理学期运营智能体计划','MANAGE'),
    ('76000000-0000-4000-8000-000000000103','education:operation-agent:execute','执行学期运营智能体计划','EXECUTE'),
    ('76000000-0000-4000-8000-000000000104','education:operation-agent:review','审核学期运营智能体步骤','APPROVE')
) source(id, code, name, action)
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission existing WHERE existing.permission_code = source.code
);

INSERT INTO t_role (id, create_by, create_time, built_in, role_code, role_name, status)
SELECT '76000000-0000-4000-8000-000000000105', 'SYSTEM', CURRENT_TIMESTAMP, true,
       'EDU_OPERATION_AGENT_REVIEWER', '学期运营智能体审核员', 1
WHERE NOT EXISTS (SELECT 1 FROM t_role WHERE role_code = 'EDU_OPERATION_AGENT_REVIEWER');

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN','ROLE_PLATFORM_ADMIN','EDU_ADMIN')
  AND permission.permission_code LIKE 'education:operation-agent:%'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role CROSS JOIN t_permission permission
WHERE role.role_code IN ('EDU_SCHEDULING_REVIEWER','EDU_OPERATION_AGENT_REVIEWER')
  AND permission.permission_code IN ('education:operation-agent:view','education:operation-agent:review')
ON CONFLICT DO NOTHING;

INSERT INTO edu_operation_agent_plan (
    id, create_by, create_time, plan_name, agent_code, objective, rule_text,
    cycle_type, authorized_tools_json, owner_username, visibility, enabled, plan_version, row_version
) VALUES (
    '76000000-0000-4000-8000-000000000002', 'SYSTEM', CURRENT_TIMESTAMP,
    '新学期开学全链路', 'SEMESTER_OPERATION_V1',
    '完成人员入校、新生建档、教师入职、教学任务生成、全量排课、异人审核和课表发布',
    '每个写入步骤都在独立事务边界执行；高风险步骤必须由非发起人确认；失败后保留已完成证据并从失败步骤重试。',
    'MANUAL',
    '["semester.context.check","knowledge.policy.search","admission.data.check","admission.class.assign.batch","admission.review.batch","admission.enroll.batch","student.account.provision.batch","teacher.onboarding.batch","teaching.offering.generate","teaching.readiness.check","scheduling.generate.full","scheduling.submit.best","scheduling.review.submitted","scheduling.apply.approved","scheduling.publish","account.binding.check"]',
    'SYSTEM', 'ALL', true, 1, 0
), (
    '76000000-0000-4000-8000-000000000003', 'SYSTEM', CURRENT_TIMESTAMP,
    '学期末收口与毕业准备', 'SEMESTER_OPERATION_V1',
    '核对成绩、学籍异动和事件积压，并在人工确认后生成毕业学籍变更待办',
    '先只读检查期末收口条件；毕业名单必须明确入学年份并由非发起人确认后生成待办，最终学籍变更仍走现有审批。',
    'MANUAL',
    '["knowledge.policy.search","term.end.check","term.graduation.prepare"]',
    'SYSTEM', 'ALL', true, 1, 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO edu_operation_agent_plan_step (
    id, create_by, create_time, plan_id, step_order, step_code, step_name, description,
    tool_code, risk_level, approval_required, success_criteria, tool_config_json
) VALUES
('76000000-0000-4000-8000-000000000201','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',1,'CONTEXT','读取学期上下文','读取学期、班级、学生、教师和开课统计。','semester.context.check','LOW',false,'形成开学执行基线','{"semesterCode":"2026-2027-1"}'),
('76000000-0000-4000-8000-000000000202','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',2,'POLICY','检索开学政策','从绑定知识库检索当前计划相关政策。','knowledge.policy.search','LOW',false,'保存政策引用','{"query":"新生入学 教师入职 开学排课 管理规定"}'),
('76000000-0000-4000-8000-000000000203','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',3,'ADMISSION_CHECK','检查录取名册','检查新生专业和拟分班完整性。','admission.data.check','LOW',false,'输出缺失项','{"admissionYear":2026}'),
('76000000-0000-4000-8000-000000000204','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',4,'CLASS_ASSIGN','批量拟分班','按专业、年级和目标班额均衡拟分班。','admission.class.assign.batch','HIGH',true,'候选人获得拟分班','{"admissionYear":2026,"batchLimit":100,"targetClassSize":50}'),
('76000000-0000-4000-8000-000000000205','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',5,'ADMISSION_REVIEW','批量资格复查','复查专业和拟分班完整的新生。','admission.review.batch','HIGH',true,'状态进入VERIFIED','{"admissionYear":2026,"batchLimit":100}'),
('76000000-0000-4000-8000-000000000206','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',6,'ENROLL','报到建档并生成学号','建立正式学籍档案并开通账号。','admission.enroll.batch','CRITICAL',true,'生成正式学号与账号','{"admissionYear":2026,"batchLimit":100}'),
('76000000-0000-4000-8000-000000000207','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',7,'STUDENT_ACCOUNT','补齐学生账号','为历史缺失绑定的正式学生补开账号。','student.account.provision.batch','HIGH',true,'学生账号一对一绑定','{"batchLimit":100}'),
('76000000-0000-4000-8000-000000000208','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',8,'TEACHER_ONBOARD','教师入职建档','按有效教职工建立教师档案和账号。','teacher.onboarding.batch','CRITICAL',true,'教师工号、档案和账号贯通','{"batchLimit":100}'),
('76000000-0000-4000-8000-000000000209','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',9,'OFFERING','生成教学开课任务','从有效教学分工生成开课和教学班成员。','teaching.offering.generate','HIGH',true,'开课与学生成员完整','{"semesterCode":"2026-2027-1","batchLimit":500}'),
('76000000-0000-4000-8000-000000000210','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',10,'READINESS','检查排课准备度','检查开课、学生和现有课表。','teaching.readiness.check','LOW',false,'不存在空教学班','{"semesterCode":"2026-2027-1"}'),
('76000000-0000-4000-8000-000000000211','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',11,'SCHEDULE_GENERATE','生成全量排课候选','使用普通排课全部约束生成候选。','scheduling.generate.full','MEDIUM',false,'至少生成一个完整候选','{"semesterCode":"2026-2027-1","candidateCount":1,"weekdays":5,"periodsPerDay":8}'),
('76000000-0000-4000-8000-000000000212','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',12,'SCHEDULE_SUBMIT','提交最优候选','提交发起人拥有且无未排课程的候选。','scheduling.submit.best','HIGH',true,'候选进入SUBMITTED','{"semesterCode":"2026-2027-1"}'),
('76000000-0000-4000-8000-000000000213','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',13,'SCHEDULE_REVIEW','独立审核候选','由本步骤审核人作为排课候选审核人。','scheduling.review.submitted','HIGH',true,'候选进入APPROVED','{"semesterCode":"2026-2027-1"}'),
('76000000-0000-4000-8000-000000000214','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',14,'SCHEDULE_APPLY','应用候选课表','将审核通过的候选写入正式课表。','scheduling.apply.approved','CRITICAL',true,'候选进入APPLIED','{"semesterCode":"2026-2027-1"}'),
('76000000-0000-4000-8000-000000000215','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',15,'SCHEDULE_PUBLISH','发布课表版本','生成不可变发布版本并通知用户。','scheduling.publish','CRITICAL',true,'生成发布版本','{"semesterCode":"2026-2027-1"}'),
('76000000-0000-4000-8000-000000000216','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000002',16,'ACCOUNT_CHECK','复核账号绑定','核对学生和教师账号绑定。','account.binding.check','LOW',false,'输出缺失账号数','{}'),
('76000000-0000-4000-8000-000000000301','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000003',1,'END_POLICY','检索期末政策','检索成绩发布和毕业审核政策。','knowledge.policy.search','LOW',false,'保存期末政策引用','{"query":"期末成绩发布 毕业审核 学籍异动 管理规定"}'),
('76000000-0000-4000-8000-000000000302','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000003',2,'END_CHECK','期末收口检查','核对成绩册、待办学籍异动和失败事件。','term.end.check','LOW',false,'输出收口准备度','{"semesterCode":"2026-2027-1"}'),
('76000000-0000-4000-8000-000000000303','SYSTEM',CURRENT_TIMESTAMP,'76000000-0000-4000-8000-000000000003',3,'GRADUATION_PREPARE','生成毕业学籍变更待办','为明确毕业年级生成待审批的毕业变更申请。','term.graduation.prepare','CRITICAL',true,'仅生成待审批申请','{"graduatingAdmissionYear":2023,"batchLimit":100,"effectiveDate":"2027-06-30"}')
ON CONFLICT (id) DO NOTHING;

COMMIT;

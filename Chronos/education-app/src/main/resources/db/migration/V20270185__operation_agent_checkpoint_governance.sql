BEGIN;

ALTER TABLE edu_operation_agent_plan_step
	ADD COLUMN IF NOT EXISTS checkpoint_mode varchar(32) NOT NULL DEFAULT 'NONE',
	ADD COLUMN IF NOT EXISTS policy_code varchar(64) NOT NULL DEFAULT 'OPERATION_AGENT_CHECKPOINT_V2',
	ADD COLUMN IF NOT EXISTS policy_version integer NOT NULL DEFAULT 2;

ALTER TABLE edu_operation_agent_step_run
	ADD COLUMN IF NOT EXISTS checkpoint_mode varchar(32) NOT NULL DEFAULT 'NONE',
	ADD COLUMN IF NOT EXISTS policy_code varchar(64) NOT NULL DEFAULT 'OPERATION_AGENT_CHECKPOINT_V2',
	ADD COLUMN IF NOT EXISTS policy_version integer NOT NULL DEFAULT 2,
	ADD COLUMN IF NOT EXISTS decision_action varchar(24),
	ADD COLUMN IF NOT EXISTS decision_comment varchar(1000),
	ADD COLUMN IF NOT EXISTS evidence_version integer NOT NULL DEFAULT 0,
	ADD COLUMN IF NOT EXISTS evidence_hash varchar(80);

UPDATE edu_operation_agent_plan_step
SET checkpoint_mode = CASE
	WHEN NOT approval_required THEN 'NONE'
	WHEN tool_code IN (
		'scheduling.review.submitted', 'grade.review', 'grade.change.review',
		'admission.review.batch', 'iam.role.upsert', 'iam.authorization.assign'
	) THEN 'DUAL_CONTROL'
	WHEN tool_code = 'workflow.task.complete' THEN 'BUSINESS_WORKFLOW'
	ELSE 'INITIATOR_CONFIRM'
END;

UPDATE edu_operation_agent_step_run
SET checkpoint_mode = CASE
	WHEN NOT approval_required THEN 'NONE'
	WHEN tool_code IN (
		'scheduling.review.submitted', 'grade.review', 'grade.change.review',
		'admission.review.batch', 'iam.role.upsert', 'iam.authorization.assign'
	) THEN 'DUAL_CONTROL'
	WHEN tool_code = 'workflow.task.complete' THEN 'BUSINESS_WORKFLOW'
	ELSE 'INITIATOR_CONFIRM'
END;

-- 已经卡在通用审核检查点的基础数据初始化运行，保留候选证据并交还发起人确认。
UPDATE edu_operation_agent_step_run
SET status = 'WAITING_CONFIRMATION',
	checkpoint_mode = 'INITIATOR_CONFIRM',
	message = '候选结果已生成，等待任务发起人确认写入'
WHERE status = 'WAITING_APPROVAL'
	AND tool_code IN (
		'iam.department.import.managed-file', 'iam.position.import.managed-file',
		'iam.job-level.import.managed-file', 'iam.employee.import.managed-file',
		'teacher.onboarding.batch'
	);

UPDATE edu_operation_agent_run run
SET status = 'WAITING_CONFIRMATION',
	next_action = '请核对当前步骤候选结果并确认写入'
WHERE status = 'WAITING_APPROVAL'
	AND EXISTS (
		SELECT 1 FROM edu_operation_agent_step_run step
		WHERE step.run_id = run.id
			AND step.step_order = run.current_step_order
			AND step.status = 'WAITING_CONFIRMATION'
	);

ALTER TABLE edu_operation_agent_plan_step
	DROP CONSTRAINT IF EXISTS ck_edu_operation_plan_step_checkpoint_mode;
ALTER TABLE edu_operation_agent_plan_step
	ADD CONSTRAINT ck_edu_operation_plan_step_checkpoint_mode CHECK (
		checkpoint_mode IN ('NONE', 'INITIATOR_CONFIRM', 'BUSINESS_WORKFLOW', 'DUAL_CONTROL'));

ALTER TABLE edu_operation_agent_step_run
	DROP CONSTRAINT IF EXISTS ck_edu_operation_step_run_checkpoint_mode;
ALTER TABLE edu_operation_agent_step_run
	ADD CONSTRAINT ck_edu_operation_step_run_checkpoint_mode CHECK (
		checkpoint_mode IN ('NONE', 'INITIATOR_CONFIRM', 'BUSINESS_WORKFLOW', 'DUAL_CONTROL'));

COMMIT;

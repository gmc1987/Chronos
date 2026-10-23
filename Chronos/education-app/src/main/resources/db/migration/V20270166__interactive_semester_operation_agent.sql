BEGIN;

CREATE TABLE IF NOT EXISTS edu_operation_agent_plan (
	id varchar(64) PRIMARY KEY,
	create_by varchar(128) NOT NULL,
	create_time timestamp NOT NULL,
	last_update_by varchar(128),
	last_update_time timestamp,
	plan_name varchar(128) NOT NULL,
	agent_code varchar(64) NOT NULL,
	objective varchar(1000) NOT NULL,
	rule_text text NOT NULL,
	cycle_type varchar(24) NOT NULL,
	cron_expression varchar(128),
	period_start date,
	period_end date,
	knowledge_base_id varchar(64),
	authorized_tools_json text NOT NULL,
	owner_username varchar(128) NOT NULL,
	visibility varchar(16) NOT NULL,
	enabled boolean NOT NULL DEFAULT true,
	next_scheduled_at timestamp,
	last_scheduled_at timestamp,
	plan_version integer NOT NULL DEFAULT 1,
	row_version bigint NOT NULL DEFAULT 0,
	CONSTRAINT ck_edu_operation_plan_cycle CHECK (cycle_type IN ('MANUAL', 'CRON')),
	CONSTRAINT ck_edu_operation_plan_visibility CHECK (visibility IN ('OWNER', 'ALL'))
);

CREATE TABLE IF NOT EXISTS edu_operation_agent_plan_step (
	id varchar(64) PRIMARY KEY,
	create_by varchar(128) NOT NULL,
	create_time timestamp NOT NULL,
	last_update_by varchar(128),
	last_update_time timestamp,
	plan_id varchar(64) NOT NULL REFERENCES edu_operation_agent_plan(id) ON DELETE CASCADE,
	step_order integer NOT NULL,
	step_code varchar(64) NOT NULL,
	step_name varchar(128) NOT NULL,
	description varchar(1000),
	tool_code varchar(96) NOT NULL,
	risk_level varchar(16) NOT NULL,
	approval_required boolean NOT NULL DEFAULT false,
	success_criteria varchar(1000),
	tool_config_json text NOT NULL DEFAULT '{}',
	CONSTRAINT uk_edu_operation_plan_step UNIQUE (plan_id, step_order),
	CONSTRAINT ck_edu_operation_step_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE IF NOT EXISTS edu_operation_agent_run (
	id varchar(64) PRIMARY KEY,
	create_by varchar(128) NOT NULL,
	create_time timestamp NOT NULL,
	last_update_by varchar(128),
	last_update_time timestamp,
	plan_id varchar(64) NOT NULL REFERENCES edu_operation_agent_plan(id),
	plan_version integer NOT NULL,
	owner_username varchar(128) NOT NULL,
	status varchar(32) NOT NULL,
	current_step_order integer,
	current_step_name varchar(128),
	next_action varchar(500),
	pause_requested boolean NOT NULL DEFAULT false,
	condition_version integer NOT NULL DEFAULT 1,
	plan_snapshot_json text NOT NULL,
	summary varchar(1000),
	started_at timestamp,
	paused_at timestamp,
	completed_at timestamp,
	error_message varchar(1000),
	row_version bigint NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_edu_operation_run_owner ON edu_operation_agent_run(owner_username, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_edu_operation_run_plan_status ON edu_operation_agent_run(plan_id, status);

CREATE TABLE IF NOT EXISTS edu_operation_agent_step_run (
	id varchar(64) PRIMARY KEY,
	create_by varchar(128) NOT NULL,
	create_time timestamp NOT NULL,
	last_update_by varchar(128),
	last_update_time timestamp,
	run_id varchar(64) NOT NULL REFERENCES edu_operation_agent_run(id) ON DELETE CASCADE,
	plan_step_id varchar(64) NOT NULL,
	step_order integer NOT NULL,
	step_name varchar(128) NOT NULL,
	tool_code varchar(96) NOT NULL,
	status varchar(32) NOT NULL,
	progress integer NOT NULL DEFAULT 0,
	attempt_no integer NOT NULL DEFAULT 0,
	approval_required boolean NOT NULL DEFAULT false,
	approved_by varchar(128),
	approved_at timestamp,
	message varchar(1000),
	result_json text,
	started_at timestamp,
	completed_at timestamp,
	row_version bigint NOT NULL DEFAULT 0,
	CONSTRAINT uk_edu_operation_run_step UNIQUE (run_id, step_order),
	CONSTRAINT ck_edu_operation_step_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE IF NOT EXISTS edu_operation_agent_condition (
	id varchar(64) PRIMARY KEY,
	create_by varchar(128) NOT NULL,
	create_time timestamp NOT NULL,
	last_update_by varchar(128),
	last_update_time timestamp,
	run_id varchar(64) NOT NULL REFERENCES edu_operation_agent_run(id) ON DELETE CASCADE,
	condition_version integer NOT NULL,
	condition_text text NOT NULL,
	impact_analysis text NOT NULL,
	submitted_by varchar(128) NOT NULL,
	CONSTRAINT uk_edu_operation_condition_version UNIQUE (run_id, condition_version)
);

CREATE TABLE IF NOT EXISTS edu_operation_agent_event (
	id varchar(64) PRIMARY KEY,
	create_by varchar(128) NOT NULL,
	create_time timestamp NOT NULL,
	last_update_by varchar(128),
	last_update_time timestamp,
	run_id varchar(64) NOT NULL REFERENCES edu_operation_agent_run(id) ON DELETE CASCADE,
	event_sequence bigint NOT NULL,
	event_type varchar(48) NOT NULL,
	level varchar(16) NOT NULL,
	title varchar(200) NOT NULL,
	detail text,
	metadata_json text,
	CONSTRAINT uk_edu_operation_event_sequence UNIQUE (run_id, event_sequence)
);
CREATE INDEX IF NOT EXISTS idx_edu_operation_event_run_seq
	ON edu_operation_agent_event(run_id, event_sequence);

INSERT INTO edu_operation_agent_plan (
	id, create_by, create_time, plan_name, agent_code, objective, rule_text,
	cycle_type, authorized_tools_json, owner_username, visibility, enabled, plan_version, row_version
)
VALUES (
	'76000000-0000-4000-8000-000000000001', 'SYSTEM', CURRENT_TIMESTAMP,
	'新学期开学准备度检查', 'SEMESTER_OPERATION_V1',
	'在正式开学前核验新生、账号、班级、教学任务和排课数据是否具备上线条件',
	'只读取正式业务数据并生成检查证据；任何学生建档、账号开通或正式发布操作必须另行配置高风险工具并经人工确认。',
	'MANUAL',
	'["semester.context.check","admission.data.check","account.binding.check","teaching.readiness.check"]',
	'SYSTEM', 'ALL', true, 1, 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO edu_operation_agent_plan_step (
	id, create_by, create_time, plan_id, step_order, step_code, step_name, description,
	tool_code, risk_level, approval_required, success_criteria, tool_config_json
)
VALUES
	('76000000-0000-4000-8000-000000000011', 'SYSTEM', CURRENT_TIMESTAMP,
	 '76000000-0000-4000-8000-000000000001', 1, 'SEMESTER_CONTEXT', '读取学期基础数据',
	 '统计班级、学生和开课任务，形成执行上下文。', 'semester.context.check', 'LOW', false,
	 '成功读取学期、班级、学生和开课任务统计', '{"semesterCode":"2026-2027-1"}'),
	('76000000-0000-4000-8000-000000000012', 'SYSTEM', CURRENT_TIMESTAMP,
	 '76000000-0000-4000-8000-000000000001', 2, 'ADMISSION_CHECK', '检查新生入学数据',
	 '检查录取、复查、报到、专业和拟分班完整性。', 'admission.data.check', 'LOW', false,
	 '输出新生状态和缺失字段统计', '{"admissionYear":2026}'),
	('76000000-0000-4000-8000-000000000013', 'SYSTEM', CURRENT_TIMESTAMP,
	 '76000000-0000-4000-8000-000000000001', 3, 'ACCOUNT_CHECK', '检查学生账号绑定',
	 '对比正式学生档案与有效账号绑定。', 'account.binding.check', 'LOW', false,
	 '输出缺失账号绑定数量', '{}'),
	('76000000-0000-4000-8000-000000000014', 'SYSTEM', CURRENT_TIMESTAMP,
	 '76000000-0000-4000-8000-000000000001', 4, 'TEACHING_CHECK', '检查教学与排课准备度',
	 '检查学期教学任务及课表条目。', 'teaching.readiness.check', 'LOW', false,
	 '输出教学任务和排课条目数量', '{"semesterCode":"2026-2027-1"}')
ON CONFLICT (id) DO NOTHING;

COMMIT;

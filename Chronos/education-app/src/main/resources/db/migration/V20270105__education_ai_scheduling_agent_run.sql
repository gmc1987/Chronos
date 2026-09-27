-- AI scheduling Agent Run/Step persistence and additive permission bindings.
-- V20270105 is intentionally newer than the existing education migration set.
BEGIN;

CREATE TABLE IF NOT EXISTS agent_run (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128),
    create_time TIMESTAMP,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    client_request_id VARCHAR(128) NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    owner_username VARCHAR(128) NOT NULL,
    school_id VARCHAR(64),
    semester_code VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    plan_version INTEGER NOT NULL DEFAULT 1,
    request_hash VARCHAR(64) NOT NULL,
    request_ciphertext TEXT,
    parsed_plan_json TEXT,
    confirmed_plan_json TEXT,
    related_job_id VARCHAR(64),
    model_id VARCHAR(64),
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    expires_at TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_agent_run_owner_request UNIQUE (owner_username, client_request_id),
    CONSTRAINT ck_agent_run_status CHECK (
        status IN (
            'DRAFT', 'NEEDS_CLARIFICATION', 'READY_FOR_CONFIRMATION',
            'CONFIRMED', 'QUEUED', 'RUNNING', 'CANDIDATES_READY',
            'FAILED', 'CANCELLED', 'EXPIRED'
        )
    ),
    CONSTRAINT ck_agent_run_plan_version CHECK (plan_version > 0)
);

CREATE INDEX IF NOT EXISTS idx_agent_run_owner_status
    ON agent_run (owner_username, status, create_time DESC);

CREATE TABLE IF NOT EXISTS agent_step (
    id VARCHAR(64) PRIMARY KEY,
    create_by VARCHAR(128),
    create_time TIMESTAMP,
    last_update_by VARCHAR(128),
    last_update_time TIMESTAMP,
    run_id VARCHAR(64) NOT NULL,
    step_no INTEGER NOT NULL,
    skill_code VARCHAR(64) NOT NULL,
    tool_code VARCHAR(100),
    plan_version INTEGER NOT NULL,
    step_key VARCHAR(128) NOT NULL,
    state VARCHAR(24) NOT NULL,
    input_digest VARCHAR(64) NOT NULL,
    output_summary_json TEXT,
    result_code VARCHAR(64),
    duration_ms BIGINT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    CONSTRAINT uk_agent_step_run_no UNIQUE (run_id, step_no),
    CONSTRAINT uk_agent_step_run_key UNIQUE (run_id, step_key)
);

CREATE INDEX IF NOT EXISTS idx_agent_step_run_no ON agent_step (run_id, step_no);

ALTER TABLE edu_schedule_generation_job
    ADD COLUMN IF NOT EXISTS agent_run_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_edu_schedule_job_agent_run
    ON edu_schedule_generation_job (agent_run_id);

ALTER TABLE edu_scheduling_agent_proposal
    ADD COLUMN IF NOT EXISTS agent_run_id VARCHAR(64);

ALTER TABLE edu_scheduling_agent_proposal
    ADD COLUMN IF NOT EXISTS plan_version INTEGER;

INSERT INTO t_menu (
    id, create_by, create_time, last_update_by, last_update_time,
    menu_name, order_num, parent_id, path
)
SELECT
    'f34cc5d1-8ec4-4d34-9b3f-c5c3b5b2e2a1', 'SYSTEM', CURRENT_TIMESTAMP,
    'SYSTEM', CURRENT_TIMESTAMP, 'AI 智能排课', 7,
    'e58d2b8a-fbae-4bdd-bbba-3e509d8a69bc',
    '/admin/education/scheduling/ai'
WHERE NOT EXISTS (
    SELECT 1 FROM t_menu WHERE menu_name = 'AI 智能排课'
);

INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time,
    permission_code, permission_name, permission_type, action_type,
    built_in, status, resource_type, scope_type, menu_id
)
SELECT
    source.id, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
    source.permission_code, source.permission_name, 'MENU_ACTION',
    source.action_type, true, 1, 'MENU', 'ROLE',
    (SELECT id FROM t_menu WHERE menu_name = 'AI 智能排课' LIMIT 1)
FROM (
    VALUES
        ('5d4c0ca2-4c16-4f08-bf0b-7f3b0c90e101', 'education:scheduling:ai:use', '使用 AI 智能排课', 'USE'),
        ('5d4c0ca2-4c16-4f08-bf0b-7f3b0c90e102', 'education:scheduling:ai:confirm', '确认 AI 排课计划', 'APPROVE'),
        ('5d4c0ca2-4c16-4f08-bf0b-7f3b0c90e103', 'education:scheduling:ai:operations', '运维 AI 排课任务', 'MANAGE')
) AS source(id, permission_code, permission_name, action_type)
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission p
    WHERE p.permission_code = source.permission_code
);

INSERT INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM t_role r
JOIN t_permission p ON p.permission_code IN (
    'education:scheduling:ai:use',
    'education:scheduling:ai:confirm',
    'education:scheduling:ai:operations'
)
WHERE r.role_code IN ('ROLE_PLATFORM_ADMIN', 'SUPER_ADMIN', 'EDU_ADMIN')
ON CONFLICT DO NOTHING;

COMMIT;

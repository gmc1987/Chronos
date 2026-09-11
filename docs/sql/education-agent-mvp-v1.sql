-- 智慧校园 Scheduling Agent 与 AI 教务 Agent MVP。
-- Agent 建议必须经过人工确认，确认前不会写入正式排课约束。

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('education-agent-mvp-v1'));

CREATE TABLE IF NOT EXISTS edu_scheduling_agent_proposal (
    id varchar(64) PRIMARY KEY,
    semester_code varchar(32) NOT NULL,
    request_text text NOT NULL,
    teacher_id varchar(64) NOT NULL REFERENCES edu_teacher_profile(id),
    teacher_name varchar(128) NOT NULL,
    day_of_week integer NOT NULL,
    period_no integer NOT NULL,
    constraint_type varchar(24) NOT NULL,
    reason varchar(500),
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    confirmed_by varchar(128),
    confirmed_at timestamp,
    applied_constraint_id varchar(64),
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT ck_agent_proposal_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_agent_proposal_period CHECK (period_no > 0),
    CONSTRAINT ck_agent_proposal_type CHECK (constraint_type IN ('FORBIDDEN', 'PREFERRED')),
    CONSTRAINT ck_agent_proposal_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_agent_proposal_semester_status
    ON edu_scheduling_agent_proposal (semester_code, status, create_time DESC);

UPDATE t_menu
SET path = '/admin/education/agents',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE menu_name = '智能体能力配置';

WITH agent_menu AS (
    SELECT id
    FROM t_menu
    WHERE menu_name = '智能体能力配置'
    ORDER BY create_time
    LIMIT 1
), desired AS (
    SELECT *
    FROM (VALUES
        ('education:ai:agent:use', '使用教育智能体', 'EXECUTE'),
        ('education:ai:agent:confirm', '确认教育智能体建议', 'APPROVE')
    ) AS value(permission_code, permission_name, action_type)
)
INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time,
    permission_name, permission_code, permission_type, menu_id,
    action_type, resource_type, scope_type, status, built_in, description
)
SELECT
    gen_random_uuid()::text,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
    desired.permission_name, desired.permission_code, 'MENU_ACTION',
    agent_menu.id, desired.action_type, 'MENU', 'ROLE', 1, true,
    '教育行业模板智能体权限'
FROM desired
CROSS JOIN agent_menu
ON CONFLICT (permission_code) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    menu_id = EXCLUDED.menu_id,
    action_type = EXCLUDED.action_type,
    status = 1,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

INSERT INTO t_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role
CROSS JOIN t_menu menu
WHERE role.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND menu.menu_name = '智能体能力配置'
  AND NOT EXISTS (
      SELECT 1 FROM t_role_menu existing
      WHERE existing.role_id = role.id AND existing.menu_id = menu.id
  );

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND permission.permission_code IN (
      'education:ai:agent:use',
      'education:ai:agent:confirm'
  )
  AND NOT EXISTS (
      SELECT 1 FROM t_role_permission existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );

COMMIT;

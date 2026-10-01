-- Recurring meetings extend the existing edu_meeting rows; no parallel meeting
-- aggregate is introduced. The provider credential remains integration-owned.
ALTER TABLE edu_meeting
  ADD COLUMN IF NOT EXISTS recurrence_frequency varchar(16) NOT NULL DEFAULT 'NONE',
  ADD COLUMN IF NOT EXISTS recurrence_interval integer,
  ADD COLUMN IF NOT EXISTS recurrence_by_day varchar(64),
  ADD COLUMN IF NOT EXISTS recurrence_day_of_month integer,
  ADD COLUMN IF NOT EXISTS recurrence_until timestamp,
  ADD COLUMN IF NOT EXISTS recurrence_count integer,
  ADD COLUMN IF NOT EXISTS credential_ref varchar(256);

UPDATE edu_meeting
SET exception_type = 'NONE'
WHERE exception_type IS NULL;

ALTER TABLE edu_meeting
  ALTER COLUMN exception_type SET DEFAULT 'NONE',
  ALTER COLUMN exception_type SET NOT NULL;

ALTER TABLE edu_meeting_calendar_outbox
  ADD COLUMN IF NOT EXISTS occurrence_key varchar(80),
  ADD COLUMN IF NOT EXISTS processed_at timestamp,
  ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;

ALTER TABLE edu_meeting_calendar_binding
  ADD COLUMN IF NOT EXISTS record_version bigint NOT NULL DEFAULT 0;

UPDATE edu_meeting_calendar_outbox
SET occurrence_key = meeting_id
WHERE occurrence_key IS NULL;

ALTER TABLE edu_meeting_calendar_outbox
  ALTER COLUMN occurrence_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_meeting_series_occurrence_contract
  ON edu_meeting(series_id, occurrence_key)
  WHERE series_id IS NOT NULL AND occurrence_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edu_meeting_series_start
  ON edu_meeting(series_id, start_time);

INSERT INTO t_permission (
  id, create_by, create_time, permission_code, permission_name,
  permission_type, action_type, built_in, status, resource_type, scope_type, description
)
VALUES
  (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
   'education:meeting:series:view', '查看周期会议', 'MENU_ACTION', 'VIEW', true, 1,
   'EDUCATION_MEETING', 'ROLE', '查看周期会议实例与规则'),
  (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
   'education:meeting:series:manage', '管理周期会议', 'MENU_ACTION', 'MANAGE', true, 1,
   'EDUCATION_MEETING', 'ROLE', '创建、变更和取消周期会议'),
  (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
   'education:meeting:calendar:view', '查看日历绑定', 'MENU_ACTION', 'VIEW', true, 1,
   'EDUCATION_MEETING', 'ROLE', '查看外部日历接入状态'),
  (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
   'education:meeting:calendar:manage', '管理日历绑定', 'MENU_ACTION', 'MANAGE', true, 1,
   'EDUCATION_MEETING', 'ROLE', '管理外部日历绑定')
ON CONFLICT (permission_code) DO UPDATE SET
  permission_name = EXCLUDED.permission_name,
  status = 1;

INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('ROLE_PLATFORM_ADMIN', 'EDU_ADMIN', 'SUPER_ADMIN')
  AND permission.permission_code IN (
    'education:meeting:series:view',
    'education:meeting:series:manage',
    'education:meeting:calendar:view',
    'education:meeting:calendar:manage'
  )
ON CONFLICT DO NOTHING;

-- Existing menu seeds vary by deployment; only repair rows that are present.
UPDATE t_menu
SET path = '/admin/integrations',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE menu_name = '集成中心';

UPDATE t_menu
SET path = '/admin/education/meeting/recurring',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE menu_name = '周期会议';

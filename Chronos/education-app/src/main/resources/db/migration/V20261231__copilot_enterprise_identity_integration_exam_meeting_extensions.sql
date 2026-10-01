-- Copilot production extensions. Never edit an executed migration; all changes are additive.
ALTER TABLE int_connector
  ADD COLUMN IF NOT EXISTS provider_code varchar(64),
  ADD COLUMN IF NOT EXISTS direction varchar(16),
  ADD COLUMN IF NOT EXISTS allowed_host varchar(255),
  ADD COLUMN IF NOT EXISTS health_status varchar(24),
  ADD COLUMN IF NOT EXISTS last_checked_at timestamp;

ALTER TABLE int_sync_job
  ADD COLUMN IF NOT EXISTS direction varchar(16),
  ADD COLUMN IF NOT EXISTS batch_size integer NOT NULL DEFAULT 100,
  ADD COLUMN IF NOT EXISTS cursor_value text;

ALTER TABLE edu_meeting
  ADD COLUMN IF NOT EXISTS series_id varchar(64),
  ADD COLUMN IF NOT EXISTS occurrence_key varchar(80),
  ADD COLUMN IF NOT EXISTS exception_type varchar(24);

CREATE TABLE IF NOT EXISTS iam_identity_source (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  source_code varchar(64) NOT NULL UNIQUE,
  name varchar(128) NOT NULL,
  source_type varchar(24) NOT NULL,
  issuer_url varchar(1000),
  client_id varchar(256),
  secret_ref varchar(256),
  status varchar(24) NOT NULL DEFAULT 'DRAFT',
  config_json text NOT NULL DEFAULT '{}',
  last_test_at timestamp,
  CONSTRAINT ck_iam_identity_source_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED')),
  CONSTRAINT ck_iam_identity_source_type CHECK (source_type IN ('OIDC', 'LDAP', 'AD'))
);

CREATE TABLE IF NOT EXISTS iam_external_identity (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  source_id varchar(64) NOT NULL,
  external_subject varchar(256) NOT NULL,
  user_id varchar(64) NOT NULL,
  employee_id varchar(64),
  status varchar(24) NOT NULL DEFAULT 'ACTIVE',
  linked_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at timestamp,
  CONSTRAINT uk_iam_external_identity_subject UNIQUE (source_id, external_subject),
  CONSTRAINT ck_iam_external_identity_status CHECK (status IN ('ACTIVE', 'DISABLED', 'PENDING'))
);

CREATE TABLE IF NOT EXISTS iam_mfa_factor (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  user_id varchar(64) NOT NULL,
  factor_type varchar(24) NOT NULL,
  secret_ciphertext text NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'ENROLLED',
  enrolled_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  verified_at timestamp,
  CONSTRAINT uk_iam_mfa_factor_user_type UNIQUE (user_id, factor_type),
  CONSTRAINT ck_iam_mfa_factor_type CHECK (factor_type IN ('TOTP')),
  CONSTRAINT ck_iam_mfa_factor_status CHECK (status IN ('PENDING', 'ENROLLED', 'DISABLED'))
);

CREATE TABLE IF NOT EXISTS iam_identity_sync_conflict (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  source_id varchar(64) NOT NULL,
  external_subject varchar(256) NOT NULL,
  field_name varchar(128) NOT NULL,
  current_value_hash varchar(128) NOT NULL,
  incoming_value_hash varchar(128) NOT NULL,
  reason varchar(500) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'OPEN',
  decision_by varchar(128),
  decision_at timestamp,
  CONSTRAINT ck_iam_identity_sync_conflict_status CHECK (status IN ('OPEN', 'RESOLVED', 'IGNORED'))
);

CREATE TABLE IF NOT EXISTS iam_access_review (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  scope varchar(256) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'DRAFT',
  due_at timestamp NOT NULL,
  owner varchar(128) NOT NULL,
  submitted_at timestamp,
  completed_at timestamp,
  CONSTRAINT ck_iam_access_review_status CHECK (status IN ('DRAFT', 'OPEN', 'SUBMITTED', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS iam_access_review_item (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  review_id varchar(64) NOT NULL REFERENCES iam_access_review(id) ON DELETE CASCADE,
  user_id varchar(64) NOT NULL,
  role_id varchar(64),
  permission_code varchar(256) NOT NULL,
  decision varchar(24),
  decided_by varchar(128),
  decided_at timestamp,
  CONSTRAINT uk_iam_access_review_item UNIQUE (review_id, user_id, role_id, permission_code),
  CONSTRAINT ck_iam_access_review_item_decision CHECK (decision IS NULL OR decision IN ('CONFIRM', 'REVOKE', 'REJECT'))
);

CREATE TABLE IF NOT EXISTS iam_temporary_grant (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  user_id varchar(64) NOT NULL,
  permission_code varchar(256) NOT NULL,
  reason varchar(1000) NOT NULL,
  approved_by varchar(128),
  valid_from timestamp NOT NULL,
  valid_until timestamp NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'REQUESTED',
  revoked_at timestamp,
  CONSTRAINT ck_iam_temporary_grant_status CHECK (status IN ('REQUESTED', 'APPROVED', 'ACTIVE', 'EXPIRED', 'REVOKED')),
  CONSTRAINT ck_iam_temporary_grant_window CHECK (valid_until > valid_from)
);

CREATE TABLE IF NOT EXISTS int_field_mapping (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  connector_id varchar(64) NOT NULL,
  object_type varchar(64) NOT NULL,
  source_path varchar(256) NOT NULL,
  target_field varchar(128) NOT NULL,
  transform_code varchar(64),
  required boolean NOT NULL DEFAULT false,
  version_no integer NOT NULL,
  CONSTRAINT uk_int_field_mapping_version UNIQUE (connector_id, object_type, target_field, version_no)
);

CREATE TABLE IF NOT EXISTS int_sync_cursor (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  job_id varchar(64) NOT NULL UNIQUE,
  cursor_value text,
  last_success_at timestamp,
  version_no bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS int_sync_reconciliation (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  run_id varchar(64) NOT NULL,
  object_type varchar(64) NOT NULL,
  source_count bigint NOT NULL DEFAULT 0,
  accepted_count bigint NOT NULL DEFAULT 0,
  rejected_count bigint NOT NULL DEFAULT 0,
  missing_count bigint NOT NULL DEFAULT 0,
  status varchar(24) NOT NULL DEFAULT 'PENDING',
  CONSTRAINT ck_int_sync_reconciliation_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS edu_exam_registration (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  plan_id varchar(64) NOT NULL,
  session_id varchar(64) NOT NULL,
  student_id varchar(64) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'SUBMITTED',
  source varchar(24) NOT NULL DEFAULT 'STUDENT',
  submitted_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at timestamp,
  reviewed_by varchar(128),
  reason varchar(1000),
  row_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT uk_edu_exam_registration UNIQUE (session_id, student_id),
  CONSTRAINT ck_edu_exam_registration_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

CREATE TABLE IF NOT EXISTS edu_exam_accommodation (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  registration_id varchar(64) NOT NULL REFERENCES edu_exam_registration(id) ON DELETE CASCADE,
  type_code varchar(64) NOT NULL,
  extra_minutes integer NOT NULL DEFAULT 0,
  room_requirement_json text,
  file_id varchar(64),
  status varchar(24) NOT NULL DEFAULT 'SUBMITTED',
  decided_by varchar(128),
  decided_at timestamp,
  CONSTRAINT ck_edu_exam_accommodation_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS edu_exam_admission_ticket (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  candidate_id varchar(64) NOT NULL,
  published_version integer NOT NULL,
  ticket_no varchar(64) NOT NULL,
  seat_snapshot_json text NOT NULL,
  issued_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at timestamp,
  status varchar(24) NOT NULL DEFAULT 'ISSUED',
  CONSTRAINT uk_edu_exam_admission_ticket UNIQUE (candidate_id, published_version),
  CONSTRAINT ck_edu_exam_admission_ticket_status CHECK (status IN ('ISSUED', 'REVOKED'))
);

CREATE TABLE IF NOT EXISTS edu_exam_material_ledger (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  session_id varchar(64) NOT NULL,
  material_type varchar(64) NOT NULL,
  batch_no varchar(128) NOT NULL,
  planned_quantity integer NOT NULL,
  received_quantity integer NOT NULL DEFAULT 0,
  seal_no varchar(128),
  status varchar(24) NOT NULL DEFAULT 'OPEN',
  difference_reason varchar(1000),
  CONSTRAINT ck_edu_exam_material_ledger_status CHECK (status IN ('OPEN', 'COMPLETE', 'DISCREPANCY'))
);

CREATE TABLE IF NOT EXISTS edu_exam_material_handover (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  ledger_id varchar(64) NOT NULL REFERENCES edu_exam_material_ledger(id) ON DELETE CASCADE,
  handover_by varchar(128) NOT NULL,
  received_by varchar(128) NOT NULL,
  handed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  quantity integer NOT NULL,
  difference_reason varchar(1000),
  CONSTRAINT ck_edu_exam_material_handover_distinct CHECK (handover_by <> received_by)
);

CREATE TABLE IF NOT EXISTS edu_exam_incident (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  session_id varchar(64) NOT NULL,
  room_id varchar(64),
  candidate_id varchar(64),
  incident_type varchar(64) NOT NULL,
  severity varchar(24) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'REPORTED',
  description text NOT NULL,
  reported_by varchar(128) NOT NULL,
  reported_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at timestamp,
  conclusion text,
  CONSTRAINT ck_edu_exam_incident_status CHECK (status IN ('REPORTED', 'UNDER_REVIEW', 'DISPOSED', 'CLOSED')),
  CONSTRAINT ck_edu_exam_incident_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE IF NOT EXISTS edu_exam_incident_action (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  incident_id varchar(64) NOT NULL REFERENCES edu_exam_incident(id) ON DELETE CASCADE,
  action_type varchar(64) NOT NULL,
  action_by varchar(128) NOT NULL,
  action_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  conclusion text NOT NULL,
  file_id varchar(64)
);

CREATE TABLE IF NOT EXISTS edu_meeting_series (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  organizer_username varchar(128) NOT NULL,
  title varchar(200) NOT NULL,
  timezone varchar(64) NOT NULL,
  recurrence_rule varchar(500) NOT NULL,
  start_at timestamp NOT NULL,
  end_at timestamp NOT NULL,
  until_at timestamp,
  occurrence_limit integer,
  status varchar(24) NOT NULL DEFAULT 'DRAFT',
  version_no bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_edu_meeting_series_end_condition CHECK (until_at IS NOT NULL OR occurrence_limit IS NOT NULL),
  CONSTRAINT ck_edu_meeting_series_occurrence_limit CHECK (occurrence_limit IS NULL OR occurrence_limit BETWEEN 1 AND 500),
  CONSTRAINT ck_edu_meeting_series_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_meeting_series_occurrence
  ON edu_meeting(series_id, occurrence_key)
  WHERE series_id IS NOT NULL AND occurrence_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS edu_meeting_calendar_binding (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  username varchar(128) NOT NULL,
  provider_code varchar(64) NOT NULL,
  external_calendar_id varchar(256) NOT NULL,
  credential_ref varchar(256),
  status varchar(24) NOT NULL DEFAULT 'ACTIVE',
  last_sync_at timestamp,
  CONSTRAINT uk_edu_meeting_calendar_binding UNIQUE (username, provider_code, external_calendar_id),
  CONSTRAINT ck_edu_meeting_calendar_binding_status CHECK (status IN ('ACTIVE', 'REVOKED', 'ERROR'))
);

CREATE TABLE IF NOT EXISTS edu_meeting_calendar_outbox (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  meeting_id varchar(64) NOT NULL,
  participant_username varchar(128) NOT NULL,
  event_type varchar(24) NOT NULL,
  payload_version integer NOT NULL DEFAULT 1,
  idempotency_key varchar(160) NOT NULL UNIQUE,
  status varchar(24) NOT NULL DEFAULT 'PENDING',
  attempt_count integer NOT NULL DEFAULT 0,
  next_attempt_at timestamp,
  last_error text,
  CONSTRAINT ck_edu_meeting_calendar_outbox_status CHECK (status IN ('PENDING', 'SENT', 'DEAD', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_iam_external_identity_user ON iam_external_identity(user_id);
CREATE INDEX IF NOT EXISTS idx_iam_identity_conflict_status ON iam_identity_sync_conflict(status, source_id);
CREATE INDEX IF NOT EXISTS idx_iam_temporary_grant_expiry ON iam_temporary_grant(status, valid_until);
CREATE INDEX IF NOT EXISTS idx_edu_exam_registration_session ON edu_exam_registration(session_id, status);
CREATE INDEX IF NOT EXISTS idx_edu_exam_incident_session ON edu_exam_incident(session_id, status);
CREATE INDEX IF NOT EXISTS idx_edu_meeting_series_organizer ON edu_meeting_series(organizer_username, status);
CREATE INDEX IF NOT EXISTS idx_edu_meeting_calendar_outbox_status ON edu_meeting_calendar_outbox(status, next_attempt_at);

DO $$
DECLARE
  item record;
BEGIN
  FOR item IN
    SELECT * FROM (VALUES
      ('iam:identity:source:view', '查看身份源'),
      ('iam:identity:source:manage', '管理身份源'),
      ('iam:identity:source:test', '测试身份源'),
      ('iam:identity:external:view', '查看外部身份'),
      ('iam:identity:external:manage', '绑定外部身份'),
      ('iam:identity:conflict:view', '查看同步冲突'),
      ('iam:identity:conflict:decide', '处理同步冲突'),
      ('iam:access-review:view', '查看授权复核'),
      ('iam:access-review:manage', '管理授权复核'),
      ('iam:temporary-grant:view', '查看临时授权'),
      ('iam:temporary-grant:manage', '管理临时授权'),
      ('integration:mapping:view', '查看数据映射'),
      ('integration:mapping:manage', '管理数据映射'),
      ('integration:dry-run', '执行同步预览'),
      ('integration:reconcile', '执行同步对账'),
      ('education:exam:registration:view', '查看考试报名'),
      ('education:exam:registration:manage', '管理考试报名'),
      ('education:exam:accommodation:manage', '管理考试特殊安排'),
      ('education:exam:ticket:view', '查看准考证'),
      ('education:exam:ticket:manage', '生成准考证'),
      ('education:exam:material:manage', '管理考务物资'),
      ('education:exam:incident:view', '查看考试异常'),
      ('education:exam:incident:manage', '处理考试异常'),
      ('education:meeting:series:view', '查看周期会议'),
      ('education:meeting:series:manage', '管理周期会议'),
      ('education:meeting:calendar:view', '查看日历绑定'),
      ('education:meeting:calendar:manage', '管理日历绑定')
    ) AS values(permission_code, permission_name)
  LOOP
    INSERT INTO t_permission
      (id, create_by, create_time, permission_name, permission_code, permission_type,
       action_type, resource_type, scope_type, status, built_in, description)
    VALUES
      (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, item.permission_name,
       item.permission_code, 'MENU_ACTION', 'MANAGE', 'MENU', 'ROLE', 1, true,
       item.permission_name)
    ON CONFLICT (permission_code) DO NOTHING;
  END LOOP;
END $$;
